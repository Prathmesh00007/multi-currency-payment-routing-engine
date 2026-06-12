package com.routing.controller;

import com.routing.domain.dto.PaymentInstructionRequest;
import com.routing.domain.dto.RouteResultResponse;
import com.routing.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for payment transaction lifecycle management.
 */
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

    private final PaymentService paymentService;

    /**
     * POST /api/transactions/submit
     * Creates a new payment transaction and routes it immediately.
     * Persists the result to the database.
     *
     * ISO 20022: equivalent to submitting a pacs.008 message to the payment gateway.
     *
     * @return RouteResultResponse with transactionId for subsequent status polling
     */
    @PostMapping("/submit")
    public ResponseEntity<RouteResultResponse> submitTransaction(
            @Valid @RequestBody PaymentInstructionRequest request) {
        log.info("Transaction submission: {} → {} amount={}",
            request.getSourceCurrency(), request.getTargetCurrency(), request.getAmount());
        RouteResultResponse result = paymentService.submitTransaction(request);
        HttpStatus status = result.getTransactionId() != null
            ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result);
    }

    /**
     * GET /api/transactions/{id}
     * Retrieves a stored transaction by ID.
     * Supports frontend polling for transaction status updates.
     *
     * @param id the transaction ID
     * @return stored RouteResultResponse
     */
    @GetMapping("/{id}")
    public ResponseEntity<RouteResultResponse> getTransaction(@PathVariable Long id) {
        log.debug("Fetching transaction: {}", id);
        RouteResultResponse result = paymentService.getTransaction(id);
        return ResponseEntity.ok(result);
    }
}
