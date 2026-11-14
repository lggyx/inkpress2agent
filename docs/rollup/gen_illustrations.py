# -*- coding: utf-8 -*-
"""易拉宝 v2 专属插画三张：GPT Image 2 生成。
规范：暗底 #0C0C0E（易拉宝底色一致，避免白块突兀）、青柠 #D9F24F 单强调色、
无文字（文字由 Node 排版层叠加，规避生图字形风险）、宽幅。"""
import json
import urllib.request
import subprocess
import base64
import os

raw = subprocess.run(
    ['docker', 'exec', 'inkpress2agent-mysql', 'mysql', '-uinkpress', '-pinkpress123',
     'inkpress', '-N', '-e',
     "SELECT config_key, config_value FROM system_config WHERE config_key LIKE 'ai.image.%'"],
    capture_output=True, text=True, encoding='utf-8').stdout
cfg = {}
for line in raw.splitlines():
    if '\t' in line:
        k, v = line.split('\t', 1)
        cfg[k.strip()] = v.strip()
BASE, KEY = cfg['ai.image.base_url'].rstrip('/'), cfg['ai.image.api_key']
MODEL = 'GPT Image 2'
OUT = r'D:\inkpress2agent\docs\rollup'
print('slot ok:', BASE)

DARK = ('dark charcoal background exactly #0C0C0E, '
        'one vivid lime-green accent color (#D9F24F) used only for key highlights, '
        'clean Swiss editorial 3D vector-like illustration, refined gray physical surfaces, '
        'soft studio light, subtle contact shadows, no gradients, no glow effects, no text, no letters, no labels, no watermark. ')

JOBS = {
    # 主视觉：Agent 书写 → 文章卡片升起（hero 右侧，竖幅）
    'hero_writer': (
        '3:4 vertical illustration. A sleek robotic arm holding a fountain pen, writing on a crisp white sheet of paper '
        'that floats at a slight angle; from the paper, three small white article cards with thin lime-green edge accents '
        'rise upward like paper planes. Around the arm, a few thin gray blueprint-style geometric guide lines. ' + DARK
    ),
    # 飞轮环：三段循环（无字版，横幅）
    'loop_ring': (
        'wide horizontal 2.2:1 illustration. Three bold curved arrows form a clean clockwise circular flow loop '
        'in the center: arrow 1 lime-green (#D9F24F), arrow 2 light gray, arrow 3 mid-gray; '
        'each arrow carries one small physical model object: a thumb-up badge, a stack of small cards, a fountain pen nib. '
        'A small downward branch with a snapshot camera icon exits the loop at top-right. ' + DARK
    ),
    # 记忆库：卡片飞入数据库
    'memory_stack': (
        'wide horizontal 2:1 illustration. A row of neat white index cards with thin lime-green top edges flying in an arc '
        'from the left into an open dark drawer/database slot on the right; motion trails are thin gray dashed lines; '
        'one card glows slightly lime at its corner as the active one. ' + DARK
    ),
}

ctx = __import__('ssl').create_default_context()
for cid, prompt in JOBS.items():
    body = json.dumps({'model': MODEL, 'prompt': prompt, 'size': '1440x720'}).encode('utf-8')
    req = urllib.request.Request(BASE + '/images/generations', data=body, method='POST',
                                 headers={'Content-Type': 'application/json', 'Authorization': 'Bearer ' + KEY})
    try:
        with urllib.request.urlopen(req, timeout=240, context=ctx) as r:
            resp = json.loads(r.read().decode('utf-8'))
        item = resp['data'][0]
        out = os.path.join(OUT, f'illu_{cid}.png')
        if item.get('url'):
            with urllib.request.urlopen(item['url'], timeout=120, context=ctx) as ir, open(out, 'wb') as f:
                f.write(ir.read())
        elif item.get('b64_json'):
            open(out, 'wb').write(base64.b64decode(item['b64_json']))
        print(cid, 'OK', os.path.getsize(out), 'bytes')
    except Exception as e:
        print(cid, 'ERR', str(e)[:200])
print('DONE')
