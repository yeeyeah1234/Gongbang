import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import type { Connection, Document, Hocuspocus } from '@hocuspocus/server';
import * as Y from 'yjs';
import { loadConfig, type SyncServerConfig } from '../src/config.js';
import { InvalidDocumentNameError } from '../src/documentName.js';
import {
  createSyncServer,
  type SyncConnectionContext,
  type SyncLogger,
} from '../src/server.js';
import {
  SpringAccessError,
  SpringLoadError,
  type FetchLike,
} from '../src/springClient.js';

const TEST_KEY = 'secret-sync-key-9a8b7c';
const TEST_TOKEN = 'eyJhbGciOiJIUzI1NiJ9.secret-jwt-payload.sig';

function createTestConfig(
  overrides: Partial<SyncServerConfig> = {},
): SyncServerConfig {
  return {
    host: '127.0.0.1',
    port: 0,
    springBaseUrl: 'http://127.0.0.1:8090',
    syncInternalKey: TEST_KEY,
    nodeEnv: 'test',
    debounceMs: 3_000,
    maxDebounceMs: 10_000,
    requestTimeoutMs: 5_000,
    saveMaxAttempts: 1,
    saveRetryDelayMs: 0,
    shutdownTimeoutMs: 30_000,
    ...overrides,
  };
}

function createMemoryLogger(): { logger: SyncLogger; logs: string[] } {
  const logs: string[] = [];
  return {
    logs,
    logger: {
      info: (msg) => logs.push(`INFO: ${msg}`),
      warn: (msg) => logs.push(`WARN: ${msg}`),
      error: (msg) => logs.push(`ERROR: ${msg}`),
    },
  };
}

function createFakeHocuspocusInstance(
  closedDocuments: string[],
): Hocuspocus<SyncConnectionContext> {
  return {
    closeConnections(documentName?: string) {
      if (documentName) {
        closedDocuments.push(documentName);
      }
    },
  } as unknown as Hocuspocus<SyncConnectionContext>;
}

describe('환경변수 설정 (loadConfig)', () => {
  it('SYNC_INTERNAL_KEY가 없으면 시작을 거절한다', () => {
    assert.throws(() => loadConfig({}), /SYNC_INTERNAL_KEY/);
  });

  it('기본값이 HOST=127.0.0.1, PORT=1234, SPRING_BASE_URL=http://127.0.0.1:8090, debounce 3s/10s다', () => {
    const config = loadConfig({ SYNC_INTERNAL_KEY: TEST_KEY });
    assert.equal(config.host, '127.0.0.1');
    assert.equal(config.port, 1234);
    assert.equal(config.springBaseUrl, 'http://127.0.0.1:8090');
    assert.equal(config.debounceMs, 3_000);
    assert.equal(config.maxDebounceMs, 10_000);
  });
});

