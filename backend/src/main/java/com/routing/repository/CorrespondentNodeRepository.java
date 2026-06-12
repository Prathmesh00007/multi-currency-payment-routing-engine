package com.routing.repository;

import com.routing.domain.entity.CorrespondentNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CorrespondentNodeRepository extends JpaRepository<CorrespondentNode, Long> {

    List<CorrespondentNode> findByActiveTrue();

    List<CorrespondentNode> findByBaseCurrency(String baseCurrency);

    @Query("SELECT n FROM CorrespondentNode n WHERE n.active = true AND n.baseCurrency = :currency")
    List<CorrespondentNode> findActiveNodesByCurrency(String currency);
}
