package com.routing.repository;

import com.routing.domain.entity.RoutingEdge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoutingEdgeRepository extends JpaRepository<RoutingEdge, Long> {

    /**
     * Fetch all active edges where both source and target nodes are active.
     * Critical for routing graph construction - only fully-active corridors are eligible.
     */
    @Query("""
        SELECT e FROM RoutingEdge e
        JOIN FETCH e.sourceNode s
        JOIN FETCH e.targetNode t
        WHERE e.active = true
          AND s.active = true
          AND t.active = true
    """)
    List<RoutingEdge> findAllActiveEdgesWithActiveNodes();

    @Query("SELECT e FROM RoutingEdge e JOIN FETCH e.sourceNode JOIN FETCH e.targetNode")
    List<RoutingEdge> findAllWithNodes();

    List<RoutingEdge> findBySourceNodeId(Long sourceNodeId);

    List<RoutingEdge> findByTargetNodeId(Long targetNodeId);
}
