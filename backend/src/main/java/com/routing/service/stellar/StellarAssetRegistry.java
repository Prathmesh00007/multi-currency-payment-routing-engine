package com.routing.service.stellar;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Registry mapping ISO 4217 currency codes to Stellar testnet anchor configurations.
 *
 * IMPORTANT: These are TESTNET anchor issuers ONLY.
 * Do NOT use these on Stellar mainnet — testnet anchor accounts are
 * funded by Friendbot and have no real-world value.
 *
 * Source: Stellar testnet public anchors and Stellar Laboratory.
 * Reference: https://lab.stellar.org and https://developers.stellar.org/docs/tutorials
 *
 * Asset code format: credit_alphanum4 for 1-4 char codes, credit_alphanum12 for 5-12 chars.
 * Native XLM uses asset_type=native.
 */
@Component
@Slf4j
public class StellarAssetRegistry {

    @Value("${app.stellar.usd-issuer:GBBD47IF6LWK7P7MDEVSCWR7DPUWV3NY3DTQEVFL4NAT4AQH3ZLLFLA5}")
    private String usdIssuer;

    @Value("${app.stellar.eur-issuer:GBBD47IF6LWK7P7MDEVSCWR7DPUWV3NY3DTQEVFL4NAT4AQH3ZLLFLA5}")
    private String eurIssuer;

    /**
     * Stellar testnet asset descriptors for known fiat currencies.
     * All issuers point to publicly known testnet anchor accounts.
     */
    public record StellarAsset(
        String code,
        String issuer,       // null for native XLM
        String assetType,    // "native", "credit_alphanum4", "credit_alphanum12"
        String bankingName   // Human-readable ISO 20022 label for UI
    ) {
        public boolean isNative() { return "native".equals(assetType); }
    }

    /**
     * Get Stellar testnet asset for an ISO 4217 currency code.
     * Returns null for unsupported currencies.
     */
    public StellarAsset getAsset(String currencyCode) {
        return switch (currencyCode.toUpperCase()) {
            case "XLM" -> new StellarAsset("XLM", null, "native",
                "Stellar Lumens (Bridge)");
            case "USD" -> new StellarAsset("USDC", usdIssuer, "credit_alphanum4",
                "USD Anchor [Testnet]");
            case "EUR" -> new StellarAsset("EURC", eurIssuer, "credit_alphanum4",
                "EUR Anchor [Testnet]");
            // For exotic currencies, XLM acts as bridge (no direct testnet fiat anchors for MXN/KES/INR)
            case "MXN" -> new StellarAsset("MXN", usdIssuer, "credit_alphanum4",
                "MXN Synthetic Anchor [Testnet]");
            case "KES" -> new StellarAsset("KES", usdIssuer, "credit_alphanum4",
                "KES Synthetic Anchor [Testnet]");
            case "INR" -> new StellarAsset("INR", usdIssuer, "credit_alphanum4",
                "INR Synthetic Anchor [Testnet]");
            case "GBP" -> new StellarAsset("GBP", usdIssuer, "credit_alphanum4",
                "GBP Anchor [Testnet]");
            default -> null;
        };
    }

    /**
     * Map a Stellar asset code back to ISO 4217.
     * Used when parsing Horizon path records.
     */
    public String toIso4217(String stellarCode, String stellarIssuer) {
        return switch (stellarCode.toUpperCase()) {
            case "XLM" -> "XLM";
            case "USDC" -> "USD";
            case "EURC" -> "EUR";
            case "MXN" -> "MXN";
            case "KES" -> "KES";
            case "INR" -> "INR";
            case "GBP" -> "GBP";
            default -> stellarCode;  // pass through unknown codes
        };
    }

    /**
     * Map Stellar asset code to a human-readable correspondent banking node name.
     * Used for building RouteHopDto labels in the UI.
     */
    public String toBankingLabel(String stellarCode) {
        return switch (stellarCode.toUpperCase()) {
            case "XLM" -> "Stellar Bridge Clearinghouse";
            case "USDC" -> "SDF USD Anchor / Testnet";
            case "EURC" -> "SDF EUR Anchor / Testnet";
            case "MXN" -> "Stellar MXN Gateway / Testnet";
            case "KES" -> "Stellar KES Gateway / Testnet";
            case "INR" -> "Stellar INR Gateway / Testnet";
            case "GBP" -> "Stellar GBP Anchor / Testnet";
            default -> "Stellar " + stellarCode + " Node";
        };
    }

    public String getCountryForAsset(String stellarCode) {
        return switch (stellarCode.toUpperCase()) {
            case "XLM" -> "SG";  // Stellar Development Foundation (Singapore)
            case "USDC" -> "US";
            case "EURC" -> "DE";
            case "MXN" -> "MX";
            case "KES" -> "KE";
            case "INR" -> "IN";
            case "GBP" -> "GB";
            default -> "XX";
        };
    }
}
