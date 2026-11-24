import type { IncomingMessage, ServerResponse } from 'node:http';
import {
  Server,
  type afterUnloadDocumentPayload,
  type onAuthenticatePayload,
  type onChangePayload,
  type onLoadDocumentPayload,
  type onRequestPayload,
  type onStoreDocumentPayload,
} from '@hocuspocus/server';
import * as Y from 'yjs';
import { isValidSyncKey, type SyncServerConfig } from './config.js';
import { parseDocumentName } from './documentName.js';
import {
  createSpringClient,
  type FetchLike,
  type SpringClient,
} from './springClient.js';

export interface SyncConnectionContext {
  memberId: number;
  nickname: string;
  teamId: number;
  leader?: boolean;
  colorIndex?: number;
}

export interface SyncLogger {
  info(message: string): void;
  warn(message: string): void;
  error(message: string): void;
}

export interface CreateSyncServerOptions {
  config: SyncServerConfig;
  springClient?: SpringClient;
  fetchFn?: FetchLike;
  logger?: SyncLogger;
  quiet?: boolean;
}

export interface SyncHooks {
  onAuthenticate(
    data: Pick<onAuthenticatePayload<SyncConnectionContext>, 'documentName' | 'token'>,
  ): Promise<SyncConnectionContext>;
  onLoadDocument(
    data: Pick<onLoadDocumentPayload<SyncConnectionContext>, 'documentName' | 'document'>,
  ): Promise<void>;
  onChange(
    data: Pick<onChangePayload<SyncConnectionContext>, 'documentName' | 'context'>,
  ): Promise<void>;
  onStoreDocument(
    data: Pick<
      onStoreDocumentPayload<SyncConnectionContext>,
      'documentName' | 'document' | 'lastContext' | 'instance'
    >,
  ): Promise<void>;
  afterUnloadDocument(
    data: Pick<afterUnloadDocumentPayload, 'documentName'>,
  ): Promise<void>;
  onRequest(data: onRequestPayload): Promise<void>;
}

export interface SyncServerHandle {
  server: Server<SyncConnectionContext>;
  hooks: SyncHooks;
}

const DISCONNECT_PATH = '/internal/disconnect';
const MAX_DISCONNECT_BODY_BYTES = 16 * 1024;
const defaultLogger: SyncLogger = {
  info: (message) => console.log(`[sync-server] ${message}`),
  warn: (message) => console.warn(`[sync-server] ${message}`),
  error: (message) => console.error(`[sync-server] ${message}`),
};

