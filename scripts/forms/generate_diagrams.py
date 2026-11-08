# -*- coding: utf-8 -*-
"""
生成开题报告所需规范图表：
1. 图3-1 功能模块图（严格遵循样例文档无箭头、纯正交树状结构）
2. 图4-1 技术路线图（严格遵循样例文档需求分析->架构设计->三端开发->测试部署的完整技术路线）
"""

import os
import subprocess
from PIL import Image, ImageDraw, ImageFont

def render_technical_roadmap_puml():
    puml_content = """@startuml 08-技术路线图
skinparam dpi 300
skinparam shadowing false
skinparam defaultFontName "Microsoft YaHei"
skinparam defaultFontSize 12
skinparam roundcorner 6
skinparam nodesep 60
skinparam ranksep 45
skinparam rectangle {
  BackgroundColor White
  BorderColor Black
  BorderThickness 1.5
}
skinparam arrow {
  Color Black
  Thickness 1.2
}

rectangle "<b>InkPress 个人博客与文章管理系统</b>" as ROOT

rectangle "<b>需求分析</b>\\n创作者（文章创作、自适应排版、AI辅助润色、数字资产管理）\\n读者（内容浏览、站内两级互动、关键词与语义混合检索）\\n管理员（内容入站合规审计、接口防刷限流、系统运维监控）" as REQ

rectangle "<b>架构设计：前后端分离 + 微内核分层架构</b>" as ARCH

ROOT -down-> REQ
REQ -down-> ARCH

rectangle "<b>前端开发</b>" as FE_TITLE
rectangle "<b>后端开发</b>" as BE_TITLE
rectangle "<b>数据与服务</b>" as DB_TITLE

rectangle "Vue 3 + Nuxt 3\\nElement Plus + Vditor" as FE
rectangle "Spring Boot 3 + Spring Security\\nMyBatis-Plus + RabbitMQ" as BE
rectangle "MySQL 8.0 + Redis 7\\nElasticsearch 8.11" as DB

ARCH -down-> FE_TITLE
ARCH -down-> BE_TITLE
ARCH -down-> DB_TITLE

FE_TITLE -down-> FE
BE_TITLE -down-> BE
DB_TITLE -down-> DB

FE <-> BE : "RESTful API / SSE"
BE <-> DB : "JDBC / Spring Data"

rectangle "<b>测试与部署</b>\\n单元测试、集成测试、Docker容器化部署与自动化监控" as TEST

BE -down-> TEST

@enduml
"""
    puml_path = 'docs/assets/PRD/puml/08-技术路线图.puml'
    with open(puml_path, 'w', encoding='utf-8') as f:
        f.write(puml_content)
    
    # 渲染为 png
    cmd = ['java', '-jar', 'tools/plantuml.jar', '-tpng', '-o', '../png', puml_path]
    subprocess.run(cmd, check=True)
    print("08-技术路线图.png 生成成功！")

