from copy import deepcopy
from pathlib import Path
from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.shared import Inches, Pt
from docx.table import Table
from docx.text.paragraph import Paragraph
from docx.oxml import OxmlElement
from docx.oxml.ns import qn

ROOT = Path(r"C:\Users\abc\OneDrive\Documents\IotProject")
SOURCE = ROOT / "Báo cáo BTL IOT - Chương 4 hoàn thiện.docx"
OUT = ROOT / "tmp" / "report_update" / "report_corrected.docx"
ART = OUT.parent

doc = Document(SOURCE)
blocks = list(doc.iter_inner_content())


def set_paragraph(index, value):
    p = blocks[index]
    assert isinstance(p, Paragraph), index
    if p.runs:
        p.runs[0].text = value
        for run in p.runs[1:]:
            run.text = ""
    else:
        p.add_run(value)


def set_cell(cell, value, code=False):
    p = cell.paragraphs[0]
    if p.runs:
        p.runs[0].text = value
        for run in p.runs[1:]:
            run.text = ""
        primary = p.runs[0]
    else:
        primary = p.add_run(value)
    for extra in cell.paragraphs[1:]:
        extra._element.getparent().remove(extra._element)
    if code:
        primary.font.name = "Consolas"
        primary.font.size = Pt(9)


def cell(index, row, column, value, code=False):
    table = blocks[index]
    assert isinstance(table, Table), index
    set_cell(table.cell(row, column), value, code=code)


def add_row(index, values):
    table = blocks[index]
    assert isinstance(table, Table)
    last = deepcopy(table.rows[-1]._tr)
    table._tbl.append(last)
    row = table.rows[-1]
    for target, value in zip(row.cells, values):
        set_cell(target, value)


def add_paragraph_after(block, value):
    # Reuse paragraph formatting, or create a body paragraph after a table.
    fresh = deepcopy(block._element) if isinstance(block, Paragraph) else OxmlElement("w:p")
    block._element.addnext(fresh)
    p = Paragraph(fresh, block._parent)
    if not isinstance(block, Paragraph):
        p.style = doc.styles["Normal"]
    if p.runs:
        p.runs[0].text = value
        for run in p.runs[1:]:
            run.text = ""
    else:
        p.add_run(value)
    return p


def clone_api_table(index, after, name, method, url, params, body, response):
    source = blocks[index]
    fresh = deepcopy(source._element)
    after._element.addnext(fresh)
    table = Table(fresh, doc._body)
    fields = ["Thuộc tính", "Tên API", "Method", "URL", "Params / Query", "Body", "Response"]
    values = ["Nội dung", name, method, url, params, body, response]
    for r, (label, value) in enumerate(zip(fields, values)):
        set_cell(table.cell(r, 0), label)
        set_cell(table.cell(r, 1), value, code=r in (5, 6) and value != "Không có")
    return table


def replace_picture(index, filename):
    p = blocks[index]
    assert isinstance(p, Paragraph)
    assert p._element.xpath(".//a:blip"), index
    p.clear()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.add_run().add_picture(str(ART / filename), width=Inches(6.3))


def keep_table_together(table):
    for row_index, row in enumerate(table.rows):
        trpr = row._tr.get_or_add_trPr()
        if not trpr.xpath("./w:cantSplit"):
            trpr.append(OxmlElement("w:cantSplit"))
        for c in row.cells:
            for p in c.paragraphs:
                p.paragraph_format.keep_with_next = row_index < len(table.rows) - 1


# Chương 1–2: công nghệ, dữ liệu và luồng MQTT thực tế.
cell(30, 2, 1, "HTML, CSS và JavaScript tĩnh; Chart.js; STOMP.js")
cell(45, 5, 1, "HTML, CSS và JavaScript tĩnh; Chart.js")
set_paragraph(35, "Giao diện web gồm các trang HTML, CSS và JavaScript tĩnh được Spring Boot phục vụ cùng nguồn gốc với REST API. CSS của dự án tạo bố cục đáp ứng; Chart.js biểu diễn dữ liệu cảm biến. Backend đẩy dữ liệu mới qua WebSocket/STOMP để giao diện cập nhật mà không cần tải lại trang.")
set_paragraph(36, "Khi người dùng bật hoặc tắt một trong hai đèn, frontend gửi REST API tới Spring Boot. Backend tạo lệnh PENDING với requestId và publish lên topic MQTT chung iot/device01/control; ESP32 chọn đèn bằng deviceKey, điều khiển GPIO rồi gửi trạng thái và ACK. Backend dùng ACK hợp lệ để xác định kết quả lệnh, cập nhật cơ sở dữ liệu và thông báo qua STOMP. Luồng đọc cảm biến tiếp tục chạy theo chu kỳ riêng.")
set_paragraph(57, "Subscribe: nhận lệnh cho cả hai đèn qua topic iot/device01/control và phân biệt đèn bằng deviceKey.")
set_paragraph(58, "Sau khi xử lý lệnh, ESP32 publish trạng thái GPIO của từng đèn và một ACK gắn requestId; hệ thống không đo trực tiếp LED có sáng hay không.")
# The original Spring Boot cell contains a Word field; leave it intact.
# Preserve embedded controls in this legacy table while changing one label.
for node in blocks[60].cell(6, 0)._element.xpath(".//w:t"):
    if node.text == "Bootstrap":
        node.text = "CSS tùy chỉnh"
