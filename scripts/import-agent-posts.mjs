/**
 * InkPress Agent 文章导入脚本
 * 把 content/agent/*.md（Agent 基于真实开发轨迹撰写的工程复盘）导入线上站
 *
 * 用法：node scripts/import-agent-posts.mjs [目录] [API根]
 * 行为：source=AGENT 声明写在正文内，标签带「AI 创作」，分类=开发笔记，直接发布
 */
import { readdir, readFile } from 'node:fs/promises'
import { join } from 'node:path'

const SRC_DIR = process.argv[2] || 'content/agent'
const BASE = (process.argv[3] || 'http://117.72.154.28') + '/api'
const CATEGORY_ID = 6 // 开发笔记
const EMAIL = 'evo-demo@local.dev'
const PASSWORD = 'EvoDemo2026!'

async function api(path, { method = 'GET', body, token } = {}) {
  const res = await fetch(BASE + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  })
  const json = await res.json()
  if (json.code !== 0) throw new Error(`${method} ${path} -> code=${json.code} ${json.message}`)
  return json.data
}

/** 从正文提取元信息：H1 标题、结论段（GEO）、摘要、标签 */
function parsePost(raw) {
  const titleM = raw.match(/^#\s+(.+)$/m)
  const title = titleM ? titleM[1].trim() : null
  // 结论段：第一个 **结论先行：...** 块
  const conclM = raw.match(/\*\*结论先行：(.+?)\*\*/s)
  const geo = conclM ? conclM[1].replace(/\s+/g, ' ').trim().slice(0, 200) : ''
  // 摘要：去标记后前 120 字
  const plain = raw
    .replace(/^#.*$/gm, '')
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/[*_>`|]/g, '')
    .replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/\s+/g, ' ')
    .trim()
  const summary = plain.length > 120 ? plain.slice(0, 120) + '…' : plain
  return { title, geo, summary }
}

async function main() {
  const login = await api('/auth/login', { method: 'POST', body: { email: EMAIL, password: PASSWORD } })
  const token = login.accessToken
  console.log('✓ 登录', login.user.nickname)

  // 幂等：拉取已有文章标题，已存在的跳过（防重复导入）
  const existing = new Set()
  for (let page = 1; page <= 10; page++) {
    const list = await api(`/studio/articles?page=${page}&size=50`, { token })
    for (const r of list.records) existing.add(r.title)
    if (page * 50 >= list.total) break
  }
  console.log('线上已有文章标题数:', existing.size)

  const files = (await readdir(SRC_DIR)).filter(f => f.endsWith('.md')).sort()
  console.log(`待导入 ${files.length} 篇`)

  let ok = 0, fail = 0, skip = 0
  for (const file of files) {
    const raw = await readFile(join(SRC_DIR, file), 'utf-8')
    const { title, geo, summary } = parsePost(raw)
    if (!title) { console.log(`✗ ${file}: 无标题`); fail++; continue }
    if (existing.has(title)) { console.log(`- ${file}: 已存在，跳过`); skip++; continue }
    try {
      const created = await api('/studio/articles', {
        method: 'POST',
        token,
        body: {
          categoryId: CATEGORY_ID,
          title,
          contentMd: raw,
          seoDescription: summary,
          geoSummary: geo || summary,
          tags: ['AI 创作', '工程复盘', 'Agent 架构'],
          action: 'PUBLISH',
        },
      })
      ok++
      console.log(`✓ [id=${created.id}] ${title}`)
    } catch (e) {
      fail++
      console.log(`✗ ${file}: ${e.message}`)
    }
  }
  console.log(`\n导入完成：成功 ${ok}，跳过 ${skip}，失败 ${fail}`)
}

main().catch(e => { console.error('导入失败:', e.message); process.exit(1) })
