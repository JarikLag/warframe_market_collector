package com.warframemarket.collector.api;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thin, thread-safe client for the warframe.market v2 API.
 *
 * <p>Every outbound request passes through a shared {@link RateLimiter}, so the
 * 3-requests-per-second budget holds no matter how many worker threads call in
 * parallel. Retries (including the ones triggered by HTTP 429) go through the
 * limiter again and therefore cannot burst past the budget either.
 */
public final class WarframeMarketClient implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(WarframeMarketClient.class.getName());

    private static final String BASE_URL = "https://api.warframe.market/v2";
    private static final String USER_AGENT = "WarframeMarketCollectorByJarikLag/1.0 (+https://github.com/)";

    /** Hard API budget. */
    public static final int MAX_REQUESTS_PER_SECOND = 3;
    /**
     * Slightly wider than one second so clock granularity, or the server measuring the
     * window from when it *receives* a request rather than when we send it, cannot push
     * us over three per second.
     */
    private static final Duration RATE_WINDOW = Duration.ofMillis(1100);

    private static final int MAX_ATTEMPTS = 4;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final RateLimiter limiter;
    private final String platform;
    private final String language;

    public WarframeMarketClient() {
        this("pc", "en");
    }

    public WarframeMarketClient(String platform, String language) {
        this.platform = platform;
        this.language = language;
        this.limiter = new RateLimiter(MAX_REQUESTS_PER_SECOND, RATE_WINDOW);
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.mapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    /** The complete item catalogue, including the {@code tags} used for filtering. */
    public List<ApiDtos.ApiItem> fetchAllItems() throws ApiException, InterruptedException {
        ApiDtos.ItemsResponse response = get("/items", ApiDtos.ItemsResponse.class);
        if (response.data() == null) {
            throw new ApiException("/items returned no data", -1);
        }
        return response.data();
    }

    /**
     * The five best sell orders and five best buy orders for one item.
     * This is one request per item - the dominant cost of a full refresh.
     */
    public ApiDtos.TopOrders fetchTopOrders(String slug) throws ApiException, InterruptedException {
        ApiDtos.TopOrdersResponse response =
                get("/orders/item/" + slug + "/top", ApiDtos.TopOrdersResponse.class);
        if (response.data() == null) {
            throw new ApiException("No order data for '" + slug + "'", -1);
        }
        return response.data();
    }

    private <T> T get(String path, Class<T> type) throws ApiException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + path))
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .header("Platform", platform)
                .header("Language", language)
                .header("Crossplay", Boolean.TRUE.toString())
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

        ApiException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            limiter.acquire();
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();

                if (status >= 200 && status < 300) {
                    try {
                        return mapper.readValue(response.body(), type);
                    } catch (IOException e) {
                        throw new ApiException("Malformed JSON from " + path + ": " + e.getMessage(), e);
                    }
                }
                if (status == 404) {
                    // Item withdrawn from the market - retrying will not help.
                    throw new ApiException("Not found: " + path, 404);
                }
                if (status == 429 || status >= 500) {
                    lastFailure = new ApiException("HTTP " + status + " from " + path, status);
                    backOff(response, attempt);
                    continue;
                }
                throw new ApiException("HTTP " + status + " from " + path, status);
            } catch (IOException e) {
                lastFailure = new ApiException("Request to " + path + " failed: " + e.getMessage(), e);
                backOff(null, attempt);
            }
        }
        throw lastFailure != null
                ? lastFailure
                : new ApiException("Request to " + path + " failed after " + MAX_ATTEMPTS + " attempts", -1);
    }

    /** Honours {@code Retry-After} when present, otherwise backs off exponentially. */
    private void backOff(HttpResponse<String> response, int attempt) throws InterruptedException {
        long millis = 500L * (1L << (attempt - 1));
        if (response != null) {
            Optional<String> retryAfter = response.headers().firstValue("Retry-After");
            if (retryAfter.isPresent()) {
                try {
                    millis = Math.max(millis, Long.parseLong(retryAfter.get().trim()) * 1000L);
                } catch (NumberFormatException ignored) {
                    // Retry-After may also be an HTTP date; the exponential default covers that.
                }
            }
        }
        LOG.log(Level.FINE, "Backing off {0} ms before attempt {1}", new Object[] {millis, attempt + 1});
        Thread.sleep(millis);
    }

    @Override
    public void close() {
        http.close();
    }
}
