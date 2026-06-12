package com.routing.service.fx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.routing.domain.dto.FxTickMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tier B FX Service: Finnhub.io WebSocket — live forex tick data.
 *
 * ARCHITECTURE:
 * - Connects to wss://ws.finnhub.io?token={apiKey} on startup
 * - Subscribes to OANDA forex symbols (EUR/USD, USD/MXN, USD/INR, USD/KES, GBP/USD)
 * - Caches latest tick per pair in latencyTicks ConcurrentHashMap (sub-ms access)
 * - Broadcasts each new tick to React frontend via Spring STOMP /topic/fx-ticks
 * - Auto-reconnects on disconnect with exponential backoff (up to 60s max delay)
 *
 * DEGRADATION:
 * - If API key is missing/invalid, logs a warning and stays disconnected (no crash)
 * - Pricing fallback handled by FxPricingEngine (uses Tier C or mock)
 *
 * DATA SOURCE: https://finnhub.io (free tier, register for API key, no credit card)
 */
@Service
@Slf4j
public class FinnhubWebSocketService {

    // Latest tick cache: "USD/INR" → tick price
    private final ConcurrentHashMap<String, BigDecimal> latestTicks = new ConcurrentHashMap<>();
    // Previous tick for direction detection
    private final ConcurrentHashMap<String, BigDecimal> previousTicks = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${app.fx.finnhub.api-key:REPLACE_ME}")
    private String apiKey;

    @Value("${app.fx.finnhub.ws-url:wss://ws.finnhub.io}")
    private String wsUrl;

    @Value("${app.fx.finnhub.enabled:true}")
    private boolean enabled;

    @Value("${app.fx.finnhub.symbols:OANDA:EUR_USD,OANDA:USD_MXN,OANDA:USD_INR,OANDA:USD_KES,OANDA:GBP_USD}")
    private String symbolsConfig;

    @Value("${app.fx.finnhub.reconnect-delay-ms:5000}")
    private long reconnectDelayMs;

    private volatile WebSocket webSocket;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private ScheduledExecutorService reconnectExecutor;

    public FinnhubWebSocketService(ObjectMapper objectMapper, SimpMessagingTemplate messagingTemplate) {
        this.objectMapper = objectMapper;
        this.messagingTemplate = messagingTemplate;
    }

