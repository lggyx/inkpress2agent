import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

export interface DistributorConfig {
  serverUrl: string;
  email?: string;
  deviceToken?: string;
  deviceId?: number;
  deviceName?: string;
}

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
export const CONFIG_PATH = join(ROOT, 'config.json');
export const ARTIFACTS_DIR = join(ROOT, '.artifacts');
export const PROFILES_DIR = join(ROOT, '.profiles');

export function loadConfig(): DistributorConfig {
  if (!existsSync(CONFIG_PATH)) {
    return { serverUrl: 'http://127.0.0.1:8912' };
  }
  return JSON.parse(readFileSync(CONFIG_PATH, 'utf8'));
}

export function saveConfig(cfg: DistributorConfig): void {
  writeFileSync(CONFIG_PATH, JSON.stringify(cfg, null, 2), 'utf8');
}

export function requireToken(cfg: DistributorConfig): string {
  if (!cfg.deviceToken) {
    throw new Error('尚未登录：先执行 login 命令签发设备令牌（config.json 不存在 deviceToken）');
  }
  return cfg.deviceToken;
}
