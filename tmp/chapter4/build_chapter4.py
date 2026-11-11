from pathlib import Path
from docx import Document
from docx.enum.table import WD_CELL_VERTICAL_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor


ROOT = Path(__file__).resolve().parents[2]
MEDIA = Path(__file__).resolve().parent
SOURCE = ROOT / 'Báo cáo BTL IOT.docx'
OUTPUT = ROOT / 'Báo cáo BTL IOT - Chương 4 hoàn thiện.docx'

try:
    doc = Document(SOURCE)
except PermissionError:
    # OneDrive can hold the source briefly; the first generated copy retains
    # Chapters 1–3, so it is also a safe base for a Chapter 4 revision.
    doc = Document(OUTPUT)
chapter = next(p for p in doc.paragraphs
               if p.text.strip() == 'CHƯƠNG 4. KẾT QUẢ THỰC NGHIỆM')
body = chapter._element.getparent()
last_chapter3_heading = next(i for i, p in enumerate(doc.paragraphs)
                             if p.text.startswith('3.6.5. Mapping dữ liệu API'))
anchor_paragraph = doc.paragraphs[last_chapter3_heading + 1]
assert not anchor_paragraph.text.strip()
anchor = anchor_paragraph._element
inside_chapter = False
for block in list(body):
    if block is anchor:
        inside_chapter = True
        continue
    if inside_chapter and block.tag != qn('w:sectPr'):
        body.remove(block)


def font(run, size=13, bold=False, italic=False):
    run.font.name = 'Times New Roman'
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.italic = italic
    run.font.color.rgb = RGBColor(0, 0, 0)
    rpr = run._element.get_or_add_rPr()
    rfonts = rpr.rFonts
    if rfonts is not None:
        rfonts.set(qn('w:eastAsia'), 'Times New Roman')
    return run


def para(text='', *, indent=True, after=6, keep=False):
    p = doc.add_paragraph(style='Normal')
    p.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    pf = p.paragraph_format
    pf.first_line_indent = Cm(0.8) if indent else Cm(0)
    pf.line_spacing = 1.2
    pf.space_after = Pt(after)
    pf.keep_together = keep
    font(p.add_run(text))
    return p


def heading(text, level=2, *, new_page=False):
    style = {1: 'Heading 1', 2: 'Heading 2', 3: 'Heading 3'}[level]
    p = doc.add_paragraph(style=style)
    p.alignment = WD_ALIGN_PARAGRAPH.LEFT
    pf = p.paragraph_format
    pf.first_line_indent = Cm(0)
    pf.keep_with_next = True
    pf.keep_together = True
    pf.space_before = Pt(12 if level == 1 else 10 if level == 2 else 7)
    pf.space_after = Pt(7 if level == 1 else 5)
    if new_page:
        pf.page_break_before = True
    font(p.add_run(text), 13, bold=True)
    return p


def caption(text, *, before=4, after=9):
    p = doc.add_paragraph(style='Normal')
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.first_line_indent = Cm(0)
    p.paragraph_format.space_before = Pt(before)
    p.paragraph_format.space_after = Pt(after)
    font(p.add_run(text), 11.5, italic=True)
    return p


def figure(filename, label, width=15.2):
    p = doc.add_paragraph(style='Normal')
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.first_line_indent = Cm(0)
    p.paragraph_format.space_before = Pt(4)
    p.paragraph_format.space_after = Pt(0)
    p.paragraph_format.keep_with_next = True
    p.paragraph_format.keep_together = True
    p.add_run().add_picture(str(MEDIA / filename), width=Cm(width))
    caption(label)


def cell_border(cell, color='D9D9D9'):
    tcPr = cell._tc.get_or_add_tcPr()
    borders = tcPr.first_child_found_in('w:tcBorders')
    if borders is None:
        borders = OxmlElement('w:tcBorders')
        tcPr.append(borders)
    for edge in ('top', 'left', 'bottom', 'right'):
        node = borders.find(qn('w:' + edge))
        if node is None:
            node = OxmlElement('w:' + edge)
            borders.append(node)
        node.set(qn('w:val'), 'single')
        node.set(qn('w:sz'), '5')
        node.set(qn('w:color'), color)


def cell_margin(cell, twips=85):
    tcPr = cell._tc.get_or_add_tcPr()
    margins = tcPr.first_child_found_in('w:tcMar')
    if margins is None:
        margins = OxmlElement('w:tcMar')
        tcPr.append(margins)
    for side in ('top', 'start', 'bottom', 'end'):
        node = margins.find(qn('w:' + side))
        if node is None:
            node = OxmlElement('w:' + side)
            margins.append(node)
        node.set(qn('w:w'), str(twips))
        node.set(qn('w:type'), 'dxa')


