/**
 * InkPress 毕业答辩 PPT 生成脚本
 * 三明治结构：暗色封面/创新点/结尾 + 浅色大图页；荧光酸绿品牌色贯穿
 * 用法：node scripts/ppt/build_ppt.js
 */
const pptxgen = require('pptxgenjs')
const path = require('path')

const ROOT = path.resolve(__dirname, '..', '..')
const DIAG = path.join(ROOT, 'docs', 'diagrams', 'png')
const OUT = path.join(ROOT, 'docs', 'PPT', 'InkPress答辩PPT.pptx')

// ============ 调色板 ============
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
pres.author = 'InkPress'
pres.title = '基于Spring Boot+Vue的个人博客与文章管理系统的设计与实现'
const W = 13.33, H = 7.5, M = 0.5

const TITLE = '基于Spring Boot+Vue的个人博客与文章管理系统的设计与实现'

// ============ 工具函数 ============
function txt(slide, text, o) {
  slide.addText(text, Object.assign({ fontFace: F, margin: 0 }, o))
}

function pageTitle(slide, c, no, title, sub) {
  txt(slide, no, {
    x: M, y: 0.42, w: 1.2, h: 0.5, fontSize: 15, bold: true,
    color: c.accent, fontFace: F
  })
  txt(slide, title, {
    x: M + 0.85, y: 0.4, w: W - 2 * M - 0.85, h: 0.55, fontSize: 26, bold: true, color: c.text
  })
  if (sub) {
    txt(slide, sub, {
      x: M + 0.85, y: 0.95, w: W - 2 * M - 0.85, h: 0.35, fontSize: 12.5, color: c.dim
    })
  }
}

function bullet(items, color) {
  const bu = () => ({ code: '2022', indent: 12 })
  return items.map((it, i) => ({
    text: it,
    options: Object.assign({ bullet: bu(), breakLine: true, color: color || '4A4A55', fontSize: 13.5 }, {})
  }))
}

/**
 * 行列表表格（替代 addTable）：原生文本框 + 细线分隔。
 * addTable 在跨渲染器时表头/数据行列宽会错位，此方案布局完全确定。
 */
function rowList(slide, c, opt) {
  const { x, y, widths, header, rows, rowH = 0.46, highlight = -1 } = opt
  const totalW = widths.reduce((a, b) => a + b, 0)
  // 表头
  let cx = x
  header.forEach((h, i) => {
    txt(slide, h, {
      x: cx, y, w: widths[i] - 0.12, h: 0.32, fontSize: 12.5, bold: true, color: c.text
    })
    cx += widths[i]
  })
  // 表头下粗线
  slide.addShape(pres.shapes.LINE, {
    x, y: y + 0.36, w: totalW, h: 0, line: { color: c.text, width: 1.5 }
  })
  // 数据行
  rows.forEach((row, ri) => {
    const ry = y + 0.48 + ri * rowH
    const isHi = ri === highlight
    let cx2 = x
    row.forEach((cell, ci) => {
      txt(slide, cell, {
        x: cx2, y: ry, w: widths[ci] - 0.12, h: rowH - 0.08,
        fontSize: 12, bold: isHi,
        color: isHi ? c.accent : (ci === 0 ? c.text : c.dim)
      })
      cx2 += widths[ci]
    })
    // 行间细线
    slide.addShape(pres.shapes.LINE, {
      x, y: ry + rowH - 0.07, w: totalW, h: 0, line: { color: c.line, width: 0.75 }
    })
  })
  return y + 0.48 + rows.length * rowH
}

/** 图片按比例 contain 放入指定区域并居中 */
function addImageContain(slide, file, x, y, maxW, maxH) {
  // 读取实际尺寸
  const fs = require('fs')
  const buf = fs.readFileSync(path.join(DIAG, file))
  const iw = buf.readUInt32BE(16), ih = buf.readUInt32BE(20)
  let w = maxW, h = maxW * (ih / iw)
  if (h > maxH) { h = maxH; w = maxH * (iw / ih) }
  const cx = x + (maxW - w) / 2
  slide.addImage({ path: path.join(DIAG, file), x: cx, y, w, h, sizing: { type: 'contain', w, h } })
  return { w, h, cx }
}

function caption(slide, c, text, y) {
  txt(slide, text, { x: M, y, w: W - 2 * M, h: 0.3, fontSize: 10.5, color: c.faint, align: 'center' })
}

function notes(slide, text) {
  slide.addNotes(text)
}

