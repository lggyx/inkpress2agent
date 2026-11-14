import { login } from 'D:/inkpress2agent/distributor/src/api.js';
const r = await login('evo-demo@local.dev', 'EvoDemo2026!', 'd');
console.log('api.ts login OK, token_len:', r.deviceToken.length);
