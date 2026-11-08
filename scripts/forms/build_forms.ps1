# 过程材料 docx 生成链（一键）
# 步骤：生成 -> LibreOffice 渲染 -> 按渲染结果回填目录页码 -> 程序化校验
# 前置：学校模板原件需放在 scripts/forms/templates/（不入库，从学校过程系统下载）
# 用法：powershell -ExecutionPolicy Bypass -File scripts\forms\build_forms.ps1
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))

$soffice = 'C:\Program Files\LibreOffice\program\soffice.exe'
$outDir = Join-Path (Get-Location) 'docs/过程材料'

function Wait-Pdf($path, $timeoutSec = 120) {
    for ($i = 0; $i -lt $timeoutSec; $i++) {
        if (Test-Path $path) { return }
        Start-Sleep -Seconds 1
    }
    throw "PDF 未生成: $path"
}

Write-Host '== 1/4 生成 docx ==' -ForegroundColor Cyan
python -X utf8 scripts/forms/build_forms.py
if ($LASTEXITCODE -ne 0) { throw '生成失败' }
python -X utf8 scripts/forms/build_forms2.py
if ($LASTEXITCODE -ne 0) { throw '表单生成失败' }

Write-Host '== 2/4 LibreOffice 渲染（PDF 一并作为交付附件） ==' -ForegroundColor Cyan
$kaitiPdf = Join-Path $outDir '2.开题报告-已填写.pdf'
$midPdf = Join-Path $outDir '4.中期报告-已填写.pdf'
if (Test-Path $kaitiPdf) { Remove-Item $kaitiPdf }
if (Test-Path $midPdf) { Remove-Item $midPdf }
& $soffice --headless --convert-to pdf --outdir $outDir (Join-Path $outDir '2.开题报告-已填写.docx')
Wait-Pdf $kaitiPdf
& $soffice --headless --convert-to pdf --outdir $outDir (Join-Path $outDir '4.中期报告-已填写.docx')
Wait-Pdf $midPdf

Write-Host '== 3/4 回填目录页码 ==' -ForegroundColor Cyan
python -X utf8 scripts/forms/patch_toc_pages.py
if ($LASTEXITCODE -ne 0) { throw '开题报告目录回填失败' }
python -X utf8 scripts/forms/patch_toc_pages.py (Join-Path $outDir '4.中期报告-已填写.docx') $midPdf
if ($LASTEXITCODE -ne 0) { throw '中期报告目录回填失败' }

Write-Host '== 4/4 程序化校验 ==' -ForegroundColor Cyan
python -X utf8 scripts/forms/verify_kaiti_docx.py
if ($LASTEXITCODE -ne 0) { throw '校验未通过' }

Write-Host '过程材料生成链完成（交付物在 docs/过程材料/）' -ForegroundColor Green
