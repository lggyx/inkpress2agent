/**
 * 图片批量迁移到 OSS + 迁移文章正文重写
 * 1) 遍历 web/public/images 全部文件 → 上传 /api/media/upload（evo-demo 设备令牌）→ 建立 /images/<rel> → OSS URL 映射
 * 2) 逐篇更新迁移文章（id 24-58）的 contentMd：/images/... → OSS 外链（保持 PUBLISHED 状态）
 * 3) 映射表存 docs/rollup/image-oss-map.json（防重跑重复上传）
 */
import fs from 'node:fs';
import path from 'node:path';
import { execSync } from 'node:child_process';

const BASE = 'http://117.72.154.28';
const IMG_ROOT = 'D:\\inkpress2agent\\web\\public\\images';
const MAP_PATH = 'D:\\inkpress2agent\\docs\\rollup\\image-oss-map.json';

// 1) 登录
const login = await fetch(BASE + '/api/auth/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: 'evo-demo@local.dev', password: 'EvoDemo2026!' }),
}).then(r => r.json());
const token = login.data.accessToken;
const H = { Authorization: 'Bearer ' + token };
console.log('login ok');

// 2) 上传全部图片（跳过已映射的）
const map = fs.existsSync(MAP_PATH) ? JSON.parse(fs.readFileSync(MAP_PATH, 'utf-8')) : {};
function walk(dir, base) {
  let out = [];
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, e.name);
    const rel = base ? base + '/' + e.name : e.name;
    if (e.isDirectory()) out = out.concat(walk(full, rel));
    else out.push({ full, rel });
  }
  return out;
}
const files = walk(IMG_ROOT, '');
console.log('local images:', files.length, 'already mapped:', Object.keys(map).length);

let uploaded = 0, upFail = 0;
for (const f of files) {
  const key = '/images/' + f.rel;
  if (map[key]) continue;
  try {
    const buf = fs.readFileSync(f.full);
    const ext = path.extname(f.full).toLowerCase();
    const mime = ext === '.jpg' || ext === '.jpeg' ? 'image/jpeg' : ext === '.webp' ? 'image/webp' : ext === '.gif' ? 'image/gif' : 'image/png';
    const fd = new FormData();
    fd.append('file', new Blob([buf], { type: mime }), path.basename(f.full));
    const r = await fetch(BASE + '/api/media/upload', {
      method: 'POST', headers: { Authorization: 'Bearer ' + token }, body: fd,
    }).then(r => r.json());
    if (r.code !== 0) throw new Error(r.message);
    const url = r.data.url || r.data.publicUrl;
    map[key] = url;
    uploaded++;
    if (uploaded % 50 === 0) console.log('uploaded', uploaded, '…');
  } catch (e) {
    upFail++;
    console.log('UP-FAIL', f.rel, e.message.slice(0, 80));
  }
}
fs.writeFileSync(MAP_PATH, JSON.stringify(map, null, 1));
console.log('upload done: new=' + uploaded, 'fail=' + upFail, 'map_total=' + Object.keys(map).length);

// 3) 重写迁移文章正文（id 24-58）
for (let id = 24; id <= 58; id++) {
  try {
    const row = execSync(
      `docker exec inkpress2agent-mysql mysql -uinkpress -pinkpress123 inkpress --default-character-set=utf8mb4 -N -e "SELECT content_md FROM article WHERE id=${id} AND deleted=0"`,
      { encoding: 'utf-8', maxBuffer: 32 * 1024 * 1024 });
    if (!row.trim()) continue;
    let md = row.replace(/^.*?\n/, ''); // 去掉首行列头
    // 去掉尾部的 \n 字面量（mysql -N 输出转义）
    let changed = 0;
    for (const [k, url] of Object.entries(map)) {
      if (md.includes(k)) { md = md.split(k).join(url); changed++; }
    }
    if (changed === 0) continue;
    // 取标题/分类/标签
    const meta = execSync(
      `docker exec inkpress2agent-mysql mysql -uinkpress -pinkpress123 inkpress --default-character-set=utf8mb4 -N -e "SELECT title, category_id FROM article WHERE id=${id} AND deleted=0"`,
      { encoding: 'utf-8', maxBuffer: 4 * 1024 * 1024 });
    const metaLines = meta.trim().split('\n');
    const title = metaLines[0].replace(/\t.*$/, '');
    const catId = Number(metaLines[metaLines.length - 1].split('\t').pop());
    const tags = execSync(
      `docker exec inkpress2agent-mysql mysql -uinkpress -pinkpress123 inkpress --default-character-set=utf8mb4 -N -e "SELECT t.name FROM article_tag at2 JOIN tag t ON t.id=at2.tag_id WHERE at2.article_id=${id}"`,
      { encoding: 'utf-8', maxBuffer: 1024 * 1024 }).trim().split('\n').filter(Boolean);
    const body = JSON.stringify({
      id, title, contentMd: md, categoryId: catId, tags, action: 'PUBLISH', confirmLowScore: true,
    });
    const r = await fetch(BASE + '/api/studio/articles', {
      method: 'POST', headers: { ...H, 'Content-Type': 'application/json' },
      body: Buffer.from(body),
    }).then(r => r.json());
    console.log('article', id, '->', r.code === 0 ? 'OK ' + r.data.status : 'FAIL ' + r.message);
  } catch (e) {
    console.log('ART-FAIL', id, e.message.slice(0, 100));
  }
}
console.log('ALL DONE');
