// 易拉宝 80x180cm @180DPI = 5760x12960 像素，Node 原生渲染（无浏览器）
// 视觉与 rollup.html 同构：工程暗色 #0C0C0E × 青柠 #D9F24F
const { createCanvas, GlobalFonts, loadImage } = require('@napi-rs/canvas');
const fs = require('fs');
const path = require('path');

// 注册中文字体（Windows 微软雅黑）
const fontsDir = 'C:\\Windows\\Fonts';
try { GlobalFonts.registerFromPath(path.join(fontsDir, 'msyhbd.ttc'), 'MSYH-B'); } catch (e) {}
try { GlobalFonts.registerFromPath(path.join(fontsDir, 'msyh.ttc'), 'MSYH'); } catch (e) {}
const F = (w) => (w >= 800 ? 'MSYH-B' : 'MSYH');

const W = 5760, H = 12960;
// 设计稿 1440x3240 的 4 倍
const S = 4;
const canvas = createCanvas(W, H);
const ctx = canvas.getContext('2d');

// ---------- 基础工具 ----------
function rr(x, y, w, h, r) { // 圆角矩形路径
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + w, y, x + w, y + h, r);
  ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r);
  ctx.arcTo(x, y, x + w, y, r);
  ctx.closePath();
}
function fillRR(x, y, w, h, r, color) { rr(x, y, w, h, r); ctx.fillStyle = color; ctx.fill(); }
function text(str, x, y, size, color, weight, ls) {
  ctx.fillStyle = color;
  ctx.font = `${weight} ${size}px ${F(weight)}`;
  if (ls) ctx.letterSpacing = ls + 'px'; else ctx.letterSpacing = '0px';
  ctx.fillText(str, x, y);
}
function textWidth(str, size, weight) {
  ctx.font = `${weight} ${size}px ${F(weight)}`;
  return ctx.measureText(str).width;
}
// 自动换行（按像素宽），返回行数
function wrapText(str, x, y, maxW, size, lh, color, weight) {
  ctx.font = `${weight} ${size}px ${F(weight)}`;
  const chars = Array.from(str);
  let line = '', yy = y;
  const lines = [];
  for (const ch of chars) {
    if (ctx.measureText(line + ch).width > maxW && line) { lines.push(line); line = ch; }
    else line += ch;
  }
  if (line) lines.push(line);
  for (const ln of lines) { ctx.fillStyle = color; ctx.fillText(ln, x, yy); yy += lh; }
  return yy;
}

// ---------- 背景 ----------
ctx.fillStyle = '#0C0C0E';
ctx.fillRect(0, 0, W, H);

// ---------- 品牌区 ----------
text('INK', 360, 500, 200, '#FFFFFF', 800, 8);
text('PRESS', 360 + textWidth('INK', 200, 800) + 8, 500, 200, '#D9F24F', 800, 8);
const tagText = 'BOHACK 2026 · 日新赛道 · T69 伊格小队';
text(tagText, W - 360 - textWidth(tagText, 118, 400), 480, 118, '#8A8A93', 400, 24);

// ---------- 主标题区 ----------
const kickText = '会自我进化的 AI 博客';
const kickW = textWidth(kickText, 145, 800) + 100;
fillRR(360, 1010, kickW, 200, 30, '#D9F24F');
text(kickText, 410, 1155, 145, '#0C0C0E', 800, 16);

const l1 = '文章自己长，';
const l2a = '流量';
const l2b = '自己来';
const l2c = '。';
let yy = 1720;
text(l1, 350, yy, 470, '#FFFFFF', 900, 6);
yy += 580;
let xx = 350;
text(l2a, xx, yy, 470, '#FFFFFF', 900, 6); xx += textWidth(l2a, 470, 900) + 10;
text(l2b, xx, yy, 470, '#D9F24F', 900, 6); xx += textWidth(l2b, 470, 900) + 10;
text(l2c, xx, yy, 470, '#5A5A62', 400, 6);

// 副标题（三段混排，手工分段着色）
yy += 230;
const subLine = (parts, y) => { let x = 360; for (const [t, c, b] of parts) { text(t, x, y, 150, c, b ? 800 : 400); x += textWidth(t, 150, b ? 800 : 400); } };
subLine([['Agent 从', '#c9c9cf', 0], ['开发轨迹', '#FFFFFF', 1], ['里写文章 · ', '#c9c9cf', 0], ['GEO/SEO', '#FFFFFF', 1]], yy);
yy += 205;
subLine([['让 AI 搜索引擎主动引用 · ', '#c9c9cf', 0], ['每一次反馈都让下一篇', '#c9c9cf', 0], ['更好', '#FFFFFF', 1]], yy);

