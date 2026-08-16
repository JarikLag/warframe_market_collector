package com.warframemarket.collector.api;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    /**
     * The invariant that actually matters: with N permits per window, the (i+N)-th grant
     * must not happen sooner than one window after the i-th.
     */
    @Test
    void neverGrantsMorePermitsThanTheWindowAllows() throws Exception {
        Duration window = Duration.ofMillis(300);
        RateLimiter limiter = new RateLimiter(3, window);

        List<Long> grants = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            limiter.acquire();
            grants.add(System.nanoTime());
        }

        assertWindowRespected(grants, 3, window);
    }

    @Test
    void holdsAcrossConcurrentCallers() throws Exception {
        Duration window = Duration.ofMillis(300);
        RateLimiter limiter = new RateLimiter(3, window);
        List<Long> grants = Collections.synchronizedList(new ArrayList<>());

        int callers = 6;
        int perCaller = 3;
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            for (int i = 0; i < callers; i++) {
                pool.execute(() -> {
                    try {
                        ready.countDown();
                        go.await();
                        for (int n = 0; n < perCaller; n++) {
                            limiter.acquire();
                            grants.add(System.nanoTime());
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            ready.await();
            go.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "workers did not finish in time");
        } finally {
            pool.shutdownNow();
        }

        List<Long> sorted = new ArrayList<>(grants);
        Collections.sort(sorted);
        assertWindowRespected(sorted, 3, window);
    }

    private static void assertWindowRespected(List<Long> grantNanos, int permits, Duration window) {
        long windowNanos = window.toNanos();
        // Timers are not perfectly precise; allow a small slack.
        long slackNanos = Duration.ofMillis(20).toNanos();
        for (int i = 0; i + permits < grantNanos.size(); i++) {
            long delta = grantNanos.get(i + permits) - grantNanos.get(i);
            assertTrue(delta >= windowNanos - slackNanos,
                    "grants %d and %d were only %d ms apart, window is %d ms"
                            .formatted(i, i + permits, delta / 1_000_000, window.toMillis()));
        }
    }
}
