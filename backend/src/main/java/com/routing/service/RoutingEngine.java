package com.routing.service;

import com.routing.domain.entity.CorrespondentNode;
import com.routing.domain.entity.RoutingEdge;
import com.routing.domain.enums.OptimizationPreference;
import com.routing.exception.NoRouteAvailableException;
import com.routing.repository.RoutingEdgeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Core Dijkstra-based routing engine for correspondent banking path selection.
 *
 * Algorithm:
 * - Builds a directed weighted graph from active RoutingEdges between active CorrespondentNodes
 * - Computes shortest path using Dijkstra's algorithm
 * - Edge weights depend on optimization preference:
 *   COST  → weight = baseFee + fxSpreadMargin + transferSurcharge (as doubles)
 *   SPEED → weight = latencyMs
 *
 * Returns an ordered list of RoutingEdge objects representing the optimal path.
 *
 * NOTE: All network topology, fees, and latency values are SYNTHETIC MOCK DATA.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RoutingEngine {

    private final RoutingEdgeRepository routingEdgeRepository;

    @Value("${app.routing.max-hops:6}")
    private int maxHops;

    @Value("${app.routing.default-transfer-surcharge:0.002}")
    private double defaultTransferSurcharge;

    /**
     * Find the optimal route from source currency to target currency.
     *
     * @param sourceCurrency ISO 4217 source currency (e.g., "USD")
     * @param targetCurrency ISO 4217 target currency (e.g., "INR")
     * @param preference COST or SPEED optimization
     * @return ordered list of RoutingEdge objects (the hops)
     * @throws NoRouteAvailableException if no valid path exists
     */
    public List<RoutingEdge> findOptimalRoute(
            String sourceCurrency,
            String targetCurrency,
            OptimizationPreference preference) {

        log.debug("Finding optimal route: {} → {} ({})", sourceCurrency, targetCurrency, preference);

        // Load all active edges (with active source + target nodes)
        List<RoutingEdge> activeEdges = routingEdgeRepository.findAllActiveEdgesWithActiveNodes();

        if (activeEdges.isEmpty()) {
            throw new NoRouteAvailableException(
                "No active routing corridors available in the network.");
        }

        // Build adjacency graph: nodeId → list of edges
        Map<Long, List<RoutingEdge>> adjacency = buildAdjacencyGraph(activeEdges);

        // Find source nodes (those with baseCurrency = sourceCurrency)
        // Find target nodes (those with baseCurrency = targetCurrency)
        Set<Long> sourceNodeIds = findNodesByBaseCurrency(activeEdges, sourceCurrency);
        Set<Long> targetNodeIds = findNodesByBaseCurrency(activeEdges, targetCurrency);

        if (sourceNodeIds.isEmpty()) {
            throw new NoRouteAvailableException(
                String.format("No active node found with base currency %s.", sourceCurrency));
        }
        if (targetNodeIds.isEmpty()) {
            throw new NoRouteAvailableException(
                String.format("No active node found with base currency %s. " +
                    "Supported currencies: MXN, KES, INR, USD, EUR, GBP.", targetCurrency));
        }

        // Run Dijkstra from all source nodes simultaneously (multi-source)
        return runDijkstra(adjacency, sourceNodeIds, targetNodeIds, preference);
    }

    /**
     * Dijkstra's algorithm on the routing graph.
     * Supports multiple source nodes (pick the best entry point automatically).
     */
    private List<RoutingEdge> runDijkstra(
            Map<Long, List<RoutingEdge>> adjacency,
            Set<Long> sourceNodeIds,
            Set<Long> targetNodeIds,
            OptimizationPreference preference) {

        // dist[nodeId] = best cumulative weight to reach nodeId
        Map<Long, Double> dist = new HashMap<>();
        // prev[nodeId] = the RoutingEdge used to reach nodeId on the best path
        Map<Long, RoutingEdge> prev = new HashMap<>();
        // hop count to detect max-hops violation
        Map<Long, Integer> hopCount = new HashMap<>();

        // Priority queue: (cumulative weight, nodeId)
        PriorityQueue<long[]> pq = new PriorityQueue<>(Comparator.comparingDouble(a -> a[1]));
        // Using long[] with double bits: [nodeId, distBits]

        // Better to use a custom structure
        PriorityQueue<DijkstraNode> queue = new PriorityQueue<>(
            Comparator.comparingDouble(DijkstraNode::weight));

        // Initialize: source nodes have weight 0
        for (Long sourceId : sourceNodeIds) {
            dist.put(sourceId, 0.0);
            hopCount.put(sourceId, 0);
            queue.offer(new DijkstraNode(sourceId, 0.0));
        }

        while (!queue.isEmpty()) {
            DijkstraNode current = queue.poll();
            long currentId = current.nodeId();
            double currentDist = current.weight();

            // Skip if we've found a better path already
            if (currentDist > dist.getOrDefault(currentId, Double.MAX_VALUE)) {
                continue;
            }

            // Check max hops constraint
            int currentHops = hopCount.getOrDefault(currentId, 0);
            if (currentHops >= maxHops) {
                log.debug("Max hops {} reached at node {}", maxHops, currentId);
                continue;
            }

            // Relax outgoing edges
            List<RoutingEdge> edges = adjacency.getOrDefault(currentId, List.of());
            for (RoutingEdge edge : edges) {
                long neighborId = edge.getTargetNode().getId();
                double edgeWeight = computeEdgeWeight(edge, preference);
                double newDist = currentDist + edgeWeight;

                if (newDist < dist.getOrDefault(neighborId, Double.MAX_VALUE)) {
                    dist.put(neighborId, newDist);
                    prev.put(neighborId, edge);
                    hopCount.put(neighborId, currentHops + 1);
                    queue.offer(new DijkstraNode(neighborId, newDist));
                }
            }
        }

        // Find the best reachable target node
        Long bestTargetId = null;
        double bestDist = Double.MAX_VALUE;
        for (Long targetId : targetNodeIds) {
            double d = dist.getOrDefault(targetId, Double.MAX_VALUE);
            if (d < bestDist) {
                bestDist = d;
                bestTargetId = targetId;
            }
        }

        if (bestTargetId == null || bestDist == Double.MAX_VALUE) {
            log.warn("No path found from {} source nodes to {} target nodes",
                sourceNodeIds.size(), targetNodeIds.size());
            throw new NoRouteAvailableException(
                "No valid routing path found. All routes may be blocked due to " +
                "inactive nodes, insufficient liquidity, or unsupported corridors.");
        }

        // Reconstruct path by tracing prev pointers
        List<RoutingEdge> path = reconstructPath(prev, bestTargetId, sourceNodeIds);
        log.info("Optimal route found: {} hops, total weight={:.4f}, preference={}",
            path.size(), bestDist, preference);

        return path;
    }

    /**
     * Reconstructs the edge path from Dijkstra's prev map.
     */
    private List<RoutingEdge> reconstructPath(
            Map<Long, RoutingEdge> prev,
            Long targetId,
            Set<Long> sourceNodeIds) {

        LinkedList<RoutingEdge> path = new LinkedList<>();
        Long current = targetId;

        while (prev.containsKey(current)) {
            RoutingEdge edge = prev.get(current);
            path.addFirst(edge);
            current = edge.getSourceNode().getId();
        }

        if (!sourceNodeIds.contains(current)) {
            throw new NoRouteAvailableException("Path reconstruction failed - broken path chain.");
        }

        return new ArrayList<>(path);
    }

    /**
     * Computes the weight of a routing edge based on optimization preference.
     *
     * COST mode:  weight = baseFee + fxSpreadMargin + transferSurcharge
     *             (all as percentages, e.g. 0.002 = 0.2%)
     * SPEED mode: weight = latencyMs (raw milliseconds)
     */
    private double computeEdgeWeight(RoutingEdge edge, OptimizationPreference preference) {
        return switch (preference) {
            case COST -> edge.getBaseFee().doubleValue()
                       + edge.getFxSpreadMargin().doubleValue()
                       + defaultTransferSurcharge;
            case SPEED -> edge.getLatencyMs().doubleValue();
        };
    }

    /**
     * Builds adjacency map: sourceNodeId → list of outgoing edges.
     */
    private Map<Long, List<RoutingEdge>> buildAdjacencyGraph(List<RoutingEdge> edges) {
        Map<Long, List<RoutingEdge>> graph = new HashMap<>();
        for (RoutingEdge edge : edges) {
            Long sourceId = edge.getSourceNode().getId();
            graph.computeIfAbsent(sourceId, k -> new ArrayList<>()).add(edge);
        }
        log.debug("Built routing graph with {} nodes, {} edges",
            graph.size(), edges.size());
        return graph;
    }

    /**
     * Finds node IDs from active edges where baseCurrency matches.
     */
    private Set<Long> findNodesByBaseCurrency(List<RoutingEdge> edges, String currency) {
        Set<Long> nodeIds = new HashSet<>();
        for (RoutingEdge edge : edges) {
            if (currency.equals(edge.getSourceNode().getBaseCurrency())) {
                nodeIds.add(edge.getSourceNode().getId());
            }
            if (currency.equals(edge.getTargetNode().getBaseCurrency())) {
                nodeIds.add(edge.getTargetNode().getId());
            }
        }
        return nodeIds;
    }

    /** Internal Dijkstra node record */
    private record DijkstraNode(long nodeId, double weight) {}
}
