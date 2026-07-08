import zlib

lines = [
    "DocuMind Internal Policy Document",
    "",
    "Section 1: Vacation Policy",
    "All full-time employees at DocuMind are entitled to 22 days of paid",
    "vacation per calendar year, accrued monthly. Unused vacation days can",
    "roll over up to a maximum of 5 days into the next year.",
    "",
    "Section 2: Remote Work Policy",
    "Employees may work remotely up to 3 days per week. Any fully remote",
    "arrangement requires manager approval and a signed remote work agreement.",
    "",
    "Section 3: Expense Reimbursement",
    "Business expenses under 50 dollars do not require pre-approval. Expenses",
    "over 50 dollars require manager sign-off before the purchase is made. All",
    "reimbursement requests must be submitted within 30 days of the expense.",
]

content_lines = ["BT", "/F1 12 Tf", "50 780 Td", "14 TL"]
for i, line in enumerate(lines):
    escaped = line.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
    if i == 0:
        content_lines.append(f"({escaped}) Tj")
    else:
        content_lines.append("T*")
        content_lines.append(f"({escaped}) Tj")
content_lines.append("ET")
content_stream = "\n".join(content_lines).encode("latin-1")

objects = []
objects.append(b"<< /Type /Catalog /Pages 2 0 R >>")
objects.append(b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>")
objects.append(b"<< /Type /Page /Parent 2 0 R /Resources << /Font << /F1 4 0 R >> >> /MediaBox [0 0 612 792] /Contents 5 0 R >>")
objects.append(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")
compressed = content_stream
objects.append(b"<< /Length " + str(len(compressed)).encode() + b" >>\nstream\n" + compressed + b"\nendstream")

pdf = bytearray()
pdf += b"%PDF-1.4\n"
offsets = [0]
for idx, obj in enumerate(objects, start=1):
    offsets.append(len(pdf))
    pdf += f"{idx} 0 obj\n".encode() + obj + b"\nendobj\n"

xref_offset = len(pdf)
n = len(objects) + 1
pdf += f"xref\n0 {n}\n".encode()
pdf += b"0000000000 65535 f \n"
for off in offsets[1:]:
    pdf += f"{off:010d} 00000 n \n".encode()
pdf += b"trailer\n"
pdf += f"<< /Size {n} /Root 1 0 R >>\n".encode()
pdf += b"startxref\n"
pdf += str(xref_offset).encode() + b"\n"
pdf += b"%%EOF"

with open("test-doc.pdf", "wb") as f:
    f.write(pdf)

print("wrote", len(pdf), "bytes")
