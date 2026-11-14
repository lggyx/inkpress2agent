<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { parseMetrics, acceptanceRate, useEvolution } from '~/composables/useEvolution'
import type { EvolutionRun, RunMetrics, WritingMemoryItem, MemorySnapshotItem } from '~/composables/useEvolution'

// 进化演示面板（BoHack 日新赛道）：现场两次运行的可量化对比、可见的学习记忆、可演示的回退
const {
  activeRun, refreshActive, startRun, finishRun,
  listRuns, listMemory, listSnapshots, snapshot, rollback,
} = useEvolution()

const runs = ref<EvolutionRun[]>([])
const memories = ref<WritingMemoryItem[]>([])
const snapshots = ref<MemorySnapshotItem[]>([])
const busy = ref(false)

const CATEGORY_TEXT: Record<string, string> = {
  STYLE: '风格', TONE: '语气', STRUCTURE: '结构', LENGTH: '篇幅', FACT: '事实',
}
const SOURCE_TEXT: Record<string, string> = {
  DISTILL: 'AI 蒸馏', CORRECTION: '作者纠正', RESTORED: '快照回退',
}

const finished = computed(() => runs.value.filter(r => r.status === 'FINISHED'))
const runA = computed(() => finished.value[1] ?? null)
const runB = computed(() => finished.value[0] ?? null)
const metricsA = computed(() => parseMetrics(runA.value))
const metricsB = computed(() => parseMetrics(runB.value))
const rateA = computed(() => acceptanceRate(metricsA.value))
const rateB = computed(() => acceptanceRate(metricsB.value))
const rateDelta = computed(() =>
  rateA.value === null || rateB.value === null ? null : rateB.value - rateA.value)

async function refreshAll() {
  try {
    const [r, m, s] = await Promise.all([listRuns(), listMemory(), listSnapshots()])
    runs.value = r
    memories.value = m
    snapshots.value = s
  } catch (e) { ElMessage.error((e as Error).message) }
}

async function onStart() {
  busy.value = true
  try {
    await startRun(null)
    ElMessage.success(`运行 #${activeRun.value?.id} 已开始：去 AI 工作台写作，给输出 👍/👎 或直接采纳`)
    await refreshAll()
  } catch (e) { ElMessage.error((e as Error).message) } finally { busy.value = false }
}

async function onFinish() {
  try {
    await ElMessageBox.confirm(
      '将核算本运行的采纳率、修改幅度并蒸馏写作记忆（AI 把反馈提炼为可执行的偏好规则），结束吗？',
      '结束运行', { confirmButtonText: '结束并蒸馏', cancelButtonText: '再写会儿', type: 'info' },
    )
  } catch { return }
  busy.value = true
  try {
    const run = await finishRun()
    const m = parseMetrics(run)
    ElMessage.success(`运行 #${run.id} 已结束：${m?.memoryLearned ?? 0} 条新记忆已沉淀`)
    await refreshAll()
  } catch (e) { ElMessage.error((e as Error).message) } finally { busy.value = false }
}

async function onSnapshot() {
  busy.value = true
  try {
    await snapshot('运行前快照')
    ElMessage.success('快照已留存：随时可一键回退到当前记忆状态')
    await refreshAll()
  } catch (e) { ElMessage.error((e as Error).message) } finally { busy.value = false }
}

async function onRollback(s: MemorySnapshotItem) {
  try {
    await ElMessageBox.confirm(
      `回退到「${s.label ?? '快照'}」（${s.createdAt.slice(0, 19).replace('T', ' ')}）？之后新学的记忆会失效，快照内的记忆重新生效。`,
      '回退记忆', { confirmButtonText: '回退', cancelButtonText: '取消', type: 'warning' },
    )
  } catch { return }
  busy.value = true
  try {
    const r = await rollback(s.id)
    ElMessage.success(`已回退：失效 ${r.superseded} 条，恢复 ${r.restored} 条`)
    await refreshAll()
  } catch (e) { ElMessage.error((e as Error).message) } finally { busy.value = false }
}

function fmtTime(run: EvolutionRun): string {
  const s = (run.startedAt || '').slice(11, 19)
  const e = (run.finishedAt || '').slice(11, 19)
  return `${run.startedAt.slice(5, 10).replace('-', '/')} ${s} → ${e}`
}

