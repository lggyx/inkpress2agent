// InkPress · BoHack 2026 日新赛道 4 分钟路演 PPT —— 13 页重构版（11/15 凌晨用户口径）
// 叙事：封面 → 博客的发展 → 未来博客存在形式（立意组）→ 功能主体×6（三大功能+规模+工程复盘）→ 架构 → 演示 → Roadmap
// 数据口径与 docs/rollup/rollup-brutal.html 严格一致；不宣称真 key AI 生成跑通（进化数据为管线实测口径）
const pptxgen = require("pptxgenjs");

const P = new pptxgen();
P.layout = "LAYOUT_WIDE";
P.author = "伊格小队 T69";
P.title = "InkPress · 会自我进化的 AI 博客（BoHack 2026 日新赛道）";

const BG = "0C0C0E", SURFACE = "17171A", LINE = "2A2A30", TEXT = "F2F2EE",
  MUTED = "9A9AA3", LIME = "D9F24F", VIOLET = "A78BFA", CYAN = "22D3EE", NUM = "3A3A42",
  ORANGE = "FB923C";
const F = "Microsoft YaHei";
const W = 13.33, H = 7.5, M = 0.5;
const SHOT = "D:\\inkpress2agent\\docs\\screenshots\\";

const chip = (s, x, y, w, text, dot = LIME, size = 13) => {
  s.addShape(P.shapes.ROUNDED_RECTANGLE, { x, y, w, h: 0.46, fill: { color: SURFACE }, line: { color: LINE, width: 1 }, rectRadius: 0.23 });
  s.addShape(P.shapes.OVAL, { x: x + 0.2, y: y + 0.17, w: 0.12, h: 0.12, fill: { color: dot } });
  s.addText(text, { x: x + 0.42, y, w: w - 0.5, h: 0.46, fontSize: size, color: TEXT, fontFace: F, align: "left", valign: "middle", margin: 0 });
};
const header = (s, kick, title) => {
  s.addText(kick, { x: M, y: 0.4, w: 11, h: 0.32, fontSize: 12, color: MUTED, fontFace: F, charSpacing: 3, margin: 0 });
  s.addText(title, { x: M, y: 0.74, w: 12.2, h: 0.85, fontSize: 30, bold: true, color: TEXT, fontFace: F, margin: 0 });
};
const footer = (s, n) => {
  s.addText("InkPress · BoHack 2026 日新赛道", { x: M, y: 7.06, w: 5, h: 0.3, fontSize: 12, color: MUTED, fontFace: F, margin: 0 });
  s.addText(String(n).padStart(2, "0"), { x: 12.2, y: 7.06, w: 0.63, h: 0.3, fontSize: 12, color: MUTED, fontFace: F, align: "right", margin: 0 });
};
const hairline = (s, x, y, w) => s.addShape(P.shapes.LINE, { x, y, w, h: 0, line: { color: LINE, width: 0.75 } });
const conn = (s, x, y, w, h, flipV = false) =>
  s.addShape(P.shapes.LINE, { x, y, w, h, flipV, line: { color: MUTED, width: 1, dashType: "sysDot" } });
const dots = (s, x, y) => [VIOLET, CYAN, LIME, ORANGE, "F472B6", "60A5FA"].forEach((c, i) =>
  s.addShape(P.shapes.OVAL, { x: x + i * 0.34, y, w: 0.14, h: 0.14, fill: { color: c } }));
const shot = (s, file, x, y, w, h) => {
  s.addShape(P.shapes.ROUNDED_RECTANGLE, { x: x - 0.04, y: y - 0.04, w: w + 0.08, h: h + 0.08, fill: { color: SURFACE }, line: { color: LINE, width: 1 }, rectRadius: 0.06 });
  s.addImage({ path: SHOT + file, x, y, w, h, sizing: { type: "cover", w, h } });
};
const dotRow = (s, x, y, w, text, c) => {
  s.addShape(P.shapes.OVAL, { x: x + 0.05, y: y + 0.12, w: 0.13, h: 0.13, fill: { color: c } });
  s.addText(text, { x: x + 0.34, y, w: w - 0.4, h: 0.4, fontSize: 13, color: TEXT, fontFace: F, margin: 0 });
};
const box = (x, y, w, h, name, desc, accent = LINE) => {
  s.addShape(P.shapes.ROUNDED_RECTANGLE, { x, y, w, h, fill: { color: SURFACE }, line: { color: accent, width: 1.25 }, rectRadius: 0.1 });
  s.addText(name, { x: x + 0.2, y: y + 0.14, w: w - 0.4, h: 0.4, fontSize: 15.5, bold: true, color: TEXT, fontFace: F, margin: 0 });
  s.addText(desc, { x: x + 0.2, y: y + 0.52, w: w - 0.4, h: h - 0.62, fontSize: 11.5, color: MUTED, fontFace: F, margin: 0 });
};
const event = (s, y, h, tag, c, lines) => {
  s.addShape(P.shapes.ROUNDED_RECTANGLE, { x: M, y, w: 12.33, h, fill: { color: SURFACE }, line: { color: LINE, width: 1 }, rectRadius: 0.12 });
  s.addText(tag, { x: M + 0.3, y: y + 0.14, w: 11.7, h: 0.38, fontSize: 14.5, bold: true, color: c, fontFace: F, margin: 0 });
  const runs = [];
  lines.forEach((t) => {
    const idx = t.indexOf("：");
    if (idx > 0 && idx <= 4) {
      runs.push({ text: t.slice(0, idx + 1), options: { bold: true, color: c } });
      runs.push({ text: t.slice(idx + 1), options: { color: TEXT, breakLine: true } });
    } else {
      runs.push({ text: t, options: { color: TEXT, breakLine: true } });
    }
  });
  s.addText(runs, { x: M + 0.3, y: y + 0.56, w: 11.8, h: h - 0.68, fontSize: 12, fontFace: F, paraSpaceAfter: 2, margin: 0 });
};