add_row(72, ["sensor_key", "VARCHAR(50)", "UNIQUE, NOT NULL", "Mã cảm biến: temperature, humidity, light"])
add_row(75, ["device_key", "VARCHAR(50)", "UNIQUE, NOT NULL", "Mã đèn: lamp1 hoặc lamp2"])
add_row(75, ["status_confirmed_at", "DATETIME", "NULL", "Lần cuối nhận trạng thái xác nhận từ ESP32"])
add_row(81, ["request_id", "VARCHAR(36)", "UNIQUE, NULL", "Ghép lệnh mới với ACK; bản ghi cũ có thể để trống"])
set_paragraph(87, "Khi ESP32 trả ACK SUCCESS hợp lệ cho requestId và trạng thái GPIO khớp lệnh, action_history.status thành SUCCESS; devices.status và status_confirmed_at được cập nhật.")
set_paragraph(91, "Mật khẩu người dùng được lưu dưới dạng giá trị băm, không lưu văn bản rõ; backend kiểm tra mật khẩu bằng bộ mã hóa mật khẩu.")
set_paragraph(108, "→ Backend publish một bản tin lên topic iot/device01/control cho cả hai đèn.")
set_paragraph(109, "Payload gồm requestId, deviceKey (lamp1 hoặc lamp2) và action (ON hoặc OFF).")
set_paragraph(110, "ESP32 dùng deviceKey để chọn đúng GPIO của đèn tương ứng.")
set_paragraph(111, "→ ESP32 subscribe iot/device01/control, nhận lệnh và thay đổi GPIO tương ứng.")
set_paragraph(112, "→ ESP32 publish trạng thái GPIO trên topic status riêng của đèn và ACK trên iot/device01/ack.")
set_paragraph(113, "→ Hai topic trạng thái được giữ riêng theo từng đèn:")
set_paragraph(116, "→ Backend xử lý ACK theo requestId, deviceKey và action, đồng thời xử lý bản tin trạng thái đèn.")
set_paragraph(117, "ACK SUCCESS hợp lệ chuyển action_history.status từ PENDING thành SUCCESS; ACK FAILED chuyển thành FAILED.")
set_paragraph(118, "ACK SUCCESS cập nhật devices.status thành ON hoặc OFF đúng với trạng thái GPIO mà ESP32 báo về.")
set_paragraph(119, "Backend ghi devices.status_confirmed_at và phát kết quả lệnh cùng trạng thái mới qua WebSocket/STOMP.")
set_paragraph(120, "Nếu không nhận ACK hợp lệ trong 5 giây sau khi gửi lệnh, backend đánh dấu TIMEOUT và thực hiện các bước sau:")
set_paragraph(123, "Phát kết quả TIMEOUT đến frontend qua /user/queue/commands; giao diện không suy đoán trạng thái vật lý của LED từ kết quả này.")
set_paragraph(132, "Các topic MQTT hiện hành gồm một topic lệnh chung, topic ACK, topic cảm biến, trạng thái từng đèn và trạng thái kết nối ESP32.")
cell(133, 2, 0, "iot/device01/control")
cell(133, 2, 3, "Lệnh ON/OFF; payload có requestId, deviceKey, action")
cell(133, 3, 0, "iot/device01/ack")
cell(133, 3, 1, "ESP32")
cell(133, 3, 2, "Backend")
cell(133, 3, 3, "Xác nhận kết quả lệnh theo requestId")
add_row(133, ["iot/device01/availability", "ESP32", "Backend", "Báo ESP32 online/offline"])
set_paragraph(139, "MySQL chịu trách nhiệm lưu trữ lâu dài số đo, thiết bị, người dùng và lịch sử điều khiển.")
set_paragraph(140, "HTML, CSS và JavaScript tĩnh chịu trách nhiệm hiển thị giao diện web; Spring Boot phục vụ các tệp này cùng nguồn gốc với API.")
set_paragraph(144, "Khi mở rộng, cần thêm cấu hình và mã xử lý cho cảm biến hoặc đèn mới; đèn có thể tiếp tục dùng topic lệnh chung với deviceKey mới, đồng thời có topic trạng thái riêng.")
set_paragraph(146, "REST và STOMP hiện dùng JWT Bearer; khi triển khai ngoài môi trường nội bộ cần cấu hình HTTPS/WSS, quyền truy cập MQTT và bảo vệ các khóa cấu hình.")
replace_picture(67, "erd.png")

