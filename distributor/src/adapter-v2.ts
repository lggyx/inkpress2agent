/**
 * PlatformAdapter v2 — 对齐 AiToEarn 的能力 Provider 模型
 * （参考 yikart/AiToEarn platforms.interface.ts：AuthProvider / PublishProvider / WorkProvider 三能力 + 注册表）
 *
 * 三种能力，每个平台适配器按需实现：
 *   AuthProvider   授权：authType 决定（Browser=本地 profile 持久登录 / QRCode=扫码 / UserHandoff=人工兜底）
 *   PublishProvider 发布：draft() 进编辑器填充 → verify() 捕获发布结果
 *   WorkProvider    回流：queryWork() 拉取已发布作品的数据（阅读/点赞/评论）
 *
 * 兼容性：v1 的 home/urlPattern/fill 由 publish 能力内部承载，存量 CLI 流程不受影响。
 */
import type { Locator, Page } from 'playwright';
import type { PendingTask } from './api.js';

/** 授权方式（对齐 AiToEarn AuthType 三分法） */
export type AuthType =
  | 'browser'      // 本地持久化 profile：人工登录一次，Cookie 长期有效（ juejin/csdn/zhihu/wechat 现状）
  | 'qrcode'       // 平台扫码授权：每次发布前可能需要重扫
  | 'user_handoff' // 自动化不可用：产出人工发布包，人工完成后回填链接

/** 授权状态（B 档：auth status 可查可续期） */
export interface AuthStatus {
  platform: string;
  authType: AuthType;
  loggedIn: boolean;          // profile 存在且未过期
  lastCheckedAt: number;
  lastLoginAt?: number;       // 最近一次人工登录时间
  profilePath?: string;       // 持久化 profile 位置
  note?: string;              // 人工提示（如「Cookie 过期请执行 auth login」）
}

/** 发布结果（A 档统一口径） */
export interface PublishResult {
  ok: boolean;
  workUrl?: string;           // 发布后详情页外链（捕获失败则为空）
  failureReason?: string;     // 失败原因（自动化异常 / 超时 / 需人工兜底）
  handoffHint?: string;       // UserHandoff 提示（剪贴板兜底说明）
}

/** 作品数据（B 档回流） */
export interface WorkStats {
  workUrl: string;
  views?: number;
  likes?: number;
  comments?: number;
  fetchedAt: number;
}

/** v2 适配器接口：auth + publish 必选，queryWork 可选 */
export interface PlatformAdapterV2 {
  platform: string;           // 平台标识（juejin / csdn / zhihu / wechat / stub）
  displayName: string;        // 展示名
  authType: AuthType;

  /** 授权能力：检查本地 profile 登录态（browser 型）；qrcode/handoff 型返回人工指引 */
  authStatus(): Promise<AuthStatus>;
  /** 触发授权流程（browser 型=打开有头浏览器等人工登录；返回后视为已登录） */
  authLogin(headless: boolean): Promise<AuthStatus>;

  /** 发布能力：进编辑器填充草稿并触发发布（v1 的 home/fill 组合） */
  publish(task: PendingTask, opts: { headless: boolean; steps: StepLog[] }): Promise<PublishResult>;
  /** 发布后详情页 URL 特征（v1 urlPattern 平移） */
  urlPattern: RegExp;

  /** 作品数据回流（可选）：从详情页提取阅读/点赞/评论 */
  queryWork?(workUrl: string, opts: { headless: boolean }): Promise<WorkStats>;
}

/** 步骤日志（v1 保留） */
export interface StepLog {
  at: number;
  action: string;
  selector?: string;
  ok: boolean;
  note?: string;
}

/** v1 → v2 渐进兼容：v1 形状的适配器可通过 wrapV1 升级进注册表 */
export interface PlatformAdapterV1 {
  home(task: PendingTask): string;
  urlPattern: RegExp;
  headlessDefault: boolean;
  fill(page: Page, task: PendingTask, steps: StepLog[]): Promise<void>;
}