// ============ S1 封面（暗） ============
{
  const s = pres.addSlide()
  s.background = { color: DARK.bg }
  txt(s, '青岛恒星科技学院', {
    x: 0, y: 1.15, w: W, h: 0.5, fontSize: 18, color: DARK.dim, align: 'center', charSpacing: 6
  })
  txt(s, '毕业论文（设计）答辩', {
    x: 0, y: 1.75, w: W, h: 0.5, fontSize: 15, color: DARK.accent, align: 'center', charSpacing: 8
  })
  txt(s, TITLE, {
    x: 1.1, y: 2.55, w: W - 2.2, h: 1.7, fontSize: 34, bold: true, color: DARK.text, align: 'center'
  })
  txt(s, 'INK // PRESS', {
    x: 0, y: 4.5, w: W, h: 0.45, fontSize: 14, color: DARK.accent, align: 'center', charSpacing: 10
  })
  const info = [
    ['答 辩 人', '张德鑫'],
    ['学      号', '2505396'],
    ['专业班级', '软件工程 · 2027专升本2班'],
    ['指导教师', '王婧']
  ]
  info.forEach(([k, v], i) => {
    const x = 1.7 + i * 2.55
    txt(s, k, { x, y: 5.5, w: 2.3, h: 0.3, fontSize: 11, color: DARK.faint, align: 'center' })
    txt(s, v, { x, y: 5.82, w: 2.3, h: 0.35, fontSize: 13, bold: true, color: DARK.text, align: 'center' })
  })
  txt(s, '2027 年 5 月', { x: 0, y: 6.7, w: W, h: 0.3, fontSize: 12, color: DARK.faint, align: 'center' })
  notes(s, '各位老师好，我的论文题目是《基于Spring Boot+Vue的个人博客与文章管理系统的设计与实现》。系统名叫 InkPress。这个题目的出发点有两个：一是个人写作者对数据自有、样式可定制的博客平台有真实需求；二是大语言模型技术成熟后，写作辅助有了新的可能。下面从六个部分汇报。')
}

// ============ S2 目录（暗） ============
{
  const s = pres.addSlide()
  s.background = { color: DARK.bg }
  txt(s, 'CONTENTS', { x: M, y: 0.6, w: 4, h: 0.5, fontSize: 15, bold: true, color: DARK.accent, charSpacing: 6 })
  txt(s, '汇报目录', { x: M, y: 1.0, w: 6, h: 0.7, fontSize: 30, bold: true, color: DARK.text })
  const items = [
    ['01', '研究背景与意义', '为什么做这个系统'],
    ['02', '国内外研究现状', '现有方案与本系统定位'],
    ['03', '系统设计与实现', '架构、数据库、核心模块'],
    ['04', '关键技术', 'AI 适配层 · SSE 流式 · 缓存一致性'],
    ['05', '创新点', '语义检索 · 混合排序 · RAG 问答'],
    ['06', '测试与总结', '26 项冒烟 · 检索实验 · 结论展望']
  ]
  items.forEach(([no, t, d], i) => {
    const y = 2.15 + i * 0.82
    txt(s, no, { x: M, y, w: 0.9, h: 0.5, fontSize: 20, bold: true, color: DARK.accent })
    txt(s, t, { x: M + 1.0, y: y - 0.02, w: 5.2, h: 0.4, fontSize: 17, bold: true, color: DARK.text })
    txt(s, d, { x: M + 1.0, y: y + 0.36, w: 6.5, h: 0.3, fontSize: 11.5, color: DARK.faint })
  })
  // 右侧系统标识
  txt(s, '墨', { x: 10.6, y: 2.9, w: 2.2, h: 1.6, fontSize: 96, bold: true, color: DARK.surface, align: 'center' })
  txt(s, 'INK//PRESS', { x: 10.6, y: 4.5, w: 2.2, h: 0.35, fontSize: 12, color: DARK.faint, align: 'center', charSpacing: 4 })
  notes(s, '汇报分六个部分。前三部分交代背景、现状和系统本身的设计实现；第四部分讲三个工程问题的解决方案；第五部分是重点，讲系统的创新点，也就是语义检索和 RAG 问答；最后是测试数据和结论。')
}

