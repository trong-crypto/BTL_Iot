from pathlib import Path
from pdf2image import convert_from_path
from PIL import Image, ImageDraw
from pypdf import PdfReader

root = Path(__file__).resolve().parent
pdf = root / "report_corrected.pdf"
pages = convert_from_path(pdf, dpi=110)
output = root / "render"
output.mkdir(exist_ok=True)
for i, page in enumerate(pages, 1):
    page.save(output / f"page-{i}.png")
for start in range(0, len(pages), 9):
    batch = pages[start:start+9]
    width = 375
    height = round(batch[0].height * width / batch[0].width)
    sheet = Image.new("RGB", (3*width, 3*(height+30)), "white")
    draw = ImageDraw.Draw(sheet)
    for index, page in enumerate(batch):
        thumb = page.resize((width,height))
        x = index%3*width
        y = index//3*(height+30)
        sheet.paste(thumb,(x,y+25))
        draw.text((x+8,y+5),f"Page {start+index+1}", fill="black")
    sheet.save(root/f"contact-{start//9+1}.png")
print("pages", len(pages), "pypdf", len(PdfReader(pdf).pages))
