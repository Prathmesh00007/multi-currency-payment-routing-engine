import React from 'react';
import { Receipt, Hash, ArrowRight, CheckCircle2, XCircle, Clock, Loader2 } from 'lucide-react';
import type { RouteResultResponse } from '../types';
import { CURRENCY_FLAGS } from '../types';

interface TransactionDetailProps {
  result: RouteResultResponse | null;
  isLoading: boolean;
}

export const TransactionDetail: React.FC<TransactionDetailProps> = ({ result, isLoading }) => {
  if (!result && !isLoading) {
    return (
      <div className="card p-5">
        <div className="section-header">Transaction Detail</div>
        <div className="flex flex-col items-center justify-center py-6 text-slate-600 gap-2">
          <Receipt size={24} />
          <p className="text-xs">No transaction yet</p>
        </div>
      </div>
    );
  }

  return (
    <div className="card p-5 animate-fade-in">
      <div className="flex items-center justify-between mb-4">
        <div className="section-header mb-0">Transaction Detail</div>
        {isLoading && <Loader2 size={12} className="text-slate-500 animate-spin" />}
      </div>

      {result && (
        <div className="space-y-3">
          {/* Status + ID */}
          <div className="flex items-center justify-between">
            <StatusBadge status={result.status} />
            {result.transactionId && (
              <div className="flex items-center gap-1.5 text-xs text-slate-500 font-mono">
                <Hash size={11} />
                <span>TXN-{String(result.transactionId).padStart(8, '0')}</span>
              </div>
            )}
          </div>

          {/* Route summary */}
          <div className="bg-slate-800/60 rounded-xl border border-slate-700/50 p-3">
            <div className="flex items-center gap-2 mb-2">
              <span className="text-xs text-slate-500 uppercase tracking-wider font-semibold">Payment Route</span>
            </div>
            <div className="flex items-center gap-1.5 flex-wrap">
              {result.selectedPath && result.selectedPath.length > 0 ? (
                result.selectedPath.map((hop, idx) => (
                  <React.Fragment key={hop.nodeId}>
                    <span className="text-xs font-semibold text-slate-300">
                      {hop.bankName}
                    </span>
                    {idx < result.selectedPath.length - 1 && (
                      <ArrowRight size={10} className="text-indigo-400 flex-shrink-0" />
                    )}
                  </React.Fragment>
                ))
              ) : (
                <span className="text-xs text-slate-600">
                  {result.failureReason ?? 'No route available'}
                </span>
              )}
            </div>
          </div>

          {/* Key details grid */}
          <div className="grid grid-cols-2 gap-2">
            <DetailItem
              label="Source Currency"
              value={`${CURRENCY_FLAGS[result.sourceCurrency] ?? ''} ${result.sourceCurrency}`}
            />
            <DetailItem
              label="Target Currency"
              value={`${CURRENCY_FLAGS[result.targetCurrency] ?? ''} ${result.targetCurrency}`}
            />
            <DetailItem
              label="Amount"
              value={`$${Number(result.amount).toLocaleString()} USD`}
            />
            <DetailItem
              label="Optimization"
              value={result.optimizationPreference === 'COST' ? '💰 Min Cost' : '⚡ Min Latency'}
            />
            {result.totalCost != null && (
              <DetailItem
                label="Total Cost"
                value={`$${Number(result.totalCost).toFixed(2)} USD`}
                highlight
              />
            )}
            {result.estimatedSettlementTime && (
              <DetailItem
                label="Settlement ETA"
                value={result.estimatedSettlementTime}
              />
            )}
          </div>

          {/* Failure reason */}
          {result.status === 'FAILED' && result.failureReason && (
            <div className="flex items-start gap-2 bg-red-500/10 border border-red-500/20 rounded-xl p-3">
              <XCircle size={12} className="text-red-400 flex-shrink-0 mt-0.5" />
              <div>
                <div className="text-xs font-semibold text-red-400 mb-0.5">Failure Reason</div>
                <div className="text-xs text-red-400/70">{result.failureReason}</div>
              </div>
            </div>
          )}

          {/* Timestamp */}
          {result.calculatedAt && (
            <div className="flex items-center gap-1.5 text-xs text-slate-600 pt-1 border-t border-slate-800">
              <Clock size={10} />
              <span>Calculated at {new Date(result.calculatedAt).toLocaleTimeString()}</span>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

// ─── Sub-components ───────────────────────────────────────────────────────────

const StatusBadge: React.FC<{ status: string }> = ({ status }) => {
  const configs: Record<string, { icon: React.ReactNode; className: string }> = {
    ROUTED: {
      icon: <CheckCircle2 size={11} />,
      className: 'badge-success',
    },
    FAILED: {
      icon: <XCircle size={11} />,
      className: 'badge-danger',
    },
    PENDING: {
      icon: <Loader2 size={11} className="animate-spin" />,
      className: 'badge-warning',
    },
    SETTLED: {
      icon: <CheckCircle2 size={11} />,
      className: 'badge-success',
    },
  };

  const config = configs[status] ?? { icon: null, className: 'badge-info' };

  return (
    <div className={config.className + ' badge'}>
      {config.icon}
      {status}
    </div>
  );
};

interface DetailItemProps {
  label: string;
  value: string;
  highlight?: boolean;
}

const DetailItem: React.FC<DetailItemProps> = ({ label, value, highlight }) => (
  <div className="bg-slate-800/50 rounded-lg p-2.5 border border-slate-700/50">
    <div className="text-xs text-slate-500 mb-0.5">{label}</div>
    <div className={`text-xs font-semibold font-mono ${highlight ? 'text-indigo-400' : 'text-slate-300'}`}>
      {value}
    </div>
  </div>
);
