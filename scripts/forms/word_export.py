# -*- coding: utf-8 -*-
import subprocess
import os

ps1_content = """
$word = New-Object -ComObject Word.Application
$word.Visible = $false
try {
    Write-Output "Word loaded successfully"
    $docPath = Resolve-Path $args[0]
    $pdfPath = [System.IO.Path]::ChangeExtension($docPath, ".pdf")
    $doc = $word.Documents.Open($docPath)
    # 17 = wdFormatPDF
    $doc.SaveAs([ref]$pdfPath, [ref]17)
    $doc.Close([ref]$false)
    Write-Output "Saved PDF: $pdfPath"
} finally {
    $word.Quit()
    [System.Runtime.InteropServices.Marshal]::ReleaseComObject($word) | Out-Null
    [System.GC]::Collect()
    [System.GC]::WaitForPendingFinalizers()
}
"""

with open('scripts/forms/convert_word.ps1', 'w', encoding='utf-8') as f:
    f.write(ps1_content)

print("convert_word.ps1 written successfully!")