function bpToPercent(bp: number | undefined): string {
  return bp === undefined ? '—' : (bp / 100).toFixed(1) + '%'
}

/** 快照条目计数（snapshot_json 为 ACTIVE 记忆数组） */
function parseSnapshotCount(s: MemorySnapshotItem): number {
  try { return (JSON.parse(s.snapshotJson) as unknown[]).length } catch { return 0 }
}

onMounted(async () => {
  await refreshActive()
  await refreshAll()
})
</script>

<template>
  <div class="page">
    <div class="page-head">
      <h2>进化面板</h2>
      <span class="sub">日新赛道 · 现场跑两次，第二次比第一次好在哪，数据说话</span>
      <div class="head-actions">
        <button class="btn ghost" :disabled="busy" @click="refreshAll()">刷新</button>
        <button v-if="!activeRun" class="btn primary" :disabled="busy" @click="onStart">
          ▶ 开始运行
        </button>
        <button v-else class="btn primary" :disabled="busy" @click="onFinish">
          ■ 结束运行 #{{ activeRun.id }}
        </button>
      </div>
    </div>

    <!-- 运行状态条 -->
    <div class="evo-status" :class="{ on: !!activeRun }">
      <span class="dot" />
      <template v-if="activeRun">
        运行 #{{ activeRun.id }} 进行中——去
        <NuxtLink to="/studio/workbench">AI 工作台</NuxtLink>
        写作，对 AI 输出 👍 / 👎 或点「替换正文 / 追加」；期间的所有反馈都会被记住。
      </template>
      <template v-else>
        当前没有进行中的运行。演示节奏：<b>拍快照 → 开始运行 → 反馈 → 结束（出记忆）→ 再运行 → 对比 → 回退</b>。
      </template>
    </div>

    <!-- 指标对比 -->
    <div class="card">
      <h3>两次运行对比</h3>
      <div v-if="!runA && !runB" class="empty">还没有完成的运行。跑完两次后，这里自动出现对比。</div>
      <div v-else class="compare">
        <div class="run-col">
          <div class="run-name">上一次{{ runA ? ` · #${runA.id}` : '' }}</div>
          <div class="big-rate muted">{{ rateA === null ? '—' : rateA + '%' }}</div>
          <div class="metric-rows">
            <div class="mrow"><span>建议总数</span><b>{{ metricsA?.suggestionsTotal ?? 0 }}</b></div>
            <div class="mrow"><span>采纳（原样/修改）</span><b>{{ metricsA?.applied ?? 0 }}（{{ metricsA?.verbatim ?? 0 }}/{{ (metricsA?.applied ?? 0) - (metricsA?.verbatim ?? 0) }}）</b></div>
            <div class="mrow"><span>👍 / 👎</span><b>{{ metricsA?.thumbsUp ?? 0 }} / {{ metricsA?.thumbsDown ?? 0 }}</b></div>
            <div class="mrow"><span>平均修改幅度</span><b>{{ bpToPercent(metricsA?.avgEditDistanceBp) }}</b></div>
            <div class="mrow"><span>作者纠正</span><b>{{ metricsA?.corrections ?? 0 }}</b></div>
            <div class="mrow"><span>学到记忆</span><b>{{ metricsA?.memoryLearned ?? 0 }} 条</b></div>
          </div>
          <div class="run-time">{{ runA ? fmtTime(runA) : '' }}</div>
        </div>
        <div class="compare-mid">
          <div v-if="rateDelta !== null" class="delta" :class="{ up: rateDelta > 0, down: rateDelta < 0 }">
            {{ rateDelta > 0 ? '▲' : rateDelta < 0 ? '▼' : '=' }} {{ Math.abs(rateDelta) }}%
          </div>
          <div class="vs">采纳率对比</div>
        </div>
        <div class="run-col">
          <div class="run-name">最近一次{{ runB ? ` · #${runB.id}` : '' }}<span v-if="runB && metricsB?.distilled" class="tag-ai">✦ AI 注入记忆</span></div>
          <div class="big-rate">{{ rateB === null ? '—' : rateB + '%' }}</div>
          <div class="metric-rows">
            <div class="mrow"><span>建议总数</span><b>{{ metricsB?.suggestionsTotal ?? 0 }}</b></div>
            <div class="mrow"><span>采纳（原样/修改）</span><b>{{ metricsB?.applied ?? 0 }}（{{ metricsB?.verbatim ?? 0 }}/{{ (metricsB?.applied ?? 0) - (metricsB?.verbatim ?? 0) }}）</b></div>
            <div class="mrow"><span>👍 / 👎</span><b>{{ metricsB?.thumbsUp ?? 0 }} / {{ metricsB?.thumbsDown ?? 0 }}</b></div>
            <div class="mrow"><span>平均修改幅度</span><b>{{ bpToPercent(metricsB?.avgEditDistanceBp) }}</b></div>
            <div class="mrow"><span>作者纠正</span><b>{{ metricsB?.corrections ?? 0 }}</b></div>
            <div class="mrow"><span>学到记忆</span><b>{{ metricsB?.memoryLearned ?? 0 }} 条</b></div>
          </div>
          <div class="run-time">{{ runB ? fmtTime(runB) : '' }}</div>
        </div>
      </div>
    </div>

    <!-- 写作记忆 -->
    <div class="card">
      <h3>写作记忆 <span class="h-sub">AI 从你的反馈里学到的偏好规则——「学到了什么」就在这里</span></h3>
      <div v-if="!memories.length" class="empty">还没有记忆。跑一次运行并给出 👎 或修改，结束后这里会出现新条目。</div>
      <div v-else class="mem-list">
        <div v-for="m in memories" :key="m.id" class="mem-item" :class="{ off: m.status === 'SUPERSEDED' }">
          <span class="mem-cat">{{ CATEGORY_TEXT[m.category] || m.category }}</span>
          <span class="mem-content">{{ m.content }}</span>
          <span class="mem-meta">
            <span class="mem-src" :class="'s-' + m.source">{{ SOURCE_TEXT[m.source] || m.source }}</span>
            <span v-if="m.originRunId" class="mono">#run{{ m.originRunId }}</span>
            <span class="mono">{{ m.createdAt.slice(5, 19).replace('T', ' ') }}</span>
            <span v-if="m.status === 'SUPERSEDED'" class="mem-off">已失效（回退）</span>
          </span>
        </div>
      </div>
    </div>

    <!-- 快照与回退 -->
    <div class="card">
      <h3>记忆快照与回退 <span class="h-sub">「怎么回退」——评委要求当场演示</span></h3>
      <div class="snap-actions">
        <button class="abtn ok" :disabled="busy" @click="onSnapshot">📷 拍快照（留存当前全部记忆）</button>
      </div>
      <div v-if="!snapshots.length" class="empty">还没有快照。运行前先拍一个，演示完一键还原。</div>
      <div v-else class="snap-list">
        <div v-for="s in snapshots" :key="s.id" class="row snap-row">
          <div class="row-main">
            <div class="row-title">{{ s.label || '快照' }}</div>
            <div class="row-sub mono">{{ s.createdAt.slice(0, 19).replace('T', ' ') }} · {{ parseSnapshotCount(s) }} 条记忆</div>
          </div>
          <div class="row-actions">
            <button class="abtn" :disabled="busy" @click="onRollback(s)">⟲ 回退到此快照</button>
          </div>
        </div>
      </div>
    </div>

    <!-- 三行进化说明（项目说明必填三项，口径与面板一致） -->
    <div class="card brief">
      <h3>进化说明（比什么 / 学到什么 / 怎么回退）</h3>
      <ol class="brief-list">
        <li><b>比什么指标：</b>建议采纳率（采纳+👍 占全部建议反馈的比例）、平均修改幅度（AI 建议与作者定稿的归一化编辑距离）、👍/👎 计数——面板自动核算，两次运行并列对比。</li>
        <li><b>学到了什么：</b>每次运行结束时，AI 把本轮反馈蒸馏成「写作偏好记忆」条目入库，并注入下一次运行的 system 提示；新增记忆在上方列表实时可见，运行状态条与工作台横幅会显示"运行中"。</li>
        <li><b>怎么回退：</b>运行前拍记忆快照；演示后一键回退——现存记忆全部失效、快照内记忆重新生效，随后重跑同一任务即可还原第一次运行的行为。回退幂等，可反复演示。</li>
      </ol>
    </div>
  </div>
