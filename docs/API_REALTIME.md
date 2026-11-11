# API và cập nhật thời gian thực theo báo cáo

Đối chiếu mã nguồn ngày 04/11/2026. Các ID, thời gian và số đo trong ví dụ là dữ liệu minh họa, không phải dữ liệu đọc từ phần cứng.

Giao diện và API được phục vụ bởi cùng backend tại `http://127.0.0.1:8080`. Hệ thống dùng JWT Bearer, không dùng phiên HTTP hoặc cookie để xác thực. Đăng nhập bằng JSON username/password; REST và STOMP CONNECT gửi `Authorization: Bearer <accessToken>`. Không cần CSRF. Chi tiết cấu hình khóa và thời hạn: `JWT_AUTH.md` cùng thư mục.

| Method | Đường dẫn | Dữ liệu chính |
|---|---|---|
| POST | `/api/auth/login` | JSON `{ "username": "...", "password": "..." }`; trả `accessToken`, `tokenType`, `expiresIn`, `expiresAt`, `id`, `username`, `full_name` |
| GET | `/api/devices` | Mảng thiết bị: `id`, `deviceKey`, `name`, `status`, `statusConfirmedAt`, `online` |
| GET | `/api/sensor-data/latest` | `{ "temperature": 28.5, "humidity": 70.2, "light": 1530, "time": "..." }`; trường chưa có số đo là null |
| GET | `/api/sensor-data/chart?limit=20` | Mỗi cảm biến là mảng `{ "time": "...", "value": ... }`, cũ đến mới; limit 1–100 điểm mỗi cảm biến |
| GET | `/api/sensor-data/history` | `sensorId`, `from`, `to`, `page`, `size`, `sort=createdAt,desc`; phản hồi có `content`, `totalElements`, `totalPages`, `number`, `size` |
| GET | `/api/devices/{deviceId}/status` | `deviceId`, `status`, `deviceKey`, `name`, `statusConfirmedAt`, `online` |
| POST | `/api/devices/{deviceId}/control` | JSON `{ "deviceId": 1, "action": "ON" }`; trả HTTP 202 với `id`, `status: PENDING`, `timeoutSeconds: 5`, cùng `requestId` để ghép kết quả |
| GET | `/api/commands/{requestId}` | Kết quả lệnh của tài khoản đang đăng nhập: `id`, `requestId`, `deviceKey`, `action`, `status`, `createdAt` |
| GET | `/api/devices/action-history` | `deviceId`, `action`, `status`, `from`, `to`, `page`, `size`, `sort=createdAt,desc`; phản hồi phân trang như trên |

`page` bắt đầu từ 0, `size` từ 1–100. `from/to` dùng ISO ngày giờ, ví dụ `2026-11-04T10:00:00`. Bản ghi đúng biên được bao gồm. Trạng thái lệnh gồm `PENDING`, `SUCCESS`, `FAILED`, `TIMEOUT`. Ánh sáng là ADC 0–4095. Lịch sử cảm biến có thêm `sensorKey`, `sensorName`, `unit`; có thể lọc theo `sensorKey` và `value`. Lịch sử lệnh có thêm `deviceKey`, `username`; có thể lọc theo `deviceKey`.

Mặc định `page=0`, `size=20`, thứ tự giảm dần theo `createdAt`, sau đó `id`. `limit` biểu đồ mặc định 20. Các bộ lọc đều tùy chọn. `action` chỉ nhận `ON/OFF`; `status` lệnh chỉ nhận bốn giá trị viết hoa ở trên. `deviceId` trong body điều khiển là tùy chọn; nếu gửi thì phải trùng ID trên đường dẫn. ID được lấy từ database, không mặc định lamp1 luôn có ID 1.

## Đăng nhập JWT và mã HTTP

1. Gửi `POST /api/auth/login` với `Content-Type: application/json` và body `{"username":"admin","password":"mật khẩu của bạn"}`. Chọn No Auth cho yêu cầu này.
2. Thành công nhận `accessToken` (JWT), `tokenType: Bearer`, `expiresIn` (giây), `expiresAt` (ISO UTC), cùng thông tin tài khoản. Gửi `Authorization: Bearer <accessToken>` cho các API còn lại.
3. Hết hạn token, đăng nhập lại. Giao diện lưu token trong `sessionStorage` theo từng tab, tự gắn Bearer, chuyển về đăng nhập khi hết hạn hoặc nhận 401. Nút Đăng xuất xóa token trong tab và ngắt WebSocket.

