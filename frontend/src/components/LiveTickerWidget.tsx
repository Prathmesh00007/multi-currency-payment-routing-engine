import React, { useEffect, useRef, useState } from 'react';
import { TrendingUp, TrendingDown, Minus, Wifi, WifiOff, Radio } from 'lucide-react';
import type { LiveFxTick, FxRateSnapshot } from '../types';

interface LiveTickerWidgetProps {
  ticks: Map<string, LiveFxTick>;
  snapshots: Map<string, FxRateSnapshot>;
  isConnected: boolean;
  connectionStatus: string;
}

const PAIRS = [
  { key: 'USD/INR',  label: 'USD/INR',  emoji: '🇮🇳', decimals: 4 },
  { key: 'USD/MXN',  label: 'USD/MXN',  emoji: '🇲🇽', decimals: 4 },
  { key: 'USD/KES',  label: 'USD/KES',  emoji: '🇰🇪', decimals: 4 },
  { key: 'EUR/USD',  label: 'EUR/USD',  emoji: '🇪🇺', decimals: 5 },
  { key: 'GBP/USD',  label: 'GBP/USD',  emoji: '🇬🇧', decimals: 5 },
];

const SOURCE_BADGES: Record<string, { label: string; color: string }> = {
  FINNHUB:           { label: 'LIVE', color: 'text-emerald-400 bg-emerald-500/15 border-emerald-500/30' },
  EXCHANGE_RATE_API: { label: 'ER-API', color: 'text-blue-400 bg-blue-500/15 border-blue-500/30' },
  FRANKFURTER:       { label: 'ECB', color: 'text-indigo-400 bg-indigo-500/15 border-indigo-500/30' },
  UNAVAILABLE:       { label: 'N/A', color: 'text-slate-500 bg-slate-800 border-slate-700' },
};

export const LiveTickerWidget: React.FC<LiveTickerWidgetProps> = ({
  ticks, snapshots, isConnected, connectionStatus
}) => {
  return (
    <div className="border-b border-slate-800 bg-slate-900/80 backdrop-blur-sm">
      <div className="max-w-screen-2xl mx-auto px-4 py-1.5 flex items-center gap-4">
        {/* Connection indicator */}
        <div className="flex items-center gap-1.5 flex-shrink-0">
          {isConnected ? (
            <>
              <Radio size={10} className="text-emerald-400 animate-pulse" />
              <span className="text-xs font-bold text-emerald-400">LIVE TICKS</span>
            </>
          ) : (
            <>
              <WifiOff size={10} className="text-slate-500" />
              <span className="text-xs font-semibold text-slate-500">
                {connectionStatus === 'CONNECTING' ? 'CONNECTING...' : 'CACHED RATES'}
              </span>
            </>
          )}
        </div>

        <div className="w-px h-4 bg-slate-700 flex-shrink-0" />

        {/* Ticker pairs */}
        <div className="flex items-center gap-5 overflow-x-auto flex-1">
          {PAIRS.map(pair => (
            <TickerPair
              key={pair.key}
              config={pair}
              tick={ticks.get(pair.key)}
              snapshot={snapshots.get(pair.key)}
            />
          ))}
        </div>

        {/* Disclaimer */}
        <div className="hidden xl:block text-xs text-slate-600 flex-shrink-0">
          ⚠ Demo data only
        </div>
      </div>
    </div>
  );
};

// ─── Individual ticker pair ────────────────────────────────────────────────────

interface TickerPairProps {
  config: typeof PAIRS[0];
  tick: LiveFxTick | undefined;
  snapshot: FxRateSnapshot | undefined;
}

const TickerPair: React.FC<TickerPairProps> = ({ config, tick, snapshot }) => {
  const [flash, setFlash] = useState<'up' | 'down' | null>(null);
  const prevTickRef = useRef<LiveFxTick | undefined>(undefined);

  useEffect(() => {
    if (!tick) return;
    const prev = prevTickRef.current;
    if (prev && tick.timestamp !== prev.timestamp) {
      setFlash(tick.direction === 'UP' ? 'up' : tick.direction === 'DOWN' ? 'down' : null);
      const timer = setTimeout(() => setFlash(null), 600);
      return () => clearTimeout(timer);
    }
    prevTickRef.current = tick;
  }, [tick]);

  // Determine display values
  const rate = tick?.price ?? snapshot?.rate;
  const direction = tick?.direction ?? 'FLAT';
  const source = tick?.source ?? snapshot?.source ?? 'UNAVAILABLE';
  const isLive = tick != null || snapshot?.live;

  const sourceBadge = SOURCE_BADGES[source] ?? SOURCE_BADGES.UNAVAILABLE;

  const flashClass = flash === 'up'
    ? 'text-emerald-300'
    : flash === 'down'
      ? 'text-red-300'
      : direction === 'UP'
        ? 'text-emerald-400'
        : direction === 'DOWN'
          ? 'text-red-400'
          : 'text-slate-300';

  return (
    <div className="flex items-center gap-2 flex-shrink-0">
      <span className="text-xs">{config.emoji}</span>
      <div>
        <div className="flex items-center gap-1">
          <span className="text-xs font-semibold text-slate-400">{config.label}</span>
          <span className={`text-xs font-bold font-mono transition-colors duration-300 ${flashClass}`}>
            {rate != null
              ? rate.toFixed(config.decimals)
              : '—'}
          </span>
          {direction === 'UP' && <TrendingUp size={10} className="text-emerald-400" />}
          {direction === 'DOWN' && <TrendingDown size={10} className="text-red-400" />}
          {direction === 'FLAT' && <Minus size={10} className="text-slate-600" />}
        </div>
        <div className="flex items-center gap-1">
          <span className={`text-xs font-bold px-1 py-px rounded border ${sourceBadge.color}`}
            style={{ fontSize: '9px' }}>
            {sourceBadge.label}
          </span>
          {isLive && (
            <span className="w-1 h-1 rounded-full bg-emerald-400 animate-pulse" />
          )}
        </div>
      </div>
    </div>
  );
};
