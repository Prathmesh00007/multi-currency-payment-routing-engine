package com.routing.service.stellar;

import com.routing.domain.dto.FeeBreakdownDto;
import com.routing.domain.dto.RouteHopDto;
import com.routing.domain.enums.OptimizationPreference;
import com.routing.exception.NoRouteAvailableException;
import com.routing.service.stellar.StellarAssetRegistry.StellarAsset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.*;

/**
 * Stellar Testnet Routing Service — replaces the local Dijkstra RoutingEngine.
 *
 * INTEGRATION:
 * Uses Horizon REST API (no SDK required) via:
 *   GET https://horizon-testnet.stellar.org/paths/strict-send
 *   params: source_asset_type, source_asset_code, source_asset_issuer,
 *           source_amount, destination_assets
 *
 * MAPPING:
 * Stellar path hops → ISO 20022 RouteHopDto with banking terminology labels.
 * Stellar fees (stroops) → absolute USD equivalent for display.
 *
 * FALLBACK:
 * If Horizon is unreachable or returns no paths, falls back to a deterministic
 * synthetic route (labeled MOCK_STELLAR_FALLBACK) so the UI always shows something.
 *
 * ISO 20022 alignment:
 * - Source asset → DbtrAgt.FinInstnId
 * - Path hops → IntermediaryAgent1..3
 * - Destination asset → CdtrAgt.FinInstnId
 * - Fee charged → Chrgs element
 *
 * IMPORTANT: Stellar TESTNET only — no real funds involved.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StellarRoutingService {

    private final WebClient.Builder webClientBuilder;
    private final StellarAssetRegistry assetRegistry;

    @Value("${app.stellar.horizon-url:https://horizon-testnet.stellar.org}")
    private String horizonUrl;

    @Value("${app.stellar.enabled:true}")
    private boolean stellarEnabled;

    @Value("${app.stellar.timeout-ms:8000}")
    private int timeoutMs;

    @Value("${app.stellar.min-path-amount:10}")
    private String minPathAmount;

    @Value("${app.routing.stellar-base-fee-stroops:100}")
    private int baseFeeStroops;

    // 1 XLM = 10,000,000 stroops; approximately $0.12 mid-2024 (mock for display)
    private static final BigDecimal XLM_USD_PRICE = new BigDecimal("0.12");
    private static final int STROOPS_PER_XLM = 10_000_000;

    /** Result record carrying all routing outputs */
    public record StellarRouteResult(
        List<RouteHopDto> hops,
        List<FeeBreakdownDto> feeBreakdown,
        BigDecimal sourceAmount,
        BigDecimal destinationAmount,
        BigDecimal stellarFeeXlm,
        BigDecimal stellarFeeUsd,
        long totalLatencyMs,
        String routeSource  // "STELLAR_TESTNET" or "MOCK_STELLAR_FALLBACK"
    ) {}

    /**
     * Find the best payment path from sourceCurrency to targetCurrency.
     *
     * @param sourceCurrency ISO 4217 source (e.g., "USD")
     * @param targetCurrency ISO 4217 target (e.g., "INR")
     * @param amount         amount to send in sourceCurrency
     * @param preference     COST or SPEED (used to pick among multiple returned paths)
     */
    public StellarRouteResult findRoute(
            String sourceCurrency,
            String targetCurrency,
            BigDecimal amount,
            OptimizationPreference preference) {

        log.info("[StellarRouting] Finding route: {} → {} amount={} ({})",
            sourceCurrency, targetCurrency, amount, preference);

        if (!stellarEnabled) {
            log.info("[StellarRouting] Stellar disabled. Using mock fallback.");
            return buildMockFallback(sourceCurrency, targetCurrency, amount);
        }

        StellarAsset sourceAsset = assetRegistry.getAsset(sourceCurrency);
        StellarAsset destAsset = assetRegistry.getAsset(targetCurrency);

        if (sourceAsset == null || destAsset == null) {
            throw new NoRouteAvailableException(
                String.format("No Stellar testnet asset mapping for %s or %s.", sourceCurrency, targetCurrency));
        }

        try {
            return queryHorizon(sourceAsset, destAsset, amount, preference, sourceCurrency, targetCurrency);
        } catch (NoRouteAvailableException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[StellarRouting] Horizon call failed: {}. Using mock fallback.", e.getMessage());
            return buildMockFallback(sourceCurrency, targetCurrency, amount);
        }
    }

    // ─── Horizon query ────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private StellarRouteResult queryHorizon(
            StellarAsset src, StellarAsset dst,
            BigDecimal amount, OptimizationPreference preference,
            String sourceCurrency, String targetCurrency) {

        WebClient client = webClientBuilder.baseUrl(horizonUrl).build();

        // Use a minimum amount (Stellar testnet needs non-trivial amounts for paths)
        BigDecimal queryAmount = amount.max(new BigDecimal(minPathAmount));

        Map<String, Object> response;
        try {
            response = client.get()
                .uri(uriBuilder -> {
                    var b = uriBuilder.path("/paths/strict-send");

                    if (src.isNative()) {
                        b.queryParam("source_asset_type", "native");
                    } else {
                        b.queryParam("source_asset_type", src.assetType())
                         .queryParam("source_asset_code", src.code())
                         .queryParam("source_asset_issuer", src.issuer());
                    }

                    b.queryParam("source_amount", queryAmount.toPlainString());

                    if (dst.isNative()) {
                        b.queryParam("destination_assets", "native");
                    } else {
                        b.queryParam("destination_assets",
                            dst.code() + ":" + dst.issuer());
                    }

                    return b.build();
                })
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofMillis(timeoutMs))
                .block();

        } catch (WebClientResponseException e) {
            log.warn("[StellarRouting] Horizon HTTP {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return buildMockFallback(sourceCurrency, targetCurrency, amount);
        }

        if (response == null) {
            log.warn("[StellarRouting] Null response from Horizon.");
            return buildMockFallback(sourceCurrency, targetCurrency, amount);
        }

        // Extract records
        List<Map<String, Object>> records = extractRecords(response);
        if (records.isEmpty()) {
            log.info("[StellarRouting] No paths returned by Horizon for {}/{}. Using mock fallback.",
                sourceCurrency, targetCurrency);
            return buildMockFallback(sourceCurrency, targetCurrency, amount);
        }

        log.info("[StellarRouting] Horizon returned {} path(s) for {}/{}", records.size(), sourceCurrency, targetCurrency);

        // Pick the best record based on optimization preference
        Map<String, Object> best = selectBestPath(records, preference);
        return parseStellarPath(best, amount, sourceCurrency, targetCurrency);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractRecords(Map<String, Object> response) {
        try {
            Map<String, Object> embedded = (Map<String, Object>) response.get("_embedded");
            if (embedded == null) return List.of();
            Object records = embedded.get("records");
            if (records instanceof List<?> list) {
                return (List<Map<String, Object>>) list;
            }
        } catch (Exception e) {
            log.debug("[StellarRouting] Error extracting records: {}", e.getMessage());
        }
        return List.of();
    }

    /**
     * Select best Stellar path record.
     * COST mode: maximize destination_amount (best rate = most destination currency)
     * SPEED mode: minimize path length (fewer hops = faster settlement)
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> selectBestPath(
            List<Map<String, Object>> records, OptimizationPreference preference) {

        return records.stream()
            .max(Comparator.comparing(r -> {
                if (preference == OptimizationPreference.SPEED) {
                    // Fewer hops is better — negate path length
                    List<?> path = (List<?>) r.getOrDefault("path", List.of());
                    return (double) -path.size();
                } else {
                    // COST: maximize destination_amount
                    String da = String.valueOf(r.getOrDefault("destination_amount", "0"));
                    try { return Double.parseDouble(da); } catch (Exception e) { return 0.0; }
                }
            }))
            .orElse(records.get(0));
    }

    /**
     * Parses a Stellar Horizon path record into our RouteHopDto format.
     *
     * Horizon path record structure:
     * {
     *   "source_asset_type": "credit_alphanum4",
     *   "source_asset_code": "USDC",
     *   "source_amount": "10.0000000",
     *   "destination_asset_type": "credit_alphanum4",
     *   "destination_asset_code": "INR",
     *   "destination_amount": "834.5000000",
     *   "path": [
     *     {"asset_type": "native", "asset_code": "XLM"},
     *     {"asset_type": "credit_alphanum4", "asset_code": "EURC", "asset_issuer": "..."}
     *   ]
     * }
     */
    @SuppressWarnings("unchecked")
    private StellarRouteResult parseStellarPath(
            Map<String, Object> record, BigDecimal originalAmount,
            String sourceCurrency, String targetCurrency) {

        String srcCode = String.valueOf(record.getOrDefault("source_asset_code", sourceCurrency));
        String dstCode = String.valueOf(record.getOrDefault("destination_asset_code", targetCurrency));
        String destAmountStr = String.valueOf(record.getOrDefault("destination_amount", "0"));

        BigDecimal destAmount;
        try {
            destAmount = new BigDecimal(destAmountStr).setScale(4, RoundingMode.HALF_UP);
        } catch (Exception e) {
            destAmount = BigDecimal.ZERO;
        }

        // Build hop list: source → intermediate path nodes → destination
        List<RouteHopDto> hops = new ArrayList<>();
        List<FeeBreakdownDto> feeBreakdown = new ArrayList<>();
        long totalLatency = 0;

        // Origin hop
        hops.add(buildHop(srcCode, null, sourceCurrency, 0, null, null, null));

        // Intermediate hops from Stellar path array
        List<Map<String, Object>> pathNodes = (List<Map<String, Object>>)
            record.getOrDefault("path", List.of());

        for (int i = 0; i < pathNodes.size(); i++) {
            Map<String, Object> node = pathNodes.get(i);
            String assetType = String.valueOf(node.getOrDefault("asset_type", "native"));
            String assetCode = "native".equals(assetType) ? "XLM"
                : String.valueOf(node.getOrDefault("asset_code", "XLM"));
            String issuer = String.valueOf(node.getOrDefault("asset_issuer", ""));

            // Assign mock latency per hop (100-800ms range, Stellar testnet is fast)
            int hopLatency = 150 + (i * 80);
            totalLatency += hopLatency;

            // Assign mock fee per hop (Stellar is very cheap: 100 stroops each)
            BigDecimal hopFeePercent = new BigDecimal("0.0002");

            hops.add(buildHop(assetCode, issuer,
                assetRegistry.toIso4217(assetCode, issuer), i + 1,
                hopFeePercent, hopLatency,
                i == 0 ? sourceCurrency + "/" + assetCode : pathNodes.get(i - 1)
                    .getOrDefault("asset_code", "XLM") + "/" + assetCode));

            feeBreakdown.add(FeeBreakdownDto.builder()
                .fromBank(i == 0 ? assetRegistry.toBankingLabel(srcCode)
                    : assetRegistry.toBankingLabel(String.valueOf(pathNodes.get(i - 1)
                        .getOrDefault("asset_code", "XLM"))))
                .toBank(assetRegistry.toBankingLabel(assetCode))
                .corridorCurrencyPair(i == 0 ? sourceCurrency + "/" + assetCode : "XLM/" + assetCode)
                .baseFeePercentage(hopFeePercent)
                .fxSpreadMarginPercentage(new BigDecimal("0.0003"))
                .baseFeeAbsolute(originalAmount.multiply(hopFeePercent).setScale(4, RoundingMode.HALF_UP))
                .fxSpreadAbsolute(originalAmount.multiply(new BigDecimal("0.0003")).setScale(4, RoundingMode.HALF_UP))
                .latencyMs(hopLatency)
                .build());
        }

        // Final destination hop
        int finalLatency = 200;
        totalLatency += finalLatency;
        BigDecimal finalFee = new BigDecimal("0.0002");
        hops.add(buildHop(dstCode, null, targetCurrency, hops.size(),
            finalFee, finalLatency,
            (pathNodes.isEmpty() ? srcCode : String.valueOf(
                pathNodes.get(pathNodes.size() - 1).getOrDefault("asset_code", "XLM")))
                + "/" + dstCode));

        // Stellar network fee calculation
        // Total operations = path.size() + 1; fee per op = baseFeeStroops stroops
        int totalOps = pathNodes.size() + 1;
        BigDecimal stellarFeeXlm = BigDecimal.valueOf((long) totalOps * baseFeeStroops)
            .divide(BigDecimal.valueOf(STROOPS_PER_XLM), 8, RoundingMode.HALF_UP);
        BigDecimal stellarFeeUsd = stellarFeeXlm.multiply(XLM_USD_PRICE)
            .setScale(6, RoundingMode.HALF_UP);

        // Add final leg fee breakdown
        if (!pathNodes.isEmpty()) {
            String lastIntermediary = String.valueOf(
                pathNodes.get(pathNodes.size() - 1).getOrDefault("asset_code", "XLM"));
            feeBreakdown.add(FeeBreakdownDto.builder()
                .fromBank(assetRegistry.toBankingLabel(lastIntermediary))
                .toBank(assetRegistry.toBankingLabel(dstCode))
                .corridorCurrencyPair(lastIntermediary + "/" + dstCode)
                .baseFeePercentage(finalFee)
                .fxSpreadMarginPercentage(new BigDecimal("0.0003"))
                .baseFeeAbsolute(originalAmount.multiply(finalFee).setScale(4, RoundingMode.HALF_UP))
                .fxSpreadAbsolute(originalAmount.multiply(new BigDecimal("0.0003")).setScale(4, RoundingMode.HALF_UP))
                .latencyMs(finalLatency)
                .build());
        }

        log.info("[StellarRouting] Parsed {} hops, destAmount={}, stellarFee={}XLM (~${})",
            hops.size(), destAmount, stellarFeeXlm, stellarFeeUsd);

        return new StellarRouteResult(
            hops, feeBreakdown, originalAmount, destAmount,
            stellarFeeXlm, stellarFeeUsd, totalLatency, "STELLAR_TESTNET");
    }

    // ─── Mock fallback ────────────────────────────────────────────────────────

    /**
     * Deterministic mock route — used when Stellar Horizon is unreachable or returns no paths.
     * Clearly labeled as MOCK_STELLAR_FALLBACK in the response.
     * Simulates a 3-hop correspondent path similar to the original Dijkstra result.
     */
    private StellarRouteResult buildMockFallback(
            String sourceCurrency, String targetCurrency, BigDecimal amount) {

        log.info("[StellarRouting] Building MOCK_STELLAR_FALLBACK route for {}/{}",
            sourceCurrency, targetCurrency);

        // 3-hop synthetic path: source → XLM bridge → EUR/GBP bridge → target
        String bridgeCurrency = "XLM";
        String midCurrency = targetCurrency.equals("INR") ? "EURC"
            : targetCurrency.equals("KES") ? "EURC" : "XLM";

        List<RouteHopDto> hops = List.of(
            buildHop(sourceCurrency, null, sourceCurrency, 0, null, null, null),
            buildHop(bridgeCurrency, null, "XLM", 1,
                new BigDecimal("0.0002"), 150, sourceCurrency + "/XLM"),
            buildHop(midCurrency, null, midCurrency.replace("C", ""), 2,
                new BigDecimal("0.0003"), 320, "XLM/" + midCurrency),
            buildHop(targetCurrency, null, targetCurrency, 3,
                new BigDecimal("0.0002"), 280, midCurrency + "/" + targetCurrency)
        );

        List<FeeBreakdownDto> fees = List.of(
            FeeBreakdownDto.builder()
                .fromBank("Clearing Hub (" + sourceCurrency + ")")
                .toBank("Stellar Bridge Clearinghouse")
                .corridorCurrencyPair(sourceCurrency + "/XLM")
                .baseFeePercentage(new BigDecimal("0.0002"))
                .fxSpreadMarginPercentage(new BigDecimal("0.0003"))
                .baseFeeAbsolute(amount.multiply(new BigDecimal("0.0002")).setScale(4, RoundingMode.HALF_UP))
                .fxSpreadAbsolute(amount.multiply(new BigDecimal("0.0003")).setScale(4, RoundingMode.HALF_UP))
                .latencyMs(150)
                .build(),
            FeeBreakdownDto.builder()
                .fromBank("Stellar Bridge Clearinghouse")
                .toBank(assetRegistry.toBankingLabel(targetCurrency))
                .corridorCurrencyPair("XLM/" + targetCurrency)
                .baseFeePercentage(new BigDecimal("0.0005"))
                .fxSpreadMarginPercentage(new BigDecimal("0.0008"))
                .baseFeeAbsolute(amount.multiply(new BigDecimal("0.0005")).setScale(4, RoundingMode.HALF_UP))
                .fxSpreadAbsolute(amount.multiply(new BigDecimal("0.0008")).setScale(4, RoundingMode.HALF_UP))
                .latencyMs(600)
                .build()
        );

        BigDecimal stellarFeeXlm = BigDecimal.valueOf(300L)
            .divide(BigDecimal.valueOf(STROOPS_PER_XLM), 8, RoundingMode.HALF_UP);
        BigDecimal stellarFeeUsd = stellarFeeXlm.multiply(XLM_USD_PRICE).setScale(6, RoundingMode.HALF_UP);

        return new StellarRouteResult(
            hops, fees, amount, BigDecimal.ZERO,
            stellarFeeXlm, stellarFeeUsd, 750L, "MOCK_STELLAR_FALLBACK");
    }

    // ─── Builder helpers ──────────────────────────────────────────────────────

    private RouteHopDto buildHop(String stellarCode, String issuer, String isoCurrency,
                                  int index, BigDecimal fee, Integer latencyMs, String corridor) {
        String bankName = assetRegistry.toBankingLabel(stellarCode);
        String country = assetRegistry.getCountryForAsset(stellarCode);

        return RouteHopDto.builder()
            .nodeId((long) (index + 1))
            .bankName(bankName)
            .country(country)
            .baseCurrency(isoCurrency)
            .active(true)
            .hopFee(fee)
            .hopLatencyMs(latencyMs)
            .corridorCurrencyPair(corridor)
            .hopIndex(index)
            .build();
    }
}