def render_function_module_tree_pil():
    """
    使用 PIL 严格按照青岛恒星科技学院优秀开题报告样例文档（image_1.png）风格绘制：
    - 纯正交树状层次结构
    - 绝对无任何箭头
    - 顶层系统名 -> 5大核心模块 -> 各子功能卡片（纵向排列垂直文本）
    - 纯白背景、黑色圆角矩形、专业抗锯齿排版
    """
    # 模块定义（采用规范统一的6-8字专业学术用语，排版高度工整统一）
    data = [
        {
            "name": "个人中心与身份认证",
            "subs": ["用户注册与登录", "令牌认证鉴权", "个人信息维护", "消息提醒与邮件"]
        },
        {
            "name": "博客文章管理与交互",
            "subs": ["文章在线编辑", "草稿暂存与发布", "分类与标签归档", "读者评论与审核", "点赞与浏览计数"]
        },
        {
            "name": "AI智能写作辅助",
            "subs": ["流式文本润色", "语法纠错与审查", "智能摘要与标签", "内容续写与标题"]
        },
        {
            "name": "跨平台自动化辅助分发",
            "subs": ["平台自适应排版", "主流多平台适配", "本地草稿注入", "分发记录与回填"]
        },
        {
            "name": "站内混合检索与安全运维",
            "subs": ["中文分词与检索", "向量语义混合搜", "敏感词与内容审计", "访问限流与追踪", "系统健康状态监控"]
        }
    ]

    total_subs = sum(len(m["subs"]) for m in data) # 4 + 5 + 4 + 4 + 5 = 22

    # 尺寸规划 (高分辨率紧凑版，高度收缩消除卡片与层级间冗余留白)
    width = 2400
    height = 600
    img = Image.new('RGB', (width, height), 'white')
    draw = ImageDraw.Draw(img)

    # 字体加载
    font_path = 'C:/Windows/Fonts/msyh.ttc'
    if not os.path.exists(font_path):
        font_path = 'C:/Windows/Fonts/simsun.ttc'
    
    font_root = ImageFont.truetype(font_path, 28)
    font_module = ImageFont.truetype(font_path, 22)
    font_sub = ImageFont.truetype(font_path, 19)

    # 1. 绘制顶层根节点
    root_text = "基于Spring Boot与Vue的个人博客与文章管理系统"
    r_bbox = draw.textbbox((0, 0), root_text, font=font_root)
    r_w = r_bbox[2] - r_bbox[0] + 50
    r_h = r_bbox[3] - r_bbox[1] + 20
    r_x0 = (width - r_w) / 2
    r_y0 = 20
    r_x1 = r_x0 + r_w
    r_y1 = r_y0 + r_h

    draw.rounded_rectangle([r_x0, r_y0, r_x1, r_y1], radius=6, outline='black', width=3, fill='white')
    r_text_w = r_bbox[2] - r_bbox[0]
    r_text_h = r_bbox[3] - r_bbox[1]
    draw.text((r_x0 + (r_w - r_text_w) / 2, r_y0 + (r_h - r_text_h) / 2 - 2), root_text, font=font_root, fill='black')

    # 根节点下引主干垂直线
    trunk_y_start = r_y1
    trunk_y_split = r_y1 + 28
    draw.line([(width / 2, trunk_y_start), (width / 2, trunk_y_split)], fill='black', width=3)

    # 2. 规划 22 个子功能垂直卡片的水平位置
    sub_w = 74
    sub_h = 240
    gap_sub = 16
    gap_group = 34

    # 计算整体水平居中偏移量
    total_cards_w = 0
    group_widths = []
    for g_idx, m in enumerate(data):
        n = len(m["subs"])
        gw = n * sub_w + (n - 1) * gap_sub
        group_widths.append(gw)
        total_cards_w += gw
    total_cards_w += (len(data) - 1) * gap_group

    start_x = (width - total_cards_w) / 2

    # 记录每个模块的水平中心，用于连接线
    module_centers = []
    sub_positions = []

    curr_x = start_x
    for g_idx, m in enumerate(data):
        n = len(m["subs"])
        gw = group_widths[g_idx]
        mod_center = curr_x + gw / 2
        module_centers.append(mod_center)

        g_subs = []
        for s_idx, sub_text in enumerate(m["subs"]):
            sx0 = curr_x + s_idx * (sub_w + gap_sub)
            sx1 = sx0 + sub_w
            g_subs.append((sx0, sx1, sub_text))
        sub_positions.append(g_subs)

        curr_x += gw + gap_group

    # 3. 绘制主干水平分支线（无箭头！）
    min_mod_x = module_centers[0]
    max_mod_x = module_centers[-1]
    draw.line([(min_mod_x, trunk_y_split), (max_mod_x, trunk_y_split)], fill='black', width=3)

    # 4. 绘制一级模块卡片
    mod_h = 54
    mod_y0 = trunk_y_split + 22
    mod_y1 = mod_y0 + mod_h

    for idx, m in enumerate(data):
        cx = module_centers[idx]
        # 从主干水平线垂直向下连接到一级模块
        draw.line([(cx, trunk_y_split), (cx, mod_y0)], fill='black', width=3)

        # 一级模块卡片尺寸
        m_w = group_widths[idx]
        m_x0 = cx - m_w / 2
        m_x1 = cx + m_w / 2

        draw.rounded_rectangle([m_x0, mod_y0, m_x1, mod_y1], radius=6, outline='black', width=3, fill='white')
        
        # 居中文本
        m_bbox = draw.textbbox((0, 0), m["name"], font=font_module)
        m_tw = m_bbox[2] - m_bbox[0]
        m_th = m_bbox[3] - m_bbox[1]
        draw.text((cx - m_tw / 2, mod_y0 + (mod_h - m_th) / 2 - 2), m["name"], font=font_module, fill='black')

        # 5. 从一级模块向下连接到子功能分组总线（无箭头！）
        mod_trunk_y1 = mod_y1
        mod_trunk_split = mod_y1 + 26
        draw.line([(cx, mod_trunk_y1), (cx, mod_trunk_split)], fill='black', width=2)

        # 子功能总线跨度
        g_subs = sub_positions[idx]
        first_sub_cx = (g_subs[0][0] + g_subs[0][1]) / 2
        last_sub_cx = (g_subs[-1][0] + g_subs[-1][1]) / 2
        draw.line([(first_sub_cx, mod_trunk_split), (last_sub_cx, mod_trunk_split)], fill='black', width=2)

        # 各子卡片垂直引线与矩形框（无箭头！）
        sub_y0 = mod_trunk_split + 20
        sub_y1 = sub_y0 + sub_h
        for sx0, sx1, sub_text in g_subs:
            scx = (sx0 + sx1) / 2
            draw.line([(scx, mod_trunk_split), (scx, sub_y0)], fill='black', width=2)

            # 绘制竖向圆角矩形
            draw.rounded_rectangle([sx0, sub_y0, sx1, sub_y1], radius=5, outline='black', width=2, fill='white')

            # 竖向排版文字（仿样例文档每个字居中纵向排布）
            chars = list(sub_text)
            char_h = 26
            total_text_h = len(chars) * char_h
            start_ty = sub_y0 + (sub_h - total_text_h) / 2

            for c_idx, char in enumerate(chars):
                c_bbox = draw.textbbox((0, 0), char, font=font_sub)
                cw = c_bbox[2] - c_bbox[0]
                ch = c_bbox[3] - c_bbox[1]
                cx_char = sx0 + (sub_w - cw) / 2
                cy_char = start_ty + c_idx * char_h + (char_h - ch) / 2
                draw.text((cx_char, cy_char), char, font=font_sub, fill='black')

    # 紧密裁剪白边，避免图表四周留白过多
    import numpy as np
    gray = img.convert('L')
    arr = np.array(gray)
    non_white = np.where(arr < 250)
    ymin, ymax = int(np.min(non_white[0])), int(np.max(non_white[0]))
    xmin, xmax = int(np.min(non_white[1])), int(np.max(non_white[1]))

    pad = 12
    crop_box = (max(0, xmin - pad), max(0, ymin - pad), min(width, xmax + pad + 1), min(height, ymax + pad + 1))
    cropped_img = img.crop(crop_box)

    # 保存紧凑裁剪后的图片
    out_png = 'docs/assets/PRD/png/14-功能模块图.png'
    cropped_img.save(out_png, 'PNG', dpi=(300, 300))
    print(f"14-功能模块图.png (紧凑裁剪版) 生成成功！原始: {width}x{height} -> 裁剪后: {cropped_img.size[0]}x{cropped_img.size[1]}")

    # 同时更新 puml 源码以防后续同步
    puml_no_arrow = """@startuml 14-功能模块图
skinparam dpi 300
skinparam shadowing false
skinparam defaultFontName Microsoft YaHei
skinparam linetype ortho
skinparam ArrowHead none
skinparam rectangle {
  BackgroundColor White
  BorderColor Black
  BorderThickness 1.5
}

[InkPress 个人博客与文章管理系统] as ROOT

rectangle "个人中心与身份认证" as M1
rectangle "博客文章管理与交互" as M2
rectangle "AI智能写作辅助" as M3
rectangle "跨平台自动化辅助分发" as M4
rectangle "站内混合检索与安全运维" as M5

ROOT -down- M1
ROOT -down- M2
ROOT -down- M3
ROOT -down- M4
ROOT -down- M5

rectangle "用户注册与登录\\nJWT令牌鉴权\\n个人信息维护\\n消息提醒与邮件" as S1
rectangle "Markdown在线编辑\\n草稿暂存与发布\\n分类标签交叉归档\\n读者两级评论审核\\n点赞与浏览量削峰" as S2
rectangle "SSE流式文本润色\\n语法纠错与审查\\n智能摘要与标签推荐\\n内容续写与标题推荐" as S3
rectangle "平台自适应排版\\n主流多平台适配\\nPlaywright草稿注入\\n分发记录与状态回填" as S4
rectangle "IK中文分词与BM25\\n稠密向量与RRF融合\\n敏感词与内容审计\\n访问限流与链路追踪\\n系统健康状态监控" as S5

M1 -down- S1
M2 -down- S2
M3 -down- S3
M4 -down- S4
M5 -down- S5
@enduml
"""
    with open('docs/assets/PRD/puml/14-功能模块图.puml', 'w', encoding='utf-8') as f:
        f.write(puml_no_arrow)


if __name__ == '__main__':
    render_technical_roadmap_puml()
    render_function_module_tree_pil()
