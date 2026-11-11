# Cấu hình chạy dự án IoT

## ESP32

Firmware `sketch_sep11a/sketch_sep11a.ino` đọc địa chỉ broker, cổng và tài khoản từ `secrets.h` cùng thư mục sketch. Tệp này chứa cấu hình thật và đã được đưa vào `.gitignore`; không chia sẻ hoặc đưa nó lên kho mã. Dùng `secrets.example.h` làm mẫu khi cấu hình trên máy khác.

Trước khi biên dịch ESP32, cài thư viện **ArduinoJson phiên bản 7**, cùng các thư viện DHT, WiFiManager và PubSubClient đang được firmware sử dụng.

Nếu Mosquitto chạy trên laptop, `MQTT_HOST` trong `secrets.h` phải là địa chỉ IP mà ESP32 truy cập được qua Wi-Fi, không phải `localhost`. Giữ `MQTT_PORT` bằng cổng listener thực tế của Mosquitto. Sau khi đổi tệp này, biên dịch và nạp lại firmware.

### Kiểm tra kết nối phần cứng

1. Để Mosquitto chạy ở cổng 1001. Kiểm tra IP Wi-Fi hiện tại của laptop bằng `ipconfig` và bảo đảm `MQTT_HOST` trong `secrets.h` trùng IP đó. Laptop và ESP32 cần ở cùng mạng có thể truy cập lẫn nhau. IP laptop có thể đổi khi chuyển mạng; khi đó phải sửa cấu hình và nạp lại firmware.
2. Mở Serial Monitor ở **115200 baud** sau khi nạp firmware. ESP32 sẽ in IP Wi-Fi, địa chỉ broker và dòng `MQTT da ket noi va dong bo 2 den.` khi kết nối thành công. Nếu hiện `MQTT khong ket noi, ma loi = ...`, kiểm tra địa chỉ, cổng, tài khoản MQTT và tường lửa của laptop.
3. Trong cửa sổ Mosquitto chạy với `-v`, tìm kết nối của client bắt đầu bằng `esp32-led-`. Khi đã kết nối, ESP32 gửi `iot/device01/availability` là `ONLINE`, trạng thái `lamp1` và `lamp2`, rồi dữ liệu `iot/device01/sensor` khoảng mỗi 2 giây nếu DHT11 đọc được.
4. Sau khi xác nhận các bản tin từ ESP32, mới khởi động backend với các biến môi trường bên dưới. Backend sẽ kết nối cùng broker, nhận trạng thái đèn và lưu dữ liệu cảm biến vào MySQL.

Nếu Serial hiện `AutoConnect: FAILED` rồi `StartAP with SSID: ESP32-LED-Control`, ESP32 chưa vào được Wi-Fi đã lưu. Dùng điện thoại hoặc máy khác kết nối mạng `ESP32-LED-Control`, mở `http://192.168.4.1`, chọn mạng Wi-Fi **Redmi 12** và nhập lại mật khẩu của mạng đó. Cổng cấu hình chờ 180 giây; nếu hết thời gian, nhấn nút Reset trên ESP32 để mở lại. Sau khi kết nối, Serial phải hiện IP của ESP32 và dòng `MQTT da ket noi va dong bo 2 den.`. Không nhập mật khẩu Wi-Fi vào mã nguồn hoặc gửi qua ảnh chụp.

Nếu Serial liên tục hiện `DHT11 khong doc duoc`, kiểm tra chân DATA của cảm biến có nối đúng **GPIO27** như `DHT_PIN` trong firmware, cùng với VCC và GND. Kiểm tra cảm biến đang dùng thực sự là DHT11; nếu dùng cảm biến DHT dạng 4 chân rời, đường DATA cần điện trở kéo lên nguồn phù hợp. Khi DHT11 chưa đọc được, firmware sẽ bỏ qua cả lần gửi dữ liệu nhiệt độ, độ ẩm và ánh sáng lên topic `sensor`.

## Backend

Để chạy backend trên máy phát triển mà không ghi mật khẩu vào mã nguồn, mở PowerShell tại `Back_end/Back_end_Iot` và chạy `./run-local.ps1`. Script ưu tiên các biến môi trường đã đặt trong tiến trình, User hoặc Machine; chỉ hỏi mật khẩu còn thiếu và đọc tài khoản MQTT còn thiếu từ `sketch_sep11a/secrets.h`. Script đặt `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` để chỉ kiểm tra cấu trúc bảng. Biến đặt riêng trong một cửa sổ PowerShell khác hoặc trong cấu hình chạy IntelliJ không tự truyền sang cửa sổ mới. Nếu PowerShell chặn chạy script theo chính sách máy, dùng phần cấu hình biến môi trường thủ công bên dưới. Sau khi backend báo kết nối MQTT, kiểm tra dữ liệu mới bằng truy vấn:

```sql
SELECT s.sensor_key, sd.value, sd.created_at
FROM iot_db.sensor_data AS sd
JOIN iot_db.sensors AS s ON s.id = sd.sensor_id
ORDER BY sd.id DESC
LIMIT 12;
```

### Mở giao diện

