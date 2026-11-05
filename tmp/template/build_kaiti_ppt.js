/**
 * 开题答辩 PPT（2026.11 开题阶段视角，与毕业答辩 PPT 区分）
 * 结构：封面→目录→选题背景→国内外现状→研究内容→技术路线→
 *       创新点预研→进度安排→已具备条件→困难与对策→结语
 * 用法：node tmp/template/build_kaiti_ppt.js
 */
const pptxgen = require('pptxgenjs')
const path = require('path')

const ROOT = path.resolve(__dirname, '..', '..')
const DIAG = path.join(ROOT, 'docs', 'diagrams', 'png')
const OUT = path.join(ROOT, 'docs', 'PPT', 'InkPress开题答辩PPT.pptx')

const DARK = {
  bg: '0B0B0C', surface: '16161A', line: '2C2C33',
  text: 'F2F2F0', dim: '9A9AA2', faint: '62626A', accent: 'CCFF00'
}
const LIGHT = {
  bg: 'F7F7F8', surface: 'FFFFFF', line: 'E2E2E6',
  text: '1A1A1E', dim: '5C5C66', faint: '9A9AA2', accent: '6E8F16'
}
const F = '微软雅黑'

const pres = new pptxgen()
pres.layout = 'LAYOUT_WIDE'
pres.title = '开题答辩——基于Spring Boot+Vue的个人博客与文章管理系统'
const W = 13.33, M = 0.5

function txt(slide, text, o) {
  slide.addText(text, Object.assign({ fontFace: F, margin: 0 }, o))
}

function pageTitle(slide, c, no, title, sub) {
  txt(slide, no, { x: M, y: 0.42, w: 1.2, h: 0.5, fontSize: 15, bold: true, color: c.accent })
  txt(slide, title, { x: M + 0.85, y: 0.4, w: W - 2 * M - 0.85, h: 0.55, fontSize: 26, bold: true, color: c.text })
  if (sub) {
    txt(slide, sub, { x: M + 0.85, y: 0.95, w: W - 2 * M - 0.85, h: 0.35, fontSize: 12.5, color: c.dim })
  }
}

function bullet(items, color) {
  const bu = () => ({ code: '2022', indent: 12 })
  return items.map(it => ({
    text: it,
    options: { bullet: bu(), breakLine: true, color: color || '4A4A55', fontSize: 13.5 }
  }))
}

function addImageContain(slide, file, x, y, maxW, maxH) {
  const fs = require('fs')
  const buf = fs.readFileSync(path.join(DIAG, file))
  const iw = buf.readUInt32BE(16), ih = buf.readUInt32BE(20)
  let w = maxW, h = maxW * (ih / iw)
  if (h > maxH) { h = maxH; w = maxH * (iw / ih) }
  slide.addImage({ path: path.join(DIAG, file), x: x + (maxW - w) / 2, y, w, h, sizing: { type: 'contain', w, h } })
}

// ============ S1 封面 ============
{
  const s = pres.addSlide()
  s.background = { color: DARK.bg }
  txt(s, '青岛恒星科技学院', { x: 0, y: 1.15, w: W, h: 0.5, fontSize: 18, color: DARK.dim, align: 'center', charSpacing: 6 })
  txt(s, '毕业论文（设计）开题答辩', { x: 0, y: 1.78, w: W, h: 0.5, fontSize: 15, color: DARK.accent, align: 'center', charSpacing: 8 })
  txt(s, '基于Spring Boot+Vue的个人博客与\n文章管理系统的设计与实现', {
    x: 1.0, y: 2.6, w: W - 2, h: 1.7, fontSize: 32, bold: true, color: DARK.text, align: 'center'
  })
  const info = [
    ['汇 报 人', '张德鑫'],
    ['学      号', '2505396'],
    ['专业班级', '软件工程 · 2027专升本2班'],
    ['指导教师', '王婧 副教授']
  ]
  info.forEach(([k, v], i) => {
    const x = 1.7 + i * 2.55
    txt(s, k, { x, y: 5.4, w: 2.3, h: 0.3, fontSize: 11, color: DARK.faint, align: 'center' })
    txt(s, v, { x, y: 5.72, w: 2.3, h: 0.35, fontSize: 13, bold: true, color: DARK.text, align: 'center' })
  })
  txt(s, '2026 年 11 月', { x: 0, y: 6.7, w: W, h: 0.3, fontSize: 12, color: DARK.faint, align: 'center' })
  s.addNotes('各位老师好，我是软件工程专业的张德鑫，指导教师是王婧老师。今天汇报的开题题目是《基于Spring Boot+Vue的个人博客与文章管理系统的设计与实现》。计划从背景、现状、研究内容、技术路线和进度安排五个方面汇报，请各位老师指正。')
}