def shade(cell, fill):
    tcPr = cell._tc.get_or_add_tcPr()
    shd = tcPr.find(qn('w:shd'))
    if shd is None:
        shd = OxmlElement('w:shd')
        tcPr.append(shd)
    shd.set(qn('w:fill'), fill)


def make_table(title, columns, rows, widths, size=10.5):
    p = doc.add_paragraph(style='Normal')
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.first_line_indent = Cm(0)
    p.paragraph_format.space_before = Pt(5)
    p.paragraph_format.space_after = Pt(4)
    p.paragraph_format.keep_with_next = True
    font(p.add_run(title), 11.5, bold=True)
    table = doc.add_table(rows=1, cols=len(columns))
    table.autofit = False
    table.style = 'Table Grid'
    for i, width in enumerate(widths):
        table.columns[i].width = Cm(width)
    for row_index, values in enumerate([columns] + rows):
        row = table.rows[0] if row_index == 0 else table.add_row()
        for i, value in enumerate(values):
            cell = row.cells[i]
            cell.width = Cm(widths[i])
            cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
            cell_margin(cell)
            cell_border(cell)
            if row_index == 0:
                shade(cell, 'E8EDF5')
            elif row_index % 2 == 0:
                shade(cell, 'F7F9FC')
            pcell = cell.paragraphs[0]
            pcell.alignment = WD_ALIGN_PARAGRAPH.LEFT
            pcell.paragraph_format.first_line_indent = Cm(0)
            pcell.paragraph_format.space_after = Pt(0)
            pcell.paragraph_format.line_spacing = 1.08
            font(pcell.add_run(str(value)), size, bold=row_index == 0)
        trPr = row._tr.get_or_add_trPr()
        cant_split = OxmlElement('w:cantSplit')
        trPr.append(cant_split)
        if row_index == 0:
            header = OxmlElement('w:tblHeader')
            trPr.append(header)
    doc.add_paragraph().paragraph_format.space_after = Pt(1)
    return table


heading('CHƯƠNG 4. KẾT QUẢ THỰC NGHIỆM', level=1, new_page=True)
para('Chương này trình bày kết quả triển khai mô hình giám sát và điều khiển hai đèn bằng ESP32, các màn hình của ứng dụng Web và kết quả kiểm thử. Phần cứng đã được vận hành với cảm biến nhiệt độ, độ ẩm, ánh sáng và hai LED. Đối với chức năng mới dùng JWT và WebSocket/STOMP, kết quả tự động được kiểm chứng trên máy chủ thử nghiệm với cơ sở dữ liệu H2 và cổng MQTT mô phỏng; các bằng chứng này được tách riêng với quan sát trên phần cứng thật.')

heading('4.1. Kết quả triển khai hệ thống', level=2)
heading('4.1.1. Mô hình phần cứng và chương trình ESP32', level=3)
para('Mô hình dùng một ESP32 với hai LED rời, một cảm biến DHT11 và một đầu đo ánh sáng nối ngõ analog. Mỗi LED có một điện trở giới hạn dòng mắc nối tiếp trong nhánh riêng. Firmware tổ chức hai đèn bằng khóa lamp1 và lamp2; khi bổ sung thiết bị trong tương lai, mã và cấu hình của thiết bị mới sẽ được khai báo tại thời điểm bổ sung. Ở phạm vi báo cáo này, hệ thống phục vụ đúng hai đèn.')
make_table('Bảng 4.1. Kết nối phần cứng trong mô hình',
           ['Thiết bị', 'Chân ESP32', 'Vai trò', 'Dữ liệu hoặc trạng thái'], [
               ['LED 1', 'GPIO25', 'Ngõ ra số', 'lamp1: ON/OFF'],
               ['LED 2', 'GPIO26', 'Ngõ ra số', 'lamp2: ON/OFF'],
               ['DHT11', 'GPIO27', 'Ngõ vào số', 'Nhiệt độ (°C), độ ẩm (%)'],
               ['Đầu đo ánh sáng', 'GPIO34', 'Ngõ vào ADC', 'Giá trị 0–4095 ADC']
           ], [3.2, 2.4, 3.2, 7.2])