// ============ S3 研究背景与意义（暗） ============
{
  const s = pres.addSlide()
  s.background = { color: DARK.bg }
  pageTitle(s, DARK, '01', '研究背景与意义', '个人写作者的真实困境，与大模型带来的新可能')
  const cards = [
    ['数据不自有', '托管平台排版受限、导出困难，内容去留自己说了不算'],
    ['工作流割裂', '富文本编辑器打断思路，Markdown 一整套流程走不通'],
    ['AI 缺位', '润色、摘要、打标签这些机械劳动，仍要人肉完成']
  ]
  cards.forEach(([t, d], i) => {
    const x = M + i * 4.1
    txt(s, '0' + (i + 1), { x, y: 1.95, w: 3.7, h: 0.75, fontSize: 44, bold: true, color: DARK.accent })
    txt(s, t, { x, y: 2.75, w: 3.7, h: 0.4, fontSize: 18, bold: true, color: DARK.text })
    txt(s, d, { x, y: 3.2, w: 3.55, h: 0.95, fontSize: 12.5, color: DARK.dim })
  })
  txt(s, '本系统的回答', { x: M, y: 4.55, w: 4, h: 0.4, fontSize: 15, bold: true, color: DARK.accent })
  const ans = bullet([
    '数据自有：文章、评论、访问记录存在自己的 MySQL，随时可整体迁移',
    'Markdown 工作流：Vditor 所见即所得，代码高亮、图床外链统一方案',
    'AI 融入写作：润色、摘要、标签、续写、标题五项辅助，接口可插拔'
  ], DARK.dim)
  s.addText(ans, { x: M, y: 5.0, w: 12.3, h: 1.9, fontSize: 13.5, paraSpaceAfter: 7 })
  notes(s, '背景可以概括成三个矛盾。第一，托管平台用起来省事，但数据和样式都不归自己；第二，Markdown 已经是技术写作主流，但很多平台的编辑器还是富文本；第三，大模型能力明明可以用来干润色摘要这类活，但多数博客系统没把它做进核心功能。本系统就是针对这三点做的：数据放在自己的 MySQL，编辑器走 Markdown 工作流，AI 能力做成可插拔的适配层。')
}

// ============ S4 国内外研究现状（浅） ============
{
  const s = pres.addSlide()
  s.background = { color: LIGHT.bg }
  pageTitle(s, LIGHT, '02', '国内外研究现状', '现有方案解决了"发布"，但各有短板')
  rowList(s, LIGHT, {
    x: M, y: 1.75,
    widths: [1.8, 2.7, 3.5, 4.33],
    header: ['方案', '技术形态', '优势', '短板'],
    rows: [
      ['WordPress', 'PHP + MySQL 单体', '生态庞大，插件齐全', '架构偏重，加载同步'],
      ['Ghost', 'Node.js 订阅制', '编辑体验好', '定制需学模板语言'],
      ['Hugo / Hexo', '静态站点生成', '速度快、成本低', '无后台，动态功能靠第三方'],
      ['Halo', 'Java 插件化', '现代化，社区活跃', 'AI 支持停留在插件层面'],
      ['本系统', '前后端分离 + AI 一体化', '数据自有 · AI 内建 · 检索增强', '面向个人创作者的轻量方案']
    ],
    highlight: 4
  })
  txt(s, '调研结论：把"前后端分离技术栈、数据自有、AI 一体化"三者结合的轻量系统仍然少见，这是本课题的空间。', {
    x: M, y: 5.35, w: W - 2 * M, h: 0.6, fontSize: 13.5, bold: true, color: LIGHT.text
  })
  txt(s, '资料来源：W3Techs CMS 使用统计（2025）、各项目官方文档', {
    x: M, y: 6.55, w: 6, h: 0.3, fontSize: 10.5, color: LIGHT.faint
  })
  notes(s, '调研了四类有代表性的方案。WordPress 生态最大但架构偏重；Ghost 编辑体验好；Hugo、Hexo 这类静态生成器速度快但没有后台；国内的 Halo 用 Java 写、插件化做得不错，但 AI 支持还停留在插件层面。结论是：把前后端分离、数据自有、AI 一体化结合起来的轻量系统还有空间，这就是本课题的切入点。')
}

// ============ S5 研究内容（浅，功能模块图） ============
{
  const s = pres.addSlide()
  s.background = { color: LIGHT.bg }
  pageTitle(s, LIGHT, '03', '研究内容与系统功能', '三大板块：前台阅读 · 后台管理 · 智能检索')
  addImageContain(s, '02-功能模块图.png', M, 1.6, 7.4, 5.2)
  const pts = bullet([
    '前台：文章浏览、分类标签筛选、搜索、归档、评论、点赞',
    '后台：数据看板、文章管理（Markdown 编辑器）、分类标签、评论审核、用户与日志',
    'AI 写作辅助：润色（流式）、摘要、标签、续写、标题',
    '智能检索：相关文章推荐、语义搜索、RAG 站内问答'
  ], LIGHT.dim)
  s.addText(pts, { x: 8.35, y: 1.85, w: 4.5, h: 4.6, fontSize: 12.5, paraSpaceAfter: 9 })
  notes(s, '系统功能分三块。前台面向访客，核心是读和互动；后台面向作者，覆盖文章全生命周期和管理；第三块智能检索是本文特色，后面创新点部分会展开。目前站内已导入 38 篇历史文章作为真实数据。')
}

