import React, { useState, useEffect, useCallback } from 'react';
import { Activity, AlertCircle, RefreshCw } from 'lucide-react';
import { PaymentInitiator } from './components/PaymentInitiator';
import { NetworkSimulator } from './components/NetworkSimulator';
import { RoutingVisualization } from './components/RoutingVisualization';
import { FinancialLedger } from './components/FinancialLedger';
import { TransactionDetail } from './components/TransactionDetail';
import { LiveTickerWidget } from './components/LiveTickerWidget';
import { useFxWebSocket } from './hooks/useFxWebSocket';
import {
  getNetworkNodes,
  toggleNode as apiToggleNode,
  calculateRoute,
} from './api/client';
import type {
  NetworkNodeDto,
  RouteResultResponse,
  PaymentInstructionRequest,
  ApiErrorResponse,
} from './types';

/**
 * Main Dashboard — ISO 20022 Multi-Currency Payment Routing Engine
 * Hybrid architecture: Live FX ticks (Finnhub/ER-API/Frankfurter) + Stellar Testnet routing
 */
const Dashboard: React.FC = () => {
  const [nodes, setNodes] = useState<NetworkNodeDto[]>([]);
  const [routeResult, setRouteResult] = useState<RouteResultResponse | null>(null);
  const [isLoadingNodes, setIsLoadingNodes] = useState(true);
  const [isCalculating, setIsCalculating] = useState(false);
  const [lastRequest, setLastRequest] = useState<PaymentInstructionRequest | null>(null);
  const [globalError, setGlobalError] = useState<string | null>(null);
  const [toggleError, setToggleError] = useState<string | null>(null);

  // Live FX WebSocket hook
  const { ticks, snapshots, isConnected, connectionStatus } = useFxWebSocket();

  const loadNodes = useCallback(async () => {
    try {
      setIsLoadingNodes(true);
      const data = await getNetworkNodes();
      setNodes(data);
      setGlobalError(null);
    } catch (err) {
      const apiErr = err as ApiErrorResponse;
      setGlobalError(apiErr?.message ?? 'Failed to connect to the routing engine backend.');
    } finally {
      setIsLoadingNodes(false);
    }
  }, []);

  useEffect(() => { loadNodes(); }, [loadNodes]);

  const handleToggleNode = useCallback(async (nodeId: number) => {
    setToggleError(null);
    try {
      const updatedNode = await apiToggleNode(nodeId);
      setNodes(prev => prev.map(n => n.id === nodeId ? updatedNode : n));

      if (lastRequest) {
        setIsCalculating(true);
        try {
          const result = await calculateRoute(lastRequest);
          setRouteResult(result);
        } catch (e) {
          const err = e as ApiErrorResponse;
          setRouteResult(prev => prev ? {
            ...prev,
            status: 'FAILED',
            failureReason: err?.message ?? 'Route recalculation failed after node toggle.',
            selectedPath: [],
            feeBreakdown: [],
          } : null);
        } finally {
          setIsCalculating(false);
        }
      }
    } catch (err) {
      const apiErr = err as ApiErrorResponse;
      setToggleError(apiErr?.message ?? 'Failed to toggle node state.');
    }
  }, [lastRequest]);

  const handleCalculate = useCallback(async (request: PaymentInstructionRequest) => {
    setLastRequest(request);
    setIsCalculating(true);
    setGlobalError(null);
    try {
      const result = await calculateRoute(request);
      setRouteResult(result);
    } catch (err) {
      const apiErr = err as ApiErrorResponse;
      setRouteResult({
        status: 'FAILED',
        sourceCurrency: request.sourceCurrency,
        targetCurrency: request.targetCurrency,
        amount: request.amount,
        optimizationPreference: request.optimizationPreference,
        selectedPath: [],
        feeBreakdown: [],
        failureReason: apiErr?.message ?? 'Routing calculation failed.',
        calculatedAt: new Date().toISOString(),
      });
    } finally {
      setIsCalculating(false);
    }
  }, []);

  const activeNodes = nodes.filter(n => n.active).length;
  const backendDown = !!globalError && nodes.length === 0;

  return (
    <div className="min-h-screen bg-slate-950 flex flex-col">

      {/* ─── Top Navigation Bar ─────────────────────────────────────────── */}
      <header className="sticky top-0 z-50 border-b border-slate-800/80 bg-slate-950/90 backdrop-blur-sm">
        <div className="max-w-screen-2xl mx-auto px-4 py-3 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-7 h-7 rounded-lg bg-indigo-600 flex items-center justify-center">
              <Activity size={14} className="text-white" />
            </div>
            <div>
              <h1 className="text-sm font-bold text-slate-100 leading-none">
                <span className="gradient-text">ISO 20022</span> Payment Routing Engine
              </h1>
              <p className="text-xs text-slate-600 leading-none mt-0.5">
                Stellar Testnet · Multi-Tier FX · Correspondent Banking
              </p>
            </div>
          </div>

          <div className="flex items-center gap-4">
            {!backendDown && (
              <div className="hidden md:flex items-center gap-3 text-xs">
                <StatusPill
                  color={activeNodes > 4 ? 'green' : activeNodes > 2 ? 'amber' : 'red'}
                  label={`${activeNodes}/${nodes.length} Anchors Active`}
                />
                <StatusPill
                  color={isConnected ? 'green' : 'amber'}
                  label={isConnected ? 'WS Connected' : connectionStatus}
                />
              </div>
            )}
            <button
              onClick={loadNodes}
              disabled={isLoadingNodes}
              className="text-slate-500 hover:text-slate-300 transition-colors p-1.5 rounded-lg hover:bg-slate-800"
              title="Refresh network"
            >
              <RefreshCw size={14} className={isLoadingNodes ? 'animate-spin' : ''} />
            </button>
          </div>
        </div>
      </header>

      {/* ─── Live FX Ticker ─────────────────────────────────────────────── */}
      <LiveTickerWidget
        ticks={ticks}
        snapshots={snapshots}
        isConnected={isConnected}
        connectionStatus={connectionStatus}
      />

      {/* ─── Backend connection error ────────────────────────────────────── */}
      {backendDown && (
        <div className="max-w-screen-2xl mx-auto px-4 py-3 w-full">
          <div className="flex items-start gap-3 bg-red-500/10 border border-red-500/30 rounded-xl p-4">
            <AlertCircle size={16} className="text-red-400 flex-shrink-0 mt-0.5" />
            <div>
              <p className="text-sm font-semibold text-red-400">Backend Connection Failed</p>
              <p className="text-xs text-red-400/70 mt-0.5">{globalError}</p>
              <p className="text-xs text-slate-500 mt-1">
                Ensure Spring Boot is running:{' '}
                <code className="font-mono bg-slate-800 px-1 rounded">
                  cd backend && mvn spring-boot:run
                </code>
              </p>
            </div>
          </div>
        </div>
      )}

      {/* ─── Toggle error banner ─────────────────────────────────────────── */}
      {toggleError && (
        <div className="max-w-screen-2xl mx-auto px-4 py-1.5 w-full">
          <div className="flex items-center gap-2 bg-amber-500/10 border border-amber-500/30 rounded-lg px-3 py-2">
            <AlertCircle size={12} className="text-amber-400" />
            <p className="text-xs text-amber-400">{toggleError}</p>
            <button
              onClick={() => setToggleError(null)}
              className="ml-auto text-amber-400/50 hover:text-amber-400 text-xs"
            >
              ✕
            </button>
          </div>
        </div>
      )}

      {/* ─── Main 3-column Layout ────────────────────────────────────────── */}
      <main className="flex-1 max-w-screen-2xl mx-auto w-full px-4 py-4">
        <div className="grid grid-cols-12 gap-4">

          {/* Left: Network Simulator (Stellar anchor nodes) */}
          <div className="col-span-12 lg:col-span-3">
            <NetworkSimulator
              nodes={nodes}
              onToggleNode={handleToggleNode}
              isLoading={isLoadingNodes}
            />
          </div>

          {/* Center: Payment Form + Route Viz + Transaction Detail */}
          <div className="col-span-12 lg:col-span-6 flex flex-col gap-4">
            <PaymentInitiator
              onCalculate={handleCalculate}
              isLoading={isCalculating}
            />
            <RoutingVisualization
              result={routeResult}
              isLoading={isCalculating}
            />
            <TransactionDetail
              result={routeResult}
              isLoading={isCalculating}
            />
          </div>

          {/* Right: Financial Metrics Ledger */}
          <div className="col-span-12 lg:col-span-3">
            <FinancialLedger
              result={routeResult}
              isLoading={isCalculating}
            />
          </div>
        </div>
      </main>

      {/* ─── Footer ─────────────────────────────────────────────────────── */}
      <footer className="border-t border-slate-800 py-3 px-4">
        <div className="max-w-screen-2xl mx-auto flex items-center justify-between text-xs text-slate-600">
          <div className="flex items-center gap-3">
            <span>ISO 20022 Payment Routing Engine</span>
            <span>·</span>
            <span>pacs.008 · Stellar Testnet · Multi-Tier FX</span>
          </div>
          <div className="flex items-center gap-2">
            <span className="text-amber-700">⚠ Fees/liquidity: mock data</span>
            <span>·</span>
            <span>
              FX: {routeResult?.fxSourceLabel ?? routeResult?.fxRateSource ?? 'Loading...'}
            </span>
            <span>·</span>
            <span>
              Routing: {routeResult?.routingSource === 'STELLAR_TESTNET'
                ? 'Stellar Horizon'
                : routeResult?.routingSource
                  ? 'Mock Fallback'
                  : 'Waiting...'}
            </span>
          </div>
        </div>
      </footer>
    </div>
  );
};

// ─── Status Pill ──────────────────────────────────────────────────────────────

interface StatusPillProps { color: 'green' | 'amber' | 'red' | 'blue'; label: string; }
const StatusPill: React.FC<StatusPillProps> = ({ color, label }) => {
  const colors = {
    green: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30',
    amber: 'bg-amber-500/10 text-amber-400 border-amber-500/30',
    red: 'bg-red-500/10 text-red-400 border-red-500/30',
    blue: 'bg-blue-500/10 text-blue-400 border-blue-500/30',
  };
  const dotColors = {
    green: 'bg-emerald-400', amber: 'bg-amber-400',
    red: 'bg-red-400', blue: 'bg-blue-400',
  };
  return (
    <div className={`flex items-center gap-1.5 px-2.5 py-1 rounded-full border text-xs font-semibold ${colors[color]}`}>
      <div className={`w-1.5 h-1.5 rounded-full ${dotColors[color]} animate-pulse`} />
      {label}
    </div>
  );
};

export default Dashboard;
