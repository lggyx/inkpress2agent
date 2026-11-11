import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';
import { join } from 'node:path';
import { PendingTask, reportFilled, report } from './api.js';
import { adapterOf, StepLog } from './adapters.js';
import { PROFILES_DIR, ARTIFACTS_DIR, loadConfig } from './config.js';

export interface RunOptions {
  timeoutSec: number;
  keepOpen: boolean;
  headless?: boolean;
}

export interface RunResult {
  success: boolean;
  publishedUrl: string;
  detail: Record<string, unknown>;
}

/**
 * 单条分发执行：持久化 profile（平台 Cookie 不出机）→ 打开编辑器 → 填充草稿 →
 * 回报 filled → 轮询 URL 捕获外链 → 回报 report（含执行明细与截图）。
 */
export async function runTask(task: PendingTask, opts: RunOptions): Promise<RunResult> {
  const adapter = adapterOf(task.platform);
  const headless = opts.headless ?? adapter.headlessDefault;
  const t0 = Date.now();
  const steps: StepLog[] = [];

  const profileDir = join(PROFILES_DIR, task.platform);
  mkdirSync(profileDir, { recursive: true });
  mkdirSync(ARTIFACTS_DIR, { recursive: true });

  const ctx = await chromium.launchPersistentContext(profileDir, {
    headless,
    viewport: { width: 1440, height: 900 },
    timezoneId: 'Asia/Shanghai',
    permissions: ['clipboard-read', 'clipboard-write'],
  });
  const page = ctx.pages()[0] ?? (await ctx.newPage());

  try {
    const homeUrl = adapter.home(task);
    if (homeUrl !== 'about:blank') {
      await page.goto(homeUrl, { waitUntil: 'domcontentloaded', timeout: 60_000 });
    }
    steps.push({ at: Date.now() - t0, action: 'open-editor', selector: homeUrl, ok: true });

    await adapter.fill(page, task, steps);

    const draftUrl = page.url();
    const screenshot = join(ARTIFACTS_DIR, `record-${task.recordId}-filled.png`);
    await page.screenshot({ path: screenshot, fullPage: false });
    steps.push({ at: Date.now() - t0, action: 'screenshot', selector: screenshot, ok: true });

    const detail = { platform: task.platform, headless, steps, totalMs: Date.now() - t0 };
    await reportFilled(task.recordId, draftUrl, detail);
    console.log(`草稿已填充并回报 filled：${draftUrl}`);

    // 轮询 URL 变化捕获发布后外链；keepOpen 表示无限等待人工发布
    const deadline = opts.keepOpen ? Number.POSITIVE_INFINITY : Date.now() + opts.timeoutSec * 1000;
    while (Date.now() < deadline) {
      if (adapter.urlPattern.test(page.url())) {
        const publishedUrl = page.url();
        const okDetail = { ...detail, publishedUrl, totalMs: Date.now() - t0 };
        await report(task.recordId, true, publishedUrl, okDetail);
        console.log(`外链已捕获并回报 report：${publishedUrl}`);
        return { success: true, publishedUrl, detail: okDetail };
      }
      await new Promise((r) => setTimeout(r, 2000));
    }
    const timeoutDetail = { ...detail, timeoutSec: opts.timeoutSec, note: '外链捕获超时' };
    await report(task.recordId, false, '', timeoutDetail);
    console.error(`外链捕获超时（${opts.timeoutSec}s），已回报失败明细`);
    return { success: false, publishedUrl: '', detail: timeoutDetail };
  } catch (e) {
    const failDetail = { platform: task.platform, headless, steps, totalMs: Date.now() - t0, error: (e as Error).message };
    console.error(`分发执行失败：${(e as Error).message}`);
    try {
      const shot = join(ARTIFACTS_DIR, `record-${task.recordId}-error.png`);
      await page.screenshot({ path: shot });
      (failDetail as Record<string, unknown>).errorScreenshot = shot;
    } catch { /* 截图失败不影响主流程 */ }
    await report(task.recordId, false, '', failDetail).catch(() => { /* 服务端不可达时本地已有明细 */ });
    return { success: false, publishedUrl: '', detail: failDetail };
  } finally {
    if (opts.keepOpen) {
      console.log('保持浏览器打开（keep-open），发布完成后可 Ctrl+C 结束进程');
    } else {
      await ctx.close();
    }
  }
}

export function currentConfigNote(): string {
  const cfg = loadConfig();
  return cfg.serverUrl;
}