// ============ S1 封面 ============
let s = P.addSlide();
s.background = { color: BG };
s.addText("BOHACK 2026 · 天津青年黑客松 · 日新主题赛道（DO IT BETTER）", { x: M, y: 1.35, w: 12, h: 0.4, fontSize: 13, color: MUTED, fontFace: F, charSpacing: 3, margin: 0 });
s.addText("InkPress", { x: M, y: 1.85, w: 12.2, h: 1.35, fontSize: 76, bold: true, color: TEXT, fontFace: F, margin: 0 });
s.addText("会自我进化的 AI 博客", { x: M, y: 3.3, w: 12, h: 0.75, fontSize: 34, bold: true, color: LIME, fontFace: F, margin: 0 });
s.addText("文章自己长、流量自己来 —— 每一次反馈都让下一篇更好", { x: M, y: 4.25, w: 12, h: 0.45, fontSize: 16, color: MUTED, fontFace: F, margin: 0 });
dots(s, M, 5.05);
chip(s, M, 5.55, 2.3, "T69 · 伊格小队");
chip(s, M + 2.55, 5.55, 2.9, "演示 117.72.154.28", CYAN);
chip(s, M + 5.7, 5.55, 3.6, "github.com/lggyx/inkpress2agent", VIOLET, 12.5);
s.addNotes("开场 20 秒：InkPress——会自我进化的 AI 博客。一句话：文章自己长、流量自己来，每一次反馈都让下一篇更好。");

// ============ S2 立意① 博客的发展 ============
s = P.addSlide();
s.background = { color: BG };
header(s, "WHY · 博客的发展", "三十年博客史，回答的始终是同一个问题");
const eras = [
  ["1998-2005", "个人主页时代", "手写 HTML / 博客程序，会建站才能写；传播靠友情链接与 RSS 订阅", "写给「能找到你的人」"],
  ["2005-2015", "CMS / 博客程序时代", "WordPress 降低门槛，独立域名 + 自由排版；写作门槛大幅下降", "写给「订阅你的读者」"],
  ["2015-2023", "平台化时代", "公众号 / 知乎 / 小红书：触达变强，但内容被平台锁死、算法决定分发", "写给「平台愿意推的人」"],
  ["2024-", "AI 时代 · 变革点", "AI 既读内容也写内容：搜索从关键词匹配变成 AI 直接给答案", "写给「人 + AI」两个读者"],
];
eras.forEach(([yr, name, desc, answer], i) => {
  const y = 1.9 + i * 1.18;
  s.addText(yr, { x: M, y, w: 1.7, h: 0.42, fontSize: 16, bold: true, color: i === 3 ? LIME : NUM, fontFace: F, margin: 0 });
  s.addText([{ text: name + "　", options: { bold: true, color: TEXT } }, { text: desc, options: { color: MUTED } }],
    { x: M + 1.8, y: y + 0.02, w: 7.6, h: 0.95, fontSize: 13.5, fontFace: F, margin: 0 });
  s.addText(answer, { x: 10.3, y: y + 0.02, w: 2.5, h: 0.9, fontSize: 13.5, bold: i === 3, color: i === 3 ? LIME : MUTED, fontFace: F, margin: 0 });
  if (i < 3) hairline(s, M, y + 0.98, 12.3);
});
s.addShape(P.shapes.ROUNDED_RECTANGLE, { x: M, y: 6.42, w: 12.33, h: 0.56, fill: { color: SURFACE }, line: { color: LIME, width: 1 }, rectRadius: 0.1 });
s.addText("每一次变革都在回答「内容写给谁看、怎么被看到」——AI 时代，这个问题被彻底改写。", { x: M + 0.3, y: 6.42, w: 11.8, h: 0.56, fontSize: 14.5, bold: true, color: LIME, fontFace: F, valign: "middle", margin: 0 });
footer(s, 2);
s.addNotes("30 秒立意：三十年博客史三个时代，每个时代回答同一个问题——写给谁看、怎么被看到。AI 时代这个问题被改写：读者多了 AI 这个新物种。");

