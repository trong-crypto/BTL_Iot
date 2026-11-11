const { chromium } = require(process.env.IOT_PLAYWRIGHT_MODULE || 'playwright');
const assert = require('node:assert/strict');

(async () => {
    const browser = await chromium.launch({ headless: true, channel: 'msedge' });
    try {
        const context = await browser.newContext();
        const origin = process.argv[2];
        await context.route('**/*', route => new URL(route.request().url()).origin === origin
            ? route.continue() : route.abort());
        const page = await context.newPage();
        const errors = [];
        const frames = [];
        page.on('pageerror', error => errors.push(error.message));
        page.on('websocket', socket => socket.on('framesent', event => frames.push(String(event.payload))));
        await page.goto(origin + '/profile.html');
        await page.waitForURL('**/login.html');
        await page.locator('#username').fill('admin');
        await page.locator('#password').fill('wrong-password');
        await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
        await page.getByRole('alert').filter({ hasText: 'Tên đăng nhập hoặc mật khẩu không đúng' }).waitFor();
        await page.locator('#password').fill('test-admin-password');
        const devices = page.waitForResponse(r => r.url().endsWith('/api/devices') && r.status() === 200);
        await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
        await page.waitForURL('**/index.html');
        const response = await devices;
        assert.ok(response.request().headers().authorization.startsWith('Bearer '));
        assert.ok(frames.some(frame => frame.includes('CONNECT\n') && frame.includes('Authorization:Bearer ')));
        assert.equal((await context.cookies()).some(cookie => cookie.name === 'JSESSIONID'), false);
        await page.goto(origin + '/sensor-data.html');
        await page.locator('#sensorType').selectOption('time');
        await page.locator('#sensorSearchValue').fill('2026-11-04 14:39:33');
        const filtered = page.waitForResponse(r => new URL(r.url()).searchParams.get('from') === '2026-11-04T14:39:33');
        await page.getByRole('button', { name: 'Tìm kiếm', exact: true }).click();
        assert.equal((await filtered).status(), 200);
        await page.reload();
        assert.ok(await page.evaluate(() => !!JSON.parse(sessionStorage.getItem('iot.auth'))?.accessToken));
        await page.goto(origin + '/profile.html');
        await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click();
        await page.waitForURL('**/login.html');
        assert.equal(await page.evaluate(() => sessionStorage.getItem('iot.auth')), null);
        await page.goto(origin + '/history.html');
        await page.waitForURL('**/login.html');
        // Guard redirects deliberately abort page initialization with a login-required error.
        assert.ok(errors.every(message => message.includes('đăng nhập')));
        console.log('Browser JWT: login errors, REST Bearer, STOMP CONNECT, no session cookie, time filter, reload, logout and protected-page guard OK');
    } finally { await browser.close(); }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
