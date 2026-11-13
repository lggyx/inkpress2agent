-- V21 摘要模板 GEO 结构强化（论文 4.5/6.6）：geoSummary 要求「结论先行 + 要点分条」，
-- 使生成式引擎可直接提取核心结论与要点；JSON 字段契约不变（seoDescription/geoSummary/tags）。
UPDATE prompt_template
SET content = '请为以下 Markdown 文章生成两类摘要和标签，严格按 JSON 输出（不要输出其他内容）：\n{"seoDescription":"面向搜索引擎的 meta 描述，150 字以内，自然嵌入文章关键词","geoSummary":"面向生成式引擎/AI 问答引用的摘要，120 字以内：第一句给出全文核心结论（结论先行），随后用「要点：①…②…③…」分条列出 3 个关键要点，信息密度高","tags":["3-5 个关键词标签"]}\n\n文章：\n{{content}}',
    updated_at = NOW()
WHERE category = 'summary';
