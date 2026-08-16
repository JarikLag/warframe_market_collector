package com.warframemarket.collector.service;

import com.warframemarket.collector.api.ApiDtos;
import com.warframemarket.collector.api.ApiException;
import com.warframemarket.collector.api.WarframeMarketClient;
import com.warframemarket.collector.model.Category;
import com.warframemarket.collector.model.MarketItem;
import com.warframemarket.collector.model.PriceSnapshot;
import com.warframemarket.collector.store.StateStore;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs refresh jobs off the GUI thread.
 *
 * <p>A job first (optionally) re-downloads and re-filters the item catalogue, then asks
 * for each target item's top sell orders. The per-item lookups are spread over a small
 * worker pool purely to overlap network latency - the actual pacing comes from the
 * client's shared rate limiter, so throughput stays at the API budget of 3 requests per
 * second regardless of pool size.
 *
 * <p>Only one job runs at a time; {@link #cancel()} stops the current one promptly.
 */
public final class CollectorService implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(CollectorService.class.getName());

    /** Enough to keep the rate limiter saturated while some requests are slow. */
    private static final int WORKER_THREADS = 4;
    /** Checkpoint interval, so a 12-minute full refresh is never lost entirely. */
    private static final int SAVE_EVERY_N_ITEMS = 50;

    private final WarframeMarketClient client;
    private final StateStore store;
    private final MarketDatabase database;
    private final Executor callbackExecutor;
    private final List<CollectorListener> listeners = new CopyOnWriteArrayList<>();

    private final ExecutorService jobRunner = Executors.newSingleThreadExecutor(namedDaemonFactory("collector-job"));
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    private volatile ExecutorService workers;
    private volatile Thread jobThread;

    public CollectorService(WarframeMarketClient client,
                            StateStore store,
                            MarketDatabase database,
                            Executor callbackExecutor) {
        this.client = client;
        this.store = store;
        this.database = database;
        this.callbackExecutor = callbackExecutor;
    }

    public MarketDatabase database() {
        return database;
    }

    public void addListener(CollectorListener listener) {
        listeners.add(listener);
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * Queues a refresh.
     *
     * @return {@code false} if a job is already running, in which case nothing happens
     */
    public boolean start(UpdateRequest request) {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        cancelRequested.set(false);
        jobRunner.execute(() -> runJob(request));
        return true;
    }

    /** Asks the running job to stop; returns immediately. */
    public void cancel() {
        if (!running.get()) {
            return;
        }
        cancelRequested.set(true);
        ExecutorService current = workers;
        if (current != null) {
            current.shutdownNow();
        }
        Thread thread = jobThread;
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void runJob(UpdateRequest request) {
        jobThread = Thread.currentThread();
        String summary;
        boolean cancelled = false;
        Throwable failure = null;
        try {
            summary = execute(request);
        } catch (InterruptedException e) {
            Thread.interrupted(); // clear the flag; this thread is reused for later jobs
            cancelled = true;
            summary = "Update cancelled";
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Update failed", e);
            failure = e;
            summary = "Update failed: " + e.getMessage();
        } finally {
            jobThread = null;
            workers = null;
            saveQuietly();
            running.set(false);
        }
        if (cancelRequested.get()) {
            cancelled = true;
        }
        fireFinished(summary, cancelled, failure);
    }

    private String execute(UpdateRequest request) throws Exception {
        Instant startedAt = Instant.now();

        if (request.refreshCatalog() || database.isEmpty()) {
            fireProgress(0, 0, "Downloading item catalogue…");
            refreshCatalog();
            fireCatalogChanged();
        }
        throwIfCancelled();

        List<MarketItem> targets = resolveTargets(request);
        if (targets.isEmpty()) {
            return "Nothing to update";
        }

        int total = targets.size();
        AtomicInteger done = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        fireProgress(0, total, "Fetching orders for " + total + " items…");

        ExecutorService pool = Executors.newFixedThreadPool(WORKER_THREADS, namedDaemonFactory("collector-worker"));
        workers = pool;
        try {
            for (MarketItem item : targets) {
                pool.execute(() -> collectOne(item, done, failures, total, startedAt));
            }
            pool.shutdown();
            // Wait for the queue to drain. Cancellation arrives as shutdownNow() from
            // cancel(), which makes awaitTermination return once workers unwind.
            while (!pool.awaitTermination(1, TimeUnit.SECONDS)) {
                if (cancelRequested.get()) {
                    pool.shutdownNow();
                }
            }
        } finally {
            pool.shutdownNow();
            workers = null;
        }
        throwIfCancelled();

        saveQuietly();
        Duration elapsed = Duration.between(startedAt, Instant.now());
        int ok = done.get() - failures.get();
        return "Updated %d of %d items in %s%s".formatted(
                ok, total, formatDuration(elapsed),
                failures.get() > 0 ? " (" + failures.get() + " failed)" : "");
    }

    private void collectOne(MarketItem item, AtomicInteger done, AtomicInteger failures,
                            int total, Instant startedAt) {
        if (cancelRequested.get() || Thread.currentThread().isInterrupted()) {
            return;
        }
        PriceSnapshot snapshot;
        try {
            ApiDtos.TopOrders top = client.fetchTopOrders(item.slug());
            snapshot = summarise(top);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (ApiException e) {
            failures.incrementAndGet();
            snapshot = PriceSnapshot.failed(e.getMessage(), Instant.now());
        }

        database.putPrice(item.slug(), snapshot);
        fireItemUpdated(item.slug());

        int finished = done.incrementAndGet();
        if (finished % SAVE_EVERY_N_ITEMS == 0) {
            saveQuietly();
        }
        fireProgress(finished, total, progressMessage(item, finished, total, startedAt));
    }

    /**
     * Sorts the sampled sell orders by price and keeps both ends.
     *
     * <p>The API's "top" endpoint already returns the five cheapest visible sell orders;
     * sorting here makes the lowest/highest choice independent of the server's ordering.
     */
    static PriceSnapshot summarise(ApiDtos.TopOrders top) {
        List<Integer> prices = new ArrayList<>();
        List<ApiDtos.Order> sellOrders = top.sell() == null ? List.of() : top.sell();
        for (ApiDtos.Order order : sellOrders) {
            if (order == null || order.platinum() == null) {
                continue;
            }
            if (order.visible() != null && !order.visible()) {
                continue;
            }
            prices.add(order.platinum());
        }
        Instant now = Instant.now();
        if (prices.isEmpty()) {
            return PriceSnapshot.of(null, null, 0, now);
        }
        prices.sort(Integer::compare);
        return PriceSnapshot.of(prices.get(0), prices.get(prices.size() - 1), prices.size(), now);
    }

    /** Downloads every item, keeps only "mod" and "prime", and sorts them into groups. */
    private void refreshCatalog() throws ApiException, InterruptedException {
        List<ApiDtos.ApiItem> all = client.fetchAllItems();
        List<MarketItem> filtered = new ArrayList<>();
        for (ApiDtos.ApiItem apiItem : all) {
            if (apiItem.slug() == null) {
                continue;
            }
            Optional<Category> category = Category.fromTags(apiItem.tags());
            if (category.isEmpty()) {
                continue;
            }
            filtered.add(new MarketItem(
                    apiItem.id(),
                    apiItem.slug(),
                    apiItem.displayName(),
                    apiItem.tags() == null ? List.of() : List.copyOf(apiItem.tags()),
                    category.get()));
        }
        database.replaceCatalog(filtered, Instant.now());
        saveQuietly();
        LOG.log(Level.INFO, "Catalogue: {0} of {1} items kept after tag filter",
                new Object[] {filtered.size(), all.size()});
    }

    private List<MarketItem> resolveTargets(UpdateRequest request) {
        return switch (request.scope()) {
            case ALL -> database.items();
            case CATEGORY -> database.itemsIn(request.category());
            case ITEMS -> request.slugs().stream()
                    .map(database::findBySlug)
                    .flatMap(Optional::stream)
                    .toList();
        };
    }

    private String progressMessage(MarketItem item, int finished, int total, Instant startedAt) {
        String eta = "";
        long elapsedMillis = Duration.between(startedAt, Instant.now()).toMillis();
        if (finished > 3 && elapsedMillis > 0) {
            long remaining = (long) ((double) elapsedMillis / finished * (total - finished));
            eta = " · ~" + formatDuration(Duration.ofMillis(remaining)) + " left";
        }
        return "%d / %d · %s%s".formatted(finished, total, item.name(), eta);
    }

    private static String formatDuration(Duration duration) {
        long seconds = Math.max(0, duration.toSeconds());
        if (seconds < 60) {
            return seconds + " s";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return "%d min %02d s".formatted(minutes, seconds % 60);
        }
        return "%d h %02d min".formatted(minutes / 60, minutes % 60);
    }

    private void throwIfCancelled() throws InterruptedException {
        if (cancelRequested.get()) {
            throw new InterruptedException("cancelled");
        }
    }

    /** Persists the current state; failures are logged but never abort a refresh. */
    public void saveQuietly() {
        try {
            store.save(database.toPersistedState());
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Could not save local data to " + store.file(), e);
        }
    }

    private void fireCatalogChanged() {
        for (CollectorListener l : listeners) {
            callbackExecutor.execute(l::onCatalogChanged);
        }
    }

    private void fireItemUpdated(String slug) {
        for (CollectorListener l : listeners) {
            callbackExecutor.execute(() -> l.onItemUpdated(slug));
        }
    }

    private void fireProgress(int done, int total, String message) {
        for (CollectorListener l : listeners) {
            callbackExecutor.execute(() -> l.onProgress(done, total, message));
        }
    }

    private void fireFinished(String summary, boolean cancelled, Throwable failure) {
        for (CollectorListener l : listeners) {
            callbackExecutor.execute(() -> l.onFinished(summary, cancelled, failure));
        }
    }

    private static ThreadFactory namedDaemonFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    @Override
    public void close() {
        cancel();
        jobRunner.shutdownNow();
        client.close();
    }
}