### Thử nhanh với Postman

Import lại `IoT.postman_collection.json`, tên collection mới **IoT - JWT Bearer**. Điền `username`, `password` trong Variables trên máy của bạn, giữ `base_url=http://127.0.0.1:8080`. Gửi **Đăng nhập**; script tự lưu `access_token` và `expires_at`. Các yêu cầu khác chọn **Inherit auth from parent** để dùng Bearer của collection. Không cần cookie hoặc CSRF.

Gửi **Danh sách thiết bị** để lưu `device_id` theo `device_key` (mặc định lamp1), rồi mới gửi lệnh nếu muốn điều khiển thật. Collection chỉ chứa chỗ trống cho mật khẩu và token; không chia sẻ bản export chứa giá trị thật. Hết hạn token thì gửi Đăng nhập lại. Không chạy cả collection nếu chỉ muốn đọc dữ liệu vì các request bật/tắt có tác động thật.

Chỉ API đăng nhập là công khai. Các API khác cần JWT hợp lệ; HTTP Basic và form `/login` đã ngừng hỗ trợ. `/api/csrf` đã được loại bỏ. HTML/CSS/JS là tài nguyên tĩnh công khai; dữ liệu được bảo vệ tại API và STOMP. `full_name`, số đo chưa có dữ liệu và `statusConfirmedAt` có thể null.

| HTTP | Ý nghĩa |
|---|---|
| 200 | Đọc dữ liệu hoặc đăng nhập thành công |
| 202 | Backend đã tạo lệnh PENDING; cần chờ kết quả qua STOMP hoặc `/api/commands/{requestId}` |
| 400 | JSON, enum, tham số hoặc khoảng thời gian không hợp lệ; ID body không khớp path |
| 401 | Thiếu/sai/hết hạn JWT; hoặc tài khoản/mật khẩu đăng nhập sai |
| 403 | Yêu cầu không có quyền truy cập |
| 404 | Không tìm thấy thiết bị; không tìm thấy lệnh thuộc tài khoản hiện tại |
| 409 | ESP32/broker chưa sẵn sàng, chưa xác nhận trạng thái, đang có lệnh chờ hoặc publish MQTT thất bại |

Danh sách không có kết quả trả HTTP 200 với mảng rỗng; không phải 404. `SUCCESS` xác nhận GPIO, không phát hiện LED hỏng. Các lỗi hiện dùng cơ chế lỗi mặc định của Spring, chưa có DTO lỗi riêng.

## Chi tiết 9 API chính

### 1. Đăng nhập — POST `/api/auth/login`

Công khai; không cần CSRF. Header `Content-Type: application/json`. Trong Postman chọn **No Auth**.

Body:

```json
{
  "username": "admin",
  "password": "MAT_KHAU_CUA_BAN"
}
```

Phản hồi HTTP 200:

```json
{
  "id": 1,
  "username": "admin",
  "full_name": "Quản trị viên",
  "accessToken": "JWT_ACCESS_TOKEN",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "expiresAt": "2026-11-04T04:00:00Z"
}
```

`username` và `password` bắt buộc, không được để trống. Thiếu dữ liệu trả 400; sai tài khoản/mật khẩu trả 401; Content-Type không hỗ trợ trả 415. Phản hồi có `Cache-Control: no-store`. Dùng `accessToken` cho Bearer; `expiresIn` là số giây hiệu lực (mặc định 3600). Không có refresh token; hết hạn thì đăng nhập lại. Backend không tạo phiên đăng nhập HTTP.

Tài khoản lấy từ bảng `users`. `IOT_ADMIN_PASSWORD` chỉ dùng tạo tài khoản ban đầu nếu chưa tồn tại; thay biến môi trường không tự đổi mật khẩu tài khoản đã có trong database.

### 2. Danh sách thiết bị — GET `/api/devices`

Cần đăng nhập. Không có body hoặc tham số. Phản hồi HTTP 200:

```json
[
  {
    "id": 1,
    "deviceKey": "lamp1",
    "name": "Đèn 1",
    "status": "OFF",
    "statusConfirmedAt": "2026-11-04T10:00:00",
    "online": true
  },
  {
    "id": 2,
    "deviceKey": "lamp2",
    "name": "Đèn 2",
    "status": "ON",
    "statusConfirmedAt": "2026-11-04T10:00:02",
    "online": true
  }
]
```