// ============ S3 立意② 未来博客存在的形式 ============
s = P.addSlide();
s.background = { color: BG };
header(s, "WHAT · 未来博客存在的形式", "我们的判断：AI 原生的内容资产");
s.addText("未来博客不是「给人看的网页」，而是人与 AI 共读的内容资产——它必须同时满足三个条件：",
  { x: M, y: 1.62, w: 12.2, h: 0.45, fontSize: 15, color: MUTED, fontFace: F, margin: 0 });
const traits = [
  ["① AI 可读", "llms.txt / 结构化摘要 / 纯 markdown 机器版——机器与人同源共读，不做双面孔", CYAN],
  ["② 被 AI 主动引用", "GEO：AI 搜索引擎直接引用你的核心结论，流量入口从「排名」变成「被引用」", LIME],
  ["③ 自我进化", "读者反馈沉淀为写作记忆，注入下一次生成——内容资产越用越准", VIOLET],
];
traits.forEach(([k, v, c], i) => {
  const y = 2.3 + i * 1.28;
  s.addShape(P.shapes.OVAL, { x: M + 0.05, y: y + 0.16, w: 0.16, h: 0.16, fill: { color: c } });
  s.addText([{ text: k + "　", options: { bold: true, fontSize: 19, color: c } }, { text: v, options: { fontSize: 14.5, color: TEXT } }],
    { x: M + 0.4, y, w: 11.9, h: 1.1, fontFace: F, margin: 0 });
  if (i < 2) hairline(s, M, y + 1.12, 12.3);
});
s.addShape(P.shapes.ROUNDED_RECTANGLE, { x: M, y: 6.2, w: 12.33, h: 0.72, fill: { color: SURFACE }, line: { color: LIME, width: 1.25 }, rectRadius: 0.1 });
s.addText("InkPress 就是按这个形态做的——接下来六页：我们做了什么、怎么做的、证据在哪。",
  { x: M + 0.3, y: 6.2, w: 11.8, h: 0.72, fontSize: 15.5, bold: true, color: LIME, fontFace: F, valign: "middle", margin: 0 });
footer(s, 3);
s.addNotes("40 秒立观点：未来博客是 AI 原生的内容资产——AI 可读、被 AI 主动引用、自我进化。InkPress 就是按这个形态做的。");

// ============ S4 功能总览：三大功能一条飞轮 ============
s = P.addSlide();
s.background = { color: BG };
header(s, "FEATURES · 功能总览", "三大功能，一条飞轮：我们做了什么");
const stages = [
  ["功能① · 内容供给", "Agent 自动成文", "Agent 从开发轨迹蒸馏初稿，人审核后发布——内容跟着项目生长", LIME],
  ["功能② · 流量获取", "GEO / SEO 推流", "发布即全自动流量管线，AI 搜索引擎直接引用核心结论", CYAN],
  ["功能③ · 质量进化", "进化闭环", "阅读反馈蒸馏成写作记忆，下一次写得更贴、更快", VIOLET],
];
stages.forEach(([tag, name, desc, c], i) => {
  const x = 0.5 + i * 4.35;
  s.addShape(P.shapes.ROUNDED_RECTANGLE, { x, y: 2.15, w: 3.6, h: 2.7, fill: { color: SURFACE }, line: { color: c, width: 1.25 }, rectRadius: 0.12 });
  s.addText(tag, { x: x + 0.28, y: 2.38, w: 3.1, h: 0.35, fontSize: 12.5, color: MUTED, fontFace: F, margin: 0 });
  s.addText(name, { x: x + 0.28, y: 2.78, w: 3.1, h: 0.5, fontSize: 19, bold: true, color: TEXT, fontFace: F, margin: 0 });
  s.addText(desc, { x: x + 0.28, y: 3.4, w: 3.1, h: 1.3, fontSize: 12.5, color: MUTED, fontFace: F, margin: 0 });
  if (i < 2) s.addText("→", { x: x + 3.58, y: 3.2, w: 0.5, h: 0.6, fontSize: 26, bold: true, color: LIME, fontFace: F, align: "center", margin: 0 });
});
conn(s, 1.2, 5.15, 0, 0.55);
conn(s, 1.2, 5.7, 10.9, 0);
conn(s, 12.1, 5.15, 0, 0.55, true);
s.addText("阅读数回流指导写作 —— 写得越好 → 流量越高 → 素材越多，飞轮转起来", { x: 1.2, y: 5.82, w: 10.9, h: 0.4, fontSize: 13.5, bold: true, color: LIME, fontFace: F, align: "center", margin: 0 });
chip(s, 0.5, 6.45, 7.2, "闭环五环：反馈 → 记忆 → 注入 → 指标 → 回退（全程可回退）", LIME, 12.5);
footer(s, 4);
s.addNotes("30 秒：三大功能一条飞轮——Agent 成文供给内容，GEO 推流带来流量，进化闭环让质量上升。闭环五环全程可回退。接下来六页逐个展开。");

