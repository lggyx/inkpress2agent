$word = New-Object -ComObject Word.Application
$word.Visible = $false
try {
    $docPath = (Resolve-Path $args[0]).Path
    $pdfPath = [System.IO.Path]::ChangeExtension($docPath, ".pdf")
    $doc = $word.Documents.Open($docPath)
    # 清理所有可能残留的批注，确保无论如何都不出现右侧审阅边栏
    try {
        while ($doc.Comments.Count -gt 0) {
            $doc.Comments.Item(1).Delete()
        }
    } catch {
        Write-Output "Comment cleanup notice: $_"
    }
    # 刷新并重新计算目录域与页码
    try {
        $doc.Fields.Update()
        foreach ($toc in $doc.TablesOfContents) {
            $toc.Update()
        }
    } catch {
        Write-Output "TOC update notice: $_"
    }
    # 保存更新后的 docx
    $doc.Save()
    # 导出为 PDF (17 = wdFormatPDF, Item=0 即 wdExportDocumentContent，绝不包含审阅标记/批注框)
    $doc.ExportAsFixedFormat($pdfPath, 17, $false, 0, 0, 1, 1, 0, $true, $true, 1, $true, $true, $false)
    $doc.Close([ref]$false)
    Write-Output "Saved PDF via Word: $pdfPath"
} finally {
    $word.Quit()
    [System.Runtime.InteropServices.Marshal]::ReleaseComObject($word) | Out-Null
    [System.GC]::Collect()
    [System.GC]::WaitForPendingFinalizers()
}