Lấy `id` thực tế từ kết quả để dùng trong các API theo `deviceId`. `deviceKey` là `lamp1/lamp2`; `device01` là mã ESP32 trong MQTT, không phải ID của đèn. Hai đèn dùng chung trạng thái kết nối ESP32. Khi offline, `status` vẫn là trạng thái được lưu gần nhất; không đủ để khẳng định trạng thái vật lý hiện tại. `statusConfirmedAt` có thể null.

### 3. Trạng thái thiết bị — GET `/api/devices/{deviceId}/status`

Cần đăng nhập. `deviceId` là số nguyên lấy từ danh sách thiết bị. Ví dụ `/api/devices/1/status`. Phản hồi HTTP 200:

```json
{
  "deviceId": 1,
  "deviceKey": "lamp1",
  "name": "Đèn 1",
  "status": "OFF",
  "statusConfirmedAt": "2026-11-04T10:00:00",
  "online": true
}
```

ID không tồn tại trả 404. Trạng thái thiết bị chỉ có `ON/OFF`, khác trạng thái xử lý lệnh.

### 4. Điều khiển — POST `/api/devices/{deviceId}/control`

Cần JWT Bearer. Header `Content-Type: application/json`. Ví dụ `/api/devices/1/control`.

Body tối thiểu:

```json
{ "action": "ON" }
```

Để tắt, dùng `"OFF"`. Có thể thêm `"deviceId": 1`; nếu gửi, phải trùng ID trên URL.

Phản hồi HTTP 202:

```json
{
  "id": 15,
  "requestId": "da18aacc-3e28-4d83-bfac-4cce0c7965aa",
  "deviceId": 1,
  "deviceKey": "lamp1",
  "action": "ON",
  "status": "PENDING",
  "timeoutSeconds": 5
}
```

`id` là ID bản ghi lịch sử; `requestId` là mã ghép lệnh với ACK của ESP32. HTTP 202 xác nhận backend đã nhận lệnh, chưa xác nhận thực thi thành công. Nhận kết quả qua `/user/queue/commands` hoặc API số 5.

Thiếu/sai `action` hoặc ID body không khớp trả 400. Thiếu/sai/hết hạn JWT trả 401. Không có thiết bị trả 404. ESP32/broker chưa sẵn sàng, chưa xác nhận trạng thái, đèn đang có lệnh chờ hoặc gửi MQTT thất bại trả 409.

### 5. Kết quả lệnh — GET `/api/commands/{requestId}`

Cần đăng nhập. Chỉ đọc được lệnh của tài khoản hiện tại. Phản hồi HTTP 200:

```json
{
  "id": 15,
  "requestId": "da18aacc-3e28-4d83-bfac-4cce0c7965aa",
  "deviceKey": "lamp1",
  "action": "ON",
  "status": "SUCCESS",
  "createdAt": "2026-11-04T10:00:05"
}
```

Không tìm thấy hoặc lệnh thuộc tài khoản khác trả 404. `createdAt` là thời gian tạo bản ghi lệnh, không phải thời gian ACK.

| Trạng thái lệnh | Ý nghĩa |
|---|---|
| PENDING | Đang chờ phản hồi ESP32 |
| SUCCESS | ESP32 xác nhận thực thi lệnh ở mức GPIO; không phát hiện LED cháy |
| FAILED | Có lỗi gửi/thực thi lệnh được hệ thống ghi nhận |
| TIMEOUT | Không nhận được phản hồi hợp lệ trong thời hạn chờ |

`TIMEOUT` không chứng minh đèn đã tắt hoặc chưa thực thi; cần xem trạng thái được ESP32 xác nhận riêng.

### 6. Số đo mới nhất — GET `/api/sensor-data/latest`

Cần đăng nhập. Không có tham số. Phản hồi HTTP 200:

```json
{
  "temperature": 29.8,
  "humidity": 76.0,
  "light": 2350.0,
  "time": "2026-11-04T10:00:10"
}
```

Nhiệt độ theo °C, độ ẩm theo %, ánh sáng theo ADC 0–4095, không phải lux. Backend lấy bản ghi mới nhất của từng cảm biến; `time` là thời gian mới nhất trong các bản ghi được chọn. Trường chưa có dữ liệu là null. Kết quả là dữ liệu lưu gần nhất, không bảo đảm ESP32 hiện đang online.

