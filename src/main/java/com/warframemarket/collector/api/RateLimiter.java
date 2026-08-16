package com.warframemarket.collector.api;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Sliding-window rate limiter shared by every thread that talks to the API.
 *
 * <p>It records the instant of each granted permit and never lets more than
 * {@code permits} grants fall inside any window of {@code window} length. Unlike a
 * simple "sleep 1/3 s between calls" delay, this holds even when several worker
 * threads acquire concurrently and when individual requests take wildly different
 * amounts of time.
 */
public final class RateLimiter {

    private final int permits;
    private final long windowNanos;
    private final Deque<Long> grants = new ArrayDeque<>();
    private final ReentrantLock lock = new ReentrantLock(true);
    private final Condition slotFreed = lock.newCondition();

    public RateLimiter(int permits, Duration window) {
        if (permits < 1) {
            throw new IllegalArgumentException("permits must be >= 1, was " + permits);
        }
        if (window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("window must be positive, was " + window);
        }
        this.permits = permits;
        this.windowNanos = window.toNanos();
    }

    /** Blocks until a permit is available, then consumes it. */
    public void acquire() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (true) {
                long now = System.nanoTime();
                // Drop grants that have aged out of the window.
                while (!grants.isEmpty() && now - grants.peekFirst() >= windowNanos) {
                    grants.pollFirst();
                }
                if (grants.size() < permits) {
                    grants.addLast(now);
                    slotFreed.signalAll();
                    return;
                }
                long waitNanos = windowNanos - (now - grants.peekFirst());
                if (waitNanos > 0) {
                    // Releases the lock while waiting, so acquire() stays interruptible
                    // and other threads can make progress; the loop re-checks on wake-up.
                    slotFreed.awaitNanos(waitNanos);
                }
            }
        } finally {
            lock.unlock();
        }
    }
}
