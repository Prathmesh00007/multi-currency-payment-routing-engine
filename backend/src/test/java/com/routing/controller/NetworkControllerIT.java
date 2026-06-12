package com.routing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routing.domain.dto.PaymentInstructionRequest;
import com.routing.domain.enums.OptimizationPreference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the REST API endpoints.
 * Uses the full Spring Boot context with H2 in-memory database.
 * MockDataSeeder runs automatically to populate the network graph.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NetworkControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getNodes_returnsAllNodes() throws Exception {
        mockMvc.perform(get("/api/network/nodes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(14))
            .andExpect(jsonPath("$[0].bankName").isNotEmpty())
            .andExpect(jsonPath("$[0].baseCurrency").isNotEmpty());
    }

    @Test
    void toggleNode_validId_togglesActiveState() throws Exception {
        // Get nodes first to find a valid ID
        String nodesResponse = mockMvc.perform(get("/api/network/nodes"))
            .andReturn().getResponse().getContentAsString();

        // Toggle node 1
        mockMvc.perform(post("/api/network/toggle-node/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.active").value(false));

        // Toggle back
        mockMvc.perform(post("/api/network/toggle-node/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void toggleNode_invalidId_returns404() throws Exception {
        mockMvc.perform(post("/api/network/toggle-node/9999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void calculateRoute_usdToInr_returnsValidRoute() throws Exception {
        PaymentInstructionRequest request = new PaymentInstructionRequest();
        request.setSourceCurrency("USD");
        request.setTargetCurrency("INR");
        request.setAmount(new BigDecimal("10000"));
        request.setOptimizationPreference(OptimizationPreference.COST);

        mockMvc.perform(post("/api/routing/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ROUTED"))
            .andExpect(jsonPath("$.selectedPath").isArray())
            .andExpect(jsonPath("$.selectedPath.length()").value(3))
            .andExpect(jsonPath("$.totalFeePercentage").isNumber())
            .andExpect(jsonPath("$.baseFxRate").isNumber());
    }

    @Test
    void calculateRoute_usdToMxn_speedMode_returnsRoute() throws Exception {
        PaymentInstructionRequest request = new PaymentInstructionRequest();
        request.setSourceCurrency("USD");
        request.setTargetCurrency("MXN");
        request.setAmount(new BigDecimal("5000"));
        request.setOptimizationPreference(OptimizationPreference.SPEED);

        mockMvc.perform(post("/api/routing/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ROUTED"))
            .andExpect(jsonPath("$.estimatedExecutionTimeMs").isNumber());
    }

    @Test
    void calculateRoute_unsupportedTargetCurrency_returns400() throws Exception {
        PaymentInstructionRequest request = new PaymentInstructionRequest();
        request.setSourceCurrency("USD");
        request.setTargetCurrency("JPY");  // Not supported
        request.setAmount(new BigDecimal("1000"));
        request.setOptimizationPreference(OptimizationPreference.COST);

        mockMvc.perform(post("/api/routing/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_CURRENCY_PAIR"));
    }

    @Test
    void calculateRoute_invalidAmount_returns400() throws Exception {
        PaymentInstructionRequest request = new PaymentInstructionRequest();
        request.setSourceCurrency("USD");
        request.setTargetCurrency("INR");
        request.setAmount(new BigDecimal("-100"));
        request.setOptimizationPreference(OptimizationPreference.COST);

        mockMvc.perform(post("/api/routing/calculate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));
    }

    @Test
    void submitTransaction_validPayment_createsAndRoutes() throws Exception {
        PaymentInstructionRequest request = new PaymentInstructionRequest();
        request.setSourceCurrency("USD");
        request.setTargetCurrency("KES");
        request.setAmount(new BigDecimal("25000"));
        request.setOptimizationPreference(OptimizationPreference.COST);

        String responseBody = mockMvc.perform(post("/api/transactions/submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.transactionId").isNumber())
            .andExpect(jsonPath("$.status").value("ROUTED"))
            .andReturn().getResponse().getContentAsString();

        // Extract ID and fetch
        Long txnId = objectMapper.readTree(responseBody).get("transactionId").asLong();

        mockMvc.perform(get("/api/transactions/" + txnId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transactionId").value(txnId));
    }

    @Test
    void getTransaction_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/transactions/99999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }
}