### 7. Dữ liệu biểu đồ — GET `/api/sensor-data/chart`

Cần đăng nhập. Tham số `limit` là số điểm tối đa **mỗi cảm biến**, mặc định 20, hợp lệ từ 1 đến 100. Ví dụ `/api/sensor-data/chart?limit=20`.

Phản hồi HTTP 200:

```json
{
  "temperature": [
    { "time": "2026-11-04T10:00:08", "value": 29.6 },
    { "time": "2026-11-04T10:00:10", "value": 29.8 }
  ],
  "humidity": [
    { "time": "2026-11-04T10:00:10", "value": 76.0 }
  ],
  "light": [
    { "time": "2026-11-04T10:00:10", "value": 2350.0 }
  ]
}
```

Mỗi mảng xếp từ cũ đến mới. Cảm biến chưa có dữ liệu trả mảng rỗng. `limit` không hợp lệ trả 400.

### 8. Lịch sử cảm biến — GET `/api/sensor-data/history`

Cần đăng nhập. Tất cả tham số đều tùy chọn; nếu gửi nhiều bộ lọc thì kết hợp bằng AND.

| Tham số | Kiểu / giá trị | Mặc định / ý nghĩa |
|---|---|---|
| sensorId | Integer | ID cảm biến trong database |
| sensorKey | String | `temperature`, `humidity`, `light` |
| value | Double hữu hạn | Lọc giá trị bằng chính xác |
| from | ISO LocalDateTime | Từ thời gian này, bao gồm biên |
| to | ISO LocalDateTime | Đến thời gian này, bao gồm biên |
| page | Integer ≥ 0 | 0 |
| size | Integer 1–100 | 20 |
| sort | `createdAt,asc` hoặc `createdAt,desc` | `createdAt,desc`; khi trùng thời gian, xếp tiếp theo ID cùng hướng |

Ví dụ:

```text
/api/sensor-data/history?sensorKey=temperature&from=2026-11-04T00:00:00&to=2026-11-04T23:59:59&page=0&size=20&sort=createdAt,desc
```

Phản hồi HTTP 200 (ví dụ có một bản ghi):

```json
{
  "content": [
    {
      "id": 101,
      "sensorId": 1,
      "sensorName": "Temperature",
      "sensorKey": "temperature",
      "name": "Temperature",
      "unit": "°C",
      "value": 29.8,
      "createdAt": "2026-11-04T10:00:10"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "number": 0,
  "size": 20
}
```

Để dễ đọc, ví dụ trên lược bỏ các trường tương thích `items`, `total`, `page`. Phản hồi thực tế có thêm chúng: `items` bằng `content`, `total` bằng `totalElements`, `page` bằng `number`.

Không có bản ghi: `content: []`, `totalElements: 0`, `totalPages: 0`. Trang vượt quá số trang có `content: []` nhưng tổng số bản ghi vẫn giữ nguyên. Sai tham số hoặc `from > to` trả 400; lọc ID không có dữ liệu trả danh sách rỗng.

### 9. Lịch sử điều khiển — GET `/api/devices/action-history`

Cần đăng nhập. Tất cả tham số đều tùy chọn, kết hợp bằng AND.

| Tham số | Kiểu / giá trị | Ý nghĩa |
|---|---|---|
| deviceId | Integer | ID thiết bị trong database |
| deviceKey | String | Mã thiết bị, ví dụ `lamp1` |
| action | `ON` hoặc `OFF` | Lọc thao tác |
| status | `PENDING`, `SUCCESS`, `FAILED` | `FAILED` gồm cả lệnh không nhận được xác nhận đúng hạn |
| from, to | ISO LocalDateTime | Khoảng thời gian tạo lệnh, bao gồm biên |
| page, size, sort | Như API lịch sử cảm biến | Mặc định 0, 20, `createdAt,desc` |

Ví dụ `/api/devices/action-history?deviceKey=lamp1&status=SUCCESS&page=0&size=20&sort=createdAt,desc`.

Lịch sử trả `PENDING`, `SUCCESS` hoặc `FAILED`. Bản ghi hết thời gian chờ được trả về là `FAILED`.

Phản hồi HTTP 200 (lược bỏ `items/total/page` như API số 8):

