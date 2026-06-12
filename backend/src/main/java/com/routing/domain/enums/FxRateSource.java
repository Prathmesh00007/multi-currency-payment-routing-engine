package com.routing.domain.enums;

/**
 * Source of FX rate data — used for transparency and audit trail.
 * ISO 20022: maps to ExchangeRate/ContractId provenance metadata.
 */
public enum FxRateSource {

    /** Tier A: ECB reference rate from Frankfurter API (daily, no auth) */
    FRANKFURTER_API,

    /** Tier B: Live tick from Finnhub.io WebSocket (real-time, free tier) */
    FINNHUB_WEBSOCKET,

    /** Tier C: Rate from ExchangeRate-API (open.er-api.com, 5-min poll, no auth) */
    EXCHANGE_RATE_API,

    /** Fallback: Deterministic mock rate used when all live sources unavailable */
    MOCK_FALLBACK
}
