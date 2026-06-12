package com.routing.service;

import com.routing.domain.entity.CorrespondentNode;
import com.routing.domain.entity.LiquidityBalance;
import com.routing.domain.entity.RoutingEdge;
import com.routing.exception.InsufficientLiquidityException;
import com.routing.repository.LiquidityBalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LiquidityService.
 */
@ExtendWith(MockitoExtension.class)
class LiquidityServiceTest {

    @Mock
    private LiquidityBalanceRepository liquidityBalanceRepository;

    @InjectMocks
    private LiquidityService liquidityService;

    private CorrespondentNode usdNode, inrNode;
    private RoutingEdge edge;

    @BeforeEach
    void setUp() {
        usdNode = buildNode(1L, "Bank of America", "USD");
        inrNode = buildNode(2L, "HDFC Bank India", "INR");
        edge = buildEdge(usdNode, inrNode);
    }

    @Test
    void validateLiquidityAlongRoute_sufficientLiquidity_passes() {
        LiquidityBalance usdBalance = buildBalance(usdNode, "USD", "1000000", "50000", "100000");
        LiquidityBalance inrBalance = buildBalance(inrNode, "INR", "50000000", "2500000", "5000000");

        when(liquidityBalanceRepository.findByNodeIdAndCurrency(1L, "USD"))
            .thenReturn(Optional.of(usdBalance));
        when(liquidityBalanceRepository.findByNodeIdAndCurrency(2L, "INR"))
            .thenReturn(Optional.of(inrBalance));

        // Should not throw - 500,000 is well within usable liquidity of 900,000
        assertThatNoException().isThrownBy(() ->
            liquidityService.validateLiquidityAlongRoute(
                List.of(edge), new BigDecimal("500000"), "USD"));
    }

    @Test
    void validateLiquidityAlongRoute_insufficientAtSourceNode_throwsException() {
        // Available=200000, Minimum=150000, Usable=50000 - not enough for 100000
        LiquidityBalance lowBalance = buildBalance(usdNode, "USD", "200000", "5000", "150000");

        when(liquidityBalanceRepository.findByNodeIdAndCurrency(1L, "USD"))
            .thenReturn(Optional.of(lowBalance));

        assertThatThrownBy(() ->
            liquidityService.validateLiquidityAlongRoute(
                List.of(edge), new BigDecimal("100000"), "USD"))
            .isInstanceOf(InsufficientLiquidityException.class)
            .hasMessageContaining("Bank of America")
            .hasMessageContaining("Insufficient");
    }

    @Test
    void validateLiquidityAlongRoute_noBalanceRecord_throwsException() {
        when(liquidityBalanceRepository.findByNodeIdAndCurrency(anyLong(), anyString()))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
            liquidityService.validateLiquidityAlongRoute(
                List.of(edge), new BigDecimal("100"), "USD"))
            .isInstanceOf(InsufficientLiquidityException.class)
            .hasMessageContaining("no liquidity position");
    }

    @Test
    void hasLiquidity_exactlyAtMinimum_returnsFalse() {
        // Available=1000, Minimum=1000, Usable=0 - exactly at minimum (NOT sufficient)
        LiquidityBalance balance = buildBalance(usdNode, "USD", "1000", "0", "1000");
        when(liquidityBalanceRepository.findByNodeIdAndCurrency(1L, "USD"))
            .thenReturn(Optional.of(balance));

        assertThat(liquidityService.hasLiquidity(1L, "USD", new BigDecimal("1")))
            .isFalse();
    }

    @Test
    void hasLiquidity_aboveMinimum_returnsTrue() {
        // Available=2000, Minimum=1000, Usable=1000 - sufficient for 500
        LiquidityBalance balance = buildBalance(usdNode, "USD", "2000", "0", "1000");
        when(liquidityBalanceRepository.findByNodeIdAndCurrency(1L, "USD"))
            .thenReturn(Optional.of(balance));

        assertThat(liquidityService.hasLiquidity(1L, "USD", new BigDecimal("500")))
            .isTrue();
    }

    @Test
    void hasLiquidity_noRecord_returnsFalse() {
        when(liquidityBalanceRepository.findByNodeIdAndCurrency(anyLong(), anyString()))
            .thenReturn(Optional.empty());

        assertThat(liquidityService.hasLiquidity(99L, "XYZ", BigDecimal.TEN))
            .isFalse();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private CorrespondentNode buildNode(Long id, String name, String currency) {
        CorrespondentNode node = new CorrespondentNode();
        node.setId(id);
        node.setBankName(name);
        node.setBaseCurrency(currency);
        node.setActive(true);
        return node;
    }

    private RoutingEdge buildEdge(CorrespondentNode src, CorrespondentNode tgt) {
        RoutingEdge edge = new RoutingEdge();
        edge.setId(1L);
        edge.setSourceNode(src);
        edge.setTargetNode(tgt);
        edge.setActive(true);
        edge.setCorridorCurrencyPair("USD/INR");
        edge.setBaseFee(new BigDecimal("0.002"));
        edge.setFxSpreadMargin(new BigDecimal("0.003"));
        edge.setLatencyMs(500);
        return edge;
    }

    private LiquidityBalance buildBalance(CorrespondentNode node, String currency,
                                           String available, String reserved, String minimum) {
        LiquidityBalance lb = new LiquidityBalance();
        lb.setId(1L);
        lb.setNode(node);
        lb.setCurrency(currency);
        lb.setAvailableAmount(new BigDecimal(available));
        lb.setReservedAmount(new BigDecimal(reserved));
        lb.setMinimumRequiredBalance(new BigDecimal(minimum));
        return lb;
    }
}
