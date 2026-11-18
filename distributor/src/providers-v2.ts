/**
 * PlatformAdapterV2 实现 —— 三能力 Provider 注册表
 *
 * auth       → 委派 auth.ts 的探测/有头登录（browser 型持久化 profile，Cookie 不出机）
 * publish    → 复用 v1 适配器的编辑器填充链路（自动填充草稿，发布确认由人工完成，
 *              诚实口径见 distributor/README.md 能力边界；返回后浏览器保持打开）
 * queryWork  → 作品数据回流：打开已发布详情页提取阅读/点赞/评论（juejin/stub 已实现，
 *              csdn/zhihu/wechat 未适配——接口可选能力，CLI 会如实跳过并提示）
 */
import { chromium } from 'playwright';
import type { BrowserContext } from 'playwright';
import { join } from 'node:path';
import { adapters, adapterOf } from './adapters.js';
import { metaOf } from './platform-meta.js';
import { checkAuth, authLogin } from './auth.js';
import { PROFILES_DIR } from './config.js';
import type { PendingTask } from './api.js';
import type { PlatformAdapterV2, PublishResult, WorkStats } from './adapter-v2.js';

async function openProfileContext(platform: string, headless: boolean): Promise<BrowserContext> {
  const profileDir = join(PROFILES_DIR, platform);
  return chromium.launchPersistentContext(profileDir, {
    headless,
    viewport: { width: 1440, height: 900 },
    timezoneId: 'Asia/Shanghai',
    permissions: ['clipboard-read', 'clipboard-write'],
  });
}

/**
 * juejin 作品数据回流（2026-11-17 对真实文章页 DOM 校准）：
 * 阅读数 .views-count 公开可见；点赞/评论在 li.item.like / li.item.comment（文章主体在
 * 相关推荐卡片之前，取首个即主文章栏）；评论为 0 时掘金渲染文字「评论」而非数字，按 0 处理。
 */
async function queryJuejinWork(workUrl: string, opts: { headless: boolean }): Promise<WorkStats> {
  const ctx = await openProfileContext('juejin', opts.headless);
  try {
    const page = ctx.pages()[0] ?? (await ctx.newPage());
    await page.goto(workUrl, { waitUntil: 'domcontentloaded', timeout: 60_000 });
    await page.waitForTimeout(2500);
    // evaluate 用字符串表达式：tsx/esbuild 会给函数注入 __name 包装，浏览器上下文不存在该符号
    const stats = await page.evaluate(`(() => {
      const firstNum = (sels) => {
        for (const s of sels) {
          const el = document.querySelector(s);
          if (!el) continue;
          const text = (el.getAttribute('title') ?? '') + ' ' + (el.textContent ?? '');
          const m = text.replace(/,/g, '').match(/\\d+/);
          if (m) return Number(m[0]);
        }
        return undefined;
      };
      const views = firstNum(['.views-count']);
      const likes = firstNum(['li.item.like']);
      // 评论为 0 时首个 li.item.comment 显示文字「评论」，视为 0；有数字取数字
      const cEl = document.querySelector('li.item.comment');
      const cText = cEl ? (cEl.textContent || '') : '';
      const cm = cText.replace(/,/g, '').match(/\\d+/);
      const comments = cm ? Number(cm[0]) : (cText.includes('评论') ? 0 : undefined);
      return { views, likes, comments };
    })()`) as { views?: number; likes?: number; comments?: number };
    return { workUrl, ...stats, fetchedAt: Date.now() };
  } finally {
    await ctx.close();
  }
}

/** stub 作品数据回流：本地 file:// 冒烟页 hash 发布态自带统计块（确定性数字） */
async function queryStubWork(workUrl: string, _opts: { headless: boolean }): Promise<WorkStats> {
  const ctx = await chromium.launch({ headless: true });
  try {
    const page = await ctx.newPage();
    await page.goto(workUrl, { waitUntil: 'domcontentloaded', timeout: 15_000 });
    await page.waitForTimeout(200);
    const stats = await page.evaluate(`(() => ({
      views: (() => { const el = document.querySelector('#stat-views'); const m = el && (el.textContent || '').match(/\\d+/); return m ? Number(m[0]) : undefined; })(),
      likes: (() => { const el = document.querySelector('#stat-likes'); const m = el && (el.textContent || '').match(/\\d+/); return m ? Number(m[0]) : undefined; })(),
      comments: (() => { const el = document.querySelector('#stat-comments'); const m = el && (el.textContent || '').match(/\\d+/); return m ? Number(m[0]) : undefined; })(),
    }))()`) as { views?: number; likes?: number; comments?: number };
    return { workUrl, ...stats, fetchedAt: Date.now() };
  } finally {
    await ctx.close();
  }
}

function makeProvider(platform: string, queryWork?: PlatformAdapterV2['queryWork']): PlatformAdapterV2 {
  const meta = metaOf(platform);
  const v1 = adapterOf(platform);
  return {
    platform,
    displayName: meta.displayName,
    authType: meta.authType,
    urlPattern: v1.urlPattern,
    authStatus: () => checkAuth(meta.platform, meta.loginUrl, meta.loggedInSelector),
    // browser 型人工登录必须开有头浏览器；headless 形参仅为兼容 v2 接口（未来 qrcode 型可用）
    authLogin: async (_headless: boolean) => authLogin(meta.platform, meta.loginUrl, meta.loggedInSelector),
    async publish(task: PendingTask, opts: { headless: boolean; steps: { at: number; action: string; selector?: string; ok: boolean; note?: string }[] }): Promise<PublishResult> {
      const ctx = await openProfileContext(platform, opts.headless);
      const page = ctx.pages()[0] ?? (await ctx.newPage());
      try {
        const homeUrl = v1.home(task);
        if (homeUrl !== 'about:blank') {
          await page.goto(homeUrl, { waitUntil: 'domcontentloaded', timeout: 60_000 });
        }
        await v1.fill(page, task, opts.steps);
        return {
          ok: true,
          workUrl: undefined,
          handoffHint: '草稿已填充：请在打开的浏览器中人工确认发布（发布按钮属平台风控敏感动作，'
            + '自动点击实测被拦，能力边界见 distributor/README.md）',
        };
      } catch (e) {
        return { ok: false, failureReason: (e as Error).message.split('\n')[0] };
      }
      // ctx 故意不关闭：人工确认发布需要浏览器保持打开，由调用方（编排宿主）收尾
    },
    queryWork,
  };
}

const providers: Record<string, PlatformAdapterV2> = {
  juejin: makeProvider('juejin', queryJuejinWork),
  csdn: makeProvider('csdn'),
  zhihu: makeProvider('zhihu'),
  wechat: makeProvider('wechat'),
  stub: makeProvider('stub', queryStubWork),
};

export function providerOf(platform: string): PlatformAdapterV2 {
  const p = providers[platform];
  if (!p) {
    throw new Error(`未注册 v2 Provider：${platform}（可选：${Object.keys(providers).join(' / ')}）`);
  }
  return p;
}

export function allProviders(): PlatformAdapterV2[] {
  return Object.values(providers);
}