// ============ S6 总体架构（浅，架构图） ============
{
  const s = pres.addSlide()
  s.background = { color: LIGHT.bg }
  pageTitle(s, LIGHT, '03', '系统总体架构', '前后端分离，四层结构，AI 能力独立适配')
  addImageContain(s, '01-系统架构图.png', M, 1.55, 4.4, 5.35)
  const layers = [
    ['展示层', 'Vue 3 组合式 API + Vite，Element Plus / Vditor / ECharts'],
    ['接口层', 'RESTful 接口，统一 code/message/data 响应，Knife4j 在线文档'],
    ['服务层', 'Controller / Service / Mapper 三层；Security+JWT、AOP 日志、定时任务横切'],
    ['数据层', 'MySQL 8.0 权威存储；Redis 7 承担缓存、计数与向量索引']
  ]
  layers.forEach(([t, d], i) => {
    const y = 1.7 + i * 1.18
    txt(s, t, { x: 5.35, y, w: 2.2, h: 0.4, fontSize: 15.5, bold: true, color: LIGHT.text })
    txt(s, d, { x: 7.6, y: y + 0.02, w: 5.2, h: 0.95, fontSize: 12, color: LIGHT.dim })
  })
  txt(s, '关键决策：AI 调用适配 OpenAI 兼容协议，切换模型服务商只改 base-url / api-key / model 三项配置。', {
    x: 5.35, y: 6.35, w: 7.5, h: 0.6, fontSize: 12.5, bold: true, color: LIGHT.accent
  })
  notes(s, '架构是典型的前后端分离四层。数据层 MySQL 做权威存储，Redis 除了缓存还承担浏览量计数和向量索引。服务层有三个横切关注点：安全认证、操作日志、定时任务。AI 能力以独立适配层存在，上层只依赖统一接口——这是后面创新点的基础。')
}

// ============ S7 数据库设计（浅，ER 图） ============
{
  const s = pres.addSlide()
  s.background = { color: LIGHT.bg }
  pageTitle(s, LIGHT, '03', '数据库设计', '七张表，关系清晰，索引针对高频查询场景')
  addImageContain(s, '04-数据库ER图.png', M, 1.6, 8.6, 5.1)
  txt(s, '7', { x: 9.5, y: 1.8, w: 3.4, h: 1.0, fontSize: 60, bold: true, color: LIGHT.accent })
  txt(s, '张数据表', { x: 9.5, y: 2.85, w: 3.4, h: 0.35, fontSize: 14, color: LIGHT.dim })
  const pts = bullet([
    '文章与标签多对多，article_tag 关联表解耦，标签可复用',
    '评论 parent_id 自关联，支撑一层回复；三态审核流',
    '浏览量、点赞数为计数字段，由 Redis 挡高频写',
    '（article_id, status）联合索引，命中前台查询条件'
  ], LIGHT.dim)
  s.addText(pts, { x: 9.5, y: 3.45, w: 3.5, h: 3.0, fontSize: 11.5, paraSpaceAfter: 7 })
  notes(s, '数据库七张表。用户、文章、分类、标签、文章标签关联、评论、操作日志。几个设计点：文章和标签多对多用关联表解耦；评论通过 parent_id 自关联支持一层回复；浏览量这类计数字段放业务表里，因为个人博客规模没必要拆表，Redis 已经挡掉了高频写。索引上针对"某篇文章的已通过评论"这个高频组合建了联合索引。')
}