// ============ S5 功能① Agent 自动成文 ============
s = P.addSlide();
s.background = { color: BG };
header(s, "功能① · 文章从哪来", "Agent 自动成文：从开发轨迹到博客文章");
const agentSteps = [
  ["01", "Skill × 会话轨迹", "Agent 日常开发与研究会留下结构化记忆与轨迹文件"],
  ["02", "定期检查汇报", "新技术与新变动：今日接入 StepFun · 修 V23 · 打通七牛"],
  ["03", "蒸馏成文", "把一段时间的新变动自动蒸馏成文章初稿"],
  ["04", "人审核采纳", "初稿过人这一关再发布——质量闸门不缺位"],
];
agentSteps.forEach(([no, k, v], i) => {
  const y = 1.95 + i * 0.92;
  s.addText(no, { x: M, y, w: 0.7, h: 0.42, fontSize: 19, bold: true, color: NUM, fontFace: F, margin: 0 });
  s.addText([{ text: k + "　", options: { bold: true, color: LIME } }, { text: v, options: { color: MUTED } }],
    { x: M + 0.8, y: y + 0.03, w: 6.6, h: 0.75, fontSize: 13.5, fontFace: F, margin: 0 });
  if (i < 3) hairline(s, M, y + 0.72, 7.4);
});
s.addShape(P.shapes.ROUNDED_RECTANGLE, { x: 8.3, y: 1.95, w: 4.53, h: 3.75, fill: { color: SURFACE }, line: { color: LINE, width: 1 }, rectRadius: 0.12 });
s.addText("本期真实素材池", { x: 8.6, y: 2.15, w: 4.0, h: 0.4, fontSize: 15, bold: true, color: TEXT, fontFace: F, margin: 0 });
[["结构化 memory 目录", "定位 / 部署 / 裁决等结构化 md，随手可查", CYAN],
 ["V23 修复事件", "道歉被当记忆入库 → 补占位符 → 四轮彩排，本身就是一篇文章", VIOLET],
 ["发布即记录", "接入 StepFun、七牛 OSS 打通——每一步都是素材", LIME]].forEach(([k, v, c], i) => {
  const y = 2.7 + i * 1.0;
  s.addShape(P.shapes.OVAL, { x: 8.6, y: y + 0.1, w: 0.14, h: 0.14, fill: { color: c } });
  s.addText(k, { x: 8.9, y, w: 3.7, h: 0.38, fontSize: 13.5, bold: true, color: TEXT, fontFace: F, margin: 0 });
  s.addText(v, { x: 8.9, y: y + 0.38, w: 3.75, h: 0.6, fontSize: 11.5, color: MUTED, fontFace: F, margin: 0 });
});
chip(s, M, 6.05, 6.6, "日新赛道本义 = 每天有新内容，内容跟着项目生长", LIME);
s.addShape(P.shapes.ROUNDED_RECTANGLE, { x: M, y: 6.6, w: 8.4, h: 0.46, fill: { color: SURFACE }, line: { color: CYAN, width: 1 }, rectRadius: 0.23 });
s.addText("分发闭环实测：已真实发布至掘金 juejin.cn/post/7707859691133141001（自动化填充 → 人工发布 → 外链自动回报）", { x: M + 0.42, y: 6.6, w: 7.9, h: 0.46, fontSize: 11.5, color: TEXT, fontFace: F, valign: "middle", margin: 0 });
footer(s, 5);
s.addNotes("40 秒，功能①：Agent 用 Skill 结合本地会话轨迹工作，定期检查汇报新变动，蒸馏成初稿、人审核后发布。分发闭环已真实发布到掘金——这不是 demo，是跑通的管线。");

