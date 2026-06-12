package com.routing.controller;

import com.routing.domain.dto.NetworkNodeDto;
import com.routing.service.NetworkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for network topology management.
 */
@RestController
@RequestMapping("/api/network")
@RequiredArgsConstructor
@Slf4j
public class NetworkController {

    private final NetworkService networkService;

    /**
     * GET /api/network/nodes
     * Returns all correspondent nodes with their edges and liquidity snapshots.
     * Used by the frontend to render the network simulator panel.
     */
    @GetMapping("/nodes")
    public ResponseEntity<List<NetworkNodeDto>> getAllNodes() {
        List<NetworkNodeDto> nodes = networkService.getAllNodes();
        log.debug("Returning {} network nodes", nodes.size());
        return ResponseEntity.ok(nodes);
    }

    /**
     * POST /api/network/toggle-node/{id}
     * Toggles the active/inactive state of a correspondent node.
     * Inactive nodes are immediately excluded from all route calculations.
     *
     * @param id the node ID to toggle
     * @return updated node state
     */
    @PostMapping("/toggle-node/{id}")
    public ResponseEntity<NetworkNodeDto> toggleNode(@PathVariable Long id) {
        log.info("Toggle request for node ID: {}", id);
        NetworkNodeDto updated = networkService.toggleNode(id);
        return ResponseEntity.ok(updated);
    }
}
