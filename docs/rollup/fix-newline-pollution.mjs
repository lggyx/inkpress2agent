/**
 * 修复批量重写引入的字面 \n 污染：经公开 API 读（JSON 正确解码），字面反斜杠n → 真换行，写回
 */
const BASE = 'http://117.72.154.28';

const login = await fetch(BASE + '/api/auth/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: 'evo-demo@local.dev', password: 'EvoDemo2026!' }),
}).then(r => r.json());
const token = login.data.accessToken;
const H = { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' };
console.log('login ok');

let fixed = 0, clean = 0;
for (let id = 24; id <= 58; id++) {
  const resp = await fetch(BASE + '/api/articles/' + id, { headers: H }).then(r => r.json());
  const a = resp.data;
  if (!a || a.status !== 'PUBLISHED') continue;
  const md = a.contentMd || '';
  if (!md.includes('\\n')) { clean++; continue; }
  // 字面反斜杠+n → 真换行；同时修复字面 \" 不存在于此场景
  const fixedMd = md.split('\\n').join('\n');
  const body = JSON.stringify({
    id, title: a.title, contentMd: fixedMd, categoryId: a.categoryId,
    tags: a.tags || [], action: 'PUBLISH', confirmLowScore: true,
  });
  const r = await fetch(BASE + '/api/studio/articles', {
    method: 'POST', headers: { ...H, 'Content-Type': 'application/json' },
    body: Buffer.from(body),
  }).then(r => r.json());
  if (r.code === 0 && r.data.status === 'PUBLISHED') { fixed++; console.log('fixed', id); }
  else console.log('FIX-FAIL', id, r.message);
}
console.log('DONE fixed=' + fixed + ' already_clean=' + clean);
