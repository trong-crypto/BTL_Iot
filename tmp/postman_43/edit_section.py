from copy import deepcopy
from pathlib import Path

from docx import Document
from docx.oxml.ns import qn
from docx.table import Table
from docx.text.paragraph import Paragraph


BASE = Path(__file__).parent
SOURCE = BASE / "source_latest.docx"
OUTPUT = BASE / "report_postman.docx"


def replace_paragraph(paragraph: Paragraph, value: str, size: float = 13, bold: bool = False):
    paragraph.clear()
    run = paragraph.add_run(value)
    run.font.name = "Times New Roman"
    run.font.size = __import__("docx").shared.Pt(size)
    run.bold = bold


def replace_cell(cell, value: str, bold: bool = False):
    paragraph = cell.paragraphs[0]
    replace_paragraph(paragraph, value, 10, bold)


doc = Document(SOURCE)
body = list(doc.element.body)
assert Paragraph(body[355], doc).text == "4.3. Kết quả kiểm thử"
assert Paragraph(body[357], doc).text.startswith("Bảng 4.3.")
assert Paragraph(body[362], doc).text == "4.4. Đánh giá kết quả"

replace_paragraph(
    Paragraph(body[356], doc),
    "Người thực hiện gửi sáu yêu cầu REST bằng Postman tới backend tại "
    "http://127.0.0.1:8080. Yêu cầu đăng nhập dùng JSON để nhận JWT; năm yêu cầu "
    "GET tiếp theo dùng Bearer Token. Bảng 4.3 ghi lại kết quả trong các ảnh chụp; "
    "mật khẩu và giá trị token được lược bỏ khỏi báo cáo.",
)
replace_paragraph(
    Paragraph(body[357], doc),
    "Bảng 4.3. Kết quả thực nghiệm sáu API bằng Postman",
    11.5,
    True,
)

table = Table(body[358], doc)
results = [
    (
        "Chức năng",
        "Yêu cầu trên Postman",
        "Kết quả quan sát",
    ),
    (
        "Đăng nhập",
        "POST /api/auth/login",
        "200 OK; admin (id 1); trả accessToken, tokenType = Bearer, "
        "expiresIn = 3600 giây.",
    ),
    (
        "Số đo mới nhất",
        "GET /api/sensor-data/latest",
        "200 OK; 29,8 °C; 55 %; 1453 ADC; "
        "time = 2026-11-04T15:16:55.890575.",
    ),
    (
        "Danh sách thiết bị",
        "GET /api/devices",
        "200 OK; lamp1 và lamp2 đều OFF; lamp1 có online = false.",
    ),
    (
        "Lịch sử cảm biến",
        "GET /api/sensor-data/history?page=0&size=20&sort=createdAt,desc",
        "200 OK; items[0]: id 10890, light = 1453 ADC, "
        "createdAt = 2026-11-04T15:16:55.890575.",
    ),
    (
        "Dữ liệu biểu đồ",
        "GET /api/sensor-data/chart?limit=20",
        "200 OK; mảng temperature có các cặp time, value; "
        "một điểm đạt 29,3 °C.",
    ),
    (
        "Trạng thái đèn",
        "GET /api/devices/1/status",
        "200 OK; lamp1 OFF, statusConfirmedAt = "
        "2026-11-04T15:12:20 và online = false.",
    ),
]
assert len(table.rows) >= len(results)
for index, values in enumerate(results):
    for cell, value in zip(table.rows[index].cells, values):
        replace_cell(cell, value, bold=index == 0)
for row in list(table.rows)[len(results):]:
    table._tbl.remove(row._tr)

replace_paragraph(
    Paragraph(body[360], doc),
    "Cả sáu yêu cầu trong ảnh đều trả HTTP 200. Giá trị ánh sáng 1453 ADC và mốc "
    "2026-11-04T15:16:55.890575 ở API latest trùng với bản ghi đầu của API "
    "history, cho thấy hai cách truy vấn cùng phản ánh số đo đã lưu. API chart trả "
    "dãy điểm để vẽ biểu đồ. Trạng thái online = false ở lamp1 cho biết bộ điều "
    "khiển không được ghi nhận trực tuyến tại thời điểm truy vấn; status = OFF là "
    "trạng thái đã được xác nhận trước đó, không phải bằng chứng LED đang sáng hay tắt thực tế.",
)
Paragraph(body[360], doc).paragraph_format.keep_together = True

limitations = Paragraph(body[361], doc).insert_paragraph_before()
replace_paragraph(
    limitations,
    "Phạm vi thực nghiệm Postman trong các ảnh chỉ gồm đăng nhập và năm API đọc dữ "
    "liệu. Ảnh không thể hiện thử lệnh bật/tắt đèn, ACK từ ESP32, trường hợp JWT "
    "không hợp lệ hoặc cập nhật qua WebSocket/STOMP. Các kết luận về những luồng "
    "này cần dựa trên phép thử riêng; dữ liệu ngày 04/11 là dữ liệu đã lưu và "
    "không chứng minh cảm biến đang truyền trực tiếp khi chụp ảnh.",
)

doc.save(OUTPUT)
check = Document(OUTPUT)
all_text = "\n".join(p.text for p in check.paragraphs)
assert "Bảng 4.3. Kết quả thực nghiệm sáu API bằng Postman" in all_text
assert "mật khẩu và giá trị token được lược bỏ" in all_text
assert "Đối chiếu chức năng API và kết quả thử" not in all_text
print(OUTPUT)
print(OUTPUT.stat().st_size)
