# Giao thức MQTT cho hệ thống IoT

## Phạm vi và quyết định

Phiên bản đầu tiên dùng một ESP32 (`device01`), hai đèn (`lamp1`, `lamp2`) và ba cảm biến (`temperature`, `humidity`, `light`). Mã thiết bị và cảm biến là định danh ổn định; ID số trong MySQL chỉ dùng nội bộ. Khi bổ sung phần cứng, thêm mã và cấu hình tương ứng, không tạo một luồng điều khiển riêng cho từng đèn trong backend.

Tài liệu báo cáo mô tả topic điều khiển riêng cho từng đèn. Giao thức được chọn giữ **một topic điều khiển chung** và dùng JSON có `requestId`. Firmware và mã MQTT của backend đã được viết theo hợp đồng này. ESP32 đã kết nối Mosquitto, gửi trạng thái và dữ liệu cảm biến; backend đã lưu dữ liệu cảm biến vào MySQL. Lệnh bật/tắt hai đèn qua backend đã nhận `SUCCESS` khi ESP32 xác nhận mức GPIO. Phần mô tả MQTT trong báo cáo cần được cập nhật ở giai đoạn hoàn thiện tài liệu.

## Topic và quyền gửi nhận

| Topic | Bên gửi | Bên nhận | Nội dung | QoS | Retain |
| --- | --- | --- | --- | --- | --- |
| `iot/device01/sensor` | ESP32 | Backend | Một lần đo gồm ba chỉ số | 0 | Không |
| `iot/device01/control` | Backend | ESP32 | Một lệnh cho một đèn | 0 | Không |
| `iot/device01/ack` | ESP32 | Backend | Kết quả của một lệnh, có `requestId` | 0 | Không |
| `iot/device01/status/lamp1` | ESP32 | Backend | Mức GPIO điều khiển đèn 1 | 0 | Có |
| `iot/device01/status/lamp2` | ESP32 | Backend | Mức GPIO điều khiển đèn 2 | 0 | Có |
| `iot/device01/availability` | ESP32 hoặc MQTT broker | Backend | `ONLINE` hoặc `OFFLINE` | 0 | Có |

Không dùng `lamp3`, lệnh `ALL` hoặc topic `status/lamps` trong phạm vi báo cáo này. Lệnh điều khiển tuyệt đối không được retain vì ESP32 không được thực hiện lại lệnh cũ sau khi kết nối lại. Bản tin cảm biến không retain để backend không lưu một lần đo cũ như dữ liệu mới. Trạng thái đèn được retain để backend có thể đồng bộ lại sau khi subscribe.

## Payload

### Dữ liệu cảm biến

```json
{"temperature":28.5,"humidity":70.2,"light":1530}
```

ESP32 gửi sau mỗi khoảng 2 giây khi có lần đọc DHT11 hợp lệ. `temperature` tính bằng °C, `humidity` bằng %, `light` là mức ADC đảo chiều từ 0 đến 4095 theo firmware hiện tại, **không phải lux**. Backend kiểm tra JSON, kiểu số hữu hạn, độ ẩm từ 0 đến 100 và ánh sáng từ 0 đến 4095. Backend gắn cùng một thời điểm nhận cho ba bản ghi `sensor_data`; ESP32 chưa có đồng hồ thời gian thực nên không tự gửi thời điểm đo. Bản tin sai bị bỏ qua và ghi log, không lưu một phần của bộ ba.

### Lệnh điều khiển

```json
{"requestId":"550e8400-e29b-41d4-a716-446655440000","deviceKey":"lamp1","action":"ON"}
```

`requestId` là UUID do backend tạo, duy nhất cho mỗi yêu cầu. `deviceKey` chỉ nhận `lamp1` hoặc `lamp2` ở phiên bản đầu; `action` chỉ nhận `ON` hoặc `OFF`. Backend lưu bản ghi `action_history` ở trạng thái `PENDING` trước khi publish. Một lệnh chỉ điều khiển một đèn. Không publish lệnh `ALL` để mỗi lệnh có một kết quả và lịch sử rõ ràng.

### Xác nhận lệnh

```json
{"requestId":"550e8400-e29b-41d4-a716-446655440000","deviceKey":"lamp1","action":"ON","result":"SUCCESS","state":"ON"}
```

ESP32 gửi lên topic `iot/device01/ack` sau khi đã đổi chân GPIO. Nếu không thể thực hiện lệnh nhưng vẫn đọc được `requestId`, gửi `result:"FAILED"`; `state` có thể bỏ qua nếu không xác định được mức GPIO. Backend chỉ ghép xác nhận với một lệnh `PENDING` khi **cả `requestId`, `deviceKey` và `action` khớp**. Xác nhận trùng lặp không tạo thêm lịch sử hoặc đổi kết quả đã chốt. Bản tin sai cấu trúc hoặc không có `requestId` bị bỏ qua và ghi log. `SUCCESS` chỉ xác nhận mức GPIO đã đổi, không xác nhận LED phát sáng.