# Chương 3: các ví dụ API, cơ chế JWT và sơ đồ trình tự.
intro = blocks[148]
set_paragraph(148, intro.text + " Các hình giao diện ở Chương 3 minh họa thiết kế; ảnh giao diện triển khai hiện hành được trình bày ở Chương 4.")
add_paragraph_after(intro, "Quy ước API hiện hành: chỉ POST /api/auth/login là công khai; các API còn lại cần Authorization: Bearer <accessToken>. REST đọc dữ liệu trả HTTP 200, lệnh điều khiển trả HTTP 202 với trạng thái PENDING. Kết quả lệnh đến sau qua STOMP hoặc GET /api/commands/{requestId}. Dữ liệu thời gian dùng ISO dạng 2026-11-04T10:00:00; page bắt đầu từ 0, size từ 1 đến 100.")
cell(156, 4, 1, "Đăng nhập thành công, frontend nhận JWT và hạn dùng, lưu trong sessionStorage của tab rồi chuyển sang Dashboard.")
cell(156, 5, 1, "Bước 1: Người dùng nhập username và password.\nBước 2: Frontend gọi POST /api/auth/login bằng JSON.\nBước 3: Backend tra cứu users và so sánh mật khẩu băm.\nBước 4: Nếu hợp lệ, backend trả JWT, thời hạn và thông tin tài khoản (HTTP 200).\nBước 5: Frontend lưu token trong sessionStorage, mở Dashboard và gắn Bearer cho REST/STOMP tiếp theo.")
cell(156, 6, 1, "Thiếu username/password: HTTP 400. Sai tài khoản hoặc mật khẩu: HTTP 401. Lỗi cơ sở dữ liệu: HTTP 500.")
cell(162, 4, 1, "Không có; API công khai, Content-Type: application/json")
cell(162, 5, 1, '{"username":"admin","password":"MAT_KHAU"}', code=True)
cell(162, 6, 1, '{"id":1,"username":"admin","full_name":"Quản trị viên",\n"accessToken":"JWT_ACCESS_TOKEN","tokenType":"Bearer",\n"expiresIn":3600,"expiresAt":"2026-11-04T04:00:00Z"}\nHTTP 200; thiếu dữ liệu: 400; sai thông tin: 401.', code=True)
set_paragraph(165, "3.1.5. Mapping dữ liệu API – Database")
add_paragraph_after(blocks[166], "accessToken và expiresAt do backend tạo khi đăng nhập, không phải cột của bảng users; expiresIn được tính từ thời hạn JWT cấu hình.")
cell(184, 1, 2, "value, sensor_key")
cell(184, 2, 2, "value, sensor_key")
cell(184, 3, 2, "value, sensor_key")
cell(196, 4, 1, "limit tùy chọn; mặc định 20, hợp lệ 1–100 điểm mỗi cảm biến")
cell(196, 6, 1, '{"temperature":[{"time":"2026-11-04T10:00:00","value":28.1}],\n"humidity":[{"time":"2026-11-04T10:00:00","value":70.0}],\n"light":[{"time":"2026-11-04T10:00:00","value":1500.0}]}\nMỗi mảng theo thứ tự cũ đến mới.', code=True)
cell(198, 5, 2, "sensor_key")
cell(205, 5, 1, "Bước 1: Frontend gọi GET /api/devices để lấy ID thực tế và trạng thái hai đèn.\nBước 2: Người dùng chọn ON hoặc OFF; frontend gửi POST /api/devices/{deviceId}/control với JWT.\nBước 3: Backend lưu action_history ở PENDING với requestId, publish {requestId, deviceKey, action} lên iot/device01/control và trả HTTP 202.\nBước 4: ESP32 điều khiển GPIO, publish trạng thái theo đèn và ACK lên iot/device01/ack.\nBước 5: Backend đối chiếu ACK; cập nhật SUCCESS hoặc FAILED, trạng thái đã xác nhận và phát qua STOMP. Frontend cũng có thể tra GET /api/commands/{requestId}.")
cell(205, 6, 1, "Thiết bị không tồn tại: HTTP 404; action không hợp lệ: HTTP 400; broker/ESP32 chưa sẵn sàng hoặc có lệnh đang chờ: HTTP 409. Nếu không có ACK hợp lệ trong 5 giây, lệnh thành TIMEOUT; trạng thái đèn đã xác nhận không bị ghi đè. SUCCESS chỉ xác nhận xử lý GPIO, không phát hiện LED cháy.")
cell(211, 4, 1, "deviceId là tham số đường dẫn; không có query")
cell(211, 6, 1, '{"deviceId":1,"deviceKey":"lamp1","name":"Đèn 1",\n"status":"OFF","statusConfirmedAt":"2026-11-04T10:00:00",\n"online":true}\nHTTP 200; ID không tồn tại: 404.', code=True)
cell(213, 4, 1, "deviceId là tham số đường dẫn; không có query")
cell(213, 5, 1, '{"action":"ON"}\nCó thể gửi thêm deviceId nếu trùng ID trên URL.', code=True)
cell(213, 6, 1, '{"id":2,"requestId":"da18aacc-3e28-4d83-bfac-4cce0c7965aa",\n"deviceId":1,"deviceKey":"lamp1","action":"ON",\n"status":"PENDING","timeoutSeconds":5}\nHTTP 202: mới tiếp nhận lệnh, chưa xác nhận đèn đã bật.', code=True)
device_list = clone_api_table(211, blocks[210], "Danh sách thiết bị", "GET", "/api/devices", "Không có", "Không có", '[{"id":1,"deviceKey":"lamp1","name":"Đèn 1",\n"status":"OFF","statusConfirmedAt":null,"online":true}]\nHTTP 200; lấy id thực tế cho API theo deviceId.')
command = clone_api_table(211, blocks[213], "Tra cứu kết quả lệnh", "GET", "/api/commands/{requestId}", "requestId là tham số đường dẫn", "Không có", '{"id":2,"requestId":"da18aacc-3e28-4d83-bfac-4cce0c7965aa",\n"deviceKey":"lamp1","action":"ON","status":"SUCCESS",\n"createdAt":"2026-11-04T10:00:00"}\nHTTP 200; lệnh không thuộc tài khoản hiện tại: 404.')
add_paragraph_after(device_list, "")
add_paragraph_after(blocks[213], "")
add_row(215, ["requestId", "action_history", "request_id"])
add_row(215, ["Thời điểm xác nhận trạng thái", "devices", "status_confirmed_at"])
set_paragraph(216, "3.5. Use case: Xem lịch sử giá trị cảm biến")
cell(226, 4, 1, "sensorId; sensorKey; value; from; to; page; size; sort=createdAt,desc")
cell(226, 6, 1, '{"content":[{"id":1001,"sensorId":1,"sensorName":"Temperature",\n"sensorKey":"temperature","name":"Temperature","unit":"°C",\n"value":28.5,"createdAt":"2026-11-04T10:00:00"}],\n"totalElements":1,"totalPages":1,"number":0,"size":20}\nCòn có các trường tương thích items, total và page.', code=True)
add_row(228, ["content[].sensorKey", "sensors", "sensor_key"])
add_row(228, ["content[].unit", "sensors", "unit"])
cell(239, 4, 1, "deviceId; deviceKey; action; status; from; to; page; size; sort=createdAt,desc")
cell(239, 6, 1, '{"content":[{"id":125,"deviceId":1,"username":"admin",\n"deviceKey":"lamp1","deviceName":"Đèn 1","action":"ON",\n"status":"SUCCESS","createdAt":"2026-11-04T10:00:00"}],\n"totalElements":1,"totalPages":1,"number":0,"size":20}\nCòn có các trường tương thích items, total và page.', code=True)
cell(245, 5, 0, "content[].username")
cell(245, 5, 1, "users")
cell(245, 5, 2, "username")
add_row(245, ["content[].deviceId", "action_history", "device_id"])
add_row(245, ["content[].deviceKey", "devices", "device_key"])
replace_picture(159, "login_sequence.png")
replace_picture(208, "control_sequence.png")
for index in (72, 133, 162, 182, 196, 211, 213, 215, 226, 239):
    keep_table_together(blocks[index])
keep_table_together(device_list)
keep_table_together(command)
for index in (161, 181, 195, 210, 214, 225, 238):
    blocks[index].paragraph_format.keep_with_next = True

OUT.parent.mkdir(parents=True, exist_ok=True)
doc.save(OUT)
print(OUT)