figure('original-p219.png', 'Hình 4.1. Mô hình ESP32, cảm biến và hai đèn trong báo cáo gốc', width=12.0)
para('ESP32 đọc DHT11 và ADC khoảng mỗi 2 giây. Nếu lần đọc DHT11 không hợp lệ, firmware bỏ qua lần gửi đó. Giá trị ánh sáng được biểu diễn theo thang ADC 0–4095, chưa được hiệu chuẩn thành đơn vị lux. Việc ghi nhận một lệnh SUCCESS chỉ cho biết ESP32 đã xử lý GPIO và gửi xác nhận; hệ thống không có cảm biến độc lập để chứng minh LED phát sáng.')

heading('4.1.2. Truyền thông, lưu trữ và máy chủ', level=3)
para('ESP32 dùng định danh device01 và kết nối Mosquitto qua MQTT (cấu hình mặc định cổng 1001). Bản tin số đo đi qua iot/device01/sensor. Backend gửi lệnh điều khiển lên iot/device01/control, gồm requestId, deviceKey và action. ESP32 trả phản hồi qua iot/device01/ack; trạng thái riêng của từng đèn được phát ở iot/device01/status/lamp1 và iot/device01/status/lamp2. Chủ đề iot/device01/availability thông báo ONLINE/OFFLINE. Trạng thái đèn và kết nối được giữ lại trên broker để đồng bộ khi kết nối lại; phản hồi lệnh không được giữ lại.')
para('Spring Boot tiếp nhận MQTT, ghi số đo và lịch sử lệnh vào MySQL iot_db. Các bảng chính là users, sensors, sensor_data, devices và action_history. requestId là khóa ghép một lệnh Web với phản hồi từ ESP32. Backend chỉ coi lệnh hoàn tất sau khi nhận phản hồi tương ứng hoặc hết thời gian chờ; khi TIMEOUT, trạng thái ON/OFF cuối cùng đã được xác nhận vẫn được giữ nguyên.')
make_table('Bảng 4.2. Thành phần phần mềm đã triển khai',
           ['Thành phần', 'Kết quả triển khai', 'Điểm kiểm chứng'], [
               ['ESP32', 'Đọc ba đại lượng, điều khiển hai GPIO và gửi ACK', 'Firmware sketch_sep11a.ino; lần chạy phần cứng'],
               ['Mosquitto', 'Chuyển bản tin cảm biến, lệnh, ACK và trạng thái', 'Luồng MQTT theo device01'],
               ['MySQL', 'Lưu thiết bị, cảm biến, số đo, người dùng, lịch sử', 'Các bảng trong iot_db'],
               ['Spring Boot', 'REST, JWT Bearer, STOMP/WebSocket', 'Bộ thử HTTP/STOMP'],
               ['Web', 'Đăng nhập, Dashboard, tra cứu và hồ sơ', 'Ảnh giao diện và thử nghiệm trình duyệt']
           ], [3.0, 7.7, 5.3])
para('Máy chủ cung cấp giao diện HTML/CSS/JavaScript cùng nguồn gốc với API tại cổng 8080. Sau khi đăng nhập, client gửi JWT Bearer cho REST và trong khung STOMP CONNECT. Backend đẩy thay đổi qua /topic/sensor-data, /topic/devices, /topic/availability và kênh riêng /user/queue/commands. Giao diện không cần tải lại trang để nhận số đo hoặc kết quả lệnh mới.')

heading('4.2. Kết quả giao diện và chức năng', level=2)
para('Các hình trong mục này thể hiện mã giao diện hiện hành. Hình Dashboard được lấy từ ảnh chụp phiên chạy với ESP32 trong báo cáo gốc. Ảnh đăng nhập, dữ liệu cảm biến, lịch sử và hồ sơ được dựng từ giao diện hiện hành với dữ liệu minh họa trong môi trường thử, không dùng để khẳng định số đo hoặc lệnh thực tế tại thời điểm chụp.', keep=True)

heading('a. Màn hình đăng nhập', level=3)
para('Người dùng nhập tên đăng nhập và mật khẩu; frontend gửi JSON tới POST /api/auth/login. Khi xác thực thành công, backend trả JWT cùng thời hạn. Frontend lưu JWT trong sessionStorage của tab, gắn Bearer cho những API được bảo vệ và chuyển về trang đăng nhập khi token hết hạn hoặc nhận HTTP 401. Nút Đăng xuất xóa token trong tab và ngắt kết nối WebSocket.')
figure('ui-login.png', 'Hình 4.2. Màn hình đăng nhập của giao diện hiện hành', width=10.8)

