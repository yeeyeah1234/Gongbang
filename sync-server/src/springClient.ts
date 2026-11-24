export interface DocumentAccess {
  memberId: number;
  nickname: string;
  teamId: number;
  leader?: boolean;
}

export interface TeamAccess {
  memberId: number;
  nickname: string;
  teamId: number;
  leader: boolean;
  colorIndex: number;
}

export class SpringAccessError extends Error {
  readonly status: number | undefined;

  constructor(message: string, status?: number) {
    super(message);
    this.name = 'SpringAccessError';
    this.status = status;
  }
}

export class SpringLoadError extends Error {
  readonly status: number | undefined;

  constructor(message: string, status?: number) {
    super(message);
    this.name = 'SpringLoadError';
    this.status = status;
  }
}

export type SaveDocumentResult =
  | { status: 'ok' }
  | { status: 'not_found' }
  | { status: 'error'; httpStatus?: number; reason: string };

export type FetchLike = typeof fetch;

export interface SpringClientOptions {
  baseUrl: string;
  internalKey: string;
  fetchFn?: FetchLike;
  requestTimeoutMs?: number;
  saveMaxAttempts?: number;
  saveRetryDelayMs?: number;
}

export interface SpringClient {
  checkDocumentAccess(documentId: number, token: string): Promise<DocumentAccess>;
  checkTeamAccess(teamId: number, token: string): Promise<TeamAccess>;
  loadDocumentContent(documentId: number): Promise<Uint8Array | null>;
  saveDocumentContent(
    documentId: number,
    editorId: number,
    content: Uint8Array,
  ): Promise<SaveDocumentResult>;
}

const SYNC_KEY_HEADER = 'X-Sync-Key';
const DEFAULT_REQUEST_TIMEOUT_MS = 5_000;
const DEFAULT_SAVE_MAX_ATTEMPTS = 3;
const DEFAULT_SAVE_RETRY_DELAY_MS = 500;

interface ApiSuccessResponse<T> {
  success?: unknown;
  data?: T;
}