describe('onAuthenticate 권한 확인 (DOC-08)', () => {
  it('200이면 memberId·nickname·teamId를 context로 돌려주고 Authorization과 X-Sync-Key를 보낸다', async () => {
    let calledUrl = '';
    let calledMethod = '';
    let authHeader = '';
    let syncKeyHeader = '';

    const fakeFetch: FetchLike = async (input, init) => {
      calledUrl = String(input);
      calledMethod = init?.method ?? 'GET';
      const headers = new Headers(init?.headers);
      authHeader = headers.get('authorization') ?? '';
      syncKeyHeader = headers.get('x-sync-key') ?? '';

      return new Response(
        JSON.stringify({
          success: true,
          data: { memberId: 7, nickname: '상현', teamId: 3, leader: false },
          error: null,
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      );
    };

    const { hooks } = createSyncServer({
      config: createTestConfig(),
      fetchFn: fakeFetch,
    });

    const context = await hooks.onAuthenticate({
      documentName: 'doc:500',
      token: TEST_TOKEN,
    });

    assert.equal(calledMethod, 'GET');
    assert.equal(calledUrl, 'http://127.0.0.1:8090/internal/documents/500/access');
    assert.equal(authHeader, `Bearer ${TEST_TOKEN}`);
    assert.equal(syncKeyHeader, TEST_KEY);
    assert.deepEqual(context, {
      memberId: 7,
      nickname: '상현',
      teamId: 3,
    });
  });

  for (const status of [401, 403, 404] as const) {
    it(`Spring이 ${status}를 돌려주면 연결을 거절하고 키·토큰을 오류에 남기지 않는다`, async () => {
      const fakeFetch: FetchLike = async () =>
        new Response(
          JSON.stringify({
            success: false,
            data: null,
            error: { code: 'ERR', message: '거절' },
          }),
          { status, headers: { 'Content-Type': 'application/json' } },
        );

      const { hooks } = createSyncServer({
        config: createTestConfig(),
        fetchFn: fakeFetch,
      });

      await assert.rejects(
        () => hooks.onAuthenticate({ documentName: 'doc:500', token: TEST_TOKEN }),
        (err: unknown) => {
          assert.ok(err instanceof SpringAccessError);
          assert.equal(err.status, status);
          assert.ok(!err.message.includes(TEST_KEY));
          assert.ok(!err.message.includes(TEST_TOKEN));
          return true;
        },
      );
    });
  }

  it('알 수 없는 문서 이름은 Spring을 부르지 않고 즉시 거절한다', async () => {
    let fetchCalled = false;
    const fakeFetch: FetchLike = async () => {
      fetchCalled = true;
      return new Response(null, { status: 200 });
    };

    const { hooks } = createSyncServer({
      config: createTestConfig(),
      fetchFn: fakeFetch,
    });

    await assert.rejects(
      () => hooks.onAuthenticate({ documentName: 'board:10', token: TEST_TOKEN }),
      InvalidDocumentNameError,
    );
    assert.equal(fetchCalled, false);
  });
});

describe('onLoadDocument 본문 불러오기 (DOC-08)', () => {
  it('200 바이트 응답이면 Y.applyUpdate로 문서 내용을 복원한다', async () => {
    const sourceDoc = new Y.Doc();
    sourceDoc.getText('content').insert(0, '공방 문서 본문');
    const savedBytes = Y.encodeStateAsUpdate(sourceDoc);

    let calledUrl = '';
    let syncKeyHeader = '';
    const fakeFetch: FetchLike = async (input, init) => {
      calledUrl = String(input);
      syncKeyHeader = new Headers(init?.headers).get('x-sync-key') ?? '';
      return new Response(Buffer.from(savedBytes), {
        status: 200,
        headers: { 'Content-Type': 'application/octet-stream' },
      });
    };

    const { hooks } = createSyncServer({
      config: createTestConfig(),
      fetchFn: fakeFetch,
    });

    const targetDoc = new Y.Doc() as unknown as Document;
    await hooks.onLoadDocument({ documentName: 'doc:500', document: targetDoc });

    assert.equal(calledUrl, 'http://127.0.0.1:8090/internal/documents/500/content');
    assert.equal(syncKeyHeader, TEST_KEY);
    assert.equal(targetDoc.getText('content').toString(), '공방 문서 본문');
  });

  it('204 응답이면 빈 문서로 연다', async () => {
    const fakeFetch: FetchLike = async () => new Response(null, { status: 204 });

    const { hooks } = createSyncServer({
      config: createTestConfig(),
      fetchFn: fakeFetch,
    });

    const targetDoc = new Y.Doc() as unknown as Document;
    await hooks.onLoadDocument({ documentName: 'doc:500', document: targetDoc });

    assert.equal(targetDoc.getText('content').toString(), '');
  });

  it('500 오류면 빈 문서로 열지 않고 반드시 연결을 거절하며 키는 로그에 남기지 않는다', async () => {
    const { logger, logs } = createMemoryLogger();
    const fakeFetch: FetchLike = async () =>
      new Response('Internal Server Error', { status: 500 });

    const { hooks } = createSyncServer({
      config: createTestConfig(),
      fetchFn: fakeFetch,
      logger,
    });

    const targetDoc = new Y.Doc() as unknown as Document;
    await assert.rejects(
      () => hooks.onLoadDocument({ documentName: 'doc:500', document: targetDoc }),
      (err: unknown) => {
        assert.ok(err instanceof SpringLoadError);
        assert.equal(err.status, 500);
        assert.ok(!err.message.includes(TEST_KEY));
        return true;
      },
    );

    assert.ok(logs.length > 0);
    for (const line of logs) {
      assert.ok(!line.includes(TEST_KEY));
    }
  });

  it('네트워크 연결 실패 시에도 반드시 연결을 거절한다', async () => {
    const { logger } = createMemoryLogger();
    const fakeFetch: FetchLike = async () => {
      throw new TypeError('fetch failed');
    };

    const { hooks } = createSyncServer({
      config: createTestConfig(),
      fetchFn: fakeFetch,
      logger,
    });

    const targetDoc = new Y.Doc() as unknown as Document;
    await assert.rejects(
      () => hooks.onLoadDocument({ documentName: 'doc:500', document: targetDoc }),
      SpringLoadError,
    );
  });
});

describe('onChange · onStoreDocument 자동 저장 (DOC-10)', () => {
  it('마지막으로 문서를 수정한 연결의 memberId를 editorId로 전달하고 Yjs 상태를 바이트로 저장한다', async () => {
    let calledUrl = '';
    let calledMethod = '';
    let contentType = '';
    let syncKeyHeader = '';
    let receivedBody: Uint8Array | null = null;

    const fakeFetch: FetchLike = async (input, init) => {
      calledUrl = String(input);
      calledMethod = init?.method ?? '';
      const headers = new Headers(init?.headers);
      contentType = headers.get('content-type') ?? '';
      syncKeyHeader = headers.get('x-sync-key') ?? '';
      receivedBody =
        init?.body instanceof Uint8Array
          ? new Uint8Array(init.body)
          : new Uint8Array(await new Response(init?.body).arrayBuffer());

      return new Response(
        JSON.stringify({ success: true, data: null, error: null }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      );
    };

    const { hooks } = createSyncServer({
      config: createTestConfig(),
      fetchFn: fakeFetch,
    });

    const doc = new Y.Doc() as unknown as Document;
    doc.getText('content').insert(0, '함께 쓰는 문서');

    await hooks.onChange({
      documentName: 'doc:500',
      context: { memberId: 7, nickname: '상현', teamId: 3 },
    });
    await hooks.onChange({
      documentName: 'doc:500',
      context: { memberId: 12, nickname: '건우', teamId: 3 },
    });

    const closedDocuments: string[] = [];
    await hooks.onStoreDocument({
      documentName: 'doc:500',
      document: doc,
      lastContext: { memberId: 7, nickname: '상현', teamId: 3 },
      instance: createFakeHocuspocusInstance(closedDocuments),
    });

    assert.equal(calledMethod, 'PUT');
    assert.equal(
      calledUrl,
      'http://127.0.0.1:8090/internal/documents/500/content?editorId=12',
    );
    assert.equal(contentType, 'application/octet-stream');
    assert.equal(syncKeyHeader, TEST_KEY);
    assert.equal(closedDocuments.length, 0);

    const capturedBody = receivedBody as Uint8Array | null;
    assert.ok(capturedBody !== null);
    const restored = new Y.Doc();
    Y.applyUpdate(restored, capturedBody);
    assert.equal(restored.getText('content').toString(), '함께 쓰는 문서');
  });

  it('저장 응답이 404(삭제됨)이면 해당 문서 연결을 모두 끊고 이후 다시 저장하지 않는다', async () => {
    let putCount = 0;
    const fakeFetch: FetchLike = async () => {
      putCount += 1;
      return new Response(
        JSON.stringify({
          success: false,
          data: null,
          error: { code: 'RESOURCE_NOT_FOUND', message: '없음' },
        }),
        { status: 404, headers: { 'Content-Type': 'application/json' } },
      );
    };

    const { logger } = createMemoryLogger();
    const { hooks } = createSyncServer({
      config: createTestConfig({ saveMaxAttempts: 3 }),
      fetchFn: fakeFetch,
      logger,
    });

    const doc = new Y.Doc() as unknown as Document;
    const closedDocuments: string[] = [];
    const fakeInstance = createFakeHocuspocusInstance(closedDocuments);

    await hooks.onChange({
      documentName: 'doc:500',
      context: { memberId: 7, nickname: '상현', teamId: 3 },
    });
    await hooks.onStoreDocument({
      documentName: 'doc:500',
      document: doc,
      lastContext: { memberId: 7, nickname: '상현', teamId: 3 },
      instance: fakeInstance,
    });

    assert.equal(putCount, 1);
    assert.deepEqual(closedDocuments, ['doc:500']);

    // 404 이후 다시 onChange·onStoreDocument가 호출돼도 재저장하지 않아야 한다
    await hooks.onChange({
      documentName: 'doc:500',
      context: { memberId: 7, nickname: '상현', teamId: 3 },
    });
    await hooks.onStoreDocument({
      documentName: 'doc:500',
      document: doc,
      lastContext: { memberId: 7, nickname: '상현', teamId: 3 },
      instance: fakeInstance,
    });

    assert.equal(putCount, 1);
  });

  it('500 실패 시 연결을 끊지 않고 로그를 남긴 뒤 다음 저장 때 다시 시도한다', async () => {
    let attempt = 0;
    const fakeFetch: FetchLike = async () => {
      attempt += 1;
      if (attempt === 1) {
        return new Response('Server Error', { status: 500 });
      }
      return new Response(
        JSON.stringify({ success: true, data: null, error: null }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      );
    };

    const { logger, logs } = createMemoryLogger();
    const { hooks } = createSyncServer({
      config: createTestConfig({ saveMaxAttempts: 1 }),
      fetchFn: fakeFetch,
      logger,
    });

    const doc = new Y.Doc() as unknown as Document;
    const closedDocuments: string[] = [];
    const fakeInstance = createFakeHocuspocusInstance(closedDocuments);

    await hooks.onChange({
      documentName: 'doc:500',
      context: { memberId: 7, nickname: '상현', teamId: 3 },
    });

    // 1차 저장: 500 실패 (예외 미전파, 연결 유지)
    await hooks.onStoreDocument({
      documentName: 'doc:500',
      document: doc,
      lastContext: { memberId: 7, nickname: '상현', teamId: 3 },
      instance: fakeInstance,
    });

    assert.equal(closedDocuments.length, 0);
    assert.equal(logs.length, 1);
    assert.ok(!logs[0]!.includes(TEST_KEY));

    // 2차 저장: 재시도 성공
    await hooks.onStoreDocument({
      documentName: 'doc:500',
      document: doc,
      lastContext: { memberId: 7, nickname: '상현', teamId: 3 },
      instance: fakeInstance,
    });

    assert.equal(attempt, 2);
    assert.equal(closedDocuments.length, 0);
  });

  it('일시적 500 후 재시도 횟수 안에 200이 오면 저장을 완료한다', async () => {
    let calls = 0;
    const fakeFetch: FetchLike = async () => {
      calls += 1;
      if (calls < 3) {
        return new Response('Temporary Error', { status: 503 });
      }
      return new Response(
        JSON.stringify({ success: true, data: null, error: null }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      );
    };

    const { logger, logs } = createMemoryLogger();
    const { hooks } = createSyncServer({
      config: createTestConfig({ saveMaxAttempts: 3, saveRetryDelayMs: 0 }),
      fetchFn: fakeFetch,
      logger,
    });

    const doc = new Y.Doc() as unknown as Document;
    await hooks.onChange({
      documentName: 'doc:500',
      context: { memberId: 7, nickname: '상현', teamId: 3 },
    });
    await hooks.onStoreDocument({
      documentName: 'doc:500',
      document: doc,
      lastContext: { memberId: 7, nickname: '상현', teamId: 3 },
      instance: createFakeHocuspocusInstance([]),
    });

    assert.equal(calls, 3);
    assert.equal(logs.length, 0);
  });
});

describe('POST /internal/disconnect 팀원 연결 끊기 (WS-06)', () => {
  it('X-Sync-Key가 없거나 틀리면 401 INVALID_INTERNAL_KEY를 반환하고 키를 로그에 남기지 않는다', async () => {
    const { logger, logs } = createMemoryLogger();
    const { server } = createSyncServer({
      config: createTestConfig({ port: 0 }),
      logger,
      quiet: true,
    });

    await server.listen();
    try {
      const baseUrl = server.httpURL;
      const wrongKey = 'wrong-sync-key-1111';

      const resNoKey = await fetch(`${baseUrl}/internal/disconnect`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ teamId: 3, memberId: 7 }),
      });
      assert.equal(resNoKey.status, 401);
      const bodyNoKey = (await resNoKey.json()) as {
        success: boolean;
        error: { code: string };
      };
      assert.equal(bodyNoKey.success, false);
      assert.equal(bodyNoKey.error.code, 'INVALID_INTERNAL_KEY');

      const resWrongKey = await fetch(`${baseUrl}/internal/disconnect`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Sync-Key': wrongKey,
        },
        body: JSON.stringify({ teamId: 3, memberId: 7 }),
      });
      assert.equal(resWrongKey.status, 401);

      for (const line of logs) {
        assert.ok(!line.includes(TEST_KEY));
        assert.ok(!line.includes(wrongKey));
      }
    } finally {
      await server.destroy();
    }
  });

  it('키가 맞으면 해당 (teamId, memberId) 연결만 끊고 다른 연결은 유지한다', async () => {
    const { logger } = createMemoryLogger();
    const { server } = createSyncServer({
      config: createTestConfig({ port: 0 }),
      logger,
      quiet: true,
    });

    const closedIds: string[] = [];
    const makeFakeConnection = (
      id: string,
      context: SyncConnectionContext,
    ): Connection<SyncConnectionContext> =>
      ({
        context,
        close() {
          closedIds.push(id);
        },
      }) as unknown as Connection<SyncConnectionContext>;

    const targetConn = makeFakeConnection('target', {
      teamId: 3,
      memberId: 7,
      nickname: '상현',
    });
    const otherMemberConn = makeFakeConnection('otherMember', {
      teamId: 3,
      memberId: 8,
      nickname: '건우',
    });
    const otherTeamConn = makeFakeConnection('otherTeam', {
      teamId: 4,
      memberId: 7,
      nickname: '상현',
    });

    const fakeDoc = {
      getConnections: () => [targetConn, otherMemberConn, otherTeamConn],
    } as unknown as Document;

    const targetTeamConn = makeFakeConnection('targetTeam', {
      teamId: 3,
      memberId: 7,
      nickname: '상현',
      leader: false,
      colorIndex: 2,
    });
    const fakeTeamDoc = {
      getConnections: () => [targetTeamConn],
    } as unknown as Document;

    server.hocuspocus.documents.set('doc:500', fakeDoc);
    server.hocuspocus.documents.set('team:3', fakeTeamDoc);

    await server.listen();
    try {
      const res = await fetch(`${server.httpURL}/internal/disconnect`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Sync-Key': TEST_KEY,
        },
        body: JSON.stringify({ teamId: 3, memberId: 7 }),
      });

      assert.equal(res.status, 200);
      const body = (await res.json()) as { success: boolean };
      assert.equal(body.success, true);
      assert.deepEqual(closedIds, ['target', 'targetTeam']);
    } finally {
      server.hocuspocus.documents.clear();
      await server.destroy();
    }
  });
});

