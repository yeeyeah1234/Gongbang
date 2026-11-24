import { loadConfig } from './config.js';
import { createSyncServer } from './server.js';

async function main(): Promise<void> {
  const config = loadConfig();
  const { server } = createSyncServer({ config, quiet: true });

  let shuttingDown = false;
  const shutdown = async (signal: NodeJS.Signals): Promise<void> => {
    if (shuttingDown) {
      return;
    }
    shuttingDown = true;
    console.log(`[sync-server] ${signal} 수신: 대기 중인 저장을 마친 뒤 종료합니다.`);

    const forceExitTimer = setTimeout(() => {
      console.error('[sync-server] 종료 제한 시간(30초) 초과로 강제 종료합니다.');
      process.exit(1);
    }, config.shutdownTimeoutMs);
    forceExitTimer.unref();

    try {
      await server.destroy();
      clearTimeout(forceExitTimer);
      console.log('[sync-server] 정상 종료 완료.');
      process.exit(0);
    } catch (error) {
      clearTimeout(forceExitTimer);
      const reason = error instanceof Error ? error.name : 'UnknownError';
      console.error(`[sync-server] 종료 중 오류 발생: ${reason}`);
      process.exit(1);
    }
  };

  process.on('SIGTERM', () => {
    void shutdown('SIGTERM');
  });
  process.on('SIGINT', () => {
    void shutdown('SIGINT');
  });

  await server.listen();
  console.log(
    `[sync-server] 시작 완료. host=${config.host}, port=${config.port}, springBaseUrl=${config.springBaseUrl}, env=${config.nodeEnv}`,
  );
}

main().catch((error: unknown) => {
  const message = error instanceof Error ? error.message : 'UnknownError';
  console.error(`[sync-server] 시작 실패: ${message}`);
  process.exit(1);
});