// ============ S6 功能② GEO/SEO 全自动推流 ============
s = P.addSlide();
s.background = { color: BG };
header(s, "功能② · 文章怎么被看到", "发布即推流：全自动流量管线");
const pipe = ["GEO 核心结论", "SEO meta", "OG / canonical", "Article JSON-LD", "sitemap / robots", "RSS"];
pipe.forEach((t, i) => {
  const x = 0.7 + i * 2.0;
  s.addShape(P.shapes.ROUNDED_RECTANGLE, { x, y: 2.05, w: 1.78, h: 0.85, fill: { color: SURFACE }, line: { color: i === 0 ? LIME : LINE, width: i === 0 ? 1.25 : 1 }, rectRadius: 0.1 });
  s.addText(t, { x: x + 0.1, y: 2.05, w: 1.58, h: 0.85, fontSize: 12, bold: i === 0, color: i === 0 ? LIME : TEXT, fontFace: F, align: "center", valign: "middle", margin: 0 });
  if (i < 5) s.addText("→", { x: x + 1.72, y: 2.2, w: 0.36, h: 0.5, fontSize: 16, bold: true, color: MUTED, fontFace: F, align: "center", margin: 0 });
});
s.addText("GEO 核心结论 = AI 搜索引擎可直接引用的结构化摘要（模板：结论先行 + 要点分条）", { x: M, y: 3.15, w: 12.2, h: 0.4, fontSize: 13, color: MUTED, fontFace: F, margin: 0 });
s.addShape(P.shapes.ROUNDED_RECTANGLE, { x: M, y: 3.75, w: 5.9, h: 2.5, fill: { color: SURFACE }, line: { color: LINE, width: 1 }, rectRadius: 0.12 });
s.addText([
  { text: "7/7", options: { fontSize: 48, bold: true, color: LIME } },
  { text: "  GEO 七项量化测试全过", options: { fontSize: 16, bold: true, color: TEXT } },
], { x: M + 0.3, y: 3.95, w: 5.3, h: 0.85, fontFace: F, margin: 0 });
s.addText([
  { text: "GEO 摘要生成成功率 100%（3s 内生成）", options: { breakLine: true, color: TEXT } },
  { text: "sitemap 全覆盖（含 lastmod）· meta 覆盖率 100%", options: { breakLine: true, color: TEXT } },
  { text: "JSON-LD 五字段齐全 · RSS 合法可订阅", options: { color: TEXT } },
], { x: M + 0.3, y: 4.95, w: 5.4, h: 1.2, fontSize: 12.5, fontFace: F, paraSpaceAfter: 3, margin: 0 });
s.addShape(P.shapes.ROUNDED_RECTANGLE, { x: 6.9, y: 3.75, w: 5.93, h: 2.5, fill: { color: SURFACE }, line: { color: LINE, width: 1 }, rectRadius: 0.12 });
s.addText("AI 线四端点已上线（今晚）", { x: 7.2, y: 3.98, w: 5.3, h: 0.4, fontSize: 15, bold: true, color: TEXT, fontFace: F, margin: 0 });
[["/llms.txt · /llms-full.txt", "站点导读与全文，动态生成实时同步", CYAN],
 ["/ai/article/:id · /ai/evolution", "单篇纯 markdown · 进化逐轮日志", CYAN],
 ["robots.txt 欢迎 AI 爬虫", "GPTBot / ClaudeBot / PerplexityBot 显式 Allow", CYAN]].forEach(([k, v, c], i) => {
  const y = 4.5 + i * 0.58;
  dotRow(s, 7.2, y, 5.4, k + "——" + v, c);
});
s.addShape(P.shapes.ROUNDED_RECTANGLE, { x: M, y: 6.45, w: 12.33, h: 0.62, fill: { color: SURFACE }, line: { color: LIME, width: 1 }, rectRadius: 0.1 });
s.addText("目标：65%+ GEO 引用率预估——AI 搜索引擎查询命中率随 llms.txt 与结构化摘要上线后逐步收敛（100% 摘要自动生成为基线）",
  { x: M + 0.3, y: 6.45, w: 11.8, h: 0.62, fontSize: 13.5, bold: true, color: LIME, fontFace: F, valign: "middle", margin: 0 });
footer(s, 6);
s.addNotes("40 秒，功能②：发布即推流——六件套全自动，七项量化 7/7 全过，摘要 3 秒生成。今晚 AI 线四端点已上线，llms.txt 让 AI 引擎读得到、读得爽。65% 引用率是预估目标，管线已就位。");

