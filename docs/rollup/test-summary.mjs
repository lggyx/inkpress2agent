const r = await fetch('http://117.72.154.28/api/evolution/public-summary');
console.log(r.status, (await r.text()).slice(0, 400));