// ============ S2 目录 ============
{
  const s = pres.addSlide()
  s.background = { color: DARK.bg }
  txt(s, 'CONTENTS', { x: M, y: 0.6, w: 4, h: 0.5, fontSize: 15, bold: true, color: DARK.accent, charSpacing: 6 })
  txt(s, '汇报目录', { x: M, y: 1.0, w: 6, h: 0.7, fontSize: 30, bold: true, color: DARK.text })
  const items = [
    ['01', '选题背景与意义', '为什么做这个系统'],
    ['02', '国内外研究现状', '现有方案与本课题定位'],
    ['03', '研究内容', '功能范围与用例'],
    ['04', '技术路线', '选型依据与总体方案'],
    ['05', '进度安排与条件', '26 周计划与已备资源'],
    ['06', '困难与对策', '预判风险及解决方案']
  ]
  items.forEach(([no, t, d], i) => {
    const y = 2.15 + i * 0.82
    txt(s, no, { x: M, y, w: 0.9, h: 0.5, fontSize: 20, bold: true, color: DARK.accent })
    txt(s, t, { x: M + 1.0, y: y - 0.02, w: 5.2, h: 0.4, fontSize: 17, bold: true, color: DARK.text })
    txt(s, d, { x: M + 1.0, y: y + 0.36, w: 6.5, h: 0.3, fontSize: 11.5, color: DARK.faint })
  })
  txt(s, '墨', { x: 10.6, y: 2.9, w: 2.2, h: 1.6, fontSize: 96, bold: true, color: DARK.surface, align: 'center' })
  txt(s, 'INK//PRESS', { x: 10.6, y: 4.5, w: 2.2, h: 0.35, fontSize: 12, color: DARK.faint, align: 'center', charSpacing: 4 })
  s.addNotes('汇报共六部分：背景、现状、研究内容、技术路线、进度安排，最后是预判的困难和对策。')
}

// ============ S3 选题背景（暗） ============
{
  const s = pres.addSlide()
  s.background = { color: DARK.bg }
  pageTitle(s, DARK, '01', '选题背景', '创作者的困境与大模型的新可能')
  const cards = [
    ['数据不自有', '托管平台排版受限、导出困难，内容去留自己说了不算'],
    ['工作流割裂', 'Markdown 已是技术写作主流，多数平台仍是富文本'],
    ['AI 能力缺位', '润色、摘要、打标签等机械劳动仍靠人肉完成']
  ]
  cards.forEach(([t, d], i) => {
    const x = M + i * 4.1
    txt(s, '0' + (i + 1), { x, y: 1.95, w: 3.7, h: 0.75, fontSize: 44, bold: true, color: DARK.accent })
    txt(s, t, { x, y: 2.75, w: 3.7, h: 0.4, fontSize: 18, bold: true, color: DARK.text })
    txt(s, d, { x, y: 3.2, w: 3.55, h: 0.95, fontSize: 12.5, color: DARK.dim })
  })
  txt(s, '选题意义', { x: M, y: 4.55, w: 4, h: 0.4, fontSize: 15, bold: true, color: DARK.accent })
  s.addText(bullet([
    '实用：为个人创作者提供数据自有、样式可定制的博客平台',
    '锻炼：覆盖 Java Web 主要环节，补上 AI 工程化落地的实践空白',
    '可行：技术栈主流成熟，AI 接入有免费额度，成本可控'
  ], DARK.dim), { x: M, y: 5.0, w: 12.3, h: 1.9, fontSize: 13.5, paraSpaceAfter: 7 })
  s.addNotes('背景是三个矛盾：数据不归自己、Markdown 工作流走不通、AI 能力没进博客核心功能。这个选题实际价值是给创作者一个数据自有的站点，对我个人是把 Java Web 的主要环节走一遍，特别是 AI 工程化这块课程里少有涉及的。')
}

