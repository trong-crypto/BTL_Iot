from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).parent
FONT_PATH = r"C:\Windows\Fonts\arial.ttf"
BOLD_PATH = r"C:\Windows\Fonts\arialbd.ttf"


def font(size, bold=False):
    return ImageFont.truetype(BOLD_PATH if bold else FONT_PATH, size)


def arrow(draw, x1, y1, x2, y2, label, color="#24496b", size=25):
    draw.line((x1, y1, x2, y2), fill=color, width=4)
    direction = 1 if x2 > x1 else -1
    draw.polygon([(x2, y2), (x2 - direction * 16, y2 - 8),
                  (x2 - direction * 16, y2 + 8)], fill=color)
    text_width = draw.textbbox((0, 0), label, font=font(size))[2]
    draw.text(((x1 + x2 - text_width) / 2, y1 - 36), label,
              fill="#172b40", font=font(size))


def table(draw, box, title, rows):
    x, y, w = box
    row_h = 49
    h = 62 + len(rows) * row_h
    draw.rounded_rectangle((x, y, x+w, y+h), radius=9, fill="white",
                           outline="#607b93", width=3)
    draw.rounded_rectangle((x, y, x+w, y+62), radius=9,
                           fill="#d9eaf7", outline="#607b93", width=3)
    draw.rectangle((x+2, y+48, x+w-2, y+62), fill="#d9eaf7")
    draw.text((x+17, y+14), title, fill="#173c5c", font=font(32, True))
    for i, label in enumerate(rows):
        yy = y + 62 + i*row_h
        if i:
            draw.line((x, yy, x+w, yy), fill="#d0dae2", width=2)
        draw.text((x+17, yy+10), label, fill="#26333e", font=font(27))
    return (x, y, x+w, y+h)


def erd():
    im = Image.new("RGB", (1890, 1016), "white")
    d = ImageDraw.Draw(im)
    boxes = {}
    boxes["users"] = table(d, (35, 44, 495), "users",
                            ["id  PK", "username  UNIQUE", "password", "full_name", "created_at"])
    boxes["sensors"] = table(d, (696, 44, 495), "sensors",
                              ["id  PK", "sensor_key  UNIQUE", "name", "unit", "created_at"])
    boxes["devices"] = table(d, (1356, 44, 495), "devices",
                              ["id  PK", "device_key  UNIQUE", "name", "status", "status_confirmed_at", "created_at", "updated_at"])
    boxes["sensor_data"] = table(d, (698, 616, 495), "sensor_data",
                                  ["id  PK", "sensor_id  FK → sensors.id", "value", "created_at"])
    boxes["action_history"] = table(d, (45, 616, 585), "action_history",
                                     ["id  PK", "user_id  FK → users.id", "device_id  FK → devices.id",
                                      "request_id  UNIQUE", "action / status", "created_at"])
    # Relationship labels are placed on the connectors, away from field text.
    d.line((280, 351, 280, 606), fill="#537897", width=4)
    d.line((280, 606, 280, 616), fill="#537897", width=4)
    d.text((294, 483), "1 — N", fill="#24496b", font=font(25, True))
    d.line((945, 351, 945, 616), fill="#537897", width=4)
    d.text((960, 483), "1 — N", fill="#24496b", font=font(25, True))
    d.line((1600, 448, 1600, 563), fill="#537897", width=4)
    d.line((1600, 563, 565, 563), fill="#537897", width=4)
    d.line((565, 563, 565, 616), fill="#537897", width=4)
    d.text((1150, 527), "1 — N", fill="#24496b", font=font(25, True))
    im.save(OUT / "erd.png")


def sequence(path, lanes, events, height, subtitle):
    width = 1890
    im = Image.new("RGB", (width, height), "white")
    d = ImageDraw.Draw(im)
    left, right = 125, width-125
    xs = [int(left + i*(right-left)/(len(lanes)-1)) for i in range(len(lanes))]
    for x, label in zip(xs, lanes):
        tw = d.textbbox((0,0), label, font=font(28, True))[2]
        d.rounded_rectangle((x-tw/2-20, 27, x+tw/2+20, 84), 7,
                            fill="#d9eaf7", outline="#607b93", width=2)
        d.text((x-tw/2, 38), label, fill="#173c5c", font=font(28, True))
        y = 104
        while y < height-94:
            d.line((x, y, x, min(y+21, height-94)), fill="#8ba2b5", width=2)
            y += 34
    step = (height-224)//(len(events)-1)
    for i, (source, target, label) in enumerate(events):
        y = 148 + i*step
        arrow(d, xs[source], y, xs[target], y, label, size=24)
    d.text((65, height-57), subtitle, fill="#49677e", font=font(24))
    im.save(OUT / path)


def main():
    erd()
    sequence("login_sequence.png", ["Người dùng", "Frontend", "Backend", "MySQL"], [
        (0,1,"1. Nhập username và password"),
        (1,2,"2. POST /api/auth/login"),
        (2,3,"3. Tra cứu users theo username"),
        (3,2,"4. Trả tài khoản và mật khẩu băm"),
        (2,1,"5. HTTP 200: JWT, hạn dùng, thông tin user"),
        (1,0,"6. Mở Dashboard"),
        (1,2,"7. REST tiếp theo: Bearer JWT"),
    ], 1050, "Thiếu dữ liệu: HTTP 400; sai tài khoản hoặc mật khẩu: HTTP 401.")
    sequence("control_sequence.png", ["Người dùng", "Frontend", "Backend", "MySQL", "Broker", "ESP32"], [
        (1,2,"1. GET /api/devices (Bearer)"),
        (2,3,"2. Đọc ID, trạng thái đèn"),
        (3,2,"3. Trả danh sách thiết bị"),
        (0,1,"4. Chọn ON hoặc OFF"),
        (1,2,"5. POST /api/devices/{id}/control"),
        (2,3,"6. Lưu PENDING, requestId"),
        (2,4,"7. iot/device01/control"),
        (4,5,"8. Lệnh: requestId, deviceKey, action"),
        (2,1,"9. HTTP 202: PENDING, requestId"),
        (5,4,"10. Status và iot/device01/ack"),
        (4,2,"11. ACK: requestId, result, state"),
        (2,3,"12. Cập nhật SUCCESS/FAILED"),
        (2,1,"13. STOMP: /user/queue/commands"),
        (1,0,"14. Hiển thị kết quả lệnh"),
    ], 900, "Không có ACK hợp lệ trong 5 giây: TIMEOUT; HTTP 202 và STOMP có thể đến khác thứ tự.")


if __name__ == "__main__":
    main()
