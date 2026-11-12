import { spawn } from 'node:child_process';
import { loadConfig, saveConfig, requireToken } from './config.js';
import { login, pending } from './api.js';
import { runTask, RunOptions } from './runner.js';

function arg(flag: string): string | undefined {
  const i = process.argv.indexOf(flag);
  return i >= 0 ? process.argv[i + 1] : undefined;
}

function has(flag: string): boolean {
  return process.argv.includes(flag);
}

/** 剪贴板兜底（开题 7.2）：自动化失败时把排版内容复制到系统剪贴板，人工粘贴发布 */
function copyToClipboard(text: string): Promise<void> {
  return new Promise((resolve, reject) => {
    const cmd = process.platform === 'win32' ? 'clip' : process.platform === 'darwin' ? 'pbcopy' : 'xclip';
    const args = process.platform === 'linux' ? ['-selection', 'clipboard'] : [];
    const child = spawn(cmd, args);
    child.on('close', (code) => (code === 0 ? resolve() : reject(new Error(`剪贴板命令退出码 ${code}`))));
    child.on('error', reject);
    child.stdin.end(process.platform === 'win32' ? Buffer.from(text, 'utf16le') : Buffer.from(text, 'utf8'));
  });
}

async function main(): Promise<void> {
  const [cmd, ...rest] = process.argv.slice(2);
  const cfg = loadConfig();

  switch (cmd) {
    case 'login': {
      const email = arg('--email');
      const password = arg('--password') ?? process.env['INKPRESS_PASSWORD'];
      const name = arg('--name') ?? 'inkpress-distributor';
      if (arg('--server')) {
        cfg.serverUrl = arg('--server')!;
      }
      if (!email || !password) {
        throw new Error('用法：login --email <邮箱> --password <密码|环境变量 INKPRESS_PASSWORD> [--name 设备名] [--server URL]');
      }
      const r = await login(email, password, name);
      cfg.email = email;
      cfg.deviceName = name;
      cfg.deviceToken = r.deviceToken;
      cfg.deviceId = r.deviceId;
      saveConfig(cfg);
      console.log(`设备令牌已签发并保存（deviceId=${r.deviceId}，明文仅存本地 config.json，可在站内个人中心吊销）`);
      return;
    }
    case 'pending': {
      requireToken(cfg);
      const tasks = await pending();
      if (tasks.length === 0) {
        console.log('暂无待分发任务。博主端 POST /api/studio/distribution {articleId, platform} 发起。');
        return;
      }
      for (const t of tasks) {
        console.log(`#${t.recordId} [${t.platform}] ${t.title}（文章 ${t.articleId}，正文 ${t.contentMd.length} 字，HTML ${t.contentHtml.length} 字）`);
      }
      return;
    }
    case 'run': {
      requireToken(cfg);
      const recordId = Number(rest[0]);
      if (!Number.isFinite(recordId)) {
        throw new Error('用法：run <recordId> [--timeout 秒=900] [--headless] [--keep-open]');
      }
      const tasks = await pending();
      const task = tasks.find((t) => t.recordId === recordId);
      if (!task) {
        throw new Error(`待分发列表中找不到 #${recordId}（已填充/已发布的记录不再出现）`);
      }
      console.log(`开始分发 #${task.recordId} [${task.platform}] ${task.title}`);
      const opts: RunOptions = {
        timeoutSec: Number(arg('--timeout') ?? 900),
        keepOpen: has('--keep-open'),
        headless: has('--headless') ? true : undefined,
      };
      const result = await runTask(task, opts);
      process.exitCode = result.success ? 0 : 1;
      return;
    }
    case 'clipboard': {
      requireToken(cfg);
      const recordId = Number(rest[0]);
      if (!Number.isFinite(recordId)) {
        throw new Error('用法：clipboard <recordId>（复制排版内容到剪贴板，人工粘贴发布）');
      }
      const tasks = await pending();
      const task = tasks.find((t) => t.recordId === recordId);
      if (!task) {
        throw new Error(`待分发列表中找不到 #${recordId}`);
      }
      await copyToClipboard(`标题：${task.title}\n\n${task.contentMd}`);
      console.log(`#${recordId} [${task.platform}] 《${task.title}》已复制到剪贴板。`);
      console.log('请在目标平台编辑器中粘贴并手动发布；发布后把外链回报给站点（站内分发记录可补录）。');
      return;
    }
    default:
      console.log(`InkPress 本机分发工具

命令：
  login --email <邮箱> --password <密码> [--name 设备名] [--server URL]
      签发设备令牌并保存到本地 config.json（明文仅存本机，站内个人中心可吊销）
  pending
      拉取待分发任务列表（设备令牌鉴权）
  run <recordId> [--timeout 秒=900] [--headless] [--keep-open]
      打开平台编辑器自动填充草稿 → 回报 filled → 等待人工发布 → 捕获外链回报 report
      真实平台默认有头（用持久化 profile 保留平台登录态），stub 冒烟默认无头
  clipboard <recordId>
      剪贴板兜底（自动化失败/风控熔断时用）：复制排版内容，人工粘贴发布`);
      process.exitCode = cmd === undefined ? 0 : 1;
  }
}

main().catch((e: Error) => {
  console.error('错误：' + e.message);
  process.exitCode = 1;
});
