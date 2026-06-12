package com.routing.controller;

import com.routing.domain.dto.PaymentInstructionRequest;
import com.routing.domain.dto.RouteResultResponse;
import com.routing.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for route calculation (stateless).
 */
@RestController
@RequestMapping("/api/routing")
@RequiredArgsConstructor
@Slf4j
public class RoutingController {

    private final PaymentService paymentService;

    /**
     * POST /api/routing/calculate
     * Stateless route calculation - does not persist a transaction.
     * Used for interactive route preview in the frontend.
     *
     * ISO 20022 context: simulates the processing of a pacs.008 payment instruction
     * through the routing decision engine.
     *
     * Request body: ISO 20022-aligned PaymentInstructionRequest
     * Response: RouteResultResponse with path, fees, FX, timing
     */
    @PostMapping("/calculate")
    public ResponseEntity<RouteResultResponse> calculateRoute(
            @Valid @RequestBody PaymentInstructionRequest request) {
        log.info("Route calculation request: {} → {} amount={}",
            request.getSourceCurrency(), request.getTargetCurrency(), request.getAmount());
        RouteResultResponse result = paymentService.calculateRoute(request);
        return ResponseEntity.ok(result);
    }
}
