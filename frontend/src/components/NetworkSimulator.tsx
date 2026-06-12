import React from 'react';
import { Network, RefreshCw, AlertCircle, Wifi, WifiOff } from 'lucide-react';
import type { NetworkNodeDto } from '../types';
import { COUNTRY_FLAGS } from '../types';

interface NetworkSimulatorProps {
  nodes: NetworkNodeDto[];
  onToggleNode: (nodeId: number) => void;
  isLoading: boolean;
}

const CURRENCY_COLORS: Record<string, string> = {
  USD: 'text-emerald-400 bg-emerald-500/10 border-emerald-500/30',
  EUR: 'text-blue-400 bg-blue-500/10 border-blue-500/30',
  GBP: 'text-violet-400 bg-violet-500/10 border-violet-500/30',
  MXN: 'text-amber-400 bg-amber-500/10 border-amber-500/30',
  KES: 'text-red-400 bg-red-500/10 border-red-500/30',
  INR: 'text-orange-400 bg-orange-500/10 border-orange-500/30',
};

export const NetworkSimulator: React.FC<NetworkSimulatorProps> = ({
  nodes, onToggleNode, isLoading
}) => {
  const groupedNodes: Record<string, NetworkNodeDto[]> = {};
  nodes.forEach(node => {
    const group = node.baseCurrency;
    if (!groupedNodes[group]) groupedNodes[group] = [];
    groupedNodes[group].push(node);
  });

  const currencyGroups = Object.entries(groupedNodes);
  const activeCount = nodes.filter(n => n.active).length;

  return (
    <div className="card flex flex-col h-full">
      {/* Header */}
      <div className="p-4 border-b border-slate-800 flex-shrink-0">
        <div className="flex items-center justify-between mb-1">
          <div className="section-header mb-0">Network Outage Simulator</div>
          {isLoading && <RefreshCw size={12} className="text-slate-500 animate-spin" />}
        </div>
        <div className="flex items-center gap-3 mt-2">
          <div className="flex items-center gap-1.5">
            <div className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
            <span className="text-xs text-slate-400">
              <span className="text-emerald-400 font-bold">{activeCount}</span> Active
            </span>
          </div>
          <div className="flex items-center gap-1.5">
            <div className="w-2 h-2 rounded-full bg-red-400" />
            <span className="text-xs text-slate-400">
              <span className="text-red-400 font-bold">{nodes.length - activeCount}</span> Offline
            </span>
          </div>
        </div>
      </div>

      {/* Node list by currency group */}
      <div className="flex-1 overflow-y-auto p-3 space-y-3">
        {nodes.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-8 text-slate-600">
            <Network size={24} className="mb-2" />
            <p className="text-xs">No nodes loaded</p>
          </div>
        ) : (
          currencyGroups.map(([currency, currNodes]) => (
            <div key={currency}>
              {/* Group header */}
              <div className="flex items-center gap-2 mb-1.5">
                <span className={`badge text-xs border ${CURRENCY_COLORS[currency] ?? 'text-slate-400 bg-slate-800 border-slate-700'}`}>
                  {currency}
                </span>
                <div className="flex-1 h-px bg-slate-800" />
              </div>

              {/* Nodes in group */}
              <div className="space-y-1.5">
                {currNodes.map(node => (
                  <NodeRow
                    key={node.id}
                    node={node}
                    onToggle={() => onToggleNode(node.id)}
                  />
                ))}
              </div>
            </div>
          ))
        )}
      </div>

      {/* Warning if too many nodes offline */}
      {nodes.length - activeCount >= 3 && (
        <div className="p-3 border-t border-slate-800 flex-shrink-0">
          <div className="flex items-start gap-2 text-amber-400/80 bg-amber-500/10 rounded-lg p-2.5 border border-amber-500/20">
            <AlertCircle size={14} className="flex-shrink-0 mt-0.5" />
            <p className="text-xs">
              Multiple nodes offline. Route availability may be limited.
            </p>
          </div>
        </div>
      )}
    </div>
  );
};

// ─── Individual node row ───────────────────────────────────────────────────────

interface NodeRowProps {
  node: NetworkNodeDto;
  onToggle: () => void;
}

const NodeRow: React.FC<NodeRowProps> = ({ node, onToggle }) => {
  const flag = COUNTRY_FLAGS[node.country] ?? '🌐';
  const primaryLiquidity = node.liquidityBalances.find(lb => lb.currency === node.baseCurrency);
  const liquidityPct = primaryLiquidity
    ? ((primaryLiquidity.availableAmount - primaryLiquidity.minimumRequiredBalance) /
       primaryLiquidity.availableAmount * 100)
    : 0;

  return (
    <div
      className={`flex items-center gap-2.5 px-3 py-2.5 rounded-lg border transition-all duration-300 cursor-pointer group
        ${node.active
          ? 'bg-slate-800/50 border-slate-700/50 hover:border-slate-600'
          : 'bg-slate-900/50 border-slate-800 opacity-60'
        }`}
      onClick={onToggle}
    >
      {/* Status indicator */}
      <div className="flex-shrink-0">
        {node.active ? (
          <Wifi size={12} className="text-emerald-400" />
        ) : (
          <WifiOff size={12} className="text-red-400" />
        )}
      </div>

      {/* Bank info */}
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-1.5">
          <span className="text-sm">{flag}</span>
          <span className={`text-xs font-semibold truncate ${node.active ? 'text-slate-200' : 'text-slate-500'}`}>
            {node.bankName}
          </span>
        </div>
        {/* Liquidity bar */}
        {node.active && primaryLiquidity && (
          <div className="flex items-center gap-1.5 mt-1">
            <div className="flex-1 h-1 bg-slate-700 rounded-full overflow-hidden">
              <div
                className={`h-full rounded-full transition-all duration-500 ${
                  liquidityPct > 60 ? 'bg-emerald-500' :
                  liquidityPct > 30 ? 'bg-amber-500' : 'bg-red-500'
                }`}
                style={{ width: `${Math.max(5, Math.min(100, liquidityPct))}%` }}
              />
            </div>
            <span className="text-xs text-slate-600">{liquidityPct.toFixed(0)}%</span>
          </div>
        )}
      </div>

      {/* Toggle */}
      <label className="toggle-switch flex-shrink-0" onClick={e => e.stopPropagation()}>
        <input
          type="checkbox"
          checked={node.active}
          onChange={onToggle}
          onClick={e => e.stopPropagation()}
        />
        <span className="toggle-slider" />
      </label>
    </div>
  );
};
