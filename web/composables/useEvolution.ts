/**
 * 进化闭环（BoHack 日新赛道）前端共享状态：
 * - activeRun / corrections 用 useState 跨页面共享（工作台采集反馈，演示面板开/关运行）
 * - 工作台在运行期间记录 👍/👎、采纳、作者纠正；面板负责开始/结束/快照/回退与指标对比
 */
import { $api } from './useApi'

export interface EvolutionRun {
  id: number
  userId: number
  taskKind: string
  status: 'RUNNING' | 'FINISHED'
  articleId: number | null
  metricsJson: string | null
  startedAt: string
  finishedAt: string | null
}

export interface RunMetrics {
  suggestionsTotal: number
  applied: number
  verbatim: number
  thumbsUp: number
  thumbsDown: number
  avgEditDistanceBp: number
  corrections: number
  memoryLearned: number
  distilled: boolean
}

export interface WritingMemoryItem {
  id: number
  category: string
  content: string
  source: 'DISTILL' | 'CORRECTION' | 'RESTORED'
  status: 'ACTIVE' | 'SUPERSEDED'
  originRunId: number | null
  lastHitAt: string | null
  createdAt: string
}

export interface MemorySnapshotItem {
  id: number
  label: string | null
  snapshotJson: string
  createdAt: string
}

export function parseMetrics(run: EvolutionRun | null): RunMetrics | null {
  if (!run?.metricsJson) return null
  try { return JSON.parse(run.metricsJson) as RunMetrics } catch { return null }
}

/** 采纳率：采纳(含修改) + 👍 占全部建议反馈的比例 */
export function acceptanceRate(m: RunMetrics | null): number | null {
  if (!m || !m.suggestionsTotal) return null
  return Math.round(((m.applied + m.thumbsUp) / m.suggestionsTotal) * 100)
}

export function useEvolution() {
  const activeRun = useState<EvolutionRun | null>('evo-active-run', () => null)
  const corrections = useState<string[]>('evo-corrections', () => [])

  async function refreshActive(): Promise<void> {
    try {
      activeRun.value = await $api<EvolutionRun | null>('GET', '/studio/evolution/run/active')
      if (!activeRun.value) corrections.value = []
    } catch { activeRun.value = null }
  }

  async function startRun(articleId?: number | null): Promise<void> {
    activeRun.value = await $api<EvolutionRun>('POST', '/studio/evolution/run/start', {
      articleId: articleId ?? null,
    })
    corrections.value = []
  }

  async function finishRun(): Promise<EvolutionRun> {
    if (!activeRun.value) throw new Error('没有进行中的运行')
    const run = await $api<EvolutionRun>('POST', '/studio/evolution/run/finish', {
      runId: activeRun.value.id,
      finalContent: '',
      corrections: corrections.value,
    })
    activeRun.value = null
    corrections.value = []
    return run
  }

  /** 运行期间作者的文字纠正（原话），随运行结束送蒸馏 */
  function recordCorrection(text: string): void {
    if (activeRun.value && text.trim()) corrections.value.push(text.trim().slice(0, 200))
  }

  /** AI 建议反馈：type REPLACE|APPEND|CHAT，verdict UP|DOWN|APPLIED；静默失败不打断写作 */
  async function reportFeedback(
    type: 'REPLACE' | 'APPEND' | 'CHAT',
    text: string,
    verdict: 'UP' | 'DOWN' | 'APPLIED',
    articleId?: number | null,
  ): Promise<void> {
    try {
      await $api('POST', '/studio/evolution/feedback', {
        runId: activeRun.value?.id ?? null,
        articleId: articleId ?? null,
        type,
        text: (text || '').slice(0, 8000),
        verdict,
      })
    } catch { /* 进化反馈不阻塞主流程 */ }
  }

  const listRuns = () => $api<EvolutionRun[]>('GET', '/studio/evolution/runs')
  const listMemory = () => $api<WritingMemoryItem[]>('GET', '/studio/evolution/memory')
  const listSnapshots = () => $api<MemorySnapshotItem[]>('GET', '/studio/evolution/snapshots')
  const snapshot = (label?: string) => $api<MemorySnapshotItem>('POST', '/studio/evolution/snapshot', { label: label ?? null })
  const rollback = (snapshotId: number) =>
    $api<{ superseded: number; restored: number }>('POST', '/studio/evolution/rollback', { snapshotId })

  return {
    activeRun, corrections,
    refreshActive, startRun, finishRun, recordCorrection, reportFeedback,
    listRuns, listMemory, listSnapshots, snapshot, rollback,
  }
}
