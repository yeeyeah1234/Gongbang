package com.gongbang.post.service;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * PST-002 ⑤: 모집글 등록이 커밋된 뒤 외부 공모전 링크의 대표 이미지(og:image)를 불러와 저장한다.
 *
 * <p>외부 사이트가 느려도 등록 응답이 늦어지지 않게 이 클래스의 작은 스레드 풀에서 돈다. 앱 전체 설정(@EnableAsync, 공용 실행기)은 건드리지 않는다. 일이
 * 밀려 대기열이 차면 그 글은 건너뛰고 기본 이미지를 쓴다.
 */
@Slf4j
@Component
public class ExternalContestImageListener {

    private static final int QUEUE_SIZE = 50;

    private final OgImageFetcher ogImageFetcher;
    private final PostCommandService postCommandService;
    private final ExecutorService executor = newExecutor();

    public ExternalContestImageListener(
            OgImageFetcher ogImageFetcher, PostCommandService postCommandService) {
        this.ogImageFetcher = ogImageFetcher;
        this.postCommandService = postCommandService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLinked(ExternalContestLinkedEvent event) {
        executor.execute(() -> load(event));
    }

    void load(ExternalContestLinkedEvent event) {
        try {
            ogImageFetcher
                    .fetch(event.url())
                    .ifPresent(
                            imageUrl ->
                                    postCommandService.saveExternalImage(
                                            event.postId(), event.url(), imageUrl));
        } catch (RuntimeException e) {
            // 못 불러와도 화면은 기본 이미지를 쓰므로 등록에는 영향이 없다
            log.warn("모집글 {} 대표 이미지 저장 실패", event.postId(), e);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private static ExecutorService newExecutor() {
        AtomicInteger number = new AtomicInteger();
        return new ThreadPoolExecutor(
                1,
                2,
                60,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(QUEUE_SIZE),
                task -> {
                    Thread thread = new Thread(task, "og-image-" + number.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.DiscardPolicy());
    }
}