// ============ S8 关键技术一：AI 适配层（暗） ============
{
  const s = pres.addSlide()
  s.background = { color: DARK.bg }
  pageTitle(s, DARK, '04', '关键技术一：AI 可插拔适配层', '统一接口屏蔽厂商差异，切换只改配置')
  // 适配层示意图：左侧统一接口，右侧多厂商
  txt(s, '业务层', { x: 0.7, y: 2.1, w: 2.6, h: 0.5, fontSize: 16, bold: true, color: DARK.text, align: 'center' })
  txt(s, 'AiService\n润色 / 摘要 / 标签 / 续写 / 标题', { x: 0.7, y: 2.6, w: 2.6, h: 1.4, fontSize: 12, color: DARK.dim, align: 'center' })
  txt(s, '▶', { x: 3.45, y: 3.0, w: 0.7, h: 0.5, fontSize: 20, color: DARK.accent, align: 'center' })
  txt(s, 'ChatModelAdapter', { x: 4.2, y: 2.35, w: 2.9, h: 0.45, fontSize: 15.5, bold: true, color: DARK.accent, align: 'center' })
  txt(s, 'SPI 接口\ncomplete() / stream()', { x: 4.2, y: 2.85, w: 2.9, h: 1.0, fontSize: 11.5, color: DARK.dim, align: 'center' })
  txt(s, '▶', { x: 7.25, y: 3.0, w: 0.7, h: 0.5, fontSize: 20, color: DARK.accent, align: 'center' })
  const vendors = ['智谱 GLM', 'DeepSeek', '通义千问', 'Kimi', 'Ollama 本地']
  vendors.forEach((v, i) => {
    const y = 1.95 + i * 0.92
    txt(s, v, { x: 8.05, y, w: 4.5, h: 0.62, fontSize: 13.5, bold: true, color: DARK.text, align: 'center', fill: { color: DARK.surface }, line: { color: DARK.line, width: 1 } })
  })
  txt(s, '同一份 OpenAI 兼容协议请求，覆盖全部厂商', { x: 8.05, y: 6.6, w: 4.5, h: 0.3, fontSize: 10.5, color: DARK.faint, align: 'center' })
  const pts = bullet([
    '默认实现 OpenAiCompatibleAdapter：按标准格式构造请求，Bearer 鉴权',
    '提示词工程三原则：输出格式约束死、JSON 容错解析、失败降级不阻断保存',
    '本地哈希向量 / 远程 Embedding 同一 SPI，auto 模式按有无 Key 自动切换'
  ], DARK.dim)
  s.addText(pts, { x: 0.7, y: 4.55, w: 12.0, h: 2.2, fontSize: 13, paraSpaceAfter: 8 })
  notes(s, '这是 AI 模块的核心设计。业务层只依赖 ChatModelAdapter 一个接口，默认实现对接所有兼容 OpenAI 协议的服务，智谱、DeepSeek、通义、Kimi 乃至本地 Ollama 都覆盖，切换只改三个配置项。另外提示词设计有三条经验：输出格式要约束死、模型可能返回带代码块的 JSON 要做容错、解析失败时返回空值而不是抛异常，保证 AI 抽风不阻断用户保存。')
}

// ============ S9 关键技术二：浏览量缓存一致性（浅，流程图） ============
{
  const s = pres.addSlide()
  s.background = { color: LIGHT.bg }
  pageTitle(s, LIGHT, '04', '关键技术二：浏览量计数与回写', 'Redis 挡高频写，定时任务保最终一致')
  addImageContain(s, '07-浏览量计数与回写流程图.png', M, 1.6, 4.6, 5.2)
  const pts = bullet([
    '写入：Redis INCR 原子自增 + SADD 脏集合标记，O(1) 承接每次浏览',
    '回写：定时任务每分钟批量合并增量进 MySQL，后清理计数键',
    '取舍：一分钟一致性窗口换性能；崩溃最多丢一分钟增量',
    '同款思路还用于：热门文章 5 分钟缓存、点赞按访客去重（Redis Set）'
  ], LIGHT.dim)
  s.addText(pts, { x: 5.5, y: 1.95, w: 7.3, h: 4.2, fontSize: 13, paraSpaceAfter: 10 })
  notes(s, '浏览量是典型的高频写。直接写库的话热门文章会把数据库打满。方案分两段：写入时 Redis 原子自增，同时把文章 ID 放进脏集合；定时任务每分钟把增量合并进数据库。这本质是用一分钟的一致性窗口换性能，进程崩溃最多丢一分钟增量，对博客场景完全可以接受。同样的思路还用在了热门缓存和点赞去重上。')
}

