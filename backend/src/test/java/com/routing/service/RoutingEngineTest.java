package com.routing.service;

import com.routing.domain.entity.CorrespondentNode;
import com.routing.domain.entity.RoutingEdge;
import com.routing.domain.enums.OptimizationPreference;
import com.routing.exception.NoRouteAvailableException;
import com.routing.repository.RoutingEdgeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RoutingEngine (Dijkstra's algorithm).
 * Uses mock repository data to test routing logic in isolation.
 */
@ExtendWith(MockitoExtension.class)
class RoutingEngineTest {

    @Mock
    private RoutingEdgeRepository routingEdgeRepository;

    @InjectMocks
    private RoutingEngine routingEngine;

    private CorrespondentNode usdNode, eurNode, inrNode, kesNode, mxnNode;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(routingEngine, "maxHops", 6);
        ReflectionTestUtils.setField(routingEngine, "defaultTransferSurcharge", 0.002);

        usdNode = buildNode(1L, "Bank of America", "US", "USD");
        eurNode = buildNode(2L, "Deutsche Bank", "DE", "EUR");
        inrNode = buildNode(3L, "HDFC Bank India", "IN", "INR");
        kesNode = buildNode(4L, "Equity Bank Kenya", "KE", "KES");
        mxnNode = buildNode(5L, "BBVA Mexico", "MX", "MXN");
    }

    @Test
    void findOptimalRoute_costMode_selectsCheapestPath() {
        // USD → EUR → INR
        // cheap: baseFee=0.002, spread=0.002 (total weight = 0.002+0.002+0.002 = 0.006 per hop)
        // expensive: baseFee=0.005, spread=0.003
        RoutingEdge cheapEdge1 = buildEdge(1L, usdNode, eurNode, "0.002", "0.002", 800, "USD/EUR");
        RoutingEdge cheapEdge2 = buildEdge(2L, eurNode, inrNode, "0.002", "0.002", 600, "EUR/INR");
        RoutingEdge expensiveEdge = buildEdge(3L, usdNode, inrNode, "0.010", "0.008", 200, "USD/INR");

        when(routingEdgeRepository.findAllActiveEdgesWithActiveNodes())
            .thenReturn(List.of(cheapEdge1, cheapEdge2, expensiveEdge));

        List<RoutingEdge> path = routingEngine.findOptimalRoute("USD", "INR", OptimizationPreference.COST);

        assertThat(path).hasSize(2);
        assertThat(path.get(0).getSourceNode().getBankName()).isEqualTo("Bank of America");
        assertThat(path.get(0).getTargetNode().getBankName()).isEqualTo("Deutsche Bank");
        assertThat(path.get(1).getTargetNode().getBankName()).isEqualTo("HDFC Bank India");
    }

    @Test
    void findOptimalRoute_speedMode_selectsFastestPath() {
        // Fast direct route vs cheap but slow multi-hop route
        RoutingEdge slowHop1 = buildEdge(1L, usdNode, eurNode, "0.001", "0.001", 2000, "USD/EUR");
        RoutingEdge slowHop2 = buildEdge(2L, eurNode, inrNode, "0.001", "0.001", 1800, "EUR/INR");
        RoutingEdge fastDirect = buildEdge(3L, usdNode, inrNode, "0.008", "0.006", 300, "USD/INR");

        when(routingEdgeRepository.findAllActiveEdgesWithActiveNodes())
            .thenReturn(List.of(slowHop1, slowHop2, fastDirect));

        List<RoutingEdge> path = routingEngine.findOptimalRoute("USD", "INR", OptimizationPreference.SPEED);

        assertThat(path).hasSize(1);
        assertThat(path.get(0).getLatencyMs()).isEqualTo(300);
    }

    @Test
    void findOptimalRoute_noActiveEdges_throwsNoRouteAvailable() {
        when(routingEdgeRepository.findAllActiveEdgesWithActiveNodes()).thenReturn(List.of());

        assertThatThrownBy(() ->
            routingEngine.findOptimalRoute("USD", "INR", OptimizationPreference.COST))
            .isInstanceOf(NoRouteAvailableException.class)
            .hasMessageContaining("No active routing corridors");
    }

    @Test
    void findOptimalRoute_targetCurrencyNotReachable_throwsNoRouteAvailable() {
        // Only USD→EUR, no path to INR
        RoutingEdge usdEurEdge = buildEdge(1L, usdNode, eurNode, "0.002", "0.002", 400, "USD/EUR");
        when(routingEdgeRepository.findAllActiveEdgesWithActiveNodes())
            .thenReturn(List.of(usdEurEdge));

        assertThatThrownBy(() ->
            routingEngine.findOptimalRoute("USD", "INR", OptimizationPreference.COST))
            .isInstanceOf(NoRouteAvailableException.class)
            .hasMessageContaining("INR");
    }

    @Test
    void findOptimalRoute_multipleDestinations_picksOptimalTarget() {
        // Two INR nodes - routing should pick the one with lower total cost
        CorrespondentNode inrNode2 = buildNode(6L, "ICICI Bank India", "IN", "INR");
        RoutingEdge toHdfc = buildEdge(1L, usdNode, inrNode, "0.004", "0.003", 700, "USD/INR");
        RoutingEdge toIcici = buildEdge(2L, usdNode, inrNode2, "0.002", "0.001", 900, "USD/INR");

        when(routingEdgeRepository.findAllActiveEdgesWithActiveNodes())
            .thenReturn(List.of(toHdfc, toIcici));

        List<RoutingEdge> path = routingEngine.findOptimalRoute("USD", "INR", OptimizationPreference.COST);

        assertThat(path).hasSize(1);
        // ICICI has lower combined weight (0.002+0.001+0.002 = 0.005 vs 0.004+0.003+0.002 = 0.009)
        assertThat(path.get(0).getTargetNode().getBankName()).isEqualTo("ICICI Bank India");
    }

    @Test
    void findOptimalRoute_usdToMxn_returnsValidPath() {
        RoutingEdge e1 = buildEdge(1L, usdNode, eurNode, "0.0015", "0.0020", 380, "USD/EUR");
        RoutingEdge e2 = buildEdge(2L, eurNode, mxnNode, "0.0022", "0.0035", 650, "EUR/MXN");

        when(routingEdgeRepository.findAllActiveEdgesWithActiveNodes())
            .thenReturn(List.of(e1, e2));

        List<RoutingEdge> path = routingEngine.findOptimalRoute("USD", "MXN", OptimizationPreference.COST);

        assertThat(path).hasSize(2);
        assertThat(path.get(0).getCorridorCurrencyPair()).isEqualTo("USD/EUR");
        assertThat(path.get(1).getCorridorCurrencyPair()).isEqualTo("EUR/MXN");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private CorrespondentNode buildNode(Long id, String name, String country, String currency) {
        CorrespondentNode node = new CorrespondentNode();
        node.setId(id);
        node.setBankName(name);
        node.setCountry(country);
        node.setBaseCurrency(currency);
        node.setActive(true);
        return node;
    }

    private RoutingEdge buildEdge(Long id, CorrespondentNode src, CorrespondentNode tgt,
                                   String baseFee, String spread, int latency, String corridor) {
        RoutingEdge edge = new RoutingEdge();
        edge.setId(id);
        edge.setSourceNode(src);
        edge.setTargetNode(tgt);
        edge.setBaseFee(new BigDecimal(baseFee));
        edge.setFxSpreadMargin(new BigDecimal(spread));
        edge.setLatencyMs(latency);
        edge.setActive(true);
        edge.setCorridorCurrencyPair(corridor);
        return edge;
    }
}