heading('b. Dashboard và điều khiển hai đèn', level=3)
para('Dashboard hiển thị nhiệt độ, độ ẩm, ánh sáng, trạng thái kết nối ESP32, hai đèn và biểu đồ gần nhất. Ở thời điểm ảnh nguồn được chụp, giao diện hiển thị 29,8 °C, 53% và 2178 ADC; đây là một ảnh chụp đơn lẻ, không phải phép đo độ chính xác. Nút bật/tắt tạo lệnh HTTP 202 ở trạng thái PENDING. Kết quả cuối cùng được nhận qua STOMP hoặc tra cứu bằng requestId, vì vậy giao diện phân biệt việc gửi lệnh với việc ESP32 xác nhận.')
figure('original-p223.png', 'Hình 4.3. Dashboard trong ảnh chụp phiên chạy thực tế của báo cáo gốc', width=15.5)

heading('c. Màn hình dữ liệu cảm biến', level=3)
para('Màn hình hiển thị các bản ghi theo trang với ID, loại cảm biến, giá trị, đơn vị và thời gian. Người dùng có thể lọc theo cảm biến, giá trị chính xác hoặc khoảng thời gian tương ứng với ngày, phút hay giây đã nhập; kết quả chỉ gồm các bản ghi trong khoảng này. Mỗi thời điểm có nút sao chép. Dữ liệu ảnh dưới đây là dữ liệu minh họa để xem bố cục, gồm ba loại cảm biến của mô hình.')
figure('ui-sensor.png', 'Hình 4.4. Màn hình dữ liệu cảm biến với dữ liệu minh họa', width=15.5)

heading('d. Màn hình lịch sử điều khiển', level=3)
para('Lịch sử ghi người thao tác, thiết bị, hành động ON/OFF, kết quả và thời gian. Bộ lọc hỗ trợ thiết bị, hành động, trạng thái và thời gian; bảng có phân trang, sắp xếp và sao chép thời điểm. Các trạng thái PENDING, SUCCESS, FAILED và TIMEOUT phản ánh vòng đời lệnh, trong đó TIMEOUT không tự đổi trạng thái đèn đã xác nhận trước đó.', keep=True)
figure('ui-history.png', 'Hình 4.5. Màn hình lịch sử điều khiển với dữ liệu minh họa', width=15.5)

heading('e. Màn hình hồ sơ cá nhân', level=3)
para('Trang hồ sơ trình bày thông tin sinh viên và các liên kết tài liệu, thiết kế Figma, Postman. Liên kết Figma và Postman dẫn tới tài nguyên đã được cung cấp trong dự án; mục GitHub và Google Drive trên giao diện hiện vẫn là liên kết chung, cần thay bằng địa chỉ dự án cụ thể khi công bố. Trang này là phần hiển thị thông tin, không phải chức năng chỉnh sửa hồ sơ trong cơ sở dữ liệu.')
figure('ui-profile.png', 'Hình 4.6. Màn hình hồ sơ cá nhân của giao diện hiện hành', width=15.5)

heading('4.3. Kết quả kiểm thử', level=2)
para('Kiểm thử tự động được chạy trên ứng dụng Spring Boot ở cổng ngẫu nhiên, cơ sở dữ liệu H2 ở chế độ thử nghiệm và MQTT gateway mô phỏng. Kết quả gần nhất: 16 phép thử đạt, không có lỗi hoặc thất bại; một phép thử phụ thuộc broker thật được bỏ qua theo điều kiện môi trường. Bài thử trình duyệt Edge kiểm tra đường đi đăng nhập, Bearer cho REST, STOMP CONNECT, lọc thời gian, tải lại trang và đăng xuất. Các phép thử này xác minh hành vi phần mềm; chúng không thay thế đo kiểm phần cứng sau lần chuyển sang JWT.')
make_table('Bảng 4.3. Đối chiếu chức năng API và kết quả thử',
           ['Chức năng', 'Đầu vào hoặc tình huống', 'Kết quả quan sát'], [
               ['Đăng nhập', 'POST /api/auth/login: thiếu mật khẩu; sai mật khẩu; hợp lệ', 'Lần lượt HTTP 400, 401, 200; trả JWT Bearer, không tạo cookie phiên'],
               ['Bảo vệ REST', 'GET /api/devices thiếu, sai hoặc hết hạn JWT', 'HTTP 401; JWT hợp lệ đọc được hai thiết bị'],
               ['Dữ liệu cảm biến', 'Bản tin MQTT có nhiệt độ, độ ẩm, ánh sáng', 'Ghi ba bản ghi; latest, chart và history phản ánh dữ liệu; STOMP đẩy sau commit'],
               ['Trạng thái thiết bị', 'ONLINE và trạng thái lamp1 OFF', 'GET /api/devices/{deviceId}/status trả OFF đã xác nhận'],
               ['Điều khiển', 'POST /api/devices/{deviceId}/control với ON', 'HTTP 202, PENDING và requestId; ACK hợp lệ đổi kết quả thành SUCCESS'],
               ['Tra cứu lệnh', 'GET /api/commands/{requestId} bằng tài khoản khác', 'HTTP 404, không lộ lệnh của người dùng trước'],
               ['Lịch sử điều khiển', 'GET /api/devices/action-history sau ACK', 'Bản ghi có trạng thái SUCCESS'],
               ['Hết thời gian chờ', 'Gửi OFF nhưng không có ACK', 'TIMEOUT sau thời hạn; trạng thái ON đã xác nhận không bị ghi đè'],
               ['Bảo vệ STOMP', 'CONNECT thiếu/hết hạn JWT; client SEND trái phép', 'Bị từ chối; kết nối đã xác thực đóng khi JWT hết hạn']
           ], [3.0, 6.3, 6.7], size=10.1)