// ---------- 插画贴入（异步：在 QR .then() 链最前执行，导出前完成） ----------
function pasteIllustrations() {
  return Promise.all([
    loadImage(path.join(__dirname, 'illu_hero_writer.png')),
    loadImage(path.join(__dirname, 'illu_loop_ring.png')),
    loadImage(path.join(__dirname, 'illu_memory_stack.png')),
  ]).then(([hero, ring, mem]) => {
    // ① hero 右侧主视觉：机械手写文章（宽 2000，位于 y 560..1560，不压 metrics）
    const hw1 = 2000, hh1 = 1000;
    const scx = (hero.width - Math.min(hero.width, hero.height * 2)) / 2;
    ctx.drawImage(hero, scx, 0, Math.min(hero.width, hero.height * 2), hero.height,
      W - 360 - hw1, 560, hw1, hh1);
    // ② loop_ring：放三卡与 metrics 之间的空隙带（y 8640..9800 之内），右侧放置
    const rw = 2200, rh = 1100 * 0.72;
    const syc = (ring.height - ring.height * 0.72) / 2;
    ctx.drawImage(ring, (ring.width - ring.width * 0.7) / 2, syc, ring.width * 0.7, ring.height * 0.72,
      W - 360 - rw, 8640, rw, rh);
  }).catch(e => console.error('illu skip:', e.message));
}

// ---------- 飞轮三段 ----------
const steps = [
  { no: '1', ac: '#D9F24F', h2: 'Agent 自动成文 · 内容供给',
    lines: [
      [['Skill 结合', '#b9b9c1', 0], ['本地会话轨迹', '#FFFFFF', 1], ['：Agent 每天检查汇报期内的', '#b9b9c1', 0]],
      [['新技术采用与新变动，', '#b9b9c1', 0], ['自动蒸馏成文章初稿', '#D9F24F', 1], ['，', '#b9b9c1', 0]],
      [['人工一键采纳发布——项目生长，博客就生长。', '#b9b9c1', 0]],
    ] },
  { no: '2', ac: '#5EC8F8', h2: 'GEO / SEO 全自动推流 · 流量获取',
    lines: [
      [['发布即生成', '#b9b9c1', 0], ['「核心结论」结构化摘要', '#5EC8F8', 1], ['，', '#b9b9c1', 0]],
      [['AI 搜索引擎直接引用；JSON-LD / sitemap / RSS', '#b9b9c1', 0]],
      [['全链自动，', '#b9b9c1', 0], ['文章越来越容易获得流量', '#5EC8F8', 1], ['。', '#b9b9c1', 0]],
    ] },
  { no: '3', ac: '#FF8A5C', h2: '进化闭环 · 越写越好',
    lines: [
      [['读者的每一次采纳与修正都沉淀为', '#b9b9c1', 0], ['写作记忆', '#FFFFFF', 1], ['，', '#b9b9c1', 0]],
      [['注入下一次生成——同一任务第二次运行，', '#b9b9c1', 0]],
      [['修改幅度 29.9% → 1.2%', '#FF8A5C', 1], ['。', '#b9b9c1', 0]],
    ] },
];
let sy = 4680;
for (const st of steps) {
  fillRR(360, sy, W - 720, 1240, 56, '#151518');
  ctx.save(); ctx.fillStyle = st.ac; ctx.fillRect(360, sy, 56, 1240); ctx.restore();
  // 编号徽章
  const noW = textWidth(st.no, 160, 900) + 120;
  fillRR(560, sy - 110, noW, 190, 36, st.ac);
  text(st.no, 560 + 60, sy + 55, 160, '#0C0C0E', 900);
  // 标题
  text(st.h2, 700, sy + 350, 218, '#FFFFFF', 900, 6);
  // 三行正文（手动断行，绝不溢出）
  let py = sy + 620;
  for (const line of st.lines) {
    let px = 700;
    for (const [t, c, b] of line) { text(t, px, py, 145, c, b ? 800 : 400); px += textWidth(t, 145, b ? 800 : 400); }
    py += 215;
  }
  sy += 1240 + 270;
  // 箭头
  if (st.no !== '3') {
    ctx.fillStyle = '#55555E';
    const ax = W / 2 - 60, ay = sy - 210;
    ctx.beginPath();
    ctx.moveTo(ax, ay); ctx.lineTo(ax + 120, ay); ctx.lineTo(ax + 60, ay + 90); ctx.closePath(); ctx.fill();
  }
}

