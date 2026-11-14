import json, subprocess, os
os.chdir(r'D:\inkpress2agent')
S = 'docs/perf/_tmp'
os.makedirs(S, exist_ok=True)
body_f = S + '/dbg_body.json'
with open(body_f, 'wb') as f:
    f.write(json.dumps({'email': 'evo-demo@local.dev', 'password': 'EvoDemo2026!'}, ensure_ascii=False).encode('utf-8'))
out_f = S + '/dbg_out.txt'
args = ['curl', '-sS', '-m', '60', '-X', 'POST', '-H', 'Content-Type: application/json',
        '--data-binary', '@' + body_f, '-o', out_f, '-w', '%{http_code}',
        'http://117.72.154.28/api/auth/login']
r = subprocess.run(args, capture_output=True)
print('STDOUT:', r.stdout.decode('ascii', errors='replace').strip())
print('STDERR:', r.stderr.decode('utf-8', errors='replace')[:300])
print('BODY:', open(out_f, 'rb').read().decode('utf-8', errors='replace')[:300])
