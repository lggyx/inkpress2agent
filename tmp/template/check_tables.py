"""检查 PPT 表格的实际属性：行高、边距、字体、填充"""
from pptx import Presentation
from pptx.util import Emu

prs = Presentation('docs/PPT/InkPress答辩PPT.pptx')

for si, slide in enumerate(prs.slides, 1):
    for shape in slide.shapes:
        if shape.has_table:
            tbl = shape.table
            print(f'=== S{si} 表格: {len(tbl.rows)}行 x {len(tbl.columns)}列 ===')
            # 列宽
            col_w = [round(c.width / 914400, 2) for c in tbl.columns]
            print(f'  列宽(英寸): {col_w}  合计 {sum(col_w):.2f}')
            # 行高
            row_h = [round(r.height / 914400, 2) for r in tbl.rows]
            print(f'  行高(英寸): {row_h}')
            # 单元格属性抽查（第 1 个数据行）
            try:
                cell = tbl.cell(1, 0)
                print(f'  单元格(1,0) 边距: L={cell.margin_left/914400:.3f} R={cell.margin_right/914400:.3f} T={cell.margin_top/914400:.3f} B={cell.margin_bottom/914400:.3f} in')
                for p in cell.text_frame.paragraphs:
                    for r in p.runs:
                        print(f'  单元格(1,0) 字体: {r.font.name} {r.font.size.pt if r.font.size else "?"}pt')
                        break
                    break
            except Exception as e:
                print(f'  单元格检查失败: {e}')
            # 填充抽查
            for ri in range(min(2, len(tbl.rows))):
                fills = []
                for ci in range(len(tbl.columns)):
                    c = tbl.cell(ri, ci)
                    try:
                        fills.append(str(c.fill.fore_color.rgb) if c.fill.type is not None and c.fill.type == 1 else 'none')
                    except Exception:
                        fills.append('?')
                print(f'  行{ri} 填充: {fills}')
            print()