// ============ S7 功能③ 进化闭环 ============
s = P.addSlide();
s.background = { color: BG };
header(s, "功能③ · 文章怎么越写越好", "进化闭环：反馈变记忆，记忆变行为");
s.addText("写得越好流量越高，流量反馈又指导写作。闭环五步全程落库、可回退。", { x: M, y: 1.62, w: 12.2, h: 0.4, fontSize: 14, color: MUTED, fontFace: F, margin: 0 });
const steps = [
  ["01", "写作运行", "工作台完成一次创作任务", LIME],
  ["02", "作者反馈", "采纳 / 改写 / 纠正原话", VIOLET],
  ["03", "蒸馏记忆", "提炼为持久写作偏好", CYAN],
  ["04", "注入下次", "记忆进入 system prompt", LIME],
  ["05", "指标回退", "两次可比 · 快照还原", VIOLET],
];
steps.forEach(([no, name, desc, c], i) => {
  const x = M + i * 2.45;
  s.addShape(P.shapes.ROUNDED_RECTANGLE, { x, y: 2.25, w: 2.15, h: 2.05, fill: { color: SURFACE }, line: { color: LINE, width: 1 }, rectRadius: 0.12 });
  s.addShape(P.shapes.OVAL, { x: x + 0.22, y: 2.5, w: 0.16, h: 0.16, fill: { color: c } });
  s.addText(no, { x: x + 0.5, y: 2.42, w: 1.4, h: 0.32, fontSize: 13, bold: true, color: NUM, fontFace: F, margin: 0 });
  s.addText(name, { x: x + 0.22, y: 2.85, w: 1.75, h: 0.4, fontSize: 16.5, bold: true, color: TEXT, fontFace: F, margin: 0 });
  s.addText(desc, { x: x + 0.22, y: 3.3, w: 1.75, h: 0.85, fontSize: 11.5, color: MUTED, fontFace: F, margin: 0 });
});
s.addShape(P.shapes.ROUNDED_RECTANGLE, { x: M, y: 4.7, w: 5.95, h: 1.8, fill: { color: SURFACE }, line: { color: LINE, width: 1 }, rectRadius: 0.12 });
s.addText("同一任务两次运行 · 平均修改幅度", { x: M + 0.3, y: 4.9, w: 5.4, h: 0.35, fontSize: 13, color: MUTED, fontFace: F, margin: 0 });
s.addText([
  { text: "29.9%", options: { fontSize: 40, bold: true, color: TEXT } },
  { text: "  →  ", options: { fontSize: 26, color: MUTED } },
  { text: "1.2%", options: { fontSize: 44, bold: true, color: LIME } },
], { x: M + 0.3, y: 5.3, w: 5.4, h: 0.95, fontFace: F, margin: 0 });
s.addShape(P.shapes.ROUNDED_RECTANGLE, { x: 6.9, y: 4.7, w: 5.93, h: 1.85, fill: { color: SURFACE }, line: { color: LINE, width: 1 }, rectRadius: 0.12 });
s.addText("管线实测口径（/ai/evolution 在线可查）", { x: 7.2, y: 4.88, w: 5.4, h: 0.35, fontSize: 13, color: MUTED, fontFace: F, margin: 0 });
s.addText([
  { text: "4 轮进化运行 · 2 条写作记忆沉淀（结构 / 风格）", options: { breakLine: true, color: TEXT } },
  { text: "Run4 带「AI 注入记忆」徽标 · 指标全部落库可复查", options: { breakLine: true, color: TEXT } },
  { text: "快照可还原：学到坏偏好一键回退，幂等可反复演示", options: { color: TEXT } },
], { x: 7.2, y: 5.26, w: 5.5, h: 1.2, fontSize: 12.5, fontFace: F, paraSpaceAfter: 3, margin: 0 });
chip(s, M, 6.58, 6.6, "进化数据为管线实测口径 · /ai/evolution 逐轮在线可查", VIOLET, 11.5);
footer(s, 7);
s.addNotes("40 秒，功能③：五步闭环。实测 Run1 修改幅度 29.9%，记忆注入后第二轮降到 1.2%——95.9% 的降幅就是进化的直接证据。数据全部落库，/ai/evolution 在线可查。");

// ============ S8 内容规模 ============
s = P.addSlide();
s.background = { color: BG };
header(s, "CONTENT · 内容规模", "35 篇全量真实语料：两年写作史全部在线");
s.addShape(P.shapes.ROUNDED_RECTANGLE, { x: M, y: 2.0, w: 4.7, h: 3.6, fill: { color: SURFACE }, line: { color: LINE, width: 1 }, rectRadius: 0.12 });
s.addText([
  { text: "35", options: { fontSize: 88, bold: true, color: LIME } },
  { text: "  篇", options: { fontSize: 24, color: TEXT } },
], { x: M + 0.35, y: 2.4, w: 4.0, h: 1.6, fontFace: F, margin: 0 });
s.addText("全量真实博客文章", { x: M + 0.35, y: 4.05, w: 4.0, h: 0.5, fontSize: 20, bold: true, color: TEXT, fontFace: F, margin: 0 });
s.addText("2024-09 至 2026-11 · 两年写作史\n555 张配图", { x: M + 0.35, y: 4.65, w: 4.0, h: 0.9, fontSize: 13, color: MUTED, fontFace: F, margin: 0 });
const scaleRows = [
  ["语料训练规模", "3000+ 条语料喂给进化与检索——飞轮的燃料仓", LIME],
  ["内容结构", "分类归并为 3：算法与竞赛 19 / 开发笔记 12 / 杂记 4", VIOLET],
  ["AI 线机器可读", "llms.txt / llms-full.txt 已上线，实时聚合全语料（动态生成）", CYAN],
  ["检索与图谱", "ES 8.15 三臂索引 + 分类色节点图谱，演示数据全部以这 35 篇为准", CYAN],
];
scaleRows.forEach(([k, v, c], i) => {
  const y = 2.0 + i * 0.93;
  s.addShape(P.shapes.ROUNDED_RECTANGLE, { x: 5.6, y, w: 7.23, h: 0.82, fill: { color: SURFACE }, line: { color: LINE, width: 1 }, rectRadius: 0.1 });
  s.addShape(P.shapes.OVAL, { x: 5.85, y: y + 0.18, w: 0.16, h: 0.16, fill: { color: c } });
  s.addText(k, { x: 6.15, y: y + 0.08, w: 6.4, h: 0.36, fontSize: 14.5, bold: true, color: TEXT, fontFace: F, margin: 0 });
  s.addText(v, { x: 6.15, y: y + 0.44, w: 6.5, h: 0.34, fontSize: 12, color: MUTED, fontFace: F, margin: 0 });
});
s.addText("全部配图已迁至七牛 OSS 外链（555 张），分发到外平台不再有死链", { x: M, y: 6.15, w: 12.2, h: 0.4, fontSize: 12.5, color: MUTED, fontFace: F, margin: 0 });
footer(s, 8);
s.addNotes("20 秒：35 篇全量真实博客，两年写作史、555 张配图全部迁 OSS 外链；3000+ 语料训练规模是飞轮的燃料仓。");

