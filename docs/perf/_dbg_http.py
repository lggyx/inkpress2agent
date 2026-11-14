import json, subprocess, os, sys
sys.path.insert(0, r'D:\inkpress2agent\docs\perf')
os.environ['INKPRESS_BASE'] = 'http://117.72.154.28'
# 复刻 seo_tests.http()
S = r'D:\inkpress2agent\docs\perf\_tmp'
os.makedirs(S, exist_ok=True)
_seq = 0

def http(path, payload=None, token=None, method=None, ua=None):
    global _seq
    _seq += 1
    out_f = f'{S}\\out_{_seq}.txt'
    args = ['curl', '-sS', '-m', '60', '-X', method or ('POST' if payload is not None else 'GET')]
    if ua:
        args += ['-A', ua]
    if payload is not None:
        body_f = f'{S}\\body_{_seq}.json'
        open(body_f, 'wb').write(json.dumps(payload, ensure_ascii=False).encode('utf-8'))
        args += ['-H', 'Content-Type: application/json', '--data-binary', '@' + body_f]
    if token:
        args += ['-H', 'Authorization: Bearer ' + token]
    args += ['-o', out_f, '-w', '%{http_code}', os.environ['INKPRESS_BASE'] + path]
    r = subprocess.run(args, capture_output=True)
    code = int(r.stdout.decode('ascii', errors='replace').strip() or 0)
    body = open(out_f, 'rb').read().decode('utf-8', errors='replace')
    return code, body

code, body = http('/api/articles?page=1&size=100')
print('code:', code, '| body len:', len(body))
try:
    d = json.loads(body)
    recs = ((d.get('data') or {}).get('records')) or []
    print('records:', len(recs))
except Exception as e:
    print('JSON FAIL:', e, '| head:', body[:200])
