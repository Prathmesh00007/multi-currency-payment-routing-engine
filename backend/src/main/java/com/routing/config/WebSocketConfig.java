package com.routing.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

/**
 * Spring WebSocket + STOMP configuration.
 *
 * Provides:
 * 1. STOMP broker endpoint at /ws (with SockJS fallback)
 * 2. Simple in-memory message broker for /topic/* destinations
 * 3. Application destination prefix /app for @MessageMapping handlers
 *
 * Frontend connects with:
 *   const socket = new SockJS('http://localhost:8080/ws');
 *   const stompClient = new Client({ webSocketFactory: () => socket });
 *   stompClient.subscribe('/topic/fx-ticks', handler);
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Enable in-memory broker for /topic/* destinations
        registry.enableSimpleBroker("/topic");
        // Frontend @MessageMapping handlers receive on /app/*
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            // Allow CORS from Vite dev server and production
            .setAllowedOriginPatterns(
                "http://localhost:5173",
                "http://localhost:3000",
                "http://127.0.0.1:*"
            )
            // SockJS fallback for browsers without native WebSocket
            .withSockJS();
    }
}