export function createSyncServer(options: CreateSyncServerOptions): SyncServerHandle {
  const { config } = options;
  const logger = options.logger ?? defaultLogger;
  const springClient =
    options.springClient ??
    createSpringClient({
      baseUrl: config.springBaseUrl,
      internalKey: config.syncInternalKey,
      fetchFn: options.fetchFn,
      requestTimeoutMs: config.requestTimeoutMs,
      saveMaxAttempts: config.saveMaxAttempts,
      saveRetryDelayMs: config.saveRetryDelayMs,
    });

  const lastEditorByDocument = new Map<string, number>();
  const deletedDocuments = new Set<string>();

  const hooks: SyncHooks = {
    async onAuthenticate({ documentName, token }) {
      const parsed = parseDocumentName(documentName);
      const trimmedToken = typeof token === 'string' ? token.trim() : '';
      if (!trimmedToken) {
        throw new Error('인증 토큰이 필요합니다');
      }

      if (parsed.type === 'team') {
        const access = await springClient.checkTeamAccess(parsed.id, trimmedToken);
        return {
          memberId: access.memberId,
          nickname: access.nickname,
          teamId: access.teamId,
          leader: access.leader,
          colorIndex: access.colorIndex,
        };
      }

      const access = await springClient.checkDocumentAccess(parsed.id, trimmedToken);
      return {
        memberId: access.memberId,
        nickname: access.nickname,
        teamId: access.teamId,
      };
    },

    async onLoadDocument({ documentName, document }) {
      const parsed = parseDocumentName(documentName);
      if (parsed.type === 'team') {
        // WS-03: team:{teamId} 방은 awareness 전용(빈 문서). Spring 본문 조회를 호출하지 않는다.
        return;
      }
      deletedDocuments.delete(documentName);

      try {
        const bytes = await springClient.loadDocumentContent(parsed.id);
        if (bytes && bytes.byteLength > 0) {
          Y.applyUpdate(document, bytes);
        }
      } catch (error) {
        const reason = error instanceof Error ? error.message : 'UnknownError';
        logger.error(
          `문서 본문 불러오기 실패로 연결을 거절합니다. documentName=${documentName}, reason=${reason}`,
        );
        throw error;
      }
    },

    async onChange({ documentName, context }) {
      const parsed = parseDocumentName(documentName);
      if (parsed.type === 'team') {
        return;
      }
      if (deletedDocuments.has(documentName)) {
        return;
      }
      if (typeof context?.memberId === 'number') {
        lastEditorByDocument.set(documentName, context.memberId);
      }
    },

    async onStoreDocument({ documentName, document, lastContext, instance }) {
      if (deletedDocuments.has(documentName)) {
        return;
      }

      const parsed = parseDocumentName(documentName);
      if (parsed.type === 'team') {
        // WS-03: team:{teamId} 방은 awareness 전용이라 DB에 저장하지 않는다.
        return;
      }
      const editorId = lastEditorByDocument.get(documentName) ?? lastContext?.memberId;
      if (typeof editorId !== 'number') {
        logger.warn(
          `마지막 수정자(editorId)를 알 수 없어 저장을 건너뜁니다. documentName=${documentName}`,
        );
        return;
      }

      const state = Y.encodeStateAsUpdate(document);
      const result = await springClient.saveDocumentContent(parsed.id, editorId, state);

      if (result.status === 'not_found') {
        deletedDocuments.add(documentName);
        lastEditorByDocument.delete(documentName);
        logger.warn(
          `문서가 삭제되어(404) 연결을 모두 종료하고 재저장을 중단합니다. documentName=${documentName}`,
        );
        instance.closeConnections(documentName);
        return;
      }

      if (result.status === 'error') {
        logger.error(
          `문서 저장 실패(다음 저장 때 재시도). documentName=${documentName}, editorId=${editorId}, reason=${result.reason}`,
        );
      }
    },

    async afterUnloadDocument({ documentName }) {
      lastEditorByDocument.delete(documentName);
      deletedDocuments.delete(documentName);
    },

    async onRequest({ request, response, instance }) {
      const pathname = new URL(request.url ?? '/', 'http://localhost').pathname;
      if (pathname !== DISCONNECT_PATH) {
        return;
      }

      if (request.method !== 'POST') {
        writeJson(response, 405, {
          success: false,
          data: null,
          error: {
            code: 'METHOD_NOT_ALLOWED',
            message: 'POST 요청만 허용됩니다',
          },
        });
        throw null;
      }

      const headerKey = readSingleHeader(request, 'x-sync-key');
      if (!isValidSyncKey(config.syncInternalKey, headerKey)) {
        const reason = !config.syncInternalKey
          ? '설정값 없음'
          : headerKey === undefined
            ? '헤더 없음'
            : '불일치';
        logger.warn(`연결 끊기 요청 키 검증 실패(401). reason=${reason}`);
        writeJson(response, 401, {
          success: false,
          data: null,
          error: {
            code: 'INVALID_INTERNAL_KEY',
            message: '내부 인증 키가 유효하지 않습니다',
          },
        });
        throw null;
      }

      const parsedBody = await parseDisconnectBody(request);
      if (!parsedBody) {
        writeJson(response, 400, {
          success: false,
          data: null,
          error: {
            code: 'INVALID_INPUT',
            message: 'teamId와 memberId가 올바르지 않습니다',
          },
        });
        throw null;
      }

      let closedCount = 0;
      for (const document of instance.documents.values()) {
        for (const connection of document.getConnections()) {
          const ctx = connection.context as Partial<SyncConnectionContext> | undefined;
          if (ctx?.teamId === parsedBody.teamId && ctx?.memberId === parsedBody.memberId) {
            connection.close({ code: 4403, reason: 'TEAM_MEMBER_REMOVED' });
            closedCount += 1;
          }
        }
      }

      logger.info(
        `팀원 연결 끊기 처리 완료. teamId=${parsedBody.teamId}, memberId=${parsedBody.memberId}, closedCount=${closedCount}`,
      );
      writeJson(response, 200, {
        success: true,
        data: null,
        error: null,
      });
      throw null;
    },
  };

  // WS-03 ⑤ 끊김 감지 시간:
  // - 정상 이탈(탭 닫기·라우트 이동·강퇴)은 WebSocket close + awareness null 전송으로 즉시 제거된다.
  // - 비정상 단절(네트워크 끊김 등)은 명세 10초와 달리 라이브러리 기본값(Hocuspocus timeout 60초,
  //   y-protocols awareness outdatedTimeout 30초)을 유지한다. 서버 timeout을 10초로 줄이면
  //   15초마다 awareness를 갱신하는 정상 유휴 클라이언트(doc:* 포함)까지 끊기기 때문이다.
  const server = new Server<SyncConnectionContext>({
    address: config.host,
    port: config.port,
    debounce: config.debounceMs,
    maxDebounce: config.maxDebounceMs,
    quiet: options.quiet ?? true,
    stopOnSignals: false,
    onAuthenticate: hooks.onAuthenticate,
    onLoadDocument: hooks.onLoadDocument,
    onChange: hooks.onChange,
    onStoreDocument: hooks.onStoreDocument,
    afterUnloadDocument: hooks.afterUnloadDocument,
    onRequest: hooks.onRequest,
  });

  return { server, hooks };
}

function readSingleHeader(request: IncomingMessage, name: string): string | undefined {
  const value = request.headers[name];
  if (typeof value === 'string') {
    return value;
  }
  if (Array.isArray(value)) {
    return value[0];
  }
  return undefined;
}

async function parseDisconnectBody(
  request: IncomingMessage,
): Promise<{ teamId: number; memberId: number } | null> {
  const chunks: Buffer[] = [];
  let totalBytes = 0;

  try {
    for await (const chunk of request) {
      const buf = typeof chunk === 'string' ? Buffer.from(chunk, 'utf8') : (chunk as Buffer);
      totalBytes += buf.length;
      if (totalBytes > MAX_DISCONNECT_BODY_BYTES) {
        return null;
      }
      chunks.push(buf);
    }
  } catch {
    return null;
  }

  let body: unknown;
  try {
    body = JSON.parse(Buffer.concat(chunks).toString('utf8'));
  } catch {
    return null;
  }

  if (!body || typeof body !== 'object') {
    return null;
  }

  const { teamId, memberId } = body as Record<string, unknown>;
  if (
    typeof teamId !== 'number' ||
    typeof memberId !== 'number' ||
    !Number.isSafeInteger(teamId) ||
    !Number.isSafeInteger(memberId) ||
    teamId <= 0 ||
    memberId <= 0
  ) {
    return null;
  }

  return { teamId, memberId };
}

function writeJson(response: ServerResponse, statusCode: number, payload: unknown): void {
  const json = JSON.stringify(payload);
  response.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(json),
  });
  response.end(json);
}
