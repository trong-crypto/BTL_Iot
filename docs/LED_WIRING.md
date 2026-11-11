# Đấu hai LED rời với ESP32

Mỗi LED rời cần một điện trở hạn dòng riêng, kể cả khi phần mềm chỉ theo dõi mức GPIO. Xác định giá trị điện trở trước khi cấp nguồn; dự án khuyến nghị **330 Ω** cho mỗi LED ở GPIO 3,3 V. Rút USB ESP32 trước khi đổi dây và thay LED đỏ đã cháy.

```text
GPIO25 ── [330 Ω] ── chân dài (+) LED xanh ── chân ngắn (−) ── GND
GPIO26 ── [330 Ω] ── chân dài (+) LED đỏ  ── chân ngắn (−) ── GND
```

Điện trở không có cực. Có thể đặt điện trở phía sau LED thay vì phía trước, miễn mỗi nhánh có đúng một điện trở nối tiếp và không có dây tắt qua điện trở. Hai nhánh dùng chung GND của ESP32. GPIO32 và GPIO33 không cần nối.

Firmware gửi `ON/OFF` theo mức GPIO25/26. Xác nhận `SUCCESS` nghĩa là ESP32 đã đặt mức GPIO theo lệnh; nó không chứng minh LED phát sáng. LED đứt hoặc lắp sai cực vẫn có thể được báo `ON`.
