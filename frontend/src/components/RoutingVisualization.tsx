import React from 'react';
import { CheckCircle2, XCircle, Clock, ArrowRight, Building2, Globe } from 'lucide-react';
import type { RouteResultResponse, RouteHopDto } from '../types';
import { COUNTRY_FLAGS, CURRENCY_FLAGS } from '../types';

interface RoutingVisualizationProps {
  result: RouteResultResponse | null;
  isLoading: boolean;
}

export const RoutingVisualization: React.FC<RoutingVisualizationProps> = ({ result, isLoading }) => {
  if (isLoading) {
    return (
      <div className="card p-5">
        <div className="section-header">Routing Path Visualization</div>
        <div className="flex flex-col items-center justify-center py-10 gap-4">
          <div className="relative">
            <div className="w-12 h-12 border-2 border-indigo-500/30 rounded-full" />
            <div className="absolute inset-0 w-12 h-12 border-2 border-indigo-500 border-t-transparent rounded-full animate-spin" />
          </div>
          <div className="text-center">
            <p className="text-slate-300 font-semibold text-sm">Running Dijkstra's Algorithm</p>
            <p className="text-slate-600 text-xs mt-1">Traversing correspondent banking graph...</p>
          </div>
          {/* Skeleton hops */}
          <div className="flex items-center gap-2 w-full px-4">
            {[1, 2, 3].map(i => (
              <React.Fragment key={i}>
                <div className="flex-1 h-16 bg-slate-800 rounded-xl animate-pulse" />
                {i < 3 && <ArrowRight size={16} className="text-slate-700 flex-shrink-0" />}
              </React.Fragment>
            ))}
          </div>
        </div>
      </div>
    );
  }

  if (!result) {
    return (
      <div className="card p-5">
        <div className="section-header">Routing Path Visualization</div>
        <div className="flex flex-col items-center justify-center py-10 text-slate-600 gap-3">
          <Globe size={32} />
          <p className="text-sm">Submit a payment instruction to see the routing path</p>
        </div>
      </div>
    );
  }

  const isFailed = result.status === 'FAILED';

  return (
    <div className={`card p-5 transition-all duration-500 ${
      !isFailed && result.selectedPath?.length > 0 ? 'active-route-glow' : ''
    }`}>
      {/* Header */}
      <div className="flex items-center justify-between mb-4">
        <div className="section-header mb-0">Routing Path Visualization</div>
        <div className={`flex items-center gap-2 px-3 py-1 rounded-full text-xs font-bold border ${
          isFailed
            ? 'bg-red-500/15 text-red-400 border-red-500/30'
            : 'bg-emerald-500/15 text-emerald-400 border-emerald-500/30'
        }`}>
          {isFailed ? (
            <><XCircle size={12} /> ROUTE FAILED</>
          ) : (
            <><CheckCircle2 size={12} /> ROUTE ESTABLISHED</>
          )}
        </div>
      </div>

      {/* Failure state */}
      {isFailed && (
        <div className="flex items-start gap-3 bg-red-500/10 border border-red-500/20 rounded-xl p-4 mb-4">
          <XCircle size={16} className="text-red-400 flex-shrink-0 mt-0.5" />
          <div>
            <p className="text-sm font-semibold text-red-400 mb-1">Route Calculation Failed</p>
            <p className="text-xs text-red-400/70">{result.failureReason}</p>
          </div>
        </div>
      )}

      {/* Path visualization */}
      {!isFailed && result.selectedPath && result.selectedPath.length > 0 && (
        <>
          {/* Path summary bar */}
          <div className="flex items-center gap-1.5 text-xs text-slate-400 mb-4 overflow-x-auto">
            {result.selectedPath.map((hop, idx) => (
              <React.Fragment key={hop.nodeId}>
                <span className="text-slate-300 whitespace-nowrap font-medium">
                  {COUNTRY_FLAGS[hop.country] ?? '🌐'} {hop.bankName}
                </span>
                {idx < result.selectedPath.length - 1 && (
                  <ArrowRight size={12} className="text-indigo-400 flex-shrink-0" />
                )}
              </React.Fragment>
            ))}
          </div>

          {/* Visual hop cards */}
          <div className="flex items-stretch gap-2 overflow-x-auto pb-2">
            {result.selectedPath.map((hop, idx) => (
              <React.Fragment key={hop.nodeId}>
                <HopCard hop={hop} isFirst={idx === 0} isLast={idx === result.selectedPath.length - 1} />
                {idx < result.selectedPath.length - 1 && (
                  <div className="flex flex-col items-center justify-center flex-shrink-0 gap-1">
                    <EdgeConnector
                      latency={result.selectedPath[idx + 1].hopLatencyMs}
                      corridor={result.selectedPath[idx + 1].corridorCurrencyPair}
                      fee={result.selectedPath[idx + 1].hopFee}
                    />
                  </div>
                )}
              </React.Fragment>
            ))}
          </div>

          {/* Timing info */}
          <div className="flex items-center justify-between mt-4 pt-4 border-t border-slate-800">
            <div className="flex items-center gap-2 text-xs text-slate-500">
              <Clock size={12} />
              <span>Total estimated latency:</span>
              <span className="text-slate-300 font-mono font-semibold">
                {result.estimatedExecutionTimeMs}ms
              </span>
              {result.estimatedSettlementTime && (
                <span className="text-indigo-400">({result.estimatedSettlementTime})</span>
              )}
            </div>
            <div className="text-xs text-slate-500">
              {result.selectedPath.length - 1} hop{result.selectedPath.length - 1 !== 1 ? 's' : ''}
            </div>
          </div>
        </>
      )}
    </div>
  );
};

