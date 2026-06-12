import React, { useState } from 'react';
import { Send, DollarSign, Zap, TrendingDown, ArrowRight, Loader2 } from 'lucide-react';
import type { PaymentInstructionRequest, OptimizationPreference } from '../types';

interface PaymentInitiatorProps {
  onCalculate: (request: PaymentInstructionRequest) => void;
  isLoading: boolean;
}

const TARGET_CURRENCIES = [
  { code: 'INR', name: 'Indian Rupee', flag: '🇮🇳', symbol: '₹' },
  { code: 'MXN', name: 'Mexican Peso', flag: '🇲🇽', symbol: '$' },
  { code: 'KES', name: 'Kenyan Shilling', flag: '🇰🇪', symbol: 'KSh' },
];

export const PaymentInitiator: React.FC<PaymentInitiatorProps> = ({ onCalculate, isLoading }) => {
  const [targetCurrency, setTargetCurrency] = useState('INR');
  const [amount, setAmount] = useState('50000');
  const [preference, setPreference] = useState<OptimizationPreference>('COST');
  const [debtorName, setDebtorName] = useState('Acme Corp International');
  const [creditorName, setCreditorName] = useState('Apex Technologies Ltd');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const parsedAmount = parseFloat(amount);
    if (!parsedAmount || parsedAmount <= 0) return;
    onCalculate({
      sourceCurrency: 'USD',
      targetCurrency,
      amount: parsedAmount,
      optimizationPreference: preference,
      debtorName,
      creditorName,
    });
  };

  const selectedCurrency = TARGET_CURRENCIES.find(c => c.code === targetCurrency);

  return (
    <div className="card p-5">
      {/* Header */}
      <div className="flex items-center justify-between mb-5">
        <div>
          <div className="section-header mb-1">Payment Instruction</div>
          <p className="text-xs text-slate-500">ISO 20022 · pacs.008 FIToFICustmrCdtTrf</p>
        </div>
        <div className="flex items-center gap-2 px-2.5 py-1 rounded-lg bg-indigo-500/10 border border-indigo-500/20">
          <div className="w-1.5 h-1.5 rounded-full bg-indigo-400 animate-pulse" />
          <span className="text-xs font-semibold text-indigo-400">LIVE ENGINE</span>
        </div>
      </div>

      <form onSubmit={handleSubmit} className="space-y-4">
        {/* Party Details */}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="label">Debtor (Cdtr.Nm)</label>
            <input
              type="text"
              value={debtorName}
              onChange={e => setDebtorName(e.target.value)}
              className="input-field text-sm"
              placeholder="Sending institution"
            />
          </div>
          <div>
            <label className="label">Creditor (Dbtr.Nm)</label>
            <input
              type="text"
              value={creditorName}
              onChange={e => setCreditorName(e.target.value)}
              className="input-field text-sm"
              placeholder="Receiving institution"
            />
          </div>
        </div>

        {/* Currency Corridor */}
        <div className="bg-slate-800/60 rounded-xl p-4 border border-slate-700/50">
          <label className="label">Currency Corridor (IntrBkSttlmAmt)</label>
          <div className="flex items-center gap-3">
            {/* Source */}
            <div className="flex-1 flex items-center gap-2 bg-slate-900 rounded-lg px-3 py-2.5 border border-slate-700">
              <span className="text-lg">🇺🇸</span>
              <div>
                <div className="text-sm font-bold text-slate-200">USD</div>
                <div className="text-xs text-slate-500">US Dollar</div>
              </div>
            </div>

            <ArrowRight className="text-indigo-400 flex-shrink-0" size={18} />

            {/* Target */}
            <div className="flex-1">
              <select
                value={targetCurrency}
                onChange={e => setTargetCurrency(e.target.value)}
                className="input-field appearance-none cursor-pointer"
              >
                {TARGET_CURRENCIES.map(c => (
                  <option key={c.code} value={c.code}>
                    {c.flag} {c.code} — {c.name}
                  </option>
                ))}
              </select>
            </div>
          </div>
        </div>

        {/* Amount */}
        <div>
          <label className="label">Amount (Source Currency)</label>
          <div className="relative">
            <span className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400 font-mono font-bold">$</span>
            <input
              type="number"
              value={amount}
              onChange={e => setAmount(e.target.value)}
              className="input-field pl-7"
              placeholder="0.00"
              min="0.01"
              max="100000000"
              step="0.01"
            />
            <span className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-500 text-xs font-semibold">USD</span>
          </div>
          {/* Quick amount buttons */}
          <div className="flex gap-2 mt-2">
            {[10000, 50000, 250000, 1000000].map(amt => (
              <button
                key={amt}
                type="button"
                onClick={() => setAmount(amt.toString())}
                className="flex-1 text-xs text-slate-400 hover:text-indigo-400 bg-slate-800 hover:bg-slate-700 
                           rounded px-2 py-1 transition-colors border border-slate-700 hover:border-indigo-500/50"
              >
                {amt >= 1000000 ? `$${amt / 1000000}M` : `$${(amt / 1000).toFixed(0)}K`}
              </button>
            ))}
          </div>
        </div>

        {/* Optimization Toggle */}
        <div>
          <label className="label">Routing Optimization (PmtTpInf.SvcLvl)</label>
          <div className="flex rounded-lg overflow-hidden border border-slate-700 bg-slate-900">
            <button
              type="button"
              onClick={() => setPreference('COST')}
              className={`flex-1 flex items-center justify-center gap-2 py-2.5 text-sm font-semibold transition-all duration-200 ${
                preference === 'COST'
                  ? 'bg-emerald-600/20 text-emerald-400 border-r border-slate-700'
                  : 'text-slate-500 hover:text-slate-300 border-r border-slate-700'
              }`}
            >
              <TrendingDown size={14} />
              Minimize Cost
            </button>
            <button
              type="button"
              onClick={() => setPreference('SPEED')}
              className={`flex-1 flex items-center justify-center gap-2 py-2.5 text-sm font-semibold transition-all duration-200 ${
                preference === 'SPEED'
                  ? 'bg-indigo-600/20 text-indigo-400'
                  : 'text-slate-500 hover:text-slate-300'
              }`}
            >
              <Zap size={14} />
              Minimize Latency
            </button>
          </div>
          <p className="text-xs text-slate-600 mt-1.5">
            {preference === 'COST' 
              ? 'Dijkstra weight: baseFee + fxSpreadMargin + transferSurcharge' 
              : 'Dijkstra weight: latencyMs per hop'}
          </p>
        </div>

        {/* Submit */}
        <button
          type="submit"
          disabled={isLoading || !amount || parseFloat(amount) <= 0}
          className="btn-primary w-full justify-center py-3"
        >
          {isLoading ? (
            <>
              <Loader2 size={16} className="animate-spin" />
              Calculating Optimal Route...
            </>
          ) : (
            <>
              <Send size={16} />
              Calculate Optimal Route
            </>
          )}
        </button>

        {/* Amount preview */}
        {selectedCurrency && amount && parseFloat(amount) > 0 && (
          <div className="text-center text-xs text-slate-500 animate-fade-in">
            Routing <span className="text-slate-300 font-mono font-semibold">${parseFloat(amount).toLocaleString()} USD</span>
            {' → '}
            <span className="text-slate-300 font-semibold">{selectedCurrency.flag} {selectedCurrency.code}</span>
            {' via '}
            <span className="text-indigo-400">{preference === 'COST' ? 'Cheapest' : 'Fastest'}</span> corridor
          </div>
        )}
      </form>
    </div>
  );
};
