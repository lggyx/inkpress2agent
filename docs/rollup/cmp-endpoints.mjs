const body = JSON.stringify({ email: 'evo-demo@local.dev', password: 'EvoDemo2026!' });
const r = await fetch('http://117.72.154.28/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body });
const j = await r.json();
console.log('login endpoint:', r.status, 'code:', j.code, j.code === 0 ? 'OK' : j.message);
const body2 = JSON.stringify({ email: 'evo-demo@local.dev', password: 'EvoDemo2026!', deviceName: 'd' });
const r2 = await fetch('http://117.72.154.28/api/auth/device', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: body2 });
const j2 = await r2.json();
console.log('device endpoint:', r2.status, 'code:', j2.code, j2.code === 0 ? 'OK' : j2.message);
console.log('login body bytes:', Buffer.byteLength(body), '| device body bytes:', Buffer.byteLength(body2));