// ============ S10 关键技术三：SSE 流式（暗） ============
{
  const s = pres.addSlide()
  s.background = { color: DARK.bg }
  pageTitle(s, DARK, '04', '关键技术三：SSE 流式输出', '打字机效果的完整链路')
  addImageContain(s, '06-AI润色流式调用时序图.png', M, 1.55, 5.2, 5.3)
  const steps = [
    ['后端', 'HttpClient 读上游流，逐行解析 SSE 数据块，提取增量文本'],
    ['转发', 'SseEmitter 逐段推送浏览器；aiExecutor 独立线程池隔离'],
    ['前端', 'fetch + ReadableStream 手动解析，累积后 150ms 节流写入编辑器'],
    ['细节', '严格按规范消费 data: 后可选空格，杜绝正文混入多余空格']
  ]
  steps.forEach(([t, d], i) => {
    const y = 1.85 + i * 1.18
    txt(s, '0' + (i + 1), { x: 6.1, y, w: 0.75, h: 0.45, fontSize: 18, bold: true, color: DARK.accent })
    txt(s, t, { x: 6.9, y: y + 0.02, w: 1.6, h: 0.4, fontSize: 14.5, bold: true, color: DARK.text })
    txt(s, d, { x: 8.5, y: y + 0.02, w: 4.4, h: 0.95, fontSize: 11.5, color: DARK.dim })
  })
  notes(s, '流式输出分四段。后端用 JDK 自带的 HttpClient 读上游流，逐行解析 SSE 数据块；通过 SseEmitter 转发，用独立线程池隔离，避免慢请求占满容器线程；前端 fetch 加 ReadableStream 手动解析，累积文本后每 150 毫秒节流写入编辑器，形成打字机效果。有个细节：SSE 规范允许 data: 后面带一个可选空格，不处理的话润色正文每段开头都会多一个空格——这个坑调试时真实踩到过。')
}

// ============ S11 创新点一：语义检索与混合排序（暗） ============
{
  const s = pres.addSlide()
  s.background = { color: DARK.bg }
  pageTitle(s, DARK, '05', '创新点一：语义检索与混合排序', '特征哈希向量 + 加权混合，本地零依赖可用')
  txt(s, 'score = 0.85 × 语义相似度 + 0.15 × 标签重合度', {
    x: M, y: 1.6, w: 12.3, h: 0.75, fontSize: 24, bold: true, color: DARK.accent, align: 'center'
  })
  txt(s, '字符 bigram 特征哈希（512 维）· 词频开方缓和长文 · L2 归一化 · 余弦相似度', {
    x: M, y: 2.35, w: 12.3, h: 0.35, fontSize: 12.5, color: DARK.dim, align: 'center'
  })
  // 检索实验柱状图（直接放在本页）
  s.addChart(pres.charts.BAR, [{
    name: 'Top-1 相似度得分',
    labels: ['WebRTC', 'Django 入门', 'MySQL 配置', 'Vue 基础', '并查集'],
    values: [0.347, 0.288, 0.260, 0.232, 0.143]
  }], {
    x: M, y: 2.95, w: 7.3, h: 3.9, barDir: 'col',
    chartColors: ['CCFF00', 'CCFF00', 'CCFF00', 'CCFF00', 'CCFF00'],
    chartArea: { fill: { color: DARK.bg } },
    catAxisLabelColor: '9A9AA2', valAxisLabelColor: '62626A',
    catAxisLabelFontFace: F, valAxisLabelFontFace: F,
    valGridLine: { color: '2C2C33', size: 0.5 }, catGridLine: { style: 'none' },
    showValue: true, dataLabelPosition: 'outEnd', dataLabelColor: 'F2F2F0',
    dataLabelFontFace: F, dataLabelFontSize: 11,
    dataLabelFormatCode: '0.000',
    showLegend: false, showTitle: false
  })
  txt(s, '语义检索实验：5 组查询 Top-1 全部命中预期文章', {
    x: M, y: 2.62, w: 7.3, h: 0.3, fontSize: 12, bold: true, color: DARK.text
  })
  const pts = bullet([
    '选型：本地特征哈希零依赖零成本，远程 Embedding 按需切换，同一 SPI',
    '混合排序权重由来：语义承载"相关"的主要含义，标签负责聚拢同系列文章',
    '局限如实说明：无语义泛化，字面重合度低的查询排名靠后，论文中做了分析'
  ], DARK.dim)
  s.addText(pts, { x: 8.15, y: 2.95, w: 4.7, h: 3.9, fontSize: 12, paraSpaceAfter: 10 })
  txt(s, '数据来源：本系统站内 38 篇文章实测（2026.11）', {
    x: M, y: 6.95, w: 7, h: 0.3, fontSize: 10, color: DARK.faint
  })
  notes(s, '第一个创新点是语义检索。相关文章不用"同标签取最新"，而是语义相似度占 0.85、标签重合度占 0.15 的混合排序。向量用本地字符 bigram 特征哈希，512 维，零外部依赖，没有 API Key 也能用。右边是检索实验：五组查询，Top-1 全部命中。能看出分值规律——查询词和标题字面重合高的分数明显高；也如实说明了局限：无语义泛化，抽象查询的第三名之后会混入非目标文章，配置远程 Embedding 后这类噪声会减少。')
}

