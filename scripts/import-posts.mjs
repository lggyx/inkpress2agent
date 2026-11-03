/**
 * InkPress 文章导入脚本
 * 从 Astro 博客仓库（lggyx.github.io）导入 Markdown 文章到 InkPress
 *
 * 用法：
 *   node scripts/import-posts.mjs [源目录] [API根地址]
 *   node scripts/import-posts.mjs D:/WorkSpace/inkpress-import-src/src/content/posts http://localhost:28080
 *
 * 行为：
 *   - 解析 front matter（title/pubDate/chapter/tags）
 *   - 文章内 /images/ 相对路径替换为 https://lggyx.github.io/images/ 外链（图片保留在原站）
 *   - chapter 映射为中文分类（缺失则自动创建）
 *   - 以 PUBLISHED 状态导入，保留原始发布日期
 *   - summary 由正文前 120 字自动生成
 */

import { readdir, readFile } from 'node:fs/promises'
import { join } from 'node:path'

const SRC_DIR = process.argv[2] || 'D:/WorkSpace/inkpress-import-src/src/content/posts'
const BASE_URL = (process.argv[3] || 'http://localhost:28080') + '/api'
const IMG_BASE = 'https://lggyx.github.io/images/'

/** Astro chapter → InkPress 分类名 */
const CHAPTER_MAP = {
  algorithms: '算法',
  infra: '基础设施',
  frontend: '前端',
  python: 'Python',
  media: '音视频',
  desktop: '桌面端',
  projects: '项目实践',
  archive: '归档'
}

async function api(path, { method = 'GET', body, token } = {}) {
  const res = await fetch(BASE_URL + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: body ? JSON.stringify(body) : undefined
  })
  const json = await res.json()
  if (json.code !== 200) {
    throw new Error(`${method} ${path} -> code=${json.code} ${json.message}`)
  }
  return json.data
}

/** 解析 front matter 与正文 */
function parsePost(raw) {
  const match = raw.match(/^---\r?\n([\s\S]*?)\r?\n---\r?\n([\s\S]*)$/)
  if (!match) {
    return null
  }
  const meta = {}
  for (const line of match[1].split(/\r?\n/)) {
    const idx = line.indexOf(':')
    if (idx <= 0) {
      continue
    }
    const key = line.slice(0, idx).trim()
    let value = line.slice(idx + 1).trim()
    if (value.startsWith('[') && value.endsWith(']')) {
      value = value
        .slice(1, -1)
        .split(',')
        .map(s => s.trim().replace(/^["']|["']$/g, ''))
        .filter(Boolean)
    } else {
      value = value.replace(/^["']|["']$/g, '')
    }
    meta[key] = value
  }
  return { meta, body: match[2] }
}

/** 去掉 Markdown 标记，提取纯文本（用于生成摘要） */
function toPlainText(markdown) {
  return markdown
    .replace(/```[\s\S]*?```/g, ' ')           // 代码块
    .replace(/`[^`]*`/g, ' ')                   // 行内代码
    .replace(/!\[[^\]]*\]\([^)]*\)/g, ' ')      // 图片
    .replace(/\[[^\]]*\]\([^)]*\)/g, ' ')       // 链接
    .replace(/^#{1,6}\s+/gm, '')                // 标题符号
    .replace(/[*_>|-]/g, ' ')                   // 强调/引用/列表符号
    .replace(/\s+/g, ' ')
    .trim()
}

function buildSummary(body) {
  const text = toPlainText(body)
  return text.length > 120 ? text.slice(0, 120) + '…' : text
}

async function main() {
  // 1. 登录
  const login = await api('/auth/login', {
    method: 'POST',
    body: { username: 'admin', password: 'admin123' }
  })
  const token = login.token
  console.log(`✓ 已登录 admin`)

  // 2. 准备分类（chapter → 中文分类名，缺失则创建）
  const categories = await api('/categories')
  const catByName = new Map(categories.map(c => [c.name, c.id]))
  for (const name of Object.values(CHAPTER_MAP)) {
    if (!catByName.has(name)) {
      const created = await api('/admin/categories', {
        method: 'POST', token, body: { name, sort: 99 }
      }).catch(() => null)
      if (created) {
        catByName.set(name, created.id)
        console.log(`✓ 创建分类：${name}`)
      }
    }
  }
  // 重新拉取，确保 id 最新
  const freshCats = await api('/categories')
  const catId = new Map(freshCats.map(c => [c.name, c.id]))

  // 3. 遍历导入
  const files = (await readdir(SRC_DIR)).filter(f => f.endsWith('.md'))
  let ok = 0
  let skip = 0
  for (const file of files) {
    const raw = await readFile(join(SRC_DIR, file), 'utf-8')
    const parsed = parsePost(raw)
    if (!parsed || !parsed.meta.title) {
      console.log(`✗ ${file}：front matter 解析失败，跳过`)
      skip++
      continue
    }
    const { meta, body } = parsed

    // 图片路径改写为 GitHub Pages 外链
    const content = body.replace(/\]\(\/images\//g, `](${IMG_BASE}`)

    const categoryName = CHAPTER_MAP[meta.chapter] || '归档'
    const tags = Array.isArray(meta.tags) ? meta.tags : []

    try {
      await api('/admin/articles', {
        method: 'POST',
        token,
        body: {
          title: meta.title,
          summary: buildSummary(body),
          content,
          categoryId: catId.get(categoryName) || null,
          tags,
          status: 'PUBLISHED',
          publishedAt: meta.pubDate // yyyy-MM-dd
        }
      })
      ok++
      console.log(`✓ [${meta.pubDate}] ${meta.title}`)
    } catch (e) {
      console.log(`✗ ${file}：${e.message}`)
      skip++
    }
  }

  console.log(`\n导入完成：成功 ${ok} 篇，跳过 ${skip} 篇`)
}

main().catch(e => {
  console.error('导入失败：', e.message)
  process.exit(1)
})
