package com.routing.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Represents a single hop in the routing path for frontend display.
 */
@Data
@Builder
public class RouteHopDto {

    private Long nodeId;
    private String bankName;
    private String country;
    private String baseCurrency;
    private boolean active;

    /** Fee for traversing the edge INTO this hop (null for the first/origin node) */
    private BigDecimal hopFee;

    /** Latency for this hop in milliseconds (null for origin) */
    private Integer hopLatencyMs;

    /** Currency pair on the edge leading to this hop */
    private String corridorCurrencyPair;

    /** Sequence position in the path (0 = origin) */
    private int hopIndex;
}
