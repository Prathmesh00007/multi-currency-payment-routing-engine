import { useState, useEffect, useRef, useCallback } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import type { LiveFxTick, FxRateSnapshot } from '../types';
import { getAllFxRates } from '../api/client';

// Key pairs we care about in the ticker
const TRACKED_PAIRS = ['USD/INR', 'USD/MXN', 'USD/KES', 'EUR/USD', 'GBP/USD'];

interface UseFxWebSocketResult {
  ticks: Map<string, LiveFxTick>;
  snapshots: Map<string, FxRateSnapshot>;
  isConnected: boolean;
  connectionStatus: 'CONNECTING' | 'CONNECTED' | 'DISCONNECTED' | 'ERROR';
}

/**
 * Custom hook that:
 * 1. Loads an initial snapshot of all rates from GET /api/fx/rates
 * 2. Connects to Spring STOMP WebSocket at /ws
 * 3. Subscribes to /topic/fx-ticks
 * 4. Returns live tick state for the LiveTickerWidget
 *
 * Handles reconnection gracefully — if backend is unavailable, stays in DISCONNECTED state.
 */
export function useFxWebSocket(): UseFxWebSocketResult {
  const [ticks, setTicks] = useState<Map<string, LiveFxTick>>(new Map());
  const [snapshots, setSnapshots] = useState<Map<string, FxRateSnapshot>>(new Map());
  const [connectionStatus, setConnectionStatus] =
    useState<'CONNECTING' | 'CONNECTED' | 'DISCONNECTED' | 'ERROR'>('DISCONNECTED');

  const stompClientRef = useRef<Client | null>(null);

  // Load initial snapshot on mount
  useEffect(() => {
    getAllFxRates()
      .then(data => {
        const snap = new Map<string, FxRateSnapshot>();
        Object.entries(data).forEach(([pair, info]: [string, any]) => {
          snap.set(pair, {
            pair,
            rate: info.rate ? Number(info.rate) : null,
            source: info.source,
            live: info.live ?? false,
          });
        });
        setSnapshots(snap);
      })
      .catch(err => {
        console.warn('[FxWebSocket] Failed to load initial snapshot:', err?.message);
      });
  }, []);

  const handleTick = useCallback((rawBody: string) => {
    try {
      const tick: LiveFxTick = JSON.parse(rawBody);
      if (!tick.pair) return;

      setTicks(prev => {
        const next = new Map(prev);
        next.set(tick.pair, tick);
        return next;
      });

      // Also update the snapshot with live tick data
      setSnapshots(prev => {
        const next = new Map(prev);
        next.set(tick.pair, {
          pair: tick.pair,
          rate: tick.price,
          source: tick.source,
          live: true,
        });
        return next;
      });
    } catch (e) {
      console.debug('[FxWebSocket] Failed to parse tick:', e);
    }
  }, []);

  useEffect(() => {
    setConnectionStatus('CONNECTING');

    const client = new Client({
      webSocketFactory: () => new SockJS('https://multi-currency-payment-routing-engine.onrender.com/ws'),
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        setConnectionStatus('CONNECTED');
        console.info('[FxWebSocket] STOMP connected. Subscribing to /topic/fx-ticks...');
        client.subscribe('/topic/fx-ticks', message => {
          handleTick(message.body);
        });
      },
      onDisconnect: () => {
        setConnectionStatus('DISCONNECTED');
        console.info('[FxWebSocket] STOMP disconnected.');
      },
      onStompError: (frame) => {
        setConnectionStatus('ERROR');
        console.warn('[FxWebSocket] STOMP error:', frame.headers?.message);
      },
      onWebSocketError: () => {
        setConnectionStatus('DISCONNECTED');
      },
    });

    stompClientRef.current = client;

    try {
      client.activate();
    } catch (e) {
      setConnectionStatus('ERROR');
    }

    return () => {
      client.deactivate();
    };
  }, [handleTick]);

  return {
    ticks,
    snapshots,
    isConnected: connectionStatus === 'CONNECTED',
    connectionStatus,
  };
}