// ============ S12 创新点二：RAG 站内问答（暗） ============
{
  const s = pres.addSlide()
  s.background = { color: DARK.bg }
  pageTitle(s, DARK, '05', '创新点二：RAG 站内知识问答', '检索增强生成，回答带引用溯源，不编造')
  const flow = [
    ['问题向量化', '访客输入自然语言，同一 Provider 转向量'],
    ['语义检索', '全库余弦相似度取 Top-4，每篇截取 600 字'],
    ['拼装上下文', '按 [1][2][3][4] 编号，连同问题发给模型'],
    ['流式生成', 'SSE 逐段输出；先推引用来源 JSON，再推正文']
  ]
  flow.forEach(([t, d], i) => {
    const x = M + i * 3.12
    txt(s, '0' + (i + 1), { x, y: 1.75, w: 2.8, h: 0.5, fontSize: 24, bold: true, color: DARK.accent })
    txt(s, t, { x, y: 2.28, w: 2.8, h: 0.4, fontSize: 15.5, bold: true, color: DARK.text })
    txt(s, d, { x, y: 2.72, w: 2.75, h: 1.0, fontSize: 11.5, color: DARK.dim })
    if (i < 3) {
      txt(s, '▶', { x: x + 2.78, y: 1.85, w: 0.34, h: 0.5, fontSize: 16, color: DARK.faint, align: 'center' })
    }
  })
  // 引用溯源示意
  txt(s, '回答示例（界面实录逻辑）', { x: M, y: 4.05, w: 6, h: 0.35, fontSize: 13, bold: true, color: DARK.text })
  txt(s, 'Q：Redis 在项目里怎么用的？', { x: M, y: 4.45, w: 6.2, h: 0.35, fontSize: 12, color: DARK.dim })
  txt(s, 'A：Redis 在三处使用：热点文章列表缓存 5 分钟；浏览量 INCR 计数加每分钟定时回写；点赞用 Set 按访客去重 [1][3]。', {
    x: M, y: 4.82, w: 6.3, h: 1.3, fontSize: 12, color: DARK.text, fill: { color: DARK.surface }
  })
  const cites = [
    ['[1]', '第2章 相关技术 · Redis 缓存'],
    ['[3]', '第5章 系统实现 · 文章管理模块']
  ]
  cites.forEach(([n, t], i) => {
    const y = 6.2 + i * 0.36
    txt(s, n, { x: M, y, w: 0.55, h: 0.3, fontSize: 11, bold: true, color: DARK.accent })
    txt(s, t, { x: M + 0.55, y, w: 5.6, h: 0.3, fontSize: 10.5, color: DARK.faint })
  })
  const pts = bullet([
    '提示词硬约束：仅依据资料回答，资料不足明说，编造零容忍',
    '检索为空时不调模型，直接返回"站内暂无相关内容"',
    '先推来源再推正文：读者第一眼看到回答依据，可信度可验证'
  ], DARK.dim)
  s.addText(pts, { x: 7.15, y: 4.35, w: 5.7, h: 2.6, fontSize: 12, paraSpaceAfter: 9 })
  notes(s, '第二个创新点是 RAG 问答。流程四步：问题向量化、检索 Top-4、拼装编号上下文、流式生成。关键设计有三个：提示词里硬约束"只依据资料回答，资料不足就明说"；检索结果为空时直接不调模型；SSE 先推引用来源再推正文，读者第一眼就能看到回答依据的是哪几篇文章，点一下还能跳原文。右边是问答效果的示意。')
}