// ─── Hop Card ────────────────────────────────────────────────────────────────

interface HopCardProps {
  hop: RouteHopDto;
  isFirst: boolean;
  isLast: boolean;
}

const HopCard: React.FC<HopCardProps> = ({ hop, isFirst, isLast }) => {
  const flag = COUNTRY_FLAGS[hop.country] ?? '🌐';
  const currencyFlag = CURRENCY_FLAGS[hop.baseCurrency] ?? '';

  return (
    <div className={`flex flex-col items-center gap-2 min-w-[140px] max-w-[160px] rounded-xl p-3 border transition-all duration-300
      ${isFirst
        ? 'bg-blue-500/10 border-blue-500/30'
        : isLast
          ? 'bg-emerald-500/10 border-emerald-500/30'
          : 'bg-indigo-500/10 border-indigo-500/30'
      }`}
    >
      {/* Role badge */}
      <span className={`text-xs font-bold px-2 py-0.5 rounded-full ${
        isFirst
          ? 'bg-blue-500/20 text-blue-400'
          : isLast
            ? 'bg-emerald-500/20 text-emerald-400'
            : 'bg-indigo-500/20 text-indigo-400'
      }`}>
        {isFirst ? 'ORIGIN' : isLast ? 'DESTINATION' : 'INTERMEDIARY'}
      </span>

      {/* Node icon */}
      <div className={`w-10 h-10 rounded-full flex items-center justify-center text-xl border-2 ${
        isFirst ? 'border-blue-500/50' : isLast ? 'border-emerald-500/50' : 'border-indigo-500/50'
      }`}>
        <Building2 size={18} className={
          isFirst ? 'text-blue-400' : isLast ? 'text-emerald-400' : 'text-indigo-400'
        } />
      </div>

      {/* Bank name */}
      <div className="text-center">
        <div className="text-xs font-bold text-slate-200 leading-tight text-center">
          {flag} {hop.bankName}
        </div>
        <div className="text-xs text-slate-500 mt-0.5">
          {hop.country} · {hop.baseCurrency}
        </div>
      </div>

      {/* Hop stats (not shown for origin) */}
      {!isFirst && hop.hopFee != null && (
        <div className="flex flex-col gap-1 w-full text-center">
          <div className="text-xs text-slate-500">
            Fee: <span className="text-amber-400 font-mono">{(hop.hopFee * 100).toFixed(2)}%</span>
          </div>
          {hop.hopLatencyMs && (
            <div className="text-xs text-slate-500">
              +<span className="text-slate-400 font-mono">{hop.hopLatencyMs}ms</span>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

// ─── Edge Connector ───────────────────────────────────────────────────────────

interface EdgeConnectorProps {
  latency?: number;
  corridor?: string;
  fee?: number;
}

const EdgeConnector: React.FC<EdgeConnectorProps> = ({ latency, corridor, fee }) => (
  <div className="flex flex-col items-center gap-1 px-1">
    {corridor && (
      <span className="text-xs text-indigo-400/70 font-mono whitespace-nowrap">{corridor}</span>
    )}
    <svg width="40" height="12" viewBox="0 0 40 12" className="overflow-visible">
      <line
        x1="0" y1="6" x2="40" y2="6"
        stroke="#6366f1"
        strokeWidth="1.5"
        strokeDasharray="4 3"
        className="flow-line"
      />
      <polygon points="34,2 40,6 34,10" fill="#6366f1" />
    </svg>
    {latency && (
      <span className="text-xs text-slate-600 font-mono">{latency}ms</span>
    )}
  </div>
);