describe('team:{teamId} 워크스페이스 접속자 연결 (WS-03)', () => {
  it('onAuthenticate: 200이면 /internal/teams/{teamId}/access를 호출하고 leader·colorIndex를 포함한 context를 돌려준다', async () => {
    let calledUrl = '';
    let calledMethod = '';
    let authHeader = '';
    let syncKeyHeader = '';

    const fakeFetch: FetchLike = async (input, init) => {
      calledUrl = String(input);
      calledMethod = init?.method ?? 'GET';
      const headers = new Headers(init?.headers);
      authHeader = headers.get('authorization') ?? '';
      syncKeyHeader = headers.get('x-sync-key') ?? '';

      return new Response(
        JSON.stringify({
          success: true,
          data: { memberId: 7, nickname: '상현', teamId: 3, leader: true, colorIndex: 1 },
          error: null,
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      );
    };

    const { hooks } = createSyncServer({
      config: createTestConfig(),
      fetchFn: fakeFetch,
    });

    const context = await hooks.onAuthenticate({
      documentName: 'team:3',
      token: TEST_TOKEN,
    });

    assert.equal(calledMethod, 'GET');
    assert.equal(calledUrl, 'http://127.0.0.1:8090/internal/teams/3/access');
    assert.equal(authHeader, `Bearer ${TEST_TOKEN}`);
    assert.equal(syncKeyHeader, TEST_KEY);
    assert.deepEqual(context, {
      memberId: 7,
      nickname: '상현',
      teamId: 3,
      leader: true,
      colorIndex: 1,
    });
  });

  for (const status of [401, 403, 404] as const) {
    it(`onAuthenticate: Spring이 ${status}를 돌려주면 연결을 거절하고 키·토큰을 오류에 남기지 않는다`, async () => {
      const fakeFetch: FetchLike = async () =>
        new Response(
          JSON.stringify({
            success: false,
            data: null,
            error: { code: 'ERR', message: '거절' },
          }),
          { status, headers: { 'Content-Type': 'application/json' } },
        );

      const { hooks } = createSyncServer({
        config: createTestConfig(),
        fetchFn: fakeFetch,
      });

      await assert.rejects(
        () => hooks.onAuthenticate({ documentName: 'team:3', token: TEST_TOKEN }),
        (err: unknown) => {
          assert.ok(err instanceof SpringAccessError);
          assert.equal(err.status, status);
          assert.ok(!err.message.includes(TEST_KEY));
          assert.ok(!err.message.includes(TEST_TOKEN));
          return true;
        },
      );
    });
  }

  it('onLoadDocument · onChange · onStoreDocument: team 연결은 Spring 불러오기·저장 API를 호출하지 않는다', async () => {
    let fetchCount = 0;
    const fakeFetch: FetchLike = async () => {
      fetchCount += 1;
      return new Response(null, { status: 500 });
    };

    const { hooks } = createSyncServer({
      config: createTestConfig(),
      fetchFn: fakeFetch,
    });

    const doc = new Y.Doc() as unknown as Document;
    await hooks.onLoadDocument({ documentName: 'team:3', document: doc });
    await hooks.onChange({
      documentName: 'team:3',
      context: { memberId: 7, nickname: '상현', teamId: 3, leader: false, colorIndex: 2 },
    });
    await hooks.onStoreDocument({
      documentName: 'team:3',
      document: doc,
      lastContext: { memberId: 7, nickname: '상현', teamId: 3, leader: false, colorIndex: 2 },
      instance: createFakeHocuspocusInstance([]),
    });

    assert.equal(fetchCount, 0);
    assert.equal(doc.getText('content').toString(), '');
  });
});