// ============ S13 系统测试（浅） ============
{
  const s = pres.addSlide()
  s.background = { color: LIGHT.bg }
  pageTitle(s, LIGHT, '06', '系统测试', '量化验证：接口断言、功能用例、检索实验')
  txt(s, '26', { x: M, y: 1.7, w: 2.6, h: 1.1, fontSize: 62, bold: true, color: LIGHT.accent })
  txt(s, '项接口冒烟断言\n全部通过', { x: M, y: 2.85, w: 2.6, h: 0.75, fontSize: 13, color: LIGHT.dim })
  txt(s, '31', { x: M, y: 3.75, w: 2.6, h: 1.1, fontSize: 62, bold: true, color: LIGHT.accent })
  txt(s, '项功能测试用例\n含边界与安全', { x: M, y: 4.9, w: 2.6, h: 0.7, fontSize: 13, color: LIGHT.dim })
  rowList(s, LIGHT, {
    x: 3.5, y: 1.75,
    widths: [2.1, 5.6, 1.6],
    header: ['测试类型', '覆盖内容', '结果'],
    rows: [
      ['认证授权', '登录 / 越权 / 角色，401 与 403 分流', '通过'],
      ['文章模块', '分页 / 草稿隔离 / 删除级联 / 计数回写', '通过'],
      ['边界参数', '超大 pageSize 限制 50；超长标签名 400 拦截', '通过'],
      ['内容安全', 'Markdown 白名单净化；评论长度限制；邮箱不暴露', '通过'],
      ['检索实验', '5 组查询 Top-1 命中率 100%', '通过'],
      ['AI 降级', '未配置 Key 返回 2001 明确提示，主流程无感', '通过']
    ]
  })
  txt(s, '冒烟测试脚本每轮代码迭代后回归，开发期间实际拦下过点赞权限配置错误、过时断言等问题。', {
    x: 3.5, y: 5.85, w: 9.3, h: 0.6, fontSize: 12, color: LIGHT.dim
  })
  notes(s, '测试分三个层次。接口层 26 项冒烟断言全部通过，每轮迭代后回归，开发期间实际拦下过几个问题——比如点赞接口权限配置错误、文章数量变化后过时的断言。功能层 31 项用例，重点在边界和安全：超大分页参数、超长标签名、Markdown 注入、游客邮箱不暴露，这些都实测过。检索实验五组查询 Top-1 全命中。AI 模块在无密钥环境的降级提示也验证明确。')
}

// ============ S14 总结与展望（暗） ============
{
  const s = pres.addSlide()
  s.background = { color: DARK.bg }
  pageTitle(s, DARK, '06', '总结与展望', '做完了什么，还差什么')
  const done = [
    ['系统', '数据自有的博客系统：文章、分类标签、评论、看板，JWT + Redis + 容器化部署'],
    ['AI', '五项写作辅助 + RAG 问答，适配层换厂商不改码，SSE 流式全链路'],
    ['检索', '混合排序推荐 + 语义搜索，本地向量零依赖，实验 Top-1 全命中']
  ]
  done.forEach(([t, d], i) => {
    const y = 1.8 + i * 1.05
    txt(s, t, { x: M, y, w: 1.5, h: 0.45, fontSize: 17, bold: true, color: DARK.accent })
    txt(s, d, { x: M + 1.6, y: y + 0.04, w: 10.7, h: 0.8, fontSize: 13, color: DARK.text })
  })
  txt(s, '不足与后续', { x: M, y: 5.1, w: 4, h: 0.4, fontSize: 15, bold: true, color: DARK.text })
  const pts = bullet([
    'AI 功能依赖外部服务，后续可加请求队列与重试；本地向量精度到顶，值得做一次量化对比实验',
    '语义搜索接入前台搜索框；RAG 从单轮扩展到多轮对话（指代消解）'
  ], DARK.dim)
  s.addText(pts, { x: M, y: 5.55, w: 12.3, h: 1.4, fontSize: 12.5, paraSpaceAfter: 7 })
  notes(s, '总结三点：系统本身功能完整、工程质量有保障；AI 能力从适配层到流式输出是全链路落地；检索和推荐有算法设计也有实验数据。不足也如实说：AI 依赖外部服务，本地向量精度有上限，后续值得做本地与远程向量的量化对比；语义搜索可以接入前台搜索框，RAG 可以从单轮扩展到多轮。这两个方向都不用推翻现有架构。')
}

// ============ S15 致谢（暗） ============
{
  const s = pres.addSlide()
  s.background = { color: DARK.bg }
  txt(s, '墨', { x: 0, y: 1.9, w: W, h: 2.0, fontSize: 110, bold: true, color: DARK.surface, align: 'center' })
  txt(s, '感谢聆听', { x: 0, y: 4.05, w: W, h: 0.8, fontSize: 40, bold: true, color: DARK.text, align: 'center' })
  txt(s, '恳请各位老师批评指正', { x: 0, y: 4.95, w: W, h: 0.45, fontSize: 16, color: DARK.accent, align: 'center' })
  txt(s, 'INK // PRESS · 2027.05', { x: 0, y: 6.5, w: W, h: 0.3, fontSize: 11, color: DARK.faint, align: 'center', charSpacing: 6 })
  notes(s, '我的汇报到此结束。感谢指导老师在选题和设计阶段给出的具体意见，感谢同学们在调试阶段的帮助。恳请各位老师批评指正，谢谢！')
}

pres.writeFile({ fileName: OUT }).then(() => {
  console.log('生成完成:', OUT)
})
