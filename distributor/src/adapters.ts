import type { Locator, Page } from 'playwright';
import { join } from 'node:path';
import { PendingTask } from './api.js';
import { ARTIFACTS_DIR } from './config.js';

export interface StepLog {
  at: number;
  action: string;
  selector?: string;
  ok: boolean;
  note?: string;
}

export interface PlatformAdapter {
  /** 编辑器入口 URL（stub 为本地生成的 file:// 页面） */
  home(task: PendingTask): string;
  /** 发布完成后详情页 URL 特征 */
  urlPattern: RegExp;
  /** 默认是否无头（真实平台需要人工登录，默认有头） */
  headlessDefault: boolean;
  fill(page: Page, task: PendingTask, steps: StepLog[]): Promise<void>;
}

export function mdFallbackHtml(md: string): string {
  // 未知平台/服务端未转换时的极简降级：按行映射标题/段落，保证可读
  const esc = md.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  return esc.split(/\r?\n/).map((line) => {
    const t = line.trim();
    if (!t) return '';
    const h = /^(#{1,6})\s+(.*)$/.exec(t);
    if (h) return `<h${Math.min(h[1].length + 1, 6)}>${h[2]}</h${Math.min(h[1].length + 1, 6)}>`;
    return `<p>${t}</p>`;
  }).join('\n');
}

/** 把 HTML 写入剪贴板（text/html 味道），供富文本编辑器 Ctrl+V */
async function copyHtml(page: Page, html: string): Promise<void> {
  await page.evaluate(async (payload) => {
    const item = new ClipboardItem({
      'text/html': new Blob([payload], { type: 'text/html' }),
      'text/plain': new Blob([payload], { type: 'text/plain' }),
    });
    await navigator.clipboard.write([item]);
  }, html);
}

async function trySelector(page: Page, steps: StepLog[], action: string, selector: string,
                           fn: (locator: Locator) => Promise<void>): Promise<boolean> {
  const t0 = Date.now();
  try {
    const locator = page.locator(selector).first();
    await fn(locator);
    steps.push({ at: Date.now() - t0, action, selector, ok: true });
    return true;
  } catch (e) {
    steps.push({ at: Date.now() - t0, action, selector, ok: false, note: (e as Error).message.split('\n')[0] });
    return false;
  }
}

async function fillTitle(page: Page, task: PendingTask, steps: StepLog[], selectors: string[]): Promise<void> {
  for (const sel of selectors) {
    const ok = await trySelector(page, steps, 'fill-title', sel, (l) => l.fill(task.title.slice(0, 100)));
    if (ok) return;
  }
  throw new Error('标题输入框未找到：' + selectors.join(' / '));
}

/** 正文填充：优先剪贴板粘贴富文本，失败回退 innerHTML 注入（记录命中策略；以填充前后字符数增量判定成败） */
async function fillContent(page: Page, task: PendingTask, steps: StepLog[],
                           focusSelectors: string[]): Promise<void> {
  const html = task.contentHtml || mdFallbackHtml(task.contentMd);
  for (const sel of focusSelectors) {
    const focused = await trySelector(page, steps, 'focus-content', sel, (l) => l.click());
    if (!focused) continue;
    const before = await contentLength(page, sel);
    await copyHtml(page, html);
    await page.keyboard.press('Control+v');
    await page.waitForTimeout(500);
    const pasted = await contentLength(page, sel);
    if (pasted > before) {
      steps.push({ at: 0, action: 'paste-content', selector: sel, ok: true, note: `clipboard html, +${pasted - before} chars` });
      return;
    }
    // 回退：直接改写 DOM（Draft.js 类编辑器可能不认，尽力而为）
    const injected = await page.evaluate(({ s, payload }) => {
      const el = document.querySelector(s) as HTMLElement | null;
      if (!el) return 0;
      el.innerHTML = payload;
      el.dispatchEvent(new InputEvent('input', { bubbles: true }));
      return el.textContent!.trim().length;
    }, { s: sel, payload: html });
    if (injected > before) {
      steps.push({ at: 0, action: 'inject-content', selector: sel, ok: true, note: `${injected} chars (clipboard failed)` });
      return;
    }
    steps.push({ at: 0, action: 'paste-content', selector: sel, ok: false, note: `content unchanged (${before} -> ${Math.max(pasted, injected)})` });
  }
  throw new Error('正文编辑区未找到或填充失败：' + focusSelectors.join(' / '));
}

async function contentLength(page: Page, sel: string): Promise<number> {
  return page.evaluate((s) => {
    const el = document.querySelector(s);
    return el ? el.textContent!.trim().length : 0;
  }, sel);
}

/** 本地 stub 编辑器：全自动冒烟用，模拟 平台填充→发布→详情页跳转 全链路 */
function stubEditorHtml(): string {
  return `<!DOCTYPE html><html lang="zh"><head><meta charset="UTF-8"><title>Stub 平台编辑器</title></head>
<body style="font-family:system-ui;max-width:52rem;margin:2rem auto">
<h3>Stub 平台编辑器（分发链路冒烟）</h3>
<input id="title" placeholder="标题" style="width:100%;padding:8px;font-size:16px">
<div id="content" contenteditable="true" style="min-height:240px;border:1px solid #bbb;padding:12px;margin-top:8px"></div>
<button id="publish" onclick="publish()" style="margin-top:12px;padding:8px 24px">发布</button>
<span id="pub-status"></span>
<script>
function publish(){
  var t=document.querySelector('#title').value;
  if(!t){document.querySelector('#pub-status').textContent='标题必填';return;}
  location.hash='#/published/'+Date.now();
  document.querySelector('#pub-status').textContent='已发布：'+t;
}
</script></body></html>`;
}

const stub: PlatformAdapter = {
  home: () => 'about:blank',
  urlPattern: /#\/published\//,
  headlessDefault: true,
  async fill(page, task, steps) {
    const { writeFileSync, mkdirSync } = await import('node:fs');
    mkdirSync(ARTIFACTS_DIR, { recursive: true });
    const file = join(ARTIFACTS_DIR, 'stub-editor.html');
    writeFileSync(file, stubEditorHtml(), 'utf8');
    await page.goto('file://' + file.replace(/\\/g, '/'));
    steps.push({ at: 0, action: 'open-editor', selector: 'file://stub', ok: true });
    await fillTitle(page, task, steps, ['#title']);
    await fillContent(page, task, steps, ['#content']);
    await trySelector(page, steps, 'click-publish', '#publish', (l) => l.click());
    await page.waitForTimeout(300);
  },
};

/** 真实平台适配器：选择器为当前站点结构的尽力匹配，失败会完整记录到执行明细 */
const juejin: PlatformAdapter = {
  home: () => 'https://juejin.cn/editor/drafts/new',
  urlPattern: /juejin\.cn\/post\//,
  headlessDefault: false,
  async fill(page, task, steps) {
    await fillTitle(page, task, steps, [
      'textarea[placeholder*="标题"]', '.title-input textarea',
      'input[placeholder*="标题"]', 'input[placeholder*="输入文章标题"]',
      '[placeholder*="请输入标题"]', 'div[contenteditable=true][data-title]',
    ]);
    await fillContent(page, task, steps, [
      '.CodeMirror textarea', '.CodeMirror', '[contenteditable="true"]',
      '.markdown-body[contenteditable]', 'div[contenteditable=true].editor',
    ]);
  },
};

const csdn: PlatformAdapter = {
  home: () => 'https://mp.csdn.net/mp_blog/creation/editor/new',
  urlPattern: /article\/details\//,
  headlessDefault: false,
  async fill(page, task, steps) {
    await fillTitle(page, task, steps, ['input[placeholder*="标题"]', '.article-bar input']);
    await fillContent(page, task, steps, [
      '.editor__inner textarea', 'textarea[placeholder]', '.CodeMirror textarea',
      '.editor__inner[contenteditable="true"]', '[contenteditable="true"]']);
  },
};

const zhihu: PlatformAdapter = {
  home: () => 'https://zhuanlan.zhihu.com/write',
  urlPattern: /zhuanlan\.zhihu\.com\/p\//,
  headlessDefault: false,
  async fill(page, task, steps) {
    await fillTitle(page, task, steps, ['textarea[placeholder*="标题"]']);
    await fillContent(page, task, steps, ['.public-DraftEditor-content[contenteditable="true"]', '[contenteditable="true"]']);
  },
};

const wechat: PlatformAdapter = {
  home: () => 'https://mp.weixin.qq.com/',
  urlPattern: /mp\.weixin\.qq\.com\/s\b/,
  headlessDefault: false,
  async fill(page, task, steps) {
    await fillTitle(page, task, steps, ['#title', 'textarea[placeholder*="标题"]', 'input[placeholder*="标题"]']);
    await fillContent(page, task, steps, [
      '.ProseMirror[contenteditable="true"]', '#edui1_iframeholder iframe body',
      '.rich_media_content[contenteditable="true"]', '[contenteditable="true"]']);
  },
};

export const adapters: Record<string, PlatformAdapter> = { stub, juejin, csdn, zhihu, wechat };

export function adapterOf(platform: string): PlatformAdapter {
  const a = adapters[platform];
  if (!a) {
    throw new Error(`不支持的平台：${platform}（可选：${Object.keys(adapters).join(' / ')}）`);
  }
  return a;
}