    @PostConstruct
    public void init() {
        reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "finnhub-reconnect");
            t.setDaemon(true);
            return t;
        });

        if (!enabled) {
            log.info("[FinnhubWS] Disabled via config. Skipping WebSocket connection.");
            return;
        }
        if ("REPLACE_ME".equals(apiKey) || apiKey == null || apiKey.isBlank()) {
            log.warn("[FinnhubWS] No API key configured (app.fx.finnhub.api-key). " +
                "WebSocket disabled. Register free at https://finnhub.io to enable live ticks. " +
                "FX pricing will use Tier C (ExchangeRate-API) as fallback.");
            return;
        }

        running.set(true);
        connect();
    }

    @PreDestroy
    public void shutdown() {
        running.set(false);
        if (reconnectExecutor != null) reconnectExecutor.shutdownNow();
        if (webSocket != null) {
            try { webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown"); }
            catch (Exception ignored) {}
        }
        log.info("[FinnhubWS] Shutdown complete.");
    }

    /**
     * Returns the latest cached tick for a currency pair.
     * Key format: "FROM/TO" (e.g., "USD/INR")
     */
    public BigDecimal getLatestTick(String from, String to) {
        return latestTicks.get(from + "/" + to);
    }

    public Map<String, BigDecimal> getAllTicks() {
        return Map.copyOf(latestTicks);
    }

    // ─── Connection management ────────────────────────────────────────────────

    private void connect() {
        String fullUrl = wsUrl + "?token=" + apiKey;
        log.info("[FinnhubWS] Connecting to {}", wsUrl + "?token=***");

        HttpClient httpClient = HttpClient.newHttpClient();
        httpClient.newWebSocketBuilder()
            .buildAsync(URI.create(fullUrl), new FinnhubListener())
            .thenAccept(ws -> {
                this.webSocket = ws;
                reconnectAttempts.set(0);
                log.info("[FinnhubWS] Connected. Subscribing to {} symbols...", symbolList().size());
                symbolList().forEach(this::subscribeSymbol);
            })
            .exceptionally(ex -> {
                log.warn("[FinnhubWS] Connection failed: {}. Scheduling reconnect.", ex.getMessage());
                scheduleReconnect();
                return null;
            });
    }

    private void subscribeSymbol(String symbol) {
        try {
            String msg = objectMapper.writeValueAsString(
                Map.of("type", "subscribe", "symbol", symbol));
            webSocket.sendText(msg, true);
            log.debug("[FinnhubWS] Subscribed to {}", symbol);
        } catch (Exception e) {
            log.warn("[FinnhubWS] Failed to subscribe to {}: {}", symbol, e.getMessage());
        }
    }

    private void scheduleReconnect() {
        if (!running.get()) return;
        int attempts = reconnectAttempts.incrementAndGet();
        // Exponential backoff: delay = min(reconnectDelay * 2^attempts, 60s)
        long delay = Math.min(reconnectDelayMs * (1L << Math.min(attempts - 1, 4)), 60000L);
        log.info("[FinnhubWS] Reconnect attempt #{} in {}ms...", attempts, delay);
        reconnectExecutor.schedule(this::connect, delay, TimeUnit.MILLISECONDS);
    }

    private List<String> symbolList() {
        return Arrays.asList(symbolsConfig.split(","));
    }

    /**
     * Maps OANDA symbol to standard "FROM/TO" pair key.
     * e.g., "OANDA:USD_MXN" → "USD/MXN"
     */
    private String toPairKey(String symbol) {
        // OANDA:EUR_USD → EUR/USD
        String raw = symbol.replace("OANDA:", "").replace("_", "/");
        // Normalise order — we want USD as base where possible
        // e.g., EUR_USD comes in as EUR/USD, we want USD/EUR to be available too
        return raw;
    }

    // ─── WebSocket listener ───────────────────────────────────────────────────

    private class FinnhubListener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public CompletableFuture<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                processMessage(buffer.toString());
                buffer.setLength(0);
            }
            ws.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<?> onClose(WebSocket ws, int statusCode, String reason) {
            log.warn("[FinnhubWS] Connection closed (status={}, reason={}). Reconnecting...",
                statusCode, reason);
            scheduleReconnect();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            log.warn("[FinnhubWS] Error: {}. Reconnecting...", error.getMessage());
            scheduleReconnect();
        }
    }

    /**
     * Processes raw Finnhub message JSON.
     * Format:
     * {"type":"trade","data":[{"p":1.0841,"s":"OANDA:EUR_USD","t":1718000000000,"v":1}]}
     */
    private void processMessage(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            String type = root.path("type").asText();

            if ("ping".equals(type) || "heartbeat".equals(type)) return;

            if ("trade".equals(type)) {
                JsonNode trades = root.path("data");
                if (trades.isArray()) {
                    for (JsonNode trade : trades) {
                        processTrade(trade);
                    }
                }
            } else if ("error".equals(type)) {
                log.warn("[FinnhubWS] Server error: {}", root.path("msg").asText());
            }
        } catch (Exception e) {
            log.debug("[FinnhubWS] Failed to parse message: {}", e.getMessage());
        }
    }

    private void processTrade(JsonNode trade) {
        String symbol = trade.path("s").asText();
        double price = trade.path("p").asDouble();
        long timestamp = trade.path("t").asLong();

        if (symbol.isBlank() || price <= 0) return;

        String pairKey = toPairKey(symbol);
        BigDecimal newPrice = BigDecimal.valueOf(price);
        BigDecimal prevPrice = latestTicks.getOrDefault(pairKey, newPrice);

        latestTicks.put(pairKey, newPrice);
        previousTicks.put(pairKey, prevPrice);

        // Determine direction
        int cmp = newPrice.compareTo(prevPrice);
        String direction = cmp > 0 ? "UP" : cmp < 0 ? "DOWN" : "FLAT";

        // Broadcast to React via STOMP
        FxTickMessage tick = FxTickMessage.builder()
            .pair(pairKey)
            .price(newPrice)
            .previousPrice(prevPrice)
            .direction(direction)
            .source("FINNHUB")
            .timestamp(timestamp > 0 ? timestamp : Instant.now().toEpochMilli())
            .build();

        try {
            messagingTemplate.convertAndSend("/topic/fx-ticks", tick);
        } catch (Exception e) {
            log.debug("[FinnhubWS] STOMP broadcast failed: {}", e.getMessage());
        }

        log.debug("[FinnhubWS] {} = {} ({})", pairKey, newPrice, direction);
    }
}