Sau khi backend khởi động, mở `http://127.0.0.1:8080/login.html` và đăng nhập bằng tài khoản trong bảng `users` (mặc định là `admin` khi được tạo ban đầu). Trang đăng nhập gọi `/api/auth/login` bằng JSON và trả JWT Bearer. Các trang HTML/JS được Maven đóng gói cùng backend; không mở file HTML trực tiếp hoặc qua Live Server. Dashboard tải dữ liệu ban đầu qua REST rồi nhận cập nhật WebSocket/STOMP tại `/ws`; trang cảm biến/lịch sử dùng API có phân trang. Xem đường dẫn và payload trong `docs/API_REALTIME.md`. `SUCCESS` chỉ xác nhận ESP32 đổi mức GPIO, không đo LED có sáng.

### Thử điều khiển hai đèn qua backend

Backend cung cấp `GET /api/devices/{deviceId}/status` và `POST /api/devices/{deviceId}/control` theo báo cáo. `GET /api/devices` giúp tìm ID của lamp1/lamp2; `GET /api/commands/{requestId}` giúp script đối chiếu kết quả. API dùng tài khoản trong bảng `users`. `POST /api/auth/login` chỉ cần JSON username/password, trả `accessToken`, `tokenType`, `expiresIn`, `expiresAt`. Các API khác dùng header `Authorization: Bearer <accessToken>`. Giao diện và collection `docs/IoT.postman_collection.json` tự xử lý token. Không cần CSRF/cookie hoặc Basic. Xem `docs/JWT_AUTH.md` để cấu hình khóa và thời hạn. Backend mặc định chỉ nghe trên `127.0.0.1:8080`; để truy cập từ thiết bị khác, cần cấu hình `SERVER_ADDRESS` và bảo vệ kết nối phù hợp.

Khởi động lại backend sau khi cập nhật mã nguồn, giữ ESP32 đang chạy, rồi mở PowerShell tại `Back_end/Back_end_Iot` để thử từng lệnh. Script hỏi mật khẩu đăng nhập trên máy, gửi lệnh qua backend và in kết quả từ `action_history`:

```powershell
.\test-lamp.ps1 -DeviceKey lamp1 -Action ON
.\test-lamp.ps1 -DeviceKey lamp1 -Action OFF
.\test-lamp.ps1 -DeviceKey lamp2 -Action ON
.\test-lamp.ps1 -DeviceKey lamp2 -Action OFF
```

Nếu tên tài khoản khác `admin`, thêm `-Username <tên_tài_khoản>`. Có thể đối chiếu kết quả trong MySQL:

```sql
SELECT a.request_id, d.device_key, a.action, a.status, a.created_at
FROM iot_db.action_history AS a
JOIN iot_db.devices AS d ON d.id = a.device_id
ORDER BY a.id DESC
LIMIT 8;
```

`Back_end/Back_end_Iot/src/main/resources/application.yaml` đọc các biến môi trường sau:

| Biến | Mục đích | Mặc định hiện tại |
| --- | --- | --- |
| `DB_URL` | JDBC URL của MySQL | `jdbc:mysql://localhost:3306/iot_db` |
| `DB_USERNAME` | Tài khoản MySQL | `root` |
| `DB_PASSWORD` | Mật khẩu MySQL | rỗng |
| `IOT_ADMIN_USERNAME` | Tên tài khoản quản trị ban đầu | `admin` |
| `IOT_ADMIN_PASSWORD` | Mật khẩu quản trị ban đầu | không có; bắt buộc nếu chưa có tài khoản |
| `IOT_MQTT_HOST` | Broker mà backend kết nối | `localhost` |
| `IOT_MQTT_PORT` | Cổng broker | `1001` |
| `IOT_MQTT_USERNAME` | Tài khoản broker | rỗng |
| `IOT_MQTT_PASSWORD` | Mật khẩu broker | rỗng |
| `IOT_MQTT_CLIENT_ID` | Mã MQTT client của backend | `iot-backend` |
| `SERVER_ADDRESS` | Địa chỉ backend lắng nghe HTTP | `127.0.0.1` |

Ví dụ đặt biến môi trường trong PowerShell trước khi chạy backend:

```powershell
$env:DB_URL = 'jdbc:mysql://localhost:3306/iot_db'
$env:DB_USERNAME = 'root'
$env:DB_PASSWORD = '<mật khẩu MySQL>'
$env:IOT_ADMIN_PASSWORD = '<mật khẩu quản trị ban đầu>'
$env:IOT_MQTT_HOST = 'localhost'
$env:IOT_MQTT_PORT = '1001'
$env:IOT_MQTT_USERNAME = '<tài khoản broker>'
$env:IOT_MQTT_PASSWORD = '<mật khẩu broker>'
```

Các biến môi trường chỉ có hiệu lực trong cửa sổ PowerShell đã đặt chúng. MQTT client của backend sử dụng các biến trên và tự thử kết nối lại nếu broker chưa sẵn sàng.

## Kiểm tra trước khi tích hợp

1. Xác nhận ESP32 và backend cùng trỏ đến một Mosquitto broker và cùng cổng.
2. Xác nhận cả hai tài khoản MQTT có quyền publish/subscribe các topic `iot/device01/...` cần thiết.
3. Không đặt mật khẩu thật vào `application.yaml`, `secrets.example.h`, tài liệu hoặc ảnh chụp gửi đi.
