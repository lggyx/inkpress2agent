-- 修复：memory_distill 模板缺少 {{content}} 占位符，导致 AiConfigService.render() 注入的
-- 反馈摘要（buildDigest 产出的采纳/拒绝/纠正记录）整个丢失——模型只收到模板说明，
-- 曾把「缺少本次写作反馈数据」的道歉文本当作蒸馏记忆入库（2026-11-14 彩排实测）。
-- 幂等：已含占位符的模板不重复改写（管理员手工改过的版本不会被覆盖）。
UPDATE prompt_template
SET content = '你是博客作者的写作记忆蒸馏器。下面是作者在本次写作运行中对 AI 输出的全部反馈（👍 采纳 / 👎 拒绝 / 采纳后修改幅度 / 作者的文字纠正）。请提炼出能指导未来写作的持久偏好规则。\n\n本次运行反馈：\n{{content}}\n\n要求：1) 每条不超过 40 字，具体、可执行、可验证；2) 合并同类反馈，剔除只与本文相关的一次性内容；3) 只输出 JSON 数组本身，不要任何解释或代码块标记，格式为每项 {"category":"...","content":"..."}。category 只能取 STYLE / TONE / STRUCTURE / LENGTH / FACT 之一。'
WHERE category = 'memory_distill'
  AND content NOT LIKE '%{{content}}%';