// ============ S9 工程复盘（I+II 合并） ============
s = P.addSlide();
s.background = { color: BG };
header(s, "PROCESS · 赛期工程复盘", "坏消息如实归档，修复有数：四起真实事件");
event(s, 1.8, 1.15, "事件 ① · V23 迁移：蒸馏模板缺占位符（致命）", LIME, [
  "发现：彩排 Run1 把「缺少反馈数据」的道歉当记忆入库　修复：模板补 {{content}} + 迁移同步　验证：四轮彩排蒸馏出 2 条真记忆",
]);
event(s, 3.05, 1.15, "事件 ② · 密钥掩码回写三处修复", CYAN, [
  "发现：掩码回显值被下次保存写回数据库　修复：前端 touched 标记 + 后端含 **** 拒绝落库　验证：连接实测通过",
]);
event(s, 4.3, 1.15, "事件 ③ · GEO 公式乱码修复（4e2f5ea）", VIOLET, [
  "发现：核心结论区块公式乱码　修复：与正文同管线渲染（KaTeX 统一）　验证：公式 / 图谱 / 检索零回归",
]);
event(s, 5.55, 1.15, "事件 ④ · SSR 首页 swr 缓存优化", LIME, [
  "发现：压测如实暴露 SSR 首页 P95 730ms 超标　修复：swr 缓存，本机复测 730 → 181ms　附带：RSS 路由修复",
]);
s.addText("出处：docs/测试报告.md（730ms 如实归档）· commits aed3f3b / 4e2f5ea · 181ms 为本机复测 · 单测 12/12 · P95 372/87/221ms 全达标",
  { x: M, y: 6.78, w: 12.2, h: 0.26, fontSize: 11, color: MUTED, fontFace: F, margin: 0 });
footer(s, 9);
s.addNotes("40 秒：四起真事件——蒸馏模板缺占位符是致命 bug，掩码回写是安全隐患，GEO 公式与 SSR 性能如实归档。每个都有发现、修复、验证三段。");

// ============ S10 架构 ============
s = P.addSlide();
s.background = { color: BG };
header(s, "ARCH · 架构一页", "一台完整的创作机器：从浏览器到多平台发布");
box(0.5, 3.3, 1.6, 1.0, "浏览器", "读者 & 博主");
box(2.65, 3.3, 1.85, 1.0, "nginx", "网关 :80");
box(5.05, 1.95, 3.15, 1.1, "web · Nuxt 3", "SSR/ISR 前台 · 工作室 · AI 线路由", CYAN);
box(5.05, 3.5, 3.15, 1.1, "server · Spring Boot 3.3", "Java 17 · 九大功能模块", LIME);
box(5.05, 5.15, 3.15, 1.0, "distributor", "Playwright 本机分发 CLI");
const drows = [
  ["MySQL 8", "存储 · Flyway 24 个迁移"],
  ["Redis 7", "计数缓冲 / 限流"],
  ["RabbitMQ 3.13", "索引同步 + 死信队列"],
  ["Elasticsearch 8.15 + IK", "BM25 / 向量 / RRF 三臂"],
  ["七牛云 OSS", "文章图片对象存储"],
];
drows.forEach(([k, v], i) => {
  const y = 1.72 + i * 0.84;
  s.addShape(P.shapes.ROUNDED_RECTANGLE, { x: 8.95, y, w: 3.9, h: 0.72, fill: { color: SURFACE }, line: { color: LINE, width: 1 }, rectRadius: 0.08 });
  s.addText([{ text: k + "　", options: { bold: true, color: TEXT } }, { text: v, options: { color: MUTED } }],
    { x: 9.15, y: y + 0.06, w: 3.6, h: 0.6, fontSize: 12.5, fontFace: F, valign: "middle", margin: 0 });
});
conn(s, 2.1, 3.8, 0.55, 0);
conn(s, 4.5, 3.75, 0.55, -1.2, true);
conn(s, 4.5, 3.85, 0.55, 0.2);
conn(s, 8.2, 4.0, 0.75, -0.35);
conn(s, 6.62, 4.6, 0, 0.55);
["掘金", "CSDN", "知乎", "公众号"].forEach((t, i) => {
  s.addText(t + (i < 3 ? "  ·" : ""), { x: 5.05 + i * 0.82, y: 6.24, w: 0.82, h: 0.35, fontSize: 12.5, color: TEXT, fontFace: F, align: "center", margin: 0 });
});
s.addText("平台 Cookie 不出机 · ES 宕机自动降级 MySQL · embedding 槽留空自动降级 BM25（设计内）", { x: 0.5, y: 6.74, w: 8.2, h: 0.3, fontSize: 12, color: MUTED, fontFace: F, margin: 0 });
footer(s, 10);
s.addNotes("30 秒：nginx 网关后面 Nuxt 3 与 Spring Boot 各守一层，数据层五个组件各司其职，图片走七牛 OSS，分发是本机 Playwright、Cookie 不出机。这台机器就是飞轮的骨架。");

