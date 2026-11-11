# IoT Command Center

Giao diện HTML/CSS/JavaScript được Maven chép vào `static/` của backend khi chạy hoặc đóng gói. Mở `http://127.0.0.1:8080/login.html` sau khi khởi động `Back_end/Back_end_Iot/run-local.ps1`; đăng nhập bằng tài khoản trong bảng `users`. Không mở các trang bằng `file://` hay Live Server vì API và WebSocket dùng cùng địa chỉ với backend.

- `index.html`: trạng thái hai đèn, gửi lệnh và chờ ESP32 xác nhận, giá trị cảm biến mới nhất, biểu đồ dữ liệu gần đây.
- `sensor-data.html`: dữ liệu cảm biến từ MySQL, lọc và phân trang trên backend.
- `history.html`: lịch sử lệnh từ MySQL, lọc và phân trang trên backend.
- `profile.html`: trang hồ sơ trình bày tĩnh hiện có.

Bộ lọc thời gian ở hai trang nhật ký chỉ lấy bản ghi trong khoảng được nhập: `2026-11-04` tìm trong ngày đó, `2026-11-04 14:39` tìm trong phút đó, `2026-11-04 14:39:33` tìm trong giây đó. Frontend gửi cả `from` và `to` cho API; các phần thập phân giây được bao gồm. Có thể dán thời gian từ nút copy, hoặc nhập dạng `DD/MM/YYYY - HH:mm:ss`. Để trống thời gian và tìm kiếm lại để bỏ lọc.

Đăng nhập gửi JSON tới `/api/auth/login`, nhận JWT Bearer lưu trong `sessionStorage` theo tab. REST tự gắn Authorization, STOMP CONNECT cũng gửi Bearer. Token hết hạn sẽ về trang đăng nhập; nút Đăng xuất xóa token trong tab và ngắt kết nối. Không dùng cookie hoặc CSRF. HTML công khai nhưng dữ liệu API và STOMP cần JWT. Dashboard tải dữ liệu ban đầu bằng `/api/sensor-data/latest`, `/api/sensor-data/chart` và API trạng thái thiết bị, rồi nhận cập nhật qua WebSocket/STOMP tại `/ws`. Hai trang nhật ký gọi API `/api/sensor-data/history` và `/api/devices/action-history`. Không có vòng tải dashboard mỗi 5 giây. Xem hợp đồng tại `docs/API_REALTIME.md` ở thư mục gốc dự án.

Ánh sáng là mức ADC 0–4095, không phải lux. `SUCCESS` xác nhận ESP32 đã đổi mức GPIO. STOMP.js được lưu cục bộ; Chart.js và Lucide vẫn tải qua CDN, nên cần mạng để hiện biểu đồ và biểu tượng.
