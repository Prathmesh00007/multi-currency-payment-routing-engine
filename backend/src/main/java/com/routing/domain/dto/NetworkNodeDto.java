package com.routing.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Network node DTO for frontend graph display.
 * Includes liquidity snapshots and active edges.
 */
@Data
@Builder
public class NetworkNodeDto {

    private Long id;
    private String bankName;
    private String country;
    private String baseCurrency;
    private boolean active;

    /** Active outgoing edges from this node */
    private List<NetworkEdgeDto> outgoingEdges;

    /** Liquidity balances (summarized for display) */
    private List<LiquidityDto> liquidityBalances;
}
