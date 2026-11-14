# -*- coding: utf-8 -*-
"""PFFS 精简跑法：按 paper-framework-figure-studio-pro 的 S1 规范产出 4 个候选提示词（C01-C04），
直接调用 image 槽（cogview-3-flash）出图。图面主题：InkPress 进化闭环（反馈→记忆→注入→指标→回退）。
风格政策：acm-ieee-aaai-line-art（白底、精细线稿、无渐变无光效、anti-AI-artifact）；
配色语义：三个阶段三色（蓝=反馈采集，绿=记忆沉淀，橙=注入生成），主机制居中占主面积。"""
import json
import urllib.request
import urllib.error
import ssl

# 读取 image 槽配置（走 docker exec，宿主机无法直连 MySQL）
import subprocess, re
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
assert 'ai.image.base_url' in cfg, 'image slot config missing: ' + raw[:200]
BASE, KEY, MODEL = cfg['ai.image.base_url'].rstrip('/'), cfg['ai.image.api_key'], 'GPT Image 2'
print('image slot:', BASE, '| model:', MODEL, '| key_len:', len(KEY))

# S1 产物：四个互补候选（surface style = ACM/IEEE/AAAI line-art schematic，白底线稿）
COMMON = (
    'ACM/IEEE/AAAI publication line-art schematic style, pure white background, '
    'precise thin black ink lines, minimal flat pastel color fills, no gradients, no glow, no 3D, no photorealism, '
    'clean vector-like engineering diagram, compact manuscript density, main mechanism in the center occupying dominant area, '
    'no decorative background, no watermark, Chinese text labels in a clean sans-serif font, horizontal 16:9 composition. '
)

CANDIDATES = {
    # C01 横向管线式：三个模块串联 + 回退环
    'C01_pipeline': (
        'A horizontal three-stage pipeline diagram of an AI blog self-evolution loop. '
        'Stage 1 rounded rectangle labeled 「反馈采集」(blue #2B6CB0): author thumbs-up/down icons and an edit-distance ruler feeding in. '
        'Stage 2 rounded rectangle labeled 「记忆沉淀」(green #2F855A): a stack of small database cards with 「写作记忆」 tag. '
        'Stage 3 rounded rectangle labeled 「注入生成」(orange #DD6B20): a document sheet with a pen icon, output arrow to 「新文章」. '
        'A thin dashed feedback arrow curves from stage 3 output back to stage 1 input, labeled 「回退/下轮」. '
        'Small metric tags near the flow: 「29.9% → 1.2%」. ' + COMMON
    ),
    # C02 中心辐射式：中心进化引擎，四向输入输出
    'C02_hub': (
        'A hub-and-spoke diagram. Center: a large gear-and-brain composite icon labeled 「进化引擎」, '
        'drawn with clean line-art mechanics, occupying the central 40% of the canvas. '
        'Four satellite modules connected by labeled straight arrows: 「👍/👎 反馈」(top-left, blue), '
        '「写作记忆库」(top-right, green), 「文章生成」(bottom-right, orange), 「指标看板 100%」(bottom-left, gray). '
        'Arrows are bidirectional where flow returns; each arrow carries a tiny port square at both ends. ' + COMMON
    ),
    # C03 分层栈式：三层架构剖面
    'C03_layers': (
        'A three-layer stack diagram (isometric-free, flat elevation). '
        'Top layer band labeled 「表现层 · 读者与作者」: small human icons and article cards. '
        'Middle layer labeled 「进化层 · 反馈→记忆→注入」: three connected module boxes with arrows and a circular loop arrow, '
        'this layer is visually emphasized with a subtle green tint band. '
        'Bottom layer labeled 「数据层 · MySQL/ES」: cylinder icons. '
        'Vertical arrows connect layers through small port rectangles, labeled 「API」. ' + COMMON
    ),
    # C04 循环环式：闭环圆环 + 四象限
    'C04_loop': (
        'A circular loop diagram: one large clockwise circular arrow ring occupying the center, '
        'divided into four arc segments labeled clockwise 「反馈」(blue), 「记忆」(green), 「注入」(orange), 「指标」(gray). '
        'At the ring center, a document icon labeled 「越写越好」. '
        'Outside the ring at the top-right, a small branch arrow labeled 「回退 → 快照」 pointing to a snapshot icon. '
        'Keep generous white margins, labels horizontal and away from edges. ' + COMMON
    ),
}

ctx = ssl.create_default_context()
results = {}
for cid, prompt in CANDIDATES.items():
    body = json.dumps({'model': MODEL, 'prompt': prompt, 'size': '1440x720'}).encode('utf-8')
    req = urllib.request.Request(BASE + '/images/generations', data=body, method='POST',
                                 headers={'Content-Type': 'application/json', 'Authorization': 'Bearer ' + KEY})
    try:
        with urllib.request.urlopen(req, timeout=180, context=ctx) as r:
            resp = json.loads(r.read().decode('utf-8'))
        item = resp['data'][0]
        url = item.get('url')
        b64 = item.get('b64_json')
        out = rf'D:\inkpress2agent\docs\charts\pffs_{cid}.png'
        if url:
            with urllib.request.urlopen(url, timeout=120, context=ctx) as ir, open(out, 'wb') as f:
                f.write(ir.read())
        elif b64:
            import base64
            open(out, 'wb').write(base64.b64decode(b64))
        else:
            print(cid, 'NO IMAGE in response keys:', list(resp.keys()), list(item.keys())); continue
        results[cid] = out
        print(cid, 'OK ->', out, os_path_size := __import__('os').path.getsize(out), 'bytes')
    except urllib.error.HTTPError as e:
        print(cid, 'HTTP', e.code, e.read().decode('utf-8', 'ignore')[:300])
    except Exception as e:
        print(cid, 'ERR', str(e)[:200])

print('DONE', len(results), '/ 4')
