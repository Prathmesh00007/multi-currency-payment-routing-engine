import React from 'react';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell
} from 'recharts';
import {
  DollarSign, TrendingDown, Clock, Zap,
  ArrowUpRight, Info, RefreshCw
} from 'lucide-react';
import type { RouteResultResponse } from '../types';

interface FinancialLedgerProps {
  result: RouteResultResponse | null;
  isLoading: boolean;
}

export const FinancialLedger: React.FC<FinancialLedgerProps> = ({ result, isLoading }) => {
  if (isLoading) {
    return (
      <div className="card p-5">
        <div className="section-header">Financial Metrics</div>
        <div className="space-y-3">
          {[...Array(6)].map((_, i) => (
            <div key={i} className="h-12 bg-slate-800 rounded-lg animate-pulse" />
          ))}
        </div>
      </div>
    );
  }

  if (!result || result.status === 'PENDING') {
    return (
      <div className="card p-5">
        <div className="section-header">Financial Metrics Ledger</div>
        <div className="flex flex-col items-center justify-center py-8 text-slate-600 gap-2">
          <DollarSign size={28} />
          <p className="text-xs text-center">Financial breakdown will appear here after route calculation</p>
        </div>
      </div>
    );
  }

  const isFailed = result.status === 'FAILED';

  // Fee chart data
  const feeChartData = result.feeBreakdown?.map(fb => ({
    name: `${fb.fromBank.split(' ')[0]}→${fb.toBank.split(' ')[0]}`,
    baseFee: Number((fb.baseFeeAbsolute ?? 0).toFixed(2)),
    fxSpread: Number((fb.fxSpreadAbsolute ?? 0).toFixed(2)),
  })) ?? [];

  return (
    <div className="card p-5 space-y-5 animate-fade-in">
      <div className="section-header">Financial Metrics Ledger</div>

      {/* Summary metric cards */}
      {!isFailed && (
        <div className="grid grid-cols-2 gap-3">
          <MetricCard
            icon={<DollarSign size={14} />}
            label="Total Cost (USD)"
            value={result.totalCost != null
              ? `$${Number(result.totalCost).toFixed(2)}`
              : '—'}
            sub={result.totalFeePercentage != null
              ? `${(Number(result.totalFeePercentage) * 100).toFixed(3)}% of principal`
              : undefined}
            color="indigo"
          />
          <MetricCard
            icon={<Clock size={14} />}
            label="Settlement Time"
            value={result.estimatedSettlementTime ?? '—'}
            sub={result.estimatedExecutionTimeMs != null
              ? `${result.estimatedExecutionTimeMs}ms total latency`
              : undefined}
            color="blue"
          />
          <MetricCard
            icon={<TrendingDown size={14} />}
            label="FX Spread Cost"
            value={result.fxSpreadCost != null
              ? `$${Number(result.fxSpreadCost).toFixed(2)}`
              : '—'}
            color="amber"
          />
          <MetricCard
            icon={<ArrowUpRight size={14} />}
            label="Converted Amount"
            value={result.convertedAmount != null
              ? `${Number(result.convertedAmount).toLocaleString()} ${result.targetCurrency}`
              : '—'}
            color="emerald"
          />
        </div>
      )}

      {/* FX Rate Details */}
      {!isFailed && result.baseFxRate && (
        <div className="space-y-2">
          <div className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
            FX Rate Details (ISO 20022: ExchangeRate)
          </div>
          <div className="bg-slate-800/60 rounded-xl border border-slate-700/50 divide-y divide-slate-700/50">
            <FxRow label="Base Mid-Market Rate" value={`1 ${result.sourceCurrency} = ${Number(result.baseFxRate).toFixed(4)} ${result.targetCurrency}`} />
            <FxRow label="Effective Rate (w/ spread)" value={result.effectiveFxRate ? `1 ${result.sourceCurrency} = ${Number(result.effectiveFxRate).toFixed(4)} ${result.targetCurrency}` : '—'} />
            <FxRow
              label="FX Data Source"
              value={result.fxSourceLabel ?? result.fxRateSource ?? '—'}
              badge={
                result.fxRateSource === 'FINNHUB_WEBSOCKET'
                  ? { text: 'LIVE TICK', color: 'emerald' }
                  : result.fxRateSource === 'EXCHANGE_RATE_API'
                    ? { text: 'ER-API', color: 'blue' }
                    : result.fxRateSource === 'FRANKFURTER_API'
                      ? { text: 'ECB REF', color: 'indigo' }
                      : { text: 'MOCK', color: 'amber' }
              }
            />
            {result.fxIsLiveTick && (
              <div className="px-3 py-2 flex items-center gap-2">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
                <span className="text-xs text-emerald-400">Real-time tick from Finnhub WebSocket</span>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Fee Breakdown per hop */}
      {!isFailed && result.feeBreakdown && result.feeBreakdown.length > 0 && (
        <div className="space-y-2">
          <div className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
            Correspondent Fee Breakdown
          </div>
          <div className="bg-slate-800/60 rounded-xl border border-slate-700/50 divide-y divide-slate-700/50">
            {result.feeBreakdown.map((fb, idx) => (
              <div key={idx} className="px-3 py-2.5">
                <div className="flex items-center justify-between mb-1">
                  <span className="text-xs font-semibold text-slate-300">
                    {fb.fromBank} → {fb.toBank}
                  </span>
                  <span className="text-xs font-mono text-slate-400">{fb.corridorCurrencyPair}</span>
                </div>
                <div className="flex gap-3 text-xs">
                  <span className="text-slate-500">
                    Base: <span className="text-indigo-400 font-mono">${Number(fb.baseFeeAbsolute).toFixed(2)}</span>
                  </span>
                  <span className="text-slate-500">
                    Spread: <span className="text-amber-400 font-mono">${Number(fb.fxSpreadAbsolute).toFixed(2)}</span>
                  </span>
                  <span className="text-slate-500">
                    Latency: <span className="text-blue-400 font-mono">{fb.latencyMs}ms</span>
                  </span>
                </div>
              </div>
            ))}

            {/* Totals row */}
            <div className="px-3 py-2.5 bg-slate-800/40">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-slate-200">TOTAL FEES + SPREAD</span>
                <span className="text-sm font-bold font-mono text-white">
                  ${Number(result.totalCost ?? 0).toFixed(2)}
                </span>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Bar chart for fees */}
      {!isFailed && feeChartData.length > 0 && (
        <div className="space-y-2">
          <div className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
            Fee Distribution by Hop
          </div>
          <div className="bg-slate-800/40 rounded-xl border border-slate-700/50 p-3" style={{ height: 140 }}>
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={feeChartData} margin={{ top: 5, right: 5, bottom: 5, left: -20 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#334155" vertical={false} />
                <XAxis dataKey="name" tick={{ fill: '#64748b', fontSize: 10 }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fill: '#64748b', fontSize: 10 }} axisLine={false} tickLine={false} />
                <Tooltip
                  contentStyle={{ backgroundColor: '#1e293b', border: '1px solid #334155', borderRadius: '8px', fontSize: 11 }}
                  formatter={(value: number) => [`$${value.toFixed(2)}`, '']}
                />
                <Bar dataKey="baseFee" name="Base Fee" stackId="a" fill="#6366f1" radius={[0, 0, 0, 0]} />
                <Bar dataKey="fxSpread" name="FX Spread" stackId="a" fill="#f59e0b" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      )}

      {/* Stellar Network Fee */}
      {!isFailed && result.stellarFeeXlm != null && (
        <div className="space-y-2">
          <div className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
            Stellar Network (Testnet)
          </div>
          <div className="bg-slate-800/60 rounded-xl border border-slate-700/50 divide-y divide-slate-700/50">
            <FxRow
              label="Routing Source"
              value={result.routingSource === 'STELLAR_TESTNET' ? 'Stellar Horizon Testnet' : 'Mock Stellar Fallback'}
              badge={result.routingSource === 'STELLAR_TESTNET'
                ? { text: 'TESTNET', color: 'blue' }
                : { text: 'MOCK', color: 'amber' }
              }
            />
            <FxRow
              label="Network Fee (XLM)"
              value={`${Number(result.stellarFeeXlm).toFixed(8)} XLM`}
            />
            <FxRow
              label="Network Fee (USD equiv.)"
              value={result.stellarFeeUsd != null ? `~$${Number(result.stellarFeeUsd).toFixed(6)}` : '—'}
            />
          </div>
        </div>
      )}

      {/* Liquidity Check */}
      {!isFailed && result.liquidityCheckDetail && (
        <div className={`flex items-start gap-2 rounded-xl p-3 border text-xs ${
          result.liquidityCheckPassed
            ? 'bg-emerald-500/10 border-emerald-500/20 text-emerald-400'
            : 'bg-amber-500/10 border-amber-500/20 text-amber-400'
        }`}>
          <span className="flex-shrink-0 mt-0.5">{result.liquidityCheckPassed ? '✓' : '⚠'}</span>
          <div>
            <div className="font-bold mb-0.5">Internal Liquidity Check</div>
            <div className="opacity-80">{result.liquidityCheckDetail}</div>
            <div className="text-slate-600 mt-1" style={{fontSize:'10px'}}>⚠ Liquidity balances are synthetic mock data</div>
          </div>
        </div>
      )}

      {/* Data source disclaimer */}
      <div className="flex items-start gap-2 text-slate-600 border-t border-slate-800 pt-3">
        <Info size={11} className="flex-shrink-0 mt-0.5" />
        <p className="text-xs leading-relaxed">
          <strong className="text-slate-500">Fees, spreads, and liquidity</strong> are synthetic mock data.
          FX: {result.fxSourceLabel ?? result.fxRateSource}.
          Routing: {result.routingSource === 'STELLAR_TESTNET' ? 'Stellar Testnet (live)' : 'Mock fallback'}.
        </p>
      </div>
    </div>
  );
};

// ─── Sub-components ───────────────────────────────────────────────────────────

interface MetricCardProps {
  icon: React.ReactNode;
  label: string;
  value: string;
  sub?: string;
  color: 'indigo' | 'blue' | 'amber' | 'emerald';
}

const COLOR_MAP = {
  indigo: 'text-indigo-400 bg-indigo-500/10 border-indigo-500/20',
  blue: 'text-blue-400 bg-blue-500/10 border-blue-500/20',
  amber: 'text-amber-400 bg-amber-500/10 border-amber-500/20',
  emerald: 'text-emerald-400 bg-emerald-500/10 border-emerald-500/20',
};

const MetricCard: React.FC<MetricCardProps> = ({ icon, label, value, sub, color }) => (
  <div className={`rounded-xl p-3 border ${COLOR_MAP[color]}`}>
    <div className="flex items-center gap-1.5 mb-1.5">
      <span className="opacity-80">{icon}</span>
      <span className="text-xs text-slate-500 uppercase tracking-wider">{label}</span>
    </div>
    <div className="text-sm font-bold font-mono text-slate-100">{value}</div>
    {sub && <div className="text-xs text-slate-600 mt-0.5">{sub}</div>}
  </div>
);

interface FxRowProps {
  label: string;
  value: string;
  badge?: { text: string; color: 'emerald' | 'amber' | 'blue' | 'indigo' };
}

const FxRow: React.FC<FxRowProps> = ({ label, value, badge }) => (
  <div className="flex items-center justify-between px-3 py-2">
    <span className="text-xs text-slate-500">{label}</span>
    <div className="flex items-center gap-2">
      <span className="text-xs font-mono text-slate-300">{value}</span>
      {badge && (
        <span className={`text-xs font-bold px-1.5 py-0.5 rounded ${
          badge.color === 'emerald' ? 'bg-emerald-500/20 text-emerald-400'
          : badge.color === 'blue'  ? 'bg-blue-500/20 text-blue-400'
          : badge.color === 'indigo'? 'bg-indigo-500/20 text-indigo-400'
          : 'bg-amber-500/20 text-amber-400'
        }`}>
          {badge.text}
        </span>
      )}
    </div>
  </div>
);