```json
{
  "content": [
    {
      "id": 15,
      "deviceId": 1,
      "username": "admin",
      "deviceKey": "lamp1",
      "deviceName": "Đèn 1",
      "action": "ON",
      "status": "SUCCESS",
      "createdAt": "2026-11-04T10:00:05"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "number": 0,
  "size": 20
}
```

API lịch sử hiện cho tài khoản đã đăng nhập xem các bản ghi phù hợp bộ lọc của toàn hệ thống; không giới hạn theo chủ lệnh như `/api/commands/{requestId}`. Không có bộ lọc `username` hoặc `requestId` trong API lịch sử hiện tại.

## Quy ước dữ liệu và phạm vi hiện có

- Đường dẫn REST bắt đầu bằng `/api`; JSON dùng tên trường đúng như ví dụ. Riêng `full_name` dùng dấu gạch dưới.
- Ngày giờ dùng ISO như `2026-11-04T10:00:00`, có thể có phần thập phân giây; kiểu hiện tại không chứa múi giờ/offset. Không tự thêm `Z` khi gửi bộ lọc thời gian.
- ID cảm biến/thiết bị là ID database. Mã `lamp1/lamp2`, `temperature/humidity/light` dùng để nhận diện nghiệp vụ; không suy ra ID số từ chúng.
- Hiện không có API đăng ký, refresh JWT, đổi mật khẩu, CRUD thiết bị hoặc endpoint REST nhận số đo từ ESP32. ESP32 gửi số đo/ACK/trạng thái qua MQTT; xem `MQTT_PROTOCOL.md` cùng thư mục.
- Các HTTP lỗi khác có thể gồm 405 (sai method), 415 (Content-Type không hỗ trợ) và 500 (lỗi nội bộ). Không dựa vào một cấu trúc JSON lỗi tùy chỉnh vì dự án chưa định nghĩa DTO lỗi chung.

## Cấu trúc bản ghi lịch sử

`/api/sensor-data/history` trả mỗi phần tử `content` với `id`, `sensorId`, `sensorName`, `sensorKey`, `name`, `unit`, `value`, `createdAt`.

`/api/devices/action-history` trả mỗi phần tử `content` với `id`, `deviceId`, `username`, `deviceKey`, `deviceName`, `action`, `status`, `createdAt`.

`/api/sensor-data/chart` trả ba mảng `temperature`, `humidity`, `light`; thời gian mỗi điểm là chuỗi ISO đầy đủ và thứ tự từ cũ đến mới.

## WebSocket STOMP

Trình duyệt kết nối WebSocket tại `/ws`, dùng STOMP 1.2. HTTP handshake mở công khai; STOMP CONNECT bắt buộc gửi JWT Bearer và chỉ sau khi xác thực mới được SUBSCRIBE. Không gửi token qua URL hoặc cookie. Chỉ chấp nhận cùng origin. Thư viện STOMP.js 7.2.1 nằm tại `Front_end/js/vendor`, kèm giấy phép Apache 2.0.

| Destination đăng ký | Dữ liệu backend đẩy |
|---|---|
| `/topic/sensor-data` | Bộ số đo mới có cấu trúc giống `/api/sensor-data/latest` |
| `/topic/devices` | `deviceId`, `deviceKey`, `name`, `status`, `statusConfirmedAt`, `online` |
| `/topic/availability` | `{ "online": true/false }` khi ESP32 hoặc broker mất/kết nối lại |
| `/user/queue/commands` | Kết quả lệnh của chính tài khoản đang đăng nhập: `id`, `requestId`, `deviceId`, `deviceKey`, `action`, `status` |

Các sự kiện gắn với dữ liệu MySQL chỉ phát sau khi giao dịch commit. Giao dịch rollback không tạo cập nhật trên màn hình. Client chỉ được SUBSCRIBE các destination trên; không được SEND vào topic để giả mạo dữ liệu.

### Trình tự kết nối và payload mẫu

1. Gửi POST đăng nhập để lấy JWT.
2. Mở native WebSocket `ws://127.0.0.1:8080/ws` (`wss://` khi phục vụ HTTPS).
3. Gửi STOMP CONNECT với native header `Authorization: Bearer <accessToken>`, chờ CONNECTED rồi SUBSCRIBE các destination cần dùng. Đây không phải SockJS hoặc API GET JSON.
4. Thiếu/sai/hết hạn JWT ở CONNECT bị từ chối. Kết nối chưa CONNECT bị đóng sau 10 giây. Kết nối đã xác thực tự đóng khi token hết hạn; cần token mới để kết nối lại.