// ============ S4 国内外现状（浅） ============
{
  const s = pres.addSlide()
  s.background = { color: LIGHT.bg }
  pageTitle(s, LIGHT, '02', '国内外研究现状', '四类代表方案各有短板')
  const rows = [
    ['WordPress', 'PHP + MySQL 单体', '生态庞大，插件齐全', '架构偏重，加载同步'],
    ['Ghost', 'Node.js 订阅制', '编辑体验好', '定制需学模板语言'],
    ['Hugo / Hexo', '静态站点生成', '速度快、成本低', '无后台，动态功能靠第三方'],
    ['Halo', 'Java 插件化', '现代化，社区活跃', 'AI 支持停留在插件层面']
  ]
  // 表头
  const widths = [1.8, 2.7, 3.5, 4.33]
  let cx = M
  ;['方案', '技术形态', '优势', '短板'].forEach((h, i) => {
    txt(s, h, { x: cx, y: 1.75, w: widths[i] - 0.12, h: 0.32, fontSize: 12.5, bold: true, color: LIGHT.text })
    cx += widths[i]
  })
  s.addShape(pres.shapes.LINE, { x: M, y: 2.11, w: W - 2 * M, h: 0, line: { color: LIGHT.text, width: 1.5 } })
  rows.forEach((row, ri) => {
    const ry = 2.23 + ri * 0.5
    let cx2 = M
    row.forEach((cell, ci) => {
      txt(s, cell, { x: cx2, y: ry, w: widths[ci] - 0.12, h: 0.42, fontSize: 12, color: ci === 0 ? LIGHT.text : LIGHT.dim })
      cx2 += widths[ci]
    })
    s.addShape(pres.shapes.LINE, { x: M, y: ry + 0.43, w: W - 2 * M, h: 0, line: { color: LIGHT.line, width: 0.75 } })
  })
  txt(s, '调研结论：前后端分离 + 数据自有 + AI 一体化三者结合的轻量系统仍然少见——这是本课题的切入点。', {
    x: M, y: 4.55, w: W - 2 * M, h: 0.6, fontSize: 14, bold: true, color: LIGHT.text
  })
  txt(s, '资料来源：W3Techs CMS 使用统计（2025）、各项目官方文档', {
    x: M, y: 6.9, w: 7, h: 0.3, fontSize: 10.5, color: LIGHT.faint
  })
  s.addNotes('调研了四类方案：WordPress 生态最大但单体架构偏重；Ghost 编辑体验好但定制门槛高；Hugo、Hexo 这类静态生成器快但没有后台；国产 Halo 插件化做得好，AI 还停留在插件层面。结论是三者结合的轻量方案还有空间，这是切入点。')
}

// ============ S5 研究内容（浅） ============
{
  const s = pres.addSlide()
  s.background = { color: LIGHT.bg }
  pageTitle(s, LIGHT, '03', '研究内容', '两类角色 + 一条智能检索主线')
  const blocks = [
    ['前台（访客）', ['文章浏览与筛选', '评论回复、点赞', '归档时间线', '阅读增强：目录/导航/进度']],
    ['后台（管理员）', ['数据看板（ECharts）', '文章管理（Markdown 编辑器）', '分类标签、评论审核', '用户与操作日志']],
    ['AI 与智能检索', ['写作辅助：润色/摘要/标签', '续写与标题生成', '语义搜索与相关推荐', 'RAG 站内问答（引用溯源）']]
  ]
  blocks.forEach(([t, items], i) => {
    const x = M + i * 4.12
    txt(s, t, { x, y: 1.8, w: 3.8, h: 0.45, fontSize: 16, bold: true, color: LIGHT.text })
    s.addShape(pres.shapes.LINE, { x, y: 2.32, w: 3.6, h: 0, line: { color: i === 2 ? LIGHT.accent : LIGHT.line, width: i === 2 ? 2 : 1 } })
    s.addText(bullet(items, LIGHT.dim), { x, y: 2.5, w: 3.7, h: 3.8, fontSize: 12, paraSpaceAfter: 8 })
  })
  txt(s, '研究重点：智能检索模块——向量模型选型、混合排序、RAG 流程的设计与实验验证。', {
    x: M, y: 6.35, w: W - 2 * M, h: 0.5, fontSize: 13.5, bold: true, color: LIGHT.accent
  })
  s.addNotes('研究内容分三块：前台给访客，核心是读和互动；后台给作者，覆盖内容生产管理；第三块 AI 和智能检索是研究重点——特别是向量模型选型、混合排序和 RAG 问答，这是本课题区别于普通博客系统的部分。')
}

