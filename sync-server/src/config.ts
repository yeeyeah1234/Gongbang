import { timingSafeEqual } from 'node:crypto';

export interface SyncServerConfig {
  host: string;
  port: number;
  springBaseUrl: string;
  syncInternalKey: string;
  nodeEnv: string;
  debounceMs: number;
  maxDebounceMs: number;
  requestTimeoutMs: number;
  saveMaxAttempts: number;
  saveRetryDelayMs: number;
  shutdownTimeoutMs: number;
}

const DEFAULT_HOST = '127.0.0.1';
const DEFAULT_PORT = 1234;
const DEFAULT_SPRING_BASE_URL = 'http://127.0.0.1:8090';
const DEFAULT_DEBOUNCE_MS = 3_000;
const DEFAULT_MAX_DEBOUNCE_MS = 10_000;
const DEFAULT_REQUEST_TIMEOUT_MS = 5_000;
const DEFAULT_SAVE_MAX_ATTEMPTS = 3;
const DEFAULT_SAVE_RETRY_DELAY_MS = 500;
const DEFAULT_SHUTDOWN_TIMEOUT_MS = 30_000;

export function loadConfig(env: NodeJS.ProcessEnv = process.env): SyncServerConfig {
  const syncInternalKey = (env.SYNC_INTERNAL_KEY ?? '').trim();
  if (!syncInternalKey) {
    throw new Error('SYNC_INTERNAL_KEY 환경 변수가 설정되지 않았습니다.');
  }

  const host = (env.HOST ?? DEFAULT_HOST).trim() || DEFAULT_HOST;
  const port = parsePort(env.PORT);
  const springBaseUrl = normalizeSpringBaseUrl(env.SPRING_BASE_URL ?? DEFAULT_SPRING_BASE_URL);
  const nodeEnv = (env.NODE_ENV ?? 'development').trim() || 'development';

  return {
    host,
    port,
    springBaseUrl,
    syncInternalKey,
    nodeEnv,
    debounceMs: DEFAULT_DEBOUNCE_MS,
    maxDebounceMs: DEFAULT_MAX_DEBOUNCE_MS,
    requestTimeoutMs: DEFAULT_REQUEST_TIMEOUT_MS,
    saveMaxAttempts: DEFAULT_SAVE_MAX_ATTEMPTS,
    saveRetryDelayMs: DEFAULT_SAVE_RETRY_DELAY_MS,
    shutdownTimeoutMs: DEFAULT_SHUTDOWN_TIMEOUT_MS,
  };
}

function parsePort(raw: string | undefined): number {
  if (raw === undefined || raw.trim() === '') {
    return DEFAULT_PORT;
  }
  const trimmed = raw.trim();
  if (!/^\d+$/.test(trimmed)) {
    throw new Error(`PORT 환경 변수가 올바르지 않습니다: ${trimmed}`);
  }
  const parsed = Number(trimmed);
  if (!Number.isInteger(parsed) || parsed < 0 || parsed > 65535) {
    throw new Error(`PORT 환경 변수가 유효 범위(0~65535)를 벗어났습니다: ${trimmed}`);
  }
  return parsed;
}

function normalizeSpringBaseUrl(raw: string): string {
  const trimmed = raw.trim().replace(/\/+$/, '');
  if (!/^https?:\/\/.+/.test(trimmed)) {
    throw new Error(`SPRING_BASE_URL은 http:// 또는 https://로 시작해야 합니다: ${trimmed}`);
  }
  return trimmed;
}

/**
 * X-Sync-Key를 constant-time으로 비교한다. 설정 키가 비어 있거나 헤더가 없으면 false(fail-closed).
 */
export function isValidSyncKey(
  expectedKey: string,
  actualKey: string | undefined,
): boolean {
  if (!expectedKey || !actualKey) {
    return false;
  }
  const expectedBuf = Buffer.from(expectedKey, 'utf8');
  const actualBuf = Buffer.from(actualKey, 'utf8');
  if (expectedBuf.length !== actualBuf.length) {
    return false;
  }
  return timingSafeEqual(expectedBuf, actualBuf);
}
