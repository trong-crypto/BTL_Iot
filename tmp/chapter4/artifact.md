# Đặc tả chỉnh sửa Chương 4

- Nguồn giữ nguyên: `Báo cáo BTL IOT.docx` (29 trang trước chỉnh sửa, A4, Times New Roman 13 pt, lề trái 3 cm, các lề khác 2 cm).
- Tài liệu mẫu: `reference.pdf` tải từ liên kết Google Drive do người dùng cung cấp, mục IV trang in 32–46. Dùng cấu trúc 4.1 triển khai, 4.2 giao diện, 4.3 kiểm thử, 4.4 đánh giá; không sao chép nội dung sai khác của hệ ESP8266.
- Phạm vi: thay phần Chương 4 chưa hoàn thành từ đoạn `CHƯƠNG 4. KẾT QUẢ THỰC NGHIỆM` đến cuối; Chương 1–3 và các đối tượng liên quan giữ nguyên. Bản gốc không ghi đè.
- Đầu ra: một tệp DOCX tên `Báo cáo BTL IOT - Chương 4 hoàn thiện.docx` tại thư mục dự án.
- Nội dung chính: ESP32 `device01`; 2 LED tại GPIO25/26; DHT11 GPIO27; LDR GPIO34; MQTT broker cổng 1001; MySQL `iot_db`; Spring Boot, JWT Bearer, STOMP/WebSocket; HTML/CSS/JS; API theo mã hiện hành.
- Ảnh 4.1 và dashboard lấy từ DOCX nguồn; ảnh màn hình khác chụp từ frontend hiện hành với dữ liệu kiểm thử minh họa, nêu rõ trong chú thích.
- Kết quả xác thực: bộ thử 16 đạt, 1 bỏ qua do cần broker thật. Các kiểm tra HTTP/STOMP chạy trong môi trường thử H2/mock gateway. Lần chạy ESP32 thật và hai LED đã được người dùng xác nhận trước khi chuyển JWT; không gộp thành kết quả tự động mới.
- Không tuyên bố đã đo độ sáng vật lý LED, độ chính xác cảm biến, độ trễ hay vận hành dài hạn. Giá trị LDR là ADC, không phải lux.
- Kiểm tra: xuất PDF bằng Word đã có sẵn và chuyển từng trang thành PNG để xem bố cục; so sánh các trang đầu với bản gốc; rà ảnh, bảng, tiêu đề, dấu tiếng Việt và trang cuối.