export function createSpringClient(options: SpringClientOptions): SpringClient {
  const baseUrl = options.baseUrl.replace(/\/+$/, '');
  const internalKey = options.internalKey;
  const fetchFn = options.fetchFn ?? fetch;
  const requestTimeoutMs = options.requestTimeoutMs ?? DEFAULT_REQUEST_TIMEOUT_MS;
  const saveMaxAttempts = Math.max(1, options.saveMaxAttempts ?? DEFAULT_SAVE_MAX_ATTEMPTS);
  const saveRetryDelayMs = Math.max(0, options.saveRetryDelayMs ?? DEFAULT_SAVE_RETRY_DELAY_MS);

  async function checkDocumentAccess(
    documentId: number,
    token: string,
  ): Promise<DocumentAccess> {
    const url = `${baseUrl}/internal/documents/${documentId}/access`;
    let response: Response;
    try {
      response = await fetchFn(url, {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${token}`,
          [SYNC_KEY_HEADER]: internalKey,
          Accept: 'application/json',
        },
        signal: AbortSignal.timeout(requestTimeoutMs),
      });
    } catch (error) {
      throw new SpringAccessError(
        `문서 권한 확인 요청 실패: ${summarizeError(error)}`,
      );
    }

    if (response.status !== 200) {
      throw new SpringAccessError(
        `문서 권한 확인 거절 (HTTP ${response.status})`,
        response.status,
      );
    }

    let body: ApiSuccessResponse<Record<string, unknown>>;
    try {
      body = (await response.json()) as ApiSuccessResponse<Record<string, unknown>>;
    } catch {
      throw new SpringAccessError('문서 권한 응답 JSON 파싱 실패', 200);
    }

    const data = body?.data;
    if (
      body?.success !== true ||
      !data ||
      typeof data.memberId !== 'number' ||
      typeof data.nickname !== 'string' ||
      typeof data.teamId !== 'number'
    ) {
      throw new SpringAccessError('문서 권한 응답 형식이 올바르지 않습니다', 200);
    }

    return {
      memberId: data.memberId,
      nickname: data.nickname,
      teamId: data.teamId,
      leader: typeof data.leader === 'boolean' ? data.leader : undefined,
    };
  }

  async function checkTeamAccess(teamId: number, token: string): Promise<TeamAccess> {
    const url = `${baseUrl}/internal/teams/${teamId}/access`;
    let response: Response;
    try {
      response = await fetchFn(url, {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${token}`,
          [SYNC_KEY_HEADER]: internalKey,
          Accept: 'application/json',
        },
        signal: AbortSignal.timeout(requestTimeoutMs),
      });
    } catch (error) {
      throw new SpringAccessError(
        `팀 권한 확인 요청 실패: ${summarizeError(error)}`,
      );
    }

    if (response.status !== 200) {
      throw new SpringAccessError(
        `팀 권한 확인 거절 (HTTP ${response.status})`,
        response.status,
      );
    }

    let body: ApiSuccessResponse<Record<string, unknown>>;
    try {
      body = (await response.json()) as ApiSuccessResponse<Record<string, unknown>>;
    } catch {
      throw new SpringAccessError('팀 권한 응답 JSON 파싱 실패', 200);
    }

    const data = body?.data;
    if (
      body?.success !== true ||
      !data ||
      typeof data.memberId !== 'number' ||
      typeof data.nickname !== 'string' ||
      typeof data.teamId !== 'number' ||
      typeof data.leader !== 'boolean' ||
      typeof data.colorIndex !== 'number'
    ) {
      throw new SpringAccessError('팀 권한 응답 형식이 올바르지 않습니다', 200);
    }

    return {
      memberId: data.memberId,
      nickname: data.nickname,
      teamId: data.teamId,
      leader: data.leader,
      colorIndex: data.colorIndex,
    };
  }

  async function loadDocumentContent(documentId: number): Promise<Uint8Array | null> {
    const url = `${baseUrl}/internal/documents/${documentId}/content`;
    let response: Response;
    try {
      response = await fetchFn(url, {
        method: 'GET',
        headers: {
          [SYNC_KEY_HEADER]: internalKey,
        },
        signal: AbortSignal.timeout(requestTimeoutMs),
      });
    } catch (error) {
      throw new SpringLoadError(
        `문서 본문 불러오기 요청 실패: ${summarizeError(error)}`,
      );
    }

    if (response.status === 204) {
      return null;
    }

    if (response.status === 200) {
      const buffer = await response.arrayBuffer();
      return new Uint8Array(buffer);
    }

    throw new SpringLoadError(
      `문서 본문 불러오기 실패 (HTTP ${response.status})`,
      response.status,
    );
  }

  async function saveDocumentContent(
    documentId: number,
    editorId: number,
    content: Uint8Array,
  ): Promise<SaveDocumentResult> {
    const url = `${baseUrl}/internal/documents/${documentId}/content?editorId=${encodeURIComponent(String(editorId))}`;
    const bodyBytes = new Uint8Array(content);

    let lastResult: SaveDocumentResult = { status: 'error', reason: '알 수 없는 오류' };

    for (let attempt = 1; attempt <= saveMaxAttempts; attempt += 1) {
      try {
        const response = await fetchFn(url, {
          method: 'PUT',
          headers: {
            [SYNC_KEY_HEADER]: internalKey,
            'Content-Type': 'application/octet-stream',
          },
          body: bodyBytes,
          signal: AbortSignal.timeout(requestTimeoutMs),
        });

        if (response.status === 200 || response.status === 204) {
          return { status: 'ok' };
        }

        if (response.status === 404) {
          return { status: 'not_found' };
        }

        lastResult = {
          status: 'error',
          httpStatus: response.status,
          reason: `HTTP ${response.status}`,
        };

        if (!isRetryableStatus(response.status)) {
          return lastResult;
        }
      } catch (error) {
        lastResult = {
          status: 'error',
          reason: summarizeError(error),
        };
      }

      if (attempt < saveMaxAttempts && saveRetryDelayMs > 0) {
        await delay(saveRetryDelayMs * attempt);
      }
    }

    return lastResult;
  }

  return {
    checkDocumentAccess,
    checkTeamAccess,
    loadDocumentContent,
    saveDocumentContent,
  };
}

function isRetryableStatus(status: number): boolean {
  return status >= 500 || status === 408 || status === 429;
}

/**
 * 민감 정보(키·토큰·헤더)가 예외 메시지에 섞일 위험을 줄이기 위해 에러 이름/코드만 요약한다.
 */
function summarizeError(error: unknown): string {
  if (error instanceof Error) {
    const code = (error as { code?: unknown }).code;
    if (typeof code === 'string' && code.length > 0) {
      return `${error.name} (${code})`;
    }
    return error.name;
  }
  return 'UnknownError';
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