para('Các API GET /api/sensor-data/latest, /api/sensor-data/chart, /api/sensor-data/history và GET /api/devices/action-history có đường dẫn đúng với mã hiện hành. Tham số phân trang dùng page bắt đầu từ 0, size từ 1 đến 100; biểu đồ giới hạn số điểm mỗi cảm biến. Các truy vấn từ thời gian đến thời gian bao gồm cả hai mốc. Kết quả rỗng của danh sách là HTTP 200 với nội dung rỗng, không phải lỗi 404.')
para('Trong lần thử phần cứng trước đó, người thực hiện đã xác nhận ESP32 gửi được số đo vào iot_db và hai đèn có thể bật sáng sau khi thay LED hỏng, mắc điện trở nối tiếp. Bằng chứng này thuộc quá trình tích hợp thực tế trước khi đổi sang JWT; Chương 4 không suy diễn rằng toàn bộ kịch bản JWT đã được chạy lại với broker, cơ sở dữ liệu và ESP32 thật.')

heading('4.4. Đánh giá kết quả', level=2)
make_table('Bảng 4.4. Mức độ đáp ứng yêu cầu và giới hạn',
           ['Yêu cầu', 'Kết quả đạt được', 'Giới hạn cần lưu ý'], [
               ['Giám sát ba đại lượng', 'ESP32 phát bản tin; backend lưu và hiển thị theo thời gian', 'Ánh sáng ở thang ADC, chưa hiệu chuẩn lux'],
               ['Điều khiển hai đèn', 'lamp1 và lamp2 có lệnh, ACK, trạng thái và lịch sử riêng', 'SUCCESS xác nhận GPIO, không đo ánh sáng LED'],
               ['Cập nhật thời gian thực', 'Backend đẩy sự kiện qua WebSocket/STOMP sau khi ghi dữ liệu', 'Thử tự động trong môi trường giả lập MQTT'],
               ['Tra cứu dữ liệu', 'Lọc, sắp xếp, phân trang, sao chép thời gian', 'Chưa đánh giá tải lớn và vận hành dài hạn'],
               ['Bảo vệ hệ thống', 'REST và STOMP dùng JWT Bearer; API đăng nhập công khai', 'Chưa có refresh token hoặc thu hồi token phía máy chủ']
           ], [3.3, 6.5, 6.2], size=10.2)
para('Kết quả cho thấy mô hình đã đáp ứng các chức năng cốt lõi của đề tài trong phạm vi hai đèn: thu thập số đo, lưu trữ, quan sát qua Web, gửi lệnh và theo dõi xác nhận. Phần thử tự động chứng minh các luồng API, JWT và STOMP hoạt động trong môi trường thử, còn lần chạy phần cứng chứng minh đường truyền dữ liệu và thao tác đèn ở cấu hình đã kiểm tra. Để đánh giá độ tin cậy thực tế sau khi đổi xác thực, cần chạy lại kiểm thử đầu cuối với broker, MySQL và ESP32 thật; nếu muốn phát hiện LED cháy, phải bổ sung phần cứng đo dòng hoặc ánh sáng riêng.')

doc.save(OUTPUT)
print(OUTPUT)
print('paragraphs', len(doc.paragraphs), 'tables', len(doc.tables))