</template>

<style scoped>
.evo-status {
  display: flex; align-items: center; gap: 10px;
  padding: 12px 16px; margin-bottom: 16px;
  border: 1px dashed var(--sline, rgba(128,128,128,.3)); border-radius: 12px;
  color: var(--ink-soft); font-size: .9em;
}
.evo-status .dot { width: 9px; height: 9px; border-radius: 50%; background: #9aa3b5; flex-shrink: 0; }
.evo-status.on { border-color: rgba(129, 201, 149, .5); border-style: solid; background: rgba(129, 201, 149, .06); }
.evo-status.on .dot { background: #81c995; box-shadow: 0 0 8px rgba(129, 201, 149, .8); animation: pulse 1.6s infinite; }
.evo-status a { color: var(--sprimary, #d9f24f); }
@keyframes pulse { 0%, 100% { opacity: 1 } 50% { opacity: .45 } }

.card {
  border: 1px solid var(--sline, rgba(128,128,128,.25)); border-radius: 14px;
  padding: 16px 18px; margin-bottom: 16px;
  background: var(--card-soft, rgba(128,128,128,.05));
}
.card h3 { margin: 0 0 12px; font-size: 1.05em; font-weight: 700; color: var(--ink); }
.h-sub { font-size: .8em; font-weight: 500; color: var(--ink-faint); margin-left: 8px; }
.empty { color: var(--ink-faint); font-size: .88em; padding: 10px 4px; }

.compare { display: grid; grid-template-columns: 1fr auto 1fr; gap: 14px; align-items: stretch; }
.run-col {
  border: 1px solid var(--sline, rgba(128,128,128,.25)); border-radius: 12px; padding: 12px 14px;
}
.run-name { font-size: .82em; color: var(--ink-faint); margin-bottom: 6px; display: flex; align-items: center; gap: 8px; }
.tag-ai { color: #d9f24f; font-size: .9em; }
.big-rate { font-size: 2.1em; font-weight: 800; color: var(--sprimary, #d9f24f); letter-spacing: .5px; }
.big-rate.muted { color: var(--ink-faint); }
.metric-rows { margin-top: 10px; display: grid; gap: 6px; }
.mrow { display: flex; justify-content: space-between; font-size: .86em; color: var(--ink-soft); }
.mrow b { color: var(--ink); font-weight: 650; }
.run-time { margin-top: 8px; font-size: .76em; color: var(--ink-faint); }
.compare-mid { display: flex; flex-direction: column; justify-content: center; align-items: center; gap: 6px; padding: 0 4px; }
.delta { font-size: 1.3em; font-weight: 800; }
.delta.up { color: #81c995; }
.delta.down { color: #f28b82; }
.vs { font-size: .72em; color: var(--ink-faint); }

.mem-list { display: grid; gap: 8px; }
.mem-item {
  display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
  padding: 9px 12px; border-radius: 10px;
  border: 1px solid var(--sline, rgba(128,128,128,.25));
}
.mem-item.off { opacity: .45; }
.mem-item.off .mem-content { text-decoration: line-through; }
.mem-cat {
  flex-shrink: 0; font-size: .74em; padding: 2px 9px; border-radius: 999px;
  background: var(--primary-soft, rgba(217,242,79,.12)); color: var(--sprimary, #d9f24f);
}
.mem-content { flex: 1; min-width: 200px; font-size: .9em; color: var(--ink); }
.mem-meta { display: flex; align-items: center; gap: 8px; font-size: .74em; color: var(--ink-faint); }
.mem-src { padding: 1px 7px; border-radius: 999px; border: 1px solid var(--sline, rgba(128,128,128,.3)); }
.mem-src.s-DISTILL { color: #d9f24f; border-color: rgba(217,242,79,.4); }
.mem-src.s-CORRECTION { color: #f8bb66; border-color: rgba(248,187,102,.4); }
.mem-src.s-RESTORED { color: #7db4f8; border-color: rgba(125,180,248,.4); }
.mem-off { color: #f28b82; }

.snap-actions { margin-bottom: 10px; }
.snap-list { display: grid; gap: 8px; }
.snap-row { padding: 10px 12px; }

.brief-list { margin: 0; padding-left: 20px; display: grid; gap: 8px; font-size: .88em; color: var(--ink-soft); line-height: 1.6; }
.brief-list b { color: var(--ink); }

.mono { font-family: ui-monospace, SF Mono, Consolas, monospace; font-size: .92em; }

@media (max-width: 760px) {
  .compare { grid-template-columns: 1fr; }
  .compare-mid { flex-direction: row; padding: 0; }
}
</style>