// ============ S6 技术路线（浅，架构图） ============
{
  const s = pres.addSlide()
  s.background = { color: LIGHT.bg }
  pageTitle(s, LIGHT, '04', '技术路线', '前后端分离四层架构，AI 独立适配')
  addImageContain(s, '01-系统架构图.png', M, 1.55, 4.4, 5.35)
  const choices = [
    ['后端', 'Spring Boot 3.3 + MyBatis-Plus', '生态成熟，企业主流，资料最全'],
    ['前端', 'Vue 3 组合式 API + Vite', '逻辑聚合可维护，开发体验好'],
    ['认证', 'Spring Security + JWT', '无状态令牌，适合分离部署'],
    ['AI', 'OpenAI 兼容协议适配层', '换厂商只改配置，不换代码'],
    ['检索', '特征哈希向量（可切远程）', '零依赖基线，Key 后自动升级']
  ]
  choices.forEach(([t, tech, why], i) => {
    const y = 1.7 + i * 1.02
    txt(s, t, { x: 5.35, y, w: 1.2, h: 0.4, fontSize: 14.5, bold: true, color: LIGHT.text })
    txt(s, tech, { x: 6.6, y, w: 6.2, h: 0.38, fontSize: 13, bold: true, color: LIGHT.dim })
    txt(s, why, { x: 6.6, y: y + 0.4, w: 6.2, h: 0.35, fontSize: 11.5, color: LIGHT.faint })
  })
  s.addNotes('技术路线选型都给了理由：后端 Spring Boot 是企业主流；前端 Vue 3 组合式 API 适合逻辑聚合；认证用 JWT 适配分离部署；AI 走 OpenAI 兼容协议，一个适配层通吃主流厂商；检索先用零依赖的特征哈希向量做基线，有密钥后可切远程 Embedding。')
}

// ============ S7 创新点预研（暗） ============
{
  const s = pres.addSlide()
  s.background = { color: DARK.bg }
  pageTitle(s, DARK, '04', '预期创新点', '区别于普通博客系统的三个设计')
  const pts = [
    ['AI 可插拔适配层', '统一接口屏蔽厂商差异，润色/续写走 SSE 流式输出，前端打字机效果'],
    ['混合排序推荐', '相关文章 = 语义相似度 0.85 + 标签重合度 0.15，公式可解释、可调参'],
    ['RAG 站内问答', '检索增强生成：回答仅依据站内文章，附可点击引用溯源，杜绝编造']
  ]
  pts.forEach(([t, d], i) => {
    const y = 1.9 + i * 1.45
    txt(s, '0' + (i + 1), { x: M, y, w: 1.0, h: 0.6, fontSize: 30, bold: true, color: DARK.accent })
    txt(s, t, { x: M + 1.1, y: y + 0.02, w: 4.2, h: 0.45, fontSize: 17, bold: true, color: DARK.text })
    txt(s, d, { x: M + 1.1, y: y + 0.52, w: 11.0, h: 0.8, fontSize: 12.5, color: DARK.dim })
  })
  txt(s, '验证方式：检索实验（多组查询测 Top-K 命中）+ 冒烟回归断言，数据进论文实验章节。', {
    x: M, y: 6.4, w: W - 2 * M, h: 0.45, fontSize: 12.5, color: DARK.faint
  })
  s.addNotes('预期三个创新点。第一是适配层设计，换厂商不改代码；第二是混合排序，语义为主标签微调，公式可解释；第三是 RAG 问答带引用溯源，回答可验证。验证方式是检索实验加冒烟断言，实验数据会进论文。')
}

// ============ S8 进度安排（浅） ============
{
  const s = pres.addSlide()
  s.background = { color: LIGHT.bg }
  pageTitle(s, LIGHT, '05', '进度安排', '26 周：2026.11 开题 → 2027.05 答辩')
  const phases = [
    ['1-4', '2026.11', '调研与开题', '文献阅读、开题报告、开题答辩'],
    ['5-8', '2026.12', '分析与设计', '需求分析、数据库与架构设计'],
    ['9-16', '2027.01-02', '编码实现', '后端模块、前端页面、AI 与检索'],
    ['17-20', '2027.03', '联调与测试', '系统集成、检索实验、论文初稿'],
    ['21-24', '2027.04', '定稿准备', '论文修改查重、材料整理'],
    ['25-26', '2027.05', '答辩', 'PPT 制作、预演、正式答辩']
  ]
  phases.forEach(([wk, time, t, d], i) => {
    const y = 1.85 + i * 0.88
    txt(s, wk, { x: M, y, w: 1.1, h: 0.45, fontSize: 16, bold: true, color: LIGHT.accent })
    txt(s, time, { x: 1.7, y: y + 0.03, w: 1.9, h: 0.35, fontSize: 11, color: LIGHT.faint })
    txt(s, t, { x: 3.7, y, w: 2.2, h: 0.4, fontSize: 14.5, bold: true, color: LIGHT.text })
    txt(s, d, { x: 6.0, y: y + 0.04, w: 6.8, h: 0.4, fontSize: 12, color: LIGHT.dim })
    if (i < 5) {
      s.addShape(pres.shapes.LINE, { x: M, y: y + 0.68, w: W - 2 * M, h: 0, line: { color: LIGHT.line, width: 0.5 } })
    }
  })
  s.addNotes('进度 26 周分六段：11 月开题调研；12 月设计；一到二月集中编码，这是最长的阶段；三月联调测试加论文初稿；四月定稿；五月答辩。目前处在第一阶段，开题通过后立即进入设计。')
}

