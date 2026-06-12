package com.routing.service;

import com.routing.domain.entity.LiquidityBalance;
import com.routing.domain.entity.RoutingEdge;
import com.routing.exception.InsufficientLiquidityException;
import com.routing.repository.LiquidityBalanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Service for checking and managing liquidity along routing paths.
 *
 * Liquidity feasibility rule:
 *   A route is valid only if every node along the path has:
 *   availableAmount - amount >= minimumRequiredBalance
 *
 * NOTE: All liquidity amounts are SYNTHETIC MOCK DATA representing realistic
 * institutional positions. Real interbank liquidity is proprietary.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LiquidityService {

    private final LiquidityBalanceRepository liquidityBalanceRepository;

    /**
     * Validates that every node in the route has sufficient liquidity
     * for the given amount in the corridor currency.
     *
     * @param routeEdges   ordered list of routing edges (hops)
     * @param amount       payment amount in source currency
     * @param sourceCurrency source currency
     * @throws InsufficientLiquidityException if any hop node cannot support the amount
     */
    @Transactional(readOnly = true)
    public void validateLiquidityAlongRoute(
            List<RoutingEdge> routeEdges,
            BigDecimal amount,
            String sourceCurrency) {

        log.debug("Validating liquidity for {} hops, amount={} {}", 
            routeEdges.size(), amount, sourceCurrency);

        for (RoutingEdge edge : routeEdges) {
            long nodeId = edge.getSourceNode().getId();
            String currency = edge.getSourceNode().getBaseCurrency();
            String bankName = edge.getSourceNode().getBankName();

            checkNodeLiquidity(nodeId, bankName, currency, amount);
        }

        // Also check the final destination node
        if (!routeEdges.isEmpty()) {
            RoutingEdge lastEdge = routeEdges.get(routeEdges.size() - 1);
            long lastNodeId = lastEdge.getTargetNode().getId();
            String lastCurrency = lastEdge.getTargetNode().getBaseCurrency();
            String lastBankName = lastEdge.getTargetNode().getBankName();
            checkNodeLiquidity(lastNodeId, lastBankName, lastCurrency, amount);
        }

        log.debug("Liquidity validation passed for all {} hops", routeEdges.size());
    }

    /**
     * Check if a specific node has sufficient liquidity in the given currency.
     */
    @Transactional(readOnly = true)
    public boolean hasLiquidity(Long nodeId, String currency, BigDecimal amount) {
        Optional<LiquidityBalance> balance =
            liquidityBalanceRepository.findByNodeIdAndCurrency(nodeId, currency);

        return balance.map(lb -> {
            BigDecimal available = lb.getAvailableAmount().subtract(lb.getMinimumRequiredBalance());
            return available.compareTo(amount) >= 0;
        }).orElse(false);
    }

    /**
     * Get liquidity position for a node and currency.
     */
    @Transactional(readOnly = true)
    public Optional<LiquidityBalance> getLiquidityBalance(Long nodeId, String currency) {
        return liquidityBalanceRepository.findByNodeIdAndCurrency(nodeId, currency);
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private void checkNodeLiquidity(
            long nodeId, String bankName, String currency, BigDecimal amount) {

        Optional<LiquidityBalance> balance =
            liquidityBalanceRepository.findByNodeIdAndCurrency(nodeId, currency);

        if (balance.isEmpty()) {
            // Node has no tracked balance for this currency - treat as insufficient
            log.warn("No liquidity record for node {} ({}) in currency {}",
                nodeId, bankName, currency);
            throw new InsufficientLiquidityException(
                String.format("Node '%s' has no liquidity position in %s. " +
                    "This corridor may not support %s transfers.", bankName, currency, currency));
        }

        LiquidityBalance lb = balance.get();
        BigDecimal usableLiquidity = lb.getAvailableAmount().subtract(lb.getMinimumRequiredBalance());

        if (usableLiquidity.compareTo(amount) < 0) {
            log.warn("Insufficient liquidity at node {} ({}): required={}, usable={} {}",
                nodeId, bankName, amount, usableLiquidity, currency);
            throw new InsufficientLiquidityException(
                String.format("Insufficient liquidity at '%s'. " +
                    "Required: %s %s, Available (above minimum): %s %s. " +
                    "Try a smaller amount or a different route.",
                    bankName, amount, currency, usableLiquidity, currency));
        }

        log.debug("Liquidity OK at node {} ({}): required={}, usable={} {}",
            nodeId, bankName, amount, usableLiquidity, currency);
    }
}
