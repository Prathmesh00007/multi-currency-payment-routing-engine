package com.routing.config;

import com.routing.domain.entity.*;
import com.routing.repository.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * ============================================================
 * LIQUIDITY SEEDER — Simplified for Hybrid Architecture
 * ============================================================
 *
 * DATA CLASSIFICATION:
 * ✅ REAL:  ISO 20022 field naming, ISO 4217 currency codes
 * ✅ REAL:  Stellar testnet anchor node identifiers
 * ⚠️  MOCK: All liquidity values (Nostro/Vostro balances)
 *           Real interbank liquidity is proprietary and unavailable.
 *
 * REMOVED (now live):
 * - Static CorrespondentNode connectivity / RoutingEdge seeding
 *   → Replaced by Stellar Testnet Horizon /paths API
 * - Static FxRateSnapshot seeding
 *   → Replaced by FrankfurterScheduledService, FinnhubWebSocketService,
 *     and ExchangeRateApiService
 *
 * RETAINED:
 * - 6 virtual CorrespondentNode records (mapped to Stellar testnet anchors)
 *   → Needed for the Network Simulator UI panel (node toggle feature)
 * - LiquidityBalance Nostro/Vostro mock accounts for feasibility checks
 *   → Realistic institutional amounts (millions)
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class MockDataSeeder {

    private final CorrespondentNodeRepository nodeRepository;
    private final LiquidityBalanceRepository liquidityRepository;

    @PostConstruct
    @Transactional
    public void seed() {
        if (nodeRepository.count() > 0) {
            log.info("[Seeder] Data already seeded. Skipping.");
            return;
        }

        log.info("[Seeder] ====================================================");
        log.info("[Seeder] Seeding Stellar testnet anchor nodes + mock liquidity");
        log.info("[Seeder] NOTE: Routing is now live via Stellar Testnet Horizon.");
        log.info("[Seeder] NOTE: FX rates are now live via Finnhub/ExchangeRate-API/Frankfurter.");
        log.info("[Seeder] NOTE: Liquidity values are SYNTHETIC MOCK DATA.");
        log.info("[Seeder] ====================================================");

        // ─── Virtual Nodes (Stellar Testnet Anchors) ──────────────────────────
        // These nodes represent real Stellar testnet anchors.
        // They appear in the Network Simulator UI and support node-toggle functionality.
        // When a node is toggled off, the StellarRoutingService excludes that currency.

        CorrespondentNode usdAnchor  = createNode("SDF USD Anchor [Testnet]",  "US", "USD", true);
        CorrespondentNode xlmBridge  = createNode("Stellar Bridge Clearinghouse", "SG", "XLM", true);
        CorrespondentNode eurAnchor  = createNode("SDF EUR Anchor [Testnet]",  "DE", "EUR", true);
        CorrespondentNode mxnAnchor  = createNode("Stellar MXN Gateway [Testnet]", "MX", "MXN", true);
        CorrespondentNode kesAnchor  = createNode("Stellar KES Gateway [Testnet]", "KE", "KES", true);
        CorrespondentNode inrAnchor  = createNode("Stellar INR Gateway [Testnet]", "IN", "INR", true);

        nodeRepository.saveAll(List.of(usdAnchor, xlmBridge, eurAnchor, mxnAnchor, kesAnchor, inrAnchor));
        log.info("[Seeder] Created 6 virtual Stellar testnet anchor nodes.");

        // ─── Nostro / Vostro Liquidity Balances (MOCK) ────────────────────────
        //
        // Represents internal treasury positions at each anchor.
        // In production: sourced from real-time treasury management systems (TMS).
        //
        // minimumRequiredBalance = ~15% of position (regulatory buffer simulation)
        //
        // MOCK DATA — amounts in native currency units:
        //   USD: millions
        //   MXN: tens of millions (17:1 FX to USD)
        //   KES: hundreds of millions (130:1 FX to USD)
        //   INR: billions (83:1 FX to USD)

        // USD Anchor — Primary clearing node
        seedLiquidity(usdAnchor, "USD", "500_000_000", "25_000_000", "50_000_000");

        // XLM Bridge — holds XLM for bridging
        seedLiquidity(xlmBridge, "XLM", "100_000_000", "5_000_000", "10_000_000");
        seedLiquidity(xlmBridge, "USD", "25_000_000",  "1_250_000",  "2_500_000");

        // EUR Anchor
        seedLiquidity(eurAnchor, "EUR", "200_000_000", "10_000_000", "20_000_000");
        seedLiquidity(eurAnchor, "USD", "185_000_000", "9_250_000",  "18_500_000");

        // MXN Gateway
        seedLiquidity(mxnAnchor, "MXN", "2_500_000_000", "125_000_000", "250_000_000");
        seedLiquidity(mxnAnchor, "USD", "45_000_000",    "2_250_000",   "4_500_000");

        // KES Gateway
        seedLiquidity(kesAnchor, "KES", "15_000_000_000", "750_000_000", "1_500_000_000");
        seedLiquidity(kesAnchor, "USD", "25_000_000",     "1_250_000",   "2_500_000");

        // INR Gateway
        seedLiquidity(inrAnchor, "INR", "75_000_000_000", "3_750_000_000", "7_500_000_000");
        seedLiquidity(inrAnchor, "USD", "50_000_000",     "2_500_000",     "5_000_000");

        log.info("[Seeder] Created Nostro/Vostro mock liquidity balances for all 6 nodes.");
        log.info("[Seeder] ====================================================");
        log.info("[Seeder] Seeding complete. Live data sources will activate shortly.");
        log.info("[Seeder]   - FX: Frankfurter (startup) + ExchangeRate-API (5min) + Finnhub WS (if key set)");
        log.info("[Seeder]   - Routing: Stellar Testnet Horizon API (with mock fallback)");
        log.info("[Seeder] ====================================================");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private CorrespondentNode createNode(String bankName, String country,
                                          String currency, boolean active) {
        return CorrespondentNode.builder()
            .bankName(bankName)
            .country(country)
            .baseCurrency(currency)
            .active(active)
            .build();
    }

    private void seedLiquidity(CorrespondentNode node, String currency,
                                String available, String reserved, String minimum) {
        // Remove underscores from numeric strings (for readability in source above)
        liquidityRepository.save(LiquidityBalance.builder()
            .node(node)
            .currency(currency)
            .availableAmount(new BigDecimal(available.replace("_", "")))
            .reservedAmount(new BigDecimal(reserved.replace("_", "")))
            .minimumRequiredBalance(new BigDecimal(minimum.replace("_", "")))
            .build());
    }
}