Ví dụ nội dung CONNECT; `<NUL>` biểu diễn ký tự kết thúc frame `\0`, không phải chuỗi chữ cần gửi:

```text
CONNECT
accept-version:1.2
host:127.0.0.1
heart-beat:10000,10000
Authorization:Bearer JWT_ACCESS_TOKEN

<NUL>
```

Ví dụ SUBSCRIBE kết quả lệnh riêng:

```text
SUBSCRIBE
id:commands
destination:/user/queue/commands
ack:auto

<NUL>
```

Payload `/topic/sensor-data` giống API số 6. Payload `/topic/devices` giống API số 3 (dùng `deviceId`, không phải `id`). Ví dụ payload `/topic/availability`:

```json
{ "online": false }
```

Ví dụ payload `/user/queue/commands`:

```json
{
  "id": 15,
  "requestId": "da18aacc-3e28-4d83-bfac-4cce0c7965aa",
  "deviceId": 1,
  "deviceKey": "lamp1",
  "action": "ON",
  "status": "SUCCESS"
}
```

Kênh lệnh có thể phát cả `PENDING` và kết quả sau đó; ghép bằng `requestId`. Các phiên đang đăng nhập cùng tài khoản có thể nhận cùng cập nhật riêng của tài khoản đó. Kết quả WebSocket có thể tới trước phản hồi HTTP 202; client cần xử lý được cả hai thứ tự.

Server cấu hình heartbeat hai chiều 10 giây. Gói MESSAGE có nội dung JSON UTF-8 và có thể được truyền bằng WebSocket binary frame; nên dùng client STOMP hỗ trợ binary như thư viện đang có trong giao diện. Thiếu/sai JWT ở CONNECT hoặc gửi SEND trái phép bị từ chối; REST điều khiển là đường gửi lệnh của trình duyệt. Simple broker hiện tại không hỗ trợ STOMP receipt để dùng làm xác nhận đăng ký.

Dashboard dùng REST để tải trạng thái, số đo và biểu đồ ban đầu sau khi kết nối STOMP. Các cập nhật tiếp theo đến qua WebSocket, không có vòng gọi API mỗi 5 giây. Khi mạng đứt, trình duyệt tự kết nối lại và đồng bộ dữ liệu ban đầu. Các số đo được ghép theo thời gian để tránh dữ liệu REST cũ ghi đè cập nhật mới. Khi chờ lệnh mà mất thông báo, giao diện chỉ gọi một lần `/api/commands/{requestId}` để đối chiếu sau thời hạn; không tự suy đoán kết quả lệnh từ trạng thái đèn.

`GET /api/devices` giúp tra ID theo `deviceKey`; `/api/commands/{requestId}` dùng đối chiếu kết quả và cho script thử lệnh. Các đường dẫn cũ `/api/readings/...`, `/api/history`, `/api/devices/{deviceKey}/commands` được giữ để tương thích, cùng yêu cầu JWT, nhưng giao diện hiện dùng API theo báo cáo. Các trường `items/total/page` trong phản hồi phân trang cũng được giữ tương thích; frontend dùng `content/totalElements/number`.

| Method | Đường dẫn tương thích | Ghi chú |
|---|---|---|
| GET | `/api/readings/latest` | Trả map theo `sensorKey`, mỗi giá trị là bản ghi chi tiết; cấu trúc khác `/api/sensor-data/latest` |
| GET | `/api/readings` | Alias của `/api/sensor-data/history` |
| GET | `/api/history` | Alias của `/api/devices/action-history` |
| POST | `/api/devices/{deviceKey}/commands` | Điều khiển theo lamp1/lamp2, body chỉ cần `action` |

Hai API lịch sử còn nhận `before` (mốc cuối) và `order=asc/desc` để tương thích; nếu có `sort` thì hướng sắp xếp trong `sort` được ưu tiên.

Spring dùng simple broker STOMP trong backend, phù hợp bản chạy một máy chủ của dự án. Tham khảo cấu hình và bảo vệ kết nối tại [Spring STOMP](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/enable.html) và [Spring WebSocket Security](https://docs.spring.io/spring-security/reference/7.0/servlet/integrations/websocket.html).
