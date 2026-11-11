from docx import Document
from docx.table import Table
from docx.text.paragraph import Paragraph

path = r"C:\Users\abc\OneDrive\Documents\IotProject\Báo cáo BTL IOT - Chương 4 hoàn thiện.docx"
doc = Document(path)

for i, block in enumerate(doc.iter_inner_content()):
    if isinstance(block, Paragraph):
        line = block.text.strip().replace("\n", " | ")
        if line:
            print(f"P {i:03d} [{block.style.name}] {line[:260]}")
    elif isinstance(block, Table):
        print(f"T {i:03d} [{len(block.rows)}x{len(block.columns)}]")
        for j, row in enumerate(block.rows):
            vals = [cell.text.strip().replace("\n", " / ") for cell in row.cells]
            print(f"  {j:02d}: " + " || ".join(vals)[:500])
