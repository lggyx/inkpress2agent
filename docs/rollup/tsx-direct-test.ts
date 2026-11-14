const body = JSON.stringify({ email: 'evo-demo@local.dev', password: 'EvoDemo2026!', deviceName: 'd' });
const r = await fetch('http://117.72.154.28/api/auth/device', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body });
const j = await r.json();
console.log('tsx-direct:', r.status, 'code:', j.code, j.code === 0 ? 'OK' : 'msg=' + j.message);
