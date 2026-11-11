# Xác thực JWT

## Luồng sử dụng

1. `POST /api/auth/login` với JSON username/password (No Auth).
2. Lấy `accessToken` từ phản hồi. `tokenType` là `Bearer`, `expiresIn` là số giây hiệu lực, `expiresAt` là thời điểm hết hạn theo UTC.
3. REST gửi `Authorization: Bearer <accessToken>`. STOMP CONNECT gửi cùng header trong frame CONNECT; không truyền token trong URL.
4. Hết hạn token thì đăng nhập lại. Hiện không có refresh token hoặc danh sách thu hồi token.

Backend dùng Spring Security Resource Server và Nimbus JWT, ký HS256; kiểm tra chữ ký, thuật toán, issuer, audience `iot-web`, subject, thời gian phát hành và hết hạn. Không dùng cookie phiên, HTTP Basic hoặc form `/login`. `/api/csrf` đã bỏ; REST không cần CSRF vì xác thực chỉ bằng Bearer header. API dữ liệu và kênh STOMP cần JWT, còn HTML/CSS/JS là tài nguyên công khai.

WebSocket chấp nhận handshake cùng origin, xác thực tại STOMP CONNECT. Không CONNECT trong 10 giây thì đóng kết nối. Kết nối đã xác thực tự đóng khi JWT hết hạn; chỉ được SUBSCRIBE bốn destination đã định nghĩa, không được SEND vào topic để giả dữ liệu.

## Cấu hình backend

| Biến môi trường | Mặc định | Ý nghĩa |
|---|---|---|
| IOT_JWT_SECRET | Trống | Khóa ký Base64, tối thiểu 32 byte ngẫu nhiên sau giải mã |
| IOT_JWT_ISSUER | iot-command-center | Issuer được phát hành và kiểm tra |
| IOT_JWT_TTL_SECONDS | 3600 | Thời hạn 60–86400 giây |

Không cấu hình secret: backend sinh khóa ngẫu nhiên trong bộ nhớ và ghi cảnh báo; mỗi lần khởi động lại cần đăng nhập lại vì token cũ không còn hợp lệ. Không có khóa mặc định dùng chung. Để giữ khóa ổn định khi khởi động lại hoặc chạy nhiều instance, cấu hình `IOT_JWT_SECRET` bằng khóa ngẫu nhiên riêng. Khóa sai Base64 hoặc quá ngắn khiến backend dừng với thông báo cấu hình.

Ví dụ tự tạo khóa trong PowerShell, lưu vào biến môi trường người dùng mà không in giá trị:

```powershell
$jwtKeyBytes = New-Object byte[] 32
$jwtRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try { $jwtRng.GetBytes($jwtKeyBytes) } finally { $jwtRng.Dispose() }
[Environment]::SetEnvironmentVariable('IOT_JWT_SECRET', [Convert]::ToBase64String($jwtKeyBytes), 'User')
$jwtKeyBytes = $null
```

`run-local.ps1` đọc các biến JWT từ Process/User/Machine. Nếu chạy bằng IntelliJ, mở lại IDE hoặc đưa các biến vào Run Configuration để tiến trình mới nhận được cấu hình. Khóa JWT khác mật khẩu admin, MySQL và MQTT. Không đưa khóa/token thật vào mã nguồn, ảnh chụp, collection export hoặc tài liệu.

## Giao diện và đăng xuất

Giao diện lưu token trong `sessionStorage` theo từng tab để tải lại trang và chuyển trang vẫn giữ đăng nhập trong tab. Client tự gắn Bearer cho REST và CONNECT, tự chuyển về đăng nhập khi hết hạn/nhận 401. Nút Đăng xuất xóa token và ngắt WebSocket trong tab hiện tại. Token vẫn là thông tin đăng nhập có thể dùng tới khi hết hạn nếu đã bị sao chép; đăng xuất client không thu hồi token đã cấp trên server. Trang bảo vệ kiểm tra token trước khi tải dữ liệu; server vẫn là nơi xác minh JWT.

## Postman và script

Import lại `IoT.postman_collection.json` (collection **IoT - JWT Bearer**). Điền username/password trong Variables, gửi Đăng nhập; script lưu `access_token` và `expires_at`. Các API khác dùng **Inherit auth from parent**. Nếu tự tạo request, chọn Bearer Token và nhập `{{access_token}}`. Không thêm CSRF; xóa Authorization Basic cũ. Token hết hạn trả 401, gửi đăng nhập lại. Không export mật khẩu/token thật.

`test-lamp.ps1` hỏi mật khẩu, gọi API đăng nhập để nhận JWT, rồi điều khiển theo Bearer và theo dõi requestId. Không thay đổi firmware hoặc cấu trúc database.

## Kiểm thử và triển khai

Kiểm thử backend dùng H2 và gateway MQTT giả lập, kiểm tra login, JWT thiếu/sai/hết hạn, chữ ký/issuer/thời gian, REST, STOMP, quyền gửi message và tự đóng socket khi token hết hạn. Kiểm thử trình duyệt tùy chọn dùng `IOT_PLAYWRIGHT_MODULE` trỏ tới module Playwright, chạy Edge headless trên backend thử nghiệm. Các kiểm thử này không điều khiển đèn thật.

Mặc định backend chạy trên loopback HTTP để thử tại laptop. Khi phục vụ qua mạng bên ngoài, dùng HTTPS/WSS để bảo vệ mật khẩu và Bearer Token trên đường truyền.

Tham khảo: [Spring Security JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html), [STOMP token authentication](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/authentication-token-based.html), [WebSocket authorization](https://docs.spring.io/spring-security/reference/servlet/integrations/websocket.html).