Hai LED rời vẫn cần điện trở hạn dòng riêng; xem `docs/LED_WIRING.md`. Firmware không sử dụng GPIO32/33 để đo LED. LED đứt, cháy hoặc lắp sai cực vẫn có thể nhận `SUCCESS` nếu ESP32 đã đặt mức GPIO theo lệnh.

### Trạng thái đèn

Payload trên `iot/device01/status/lamp1`:

```json
{"deviceKey":"lamp1","state":"ON"}
```

Topic trạng thái thông báo mức GPIO điều khiển, chỉ gồm `ON` hoặc `OFF`; nó không đo độ sáng vật lý. Topic trạng thái không dùng để đánh dấu một lệnh `SUCCESS`; việc đó thuộc topic `ack`. ESP32 publish trạng thái sau mỗi lần đổi GPIO và publish lại trạng thái của cả hai đèn sau khi kết nối MQTT. Backend kiểm tra mã đèn trong topic trùng với `deviceKey` trong payload, rồi cập nhật trạng thái cuối được ESP32 báo về trong `devices`. Trạng thái nhận muộn sau một lệnh `TIMEOUT` vẫn được đồng bộ, nhưng không đổi lịch sử lệnh đó thành `SUCCESS`.

### Tình trạng kết nối

ESP32 cấu hình MQTT Last Will là `OFFLINE` cho `iot/device01/availability`; khi kết nối thành công, publish `ONLINE` và trạng thái hai đèn. Cả hai giá trị availability đều retain. `OFFLINE` chỉ cho biết mất kết nối ESP32, không có nghĩa đèn đang tắt. Giao diện vẫn hiển thị trạng thái cuối được xác nhận cùng dấu hiệu mất kết nối.

## Quy tắc xử lý và thời gian chờ

1. Backend chỉ cho gửi lệnh khi mã đèn hợp lệ, người dùng đã đăng nhập và không có lệnh `PENDING` khác cho cùng đèn. Hai đèn có thể được điều khiển độc lập.
2. Backend tạo `requestId`, lưu `PENDING`, rồi publish lệnh. Nếu publish không thành công, chuyển lệnh thành `FAILED` và trả lỗi cho giao diện.
3. ESP32 nhận lệnh, kiểm tra JSON và mã đèn, đổi GPIO, publish trạng thái mức GPIO và `ack`. Firmware giữ tám `requestId` gần nhất để gửi lại kết quả khi bản tin lặp, không đổi GPIO lần nữa; backend xử lý `ack` trùng theo cách không tạo tác dụng phụ. Backend không phát lại một `requestId` cũ sau khi đã chốt lệnh.
4. Khi nhận `ack` hợp lệ trong 5 giây, backend chốt `SUCCESS` hoặc `FAILED`. Chỉ chấp nhận `SUCCESS` khi `state` trùng với hành động yêu cầu; lúc đó cập nhật `devices.status` từ `ack`. Topic `status` có thể đến trước hoặc sau và được xử lý như một bản tin đồng bộ trạng thái độc lập.
5. Nếu quá 5 giây kể từ lúc publish mà không có `ack` hợp lệ, chốt `TIMEOUT`. Giữ trạng thái đèn cuối đã biết và hiển thị kết quả lệnh là chưa xác định. `ack` đến sau không đổi lại lịch sử; bản tin trạng thái vẫn cập nhật trạng thái thực tế.
6. Khi ESP32 khởi động lại hoặc kết nối lại, trạng thái hai LED được publish lại. Backend đồng bộ trạng thái từ các topic `status`; không suy luận `SUCCESS` cho lệnh cũ từ các bản tin này.

Backend cần serialize thay đổi trạng thái và kết quả lệnh trong giao dịch phù hợp để `ack` và bộ hẹn giờ không cùng chốt một lệnh hai lần. Thời gian chờ được tính ở backend bằng đồng hồ của backend.

## Cấu hình và bước triển khai tiếp theo

- Firmware đọc địa chỉ broker, cổng và thông tin đăng nhập từ `secrets.h` riêng; chỉ chia sẻ `secrets.example.h`, không sao chép thông tin đăng nhập vào tài liệu hay ví dụ.
- Backend đọc cấu hình broker từ biến môi trường và đã có MQTT client cùng API REST điều khiển/lấy kết quả lệnh có xác thực. Giao diện tải dữ liệu ban đầu bằng REST, rồi nhận số đo và trạng thái qua WebSocket/STOMP; xem `docs/API_REALTIME.md`. ID node `device01` hiện được cố định trong tên topic, cần đưa vào cấu hình khi bổ sung ESP32 thứ hai.
- Firmware hiện chỉ sử dụng hai LED, đọc lệnh JSON, gửi `ack` và Last Will. Cần cài ArduinoJson 7 trước khi biên dịch firmware.
- Khi thêm cảm biến trong tương lai, mở rộng payload cảm biến bằng một khóa mới và khai báo mã, đơn vị đo trong database. Khi thêm ESP32 thứ hai, thay `device01` bằng mã node mới ở cùng cấu trúc topic và ánh xạ node trong database.
