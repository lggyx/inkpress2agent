const bodyObj = { email: 'evo-demo@local.dev', password: 'EvoDemo2026!', deviceName: 'd' };
const body = JSON.stringify(bodyObj);
console.log('body bytes:', Buffer.byteLength(body));
console.log('body:', JSON.stringify(body));
console.log('password hex:', Array.from(Buffer.from(bodyObj.password)).map(b => b.toString(16).padStart(2, '0')).join(' '));
