package com.routing.service;

import com.routing.domain.dto.*;
import com.routing.domain.entity.*;
import com.routing.repository.CorrespondentNodeRepository;
import com.routing.repository.LiquidityBalanceRepository;
import com.routing.repository.RoutingEdgeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for network topology management.
 * Handles node listing, toggling, and network state queries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NetworkService {

    private final CorrespondentNodeRepository nodeRepository;
    private final RoutingEdgeRepository edgeRepository;
    private final LiquidityBalanceRepository liquidityRepository;

    /**
     * Returns all nodes with their active outgoing edges and liquidity snapshots.
     */
    @Transactional(readOnly = true)
    public List<NetworkNodeDto> getAllNodes() {
        List<CorrespondentNode> nodes = nodeRepository.findAll();
        List<RoutingEdge> allEdges = edgeRepository.findAllWithNodes();

        return nodes.stream()
            .map(node -> mapToDto(node, allEdges))
            .toList();
    }

    /**
     * Toggle a node's active state.
     * When deactivated: all routes through this node will be excluded from routing.
     * The RoutingEdge query already filters inactive nodes, so no cascade is needed.
     *
     * @param nodeId the node ID to toggle
     * @return updated node DTO
     */
    @Transactional
    public NetworkNodeDto toggleNode(Long nodeId) {
        CorrespondentNode node = nodeRepository.findById(nodeId)
            .orElseThrow(() -> new EntityNotFoundException(
                "Node not found with ID: " + nodeId));

        boolean wasActive = node.isActive();
        node.setActive(!wasActive);
        node = nodeRepository.save(node);

        log.info("Node {} ({}) toggled: {} → {}",
            nodeId, node.getBankName(), wasActive, node.isActive());

        List<RoutingEdge> allEdges = edgeRepository.findAllWithNodes();
        return mapToDto(node, allEdges);
    }

    // ─── Private mappers ──────────────────────────────────────────────────────

    private NetworkNodeDto mapToDto(CorrespondentNode node, List<RoutingEdge> allEdges) {
        List<NetworkEdgeDto> outgoingEdges = allEdges.stream()
            .filter(e -> e.getSourceNode().getId().equals(node.getId()))
            .map(this::mapEdgeToDto)
            .toList();

        List<LiquidityBalance> balances = liquidityRepository.findByNodeId(node.getId());
        List<LiquidityDto> liquidityDtos = balances.stream()
            .map(lb -> LiquidityDto.builder()
                .currency(lb.getCurrency())
                .availableAmount(lb.getAvailableAmount())
                .reservedAmount(lb.getReservedAmount())
                .minimumRequiredBalance(lb.getMinimumRequiredBalance())
                .updatedAt(lb.getUpdatedAt())
                .build())
            .toList();

        return NetworkNodeDto.builder()
            .id(node.getId())
            .bankName(node.getBankName())
            .country(node.getCountry())
            .baseCurrency(node.getBaseCurrency())
            .active(node.isActive())
            .outgoingEdges(outgoingEdges)
            .liquidityBalances(liquidityDtos)
            .build();
    }

    private NetworkEdgeDto mapEdgeToDto(RoutingEdge edge) {
        return NetworkEdgeDto.builder()
            .id(edge.getId())
            .sourceNodeId(edge.getSourceNode().getId())
            .targetNodeId(edge.getTargetNode().getId())
            .targetBankName(edge.getTargetNode().getBankName())
            .baseFee(edge.getBaseFee())
            .fxSpreadMargin(edge.getFxSpreadMargin())
            .latencyMs(edge.getLatencyMs())
            .active(edge.isActive())
            .corridorCurrencyPair(edge.getCorridorCurrencyPair())
            .build();
    }
}
