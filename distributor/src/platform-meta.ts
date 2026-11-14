/**
 * 平台授权元数据注册表（对齐 AiToEarn PlatformIntegrationRegistry 思路）
 * 新平台接入：在 PLATFORM_META 加一条即可获得 auth status/login 能力
 */
export interface PlatformMeta {
  platform: string;
  displayName: string;
  authType: 'browser' | 'qrcode' | 'user_handoff';
  loginUrl: string;          // 登录页（或登录后落地的个人主页）
  loggedInSelector: string;  // 已登录态在页面上的特征选择器
  handoffNote?: string;      // 自动化失败时的人工兜底说明
}

export const PLATFORM_META: Record<string, PlatformMeta> = {
  juejin: {
    platform: 'juejin', displayName: '稀土掘金', authType: 'browser',
    loginUrl: 'https://juejin.cn/',
    loggedInSelector: 'img[class*="avatar"], .avatar, [class*="user"] img',
  },
  csdn: {
    platform: 'csdn', displayName: 'CSDN', authType: 'browser',
    loginUrl: 'https://www.csdn.net/',
    loggedInSelector: 'img[class*="avatar"], .avatar-img, [class*="user-info"] img',
  },
  zhihu: {
    platform: 'zhihu', displayName: '知乎', authType: 'browser',
    loginUrl: 'https://www.zhihu.com/',
    loggedInSelector: 'img[class*="Avatar"], .TopprofileItem, button[aria-label="我的"]',
  },
  wechat: {
    platform: 'wechat', displayName: '微信公众号', authType: 'browser',
    loginUrl: 'https://mp.weixin.qq.com/',
    loggedInSelector: '.weui-desktop__block, .main-container, #js_account_area',
  },
  stub: {
    platform: 'stub', displayName: '本地测试', authType: 'user_handoff',
    loginUrl: 'about:blank', loggedInSelector: 'body',
    handoffNote: 'stub 平台为本地演练，无需授权',
  },
};

export function metaOf(platform: string): PlatformMeta {
  const m = PLATFORM_META[platform];
  if (!m) throw new Error(`未注册平台：${platform}（可选：${Object.keys(PLATFORM_META).join(' / ')}）`);
  return m;
}

export function allPlatforms(): PlatformMeta[] {
  return Object.values(PLATFORM_META);
}