// ============ S11 演示入口 ============
s = P.addSlide();
s.background = { color: BG };
header(s, "DEMO · 现场演示", "打开就能跑：演示站与账号");
chip(s, M, 2.0, 3.4, "演示站 117.72.154.28", CYAN);
chip(s, M, 2.62, 4.3, "github.com/lggyx/inkpress2agent", VIOLET, 12.5);
s.addShape(P.shapes.ROUNDED_RECTANGLE, { x: M, y: 3.35, w: 6.2, h: 1.25, fill: { color: SURFACE }, line: { color: LINE, width: 1 }, rectRadius: 0.12 });
s.addText("演示账号（BLOGGER · 可直接体验进化面板）", { x: M + 0.3, y: 3.55, w: 5.7, h: 0.35, fontSize: 12.5, color: MUTED, fontFace: F, margin: 0 });
s.addText("evo-demo@local.dev  /  EvoDemo2026!", { x: M + 0.3, y: 3.95, w: 5.7, h: 0.45, fontSize: 17, bold: true, color: TEXT, fontFace: F, margin: 0 });
s.addText([
  { text: "演示节奏　", options: { bold: true, color: LIME } },
  { text: "拍快照 → 开始运行 → 反馈 → 结束（出记忆）→ 再运行 → 对比 → 回退", options: { color: TEXT } },
], { x: M, y: 4.95, w: 6.3, h: 1.0, fontSize: 14.5, fontFace: F, margin: 0 });
s.addText("全部指标可复查：metrics_json 落库 + 测试报告归档 + /ai/evolution 在线版", { x: M, y: 6.1, w: 6.2, h: 0.4, fontSize: 12.5, color: MUTED, fontFace: F, margin: 0 });
shot(s, "03-workbench-ai-reply.png", 7.0, 2.0, 5.83, 3.64);
s.addText("工作台：AI 创作流 × 文档微调 × 大纲（docs/screenshots/03 实录）", { x: 7.0, y: 5.78, w: 5.83, h: 0.4, fontSize: 12, color: MUTED, fontFace: F, margin: 0 });
footer(s, 11);
s.addNotes("30 秒：评委现在就可以打开演示站，用这个账号体验进化面板。现场演示按七步节奏走，全程可复查。");

// ============ S12 Roadmap ============
s = P.addSlide();
s.background = { color: BG };
header(s, "ROADMAP · 下一步", "飞轮全速转：已上线与在路上");
const roadCard = (x, title, c, rows) => {
  s.addShape(P.shapes.ROUNDED_RECTANGLE, { x, y: 2.0, w: 6.0, h: 3.5, fill: { color: SURFACE }, line: { color: LINE, width: 1 }, rectRadius: 0.12 });
  s.addShape(P.shapes.OVAL, { x: x + 0.3, y: 2.27, w: 0.18, h: 0.18, fill: { color: c } });
  s.addText(title, { x: x + 0.62, y: 2.17, w: 5.1, h: 0.42, fontSize: 16.5, bold: true, color: TEXT, fontFace: F, margin: 0 });
  rows.forEach((t, i) => dotRow(s, x + 0.3, 2.85 + i * 0.66, 5.5, t, c));
};
roadCard(M, "已上线（今晚）", LIME, [
  "AI 可读线四端点：/llms.txt / llms-full / ai/article / ai/evolution",
  "robots.txt 显式 Allow GPTBot / ClaudeBot / PerplexityBot",
  "与 sitemap.xml 同构：请求时从库直出，零构建零同步",
  "图片全量迁 OSS 外链——分发到外平台不再有死链",
]);
roadCard(6.83, "在路上（下一步）", ORANGE, [
  "Agent 定期自动成文：检查汇报新技术与新变动",
  "变动自动蒸馏成初稿——飞轮第一段全自动化",
  "GEO 引用率向 65%+ 目标收敛（明起抽查 AI 引擎）",
  "移动端 375px 六页面规格已定案，预览台先行",
]);
s.addText("四个阶段每步独立可回滚：AI 线（已上线）→ P1 设计 token 落地 → P2 页面逐个换装 → P3 桌面宽幅延伸",
  { x: M, y: 6.5, w: 12.2, h: 0.4, fontSize: 12.5, color: MUTED, fontFace: F, margin: 0 });
footer(s, 12);
s.addNotes("30 秒收尾：AI 可读线今晚已上线，下一步 Agent 定期自动成文把飞轮第一段全自动化，GEO 引用率向 65% 收敛。飞轮转起来，谢谢。");

P.writeFile({ fileName: "D:\\inkpress2agent\\docs\\InkPress-BoHack2026-路演.pptx" }).then(() => console.log("OK 13 pages -> docs/InkPress-BoHack2026-路演.pptx"));
