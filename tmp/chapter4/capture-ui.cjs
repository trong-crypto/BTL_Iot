const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require(process.env.IOT_PLAYWRIGHT_MODULE);

const root = path.resolve(__dirname, '../../Front_end');
const mime = { '.html': 'text/html; charset=utf-8', '.js': 'application/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.png': 'image/png' };
const server = http.createServer((req, res) => {
  const pathname = decodeURIComponent(new URL(req.url, 'http://localhost').pathname);
  const filename = path.resolve(root, '.' + pathname);
  if (!filename.startsWith(root + path.sep)) { res.writeHead(403).end(); return; }
  fs.readFile(filename, (error, bytes) => {
    if (error) { res.writeHead(404).end(); return; }
    res.writeHead(200, { 'Content-Type': mime[path.extname(filename)] || 'application/octet-stream' }).end(bytes);
  });
});

function pageResult(content, totalElements = content.length) {
  return { content, totalElements, totalPages: Math.ceil(totalElements / 10), number: 0, size: 10 };
}

const sensorContent = [
  ['Light', 'light', 1820, 'ADC'], ['Humidity', 'humidity', 57, '%'], ['Temperature', 'temperature', 29.4, '°C'],
  ['Light', 'light', 1785, 'ADC'], ['Humidity', 'humidity', 57, '%'], ['Temperature', 'temperature', 29.3, '°C'],
  ['Light', 'light', 1761, 'ADC'], ['Humidity', 'humidity', 58, '%'], ['Temperature', 'temperature', 29.3, '°C'],
  ['Light', 'light', 1742, 'ADC']
].map(([name, sensorKey, value, unit], i) => ({ id: 120 - i, name, sensorKey, value, unit,
  createdAt: `2026-11-04T14:39:${String(33 - 2 * Math.floor(i / 3)).padStart(2, '0')}` }));

const historyContent = [
  ['Đèn 1', 'lamp1', 'ON', 'SUCCESS'], ['Đèn 2', 'lamp2', 'ON', 'SUCCESS'],
  ['Đèn 1', 'lamp1', 'OFF', 'SUCCESS'], ['Đèn 2', 'lamp2', 'OFF', 'SUCCESS'],
  ['Đèn 1', 'lamp1', 'ON', 'TIMEOUT'], ['Đèn 1', 'lamp1', 'ON', 'SUCCESS']
].map(([deviceName, deviceKey, action, status], i) => ({ id: 45 - i, username: 'admin',
  deviceName, deviceKey, action, status, createdAt: `2026-11-04T14:${String(39 - i).padStart(2, '0')}:33` }));

server.listen(0, '127.0.0.1', async () => {
  const origin = `http://127.0.0.1:${server.address().port}`;
  let browser;
  try {
    browser = await chromium.launch({ headless: true, channel: 'msedge' });
    const context = await browser.newContext({ viewport: { width: 1500, height: 870 }, deviceScaleFactor: 1 });
    await context.route('**/*', async route => {
      const url = new URL(route.request().url());
      if (url.origin !== origin) { await route.abort(); return; }
      if (url.pathname === '/api/sensor-data/history') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(pageResult(sensorContent, 22)) });
      } else if (url.pathname === '/api/devices/action-history') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(pageResult(historyContent, 12)) });
      } else await route.continue();
    });
    const page = await context.newPage();
    await page.goto(origin + '/login.html', { waitUntil: 'networkidle' });
    await page.screenshot({ path: path.join(__dirname, 'ui-login.png') });
    await page.addInitScript(() => sessionStorage.setItem('iot.auth', JSON.stringify({ accessToken: 'illustrative-ui-only', expiresAt: '2099-01-01T00:00:00Z' })));
    for (const [url, wait, file] of [
      ['/sensor-data.html', '#sensorTableBody tr', 'ui-sensor.png'],
      ['/history.html', '#historyTableBody tr', 'ui-history.png'],
      ['/profile.html', '.profile-avatar', 'ui-profile.png']
    ]) {
      await page.goto(origin + url, { waitUntil: 'networkidle' });
      await page.locator(wait).first().waitFor();
      await page.screenshot({ path: path.join(__dirname, file) });
      console.log(file);
    }
    await context.close();
  } catch (error) {
    console.error(error);
    process.exitCode = 1;
  } finally {
    if (browser) await browser.close();
    server.close();
  }
});
