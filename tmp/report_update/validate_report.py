import json
from pathlib import Path
from docx import Document

path = Path(__file__).parent / "report_corrected.docx"
doc = Document(path)
text = "\n".join([p.text for p in doc.paragraphs] +
                 [c.text for t in doc.tables for row in t.rows for c in row.cells])
for stale in ("Thymeleaf", "Bootstrap", "iot/device01/control/lamp1",
              "iot/device01/control/lamp2", "content[].userId"):
    print("stale", stale, text.count(stale))

decoder = json.JSONDecoder()
api_tables = 0
invalid = []
for table in doc.tables:
    rows = [[c.text.strip() for c in row.cells] for row in table.rows]
    if len(rows) == 7 and len(rows[0]) == 2 and rows[0][0] == "Thuộc tính" and rows[2][0] == "Method":
        api_tables += 1
        name = rows[1][1]
        for key in ("Body", "Response"):
            value = next((r[1] for r in rows if r[0] == key), "")
            if value.startswith(("{", "[")):
                try:
                    decoder.raw_decode(value)
                except json.JSONDecodeError as ex:
                    invalid.append((name, key, str(ex)))
print("api_tables", api_tables, "invalid_json", invalid)
print("images", len(doc.inline_shapes), "tables", len(doc.tables))
