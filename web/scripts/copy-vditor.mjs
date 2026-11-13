/**
 * 将 vditor 静态资产（含 KaTeX）拷贝到 public/vditor/dist，
 * 使编辑器公式预览（math.src = /vditor/dist/js/katex/katex.min.js）
 * 在本地 dev、Docker 构建等所有路径下都可用。
 *
 * 由 npm postinstall 自动触发，也可手动执行：node scripts/copy-vditor.mjs
 * 产物 public/vditor/ 已加入 .gitignore（构建产物，不入库）。
 */
import { cpSync, existsSync, mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const src = join(root, 'node_modules', 'vditor', 'dist')
const dest = join(root, 'public', 'vditor', 'dist')

if (!existsSync(src)) {
  console.warn('[copy-vditor] node_modules/vditor/dist 不存在，跳过（请先 npm install）')
  process.exit(0)
}

mkdirSync(dirname(dest), { recursive: true })
cpSync(src, dest, { recursive: true })
console.log('[copy-vditor] vditor 静态资产 ->', dest)