// ---------- 数据锚点条 ----------
const my = 9880;
fillRR(360, my, W - 720, 900, 70, '#D9F24F');
const metrics = [
  { v: '35 篇', k: '全量真实博客语料' },
  { v: '7/7', k: 'GEO 量化测试全过' },
  { v: '100%', k: '摘要自动生成成功率' },
  { v: '29.9→1.2%', k: '二轮修改幅度骤降' },
];
const cellW = (W - 720 - 240) / 4;
metrics.forEach((m, i) => {
  const cx = 460 + i * (cellW + 80);
  const vSize = m.v.length > 6 ? 210 : 280;
  text(m.v, cx + cellW / 2 - textWidth(m.v, vSize, 900) / 2, my + 400, vSize, '#0C0C0E', 900);
  // 键文字可能需换行
  const kLines = [];
  ctx.font = `400 104px ${F(400)}`;
  let ln = '';
  for (const ch of Array.from(m.k)) {
    if (ctx.measureText(ln + ch).width > cellW - 40 && ln) { kLines.push(ln); ln = ch; } else ln += ch;
  }
  if (ln) kLines.push(ln);
  kLines.forEach((kl, ki) => text(kl, cx + cellW / 2 - textWidth(kl, 104, 400) / 2, my + 590 + ki * 140, 104, '#33330F', 700));
});

// ---------- 底部：二维码 + CTA ----------
const qy = 10980;
// 二维码（标准 qrcode 包生成的 png，本地贴入）
pasteIllustrations().then(() => loadImage(path.join(__dirname, 'qr_site.png'))).then(img => {
  const qs = 1280;
  fillRR(360, qy, qs + 100, qs + 100, 60, '#FFFFFF');
  ctx.drawImage(img, 410, qy + 50, qs, qs);
  // CTA 文本
  const tx = 360 + qs + 320;
  text('现在打开，', tx, qy + 300, 240, '#FFFFFF', 900);
  text('亲眼看看', tx + textWidth('现在打开，', 240, 900), qy + 300, 240, '#D9F24F', 900);
  text('它已经写了什么', tx, qy + 590, 240, '#FFFFFF', 900);
  text('117.72.154.28', tx, qy + 900, 170, '#D9F24F', 800, 6);
  text('演示账号 evo-demo@local.dev', tx, qy + 1100, 128, '#9A9AA2', 400);
  text('进化面板 / AI 检索 / 写作记忆 现场可玩', tx, qy + 1260, 128, '#9A9AA2', 400);
  // 底部赛道条
  ctx.fillStyle = '#D9F24F';
  ctx.fillRect(0, H - 420, W, 420);
  const words = ['反馈', '记忆', '注入', '指标', '回退'];
  const wordW = words.map(w => textWidth(w, 155, 900));
  const dotW = 56;
  const total = wordW.reduce((a, b) => a + b, 0) + dotW * 4 + 160 * 8;
  let sx = (W - total) / 2;
  const wyy = H - 420 + 270;
  words.forEach((w, i) => {
    text(w, sx, wyy, 155, '#0C0C0E', 900, 24); sx += wordW[i] + 80;
    if (i < 4) { ctx.beginPath(); ctx.arc(sx + dotW / 2, wyy - 52, dotW / 2, 0, Math.PI * 2); ctx.fill(); sx += dotW + 160; }
  });
  // ---------- 导出 PNG（无损母版） ----------
  const png = canvas.toBuffer('image/png');
  fs.writeFileSync(path.join(__dirname, 'InkPress-易拉宝-80x180cm-5760x12960.png'), png);
  // 同时导出高质量 JPG（印刷常用，体积小）
  const jpg = canvas.toBuffer('image/jpeg', { quality: 0.95, chromaSubsampling: false });
  fs.writeFileSync(path.join(__dirname, 'InkPress-易拉宝-80x180cm-5760x12960.jpg'), jpg);
  console.log('EXPORTED png=' + png.length + ' jpg=' + jpg.length);
}).catch(e => { console.error('IMG ERR:', e.message); process.exit(1); });