// ============ S9 已具备条件（暗） ============
{
  const s = pres.addSlide()
  s.background = { color: DARK.bg }
  pageTitle(s, DARK, '05', '已具备的条件', '环境、资料、能力三方面就绪')
  const groups = [
    ['环境', ['JDK / Maven / Node.js / Docker 齐备', 'MySQL 与 Redis 容器一键拉起', '开发环境与部署环境一致']],
    ['资料', ['技术栈官方文档完整', 'Vaswani、Brown、Lewis 等原始论文', '同类系统源码可参考']],
    ['能力', ['已修 Java、数据库、Web 开发等课程', '熟悉 Git 与基本工程流程', '有小型 Web 项目实践经验']]
  ]
  groups.forEach(([t, items], i) => {
    const x = M + i * 4.12
    txt(s, t, { x, y: 1.85, w: 3.8, h: 0.45, fontSize: 17, bold: true, color: DARK.accent })
    s.addText(bullet(items, DARK.dim), { x, y: 2.42, w: 3.7, h: 3.4, fontSize: 12, paraSpaceAfter: 9 })
  })
  txt(s, '还需申请：大模型 API 免费额度（智谱 glm-4-flash 免费档，满足开发测试）', {
    x: M, y: 6.15, w: W - 2 * M, h: 0.4, fontSize: 12.5, color: DARK.faint
  })
  s.addNotes('条件三方面都具备：环境上工具链齐、容器拉起数据库；资料上官方文档全，检索和 RAG 都有原始论文可依；能力上先修课程都完成了。唯一要申请的是大模型 API 的免费额度，智谱有免费档，够开发测试用。')
}

// ============ S10 困难与对策（暗） ============
{
  const s = pres.addSlide()
  s.background = { color: DARK.bg }
  pageTitle(s, DARK, '06', '可能的困难与对策', '三个预判风险，各有预案')
  const risks = [
    ['大模型 API 不稳定 / 格式失控', '适配层超时捕获；提示词约束输出格式；解析失败降级为空结果，不阻断保存'],
    ['本地向量检索精度有限', '向量实现可插拔，配置远程 Embedding 自动切换；论文中对两方案做对比实验'],
    ['SSE 流式兼容性问题', '严格按规范解析（含可选空格）；前端累积渲染并支持中止；Nginx 代理关缓冲']
  ]
  risks.forEach(([q, a], i) => {
    const y = 1.95 + i * 1.5
    txt(s, 'Q', { x: M, y, w: 0.6, h: 0.5, fontSize: 22, bold: true, color: DARK.accent })
    txt(s, q, { x: M + 0.7, y: y + 0.05, w: 5.0, h: 1.2, fontSize: 14.5, bold: true, color: DARK.text })
    txt(s, 'A', { x: 6.3, y, w: 0.6, h: 0.5, fontSize: 22, bold: true, color: DARK.faint })
    txt(s, a, { x: 7.0, y: y + 0.05, w: 5.8, h: 1.2, fontSize: 12, color: DARK.dim })
  })
  s.addNotes('预判三个风险。API 不稳就做超时和格式容错，失败降级不阻断；本地向量精度到顶就切换远程，论文做对比实验说明取舍；SSE 兼容按规范实现，前端做累积和中止。三个都有明确预案，不会阻塞主线。')
}

// ============ S11 结语（暗） ============
{
  const s = pres.addSlide()
  s.background = { color: DARK.bg }
  txt(s, '墨', { x: 0, y: 1.9, w: W, h: 2.0, fontSize: 110, bold: true, color: DARK.surface, align: 'center' })
  txt(s, '恳请各位老师指导', { x: 0, y: 4.05, w: W, h: 0.8, fontSize: 38, bold: true, color: DARK.text, align: 'center' })
  txt(s, '期待您的意见与建议', { x: 0, y: 4.95, w: W, h: 0.45, fontSize: 15, color: DARK.accent, align: 'center' })
  txt(s, 'INK // PRESS · 2026.11', { x: 0, y: 6.5, w: W, h: 0.3, fontSize: 11, color: DARK.faint, align: 'center', charSpacing: 6 })
  s.addNotes('我的开题汇报到此结束。选题、技术路线和进度安排都已想清楚，风险也有预案。恳请各位老师对选题方向和技术方案给出意见，谢谢！')
}

pres.writeFile({ fileName: OUT }).then(() => console.log('生成完成:', OUT))
