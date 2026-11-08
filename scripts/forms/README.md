# 过程材料生成链（scripts/forms）

学校过程材料（任务书 / 开题报告 / 中期报告 / 各类评分表）的 docx 生成工具链。
**内容源在 `docs/`（开题报告.md 等），本目录只放生成逻辑。**

## 一键生成

```powershell
powershell -ExecutionPolicy Bypass -File scripts\forms\build_forms.ps1
```

依次执行：生成 docx → LibreOffice 渲染 PDF（一并作为交付附件）→
按渲染结果回填目录页码 → 程序化校验（40+ 项断言，任一失败即非零退出）。

## 文件说明

| 文件 | 作用 |
|---|---|
| `build_forms.py` | 任务书（跳过）/ 开题报告 / 中期报告：清模板残留、按节填内容、插入技术路线表格、写元数据、设置打开时更新目录域 |
| `build_forms2.py` | 开题检查表 / 进展中期小结表 / 周工作记录 / 答辩记录表（表格型表单） |
| `patch_toc_pages.py` | 用 LibreOffice 渲染结果回填目录（TOC 域缓存页码），使不更新域的阅读器也能显示正确页码 |
| `verify_kaiti_docx.py` | 开题报告校验：模板残留 / 空节 / 参考文献唯一性 / 表格 / 元数据 / 目录页码 / 正文格式 / 封面 |
| `build_forms.ps1` | 上述步骤的一键编排 |

## 学校模板（不入库）

学校下发的模板原件放在 `scripts/forms/templates/`（`.gitignore` 已排除）。
新环境克隆后，从学校过程管理系统下载对应模板放入该目录即可复现全部材料：

```
scripts/forms/templates/
├── docx/                                  # 模板 docx 版（pandoc 提取自 .doc）
│   ├── 2.开题报告（2025）.docx
│   └── ...
└── （学校）2025届毕业论文过程材料.zip      # 学校原始文件包
```

## 交付物

生成结果在 `docs/过程材料/`（已填写的 docx + 同名 PDF）：

- `1.任务书-已填写.docx`（正式签字版，脚本不覆盖）
- `2.开题报告-已填写.docx` / `.pdf`
- `3.开题检查表-已填写.docx`
- `4.中期报告-已填写.docx` / `.pdf`
- `5.进展中期小结表-已填写.docx`
- `6.周工作记录-已填写.docx`
- `10.答辩记录表-已填写.docx`
