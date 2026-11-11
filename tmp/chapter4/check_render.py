from pathlib import Path
from pdf2image import convert_from_path
from PIL import Image, ImageDraw
from pypdf import PdfReader

root = Path(__file__).resolve().parent
final = PdfReader(root / 'final.pdf')
original = PdfReader(root / 'original.pdf')
print('final_pages', len(final.pages), 'original_pages', len(original.pages))
same = []
for n in range(min(27, len(original.pages), len(final.pages))):
    if original.pages[n].extract_text() == final.pages[n].extract_text():
        same.append(n + 1)
print('unchanged_text_pages_1_27', len(same), 'mismatch', [n for n in range(1, 28) if n not in same])

out = root / 'final-render'
out.mkdir(exist_ok=True)
pages = convert_from_path(root / 'final.pdf', dpi=105)
for n, page in enumerate(pages, 1):
    page.save(out / f'page-{n}.png')
for start in range(0, len(pages), 9):
    batch = pages[start:start + 9]
    width = 375
    thumb_height = round(batch[0].height * width / batch[0].width)
    sheet = Image.new('RGB', (3 * width, 3 * (thumb_height + 30)), 'white')
    draw = ImageDraw.Draw(sheet)
    for idx, page in enumerate(batch):
        thumb = page.resize((width, thumb_height))
        x, y = (idx % 3) * width, (idx // 3) * (thumb_height + 30)
        sheet.paste(thumb, (x, y + 25))
        draw.text((x + 8, y + 5), f'Page {start + idx + 1}', fill='black')
    sheet.save(root / f'contact-{start // 9 + 1}.png')
print('rendered_pages', len(pages))
