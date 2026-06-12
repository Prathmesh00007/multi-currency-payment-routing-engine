// ============================================================
// TypeScript interfaces matching backend DTOs
// ISO 20022 Payment Routing Engine - Frontend Types
// ============================================================

export type OptimizationPreference = 'COST' | 'SPEED';

export type TransactionStatus = 'PENDING' | 'ROUTED' | 'FAILED' | 'SETTLED' | 'CANCELLED';

export type FxRateSource = 'FRANKFURTER_API' | 'MOCK_FALLBACK' | 'FINNHUB_WEBSOCKET' | 'EXCHANGE_RATE_API';

export type FxTickDirection = 'UP' | 'DOWN' | 'FLAT';

// ─── Live FX Tick (broadcast via STOMP /topic/fx-ticks) ─────────────────────

export interface LiveFxTick {
  pair: string;          // e.g. "USD/INR"
  price: number;
  previousPrice: number;
  direction: FxTickDirection;
  source: 'FINNHUB' | 'EXCHANGE_RATE_API' | 'FRANKFURTER';
  timestamp: number;     // epoch ms
}

export interface FxRateSnapshot {
  pair: string;
  rate: number | null;
  source: string;
  live: boolean;
}

// ─── Network Types ─────────────────────────────────────────────────────────────

export interface NetworkEdgeDto {
  id: number;
  sourceNodeId: number;
  targetNodeId: number;
  targetBankName: string;
  baseFee: number;
  fxSpreadMargin: number;
  latencyMs: number;
  active: boolean;
  corridorCurrencyPair: string;
}

export interface LiquidityDto {
  currency: string;
  availableAmount: number;
  reservedAmount: number;
  minimumRequiredBalance: number;
  updatedAt: string;
}

export interface NetworkNodeDto {
  id: number;
  bankName: string;
  country: string;
  baseCurrency: string;
  active: boolean;
  outgoingEdges: NetworkEdgeDto[];
  liquidityBalances: LiquidityDto[];
}

// ─── Payment Types ─────────────────────────────────────────────────────────────

export interface PaymentInstructionRequest {
  sourceCurrency: string;
  targetCurrency: string;
  amount: number;
  optimizationPreference: OptimizationPreference;
  debtorName?: string;
  creditorName?: string;
  endToEndId?: string;
}

export interface RouteHopDto {
  nodeId: number;
  bankName: string;
  country: string;
  baseCurrency: string;
  active: boolean;
  hopFee?: number;
  hopLatencyMs?: number;
  corridorCurrencyPair?: string;
  hopIndex: number;
}

export interface FeeBreakdownDto {
  fromBank: string;
  toBank: string;
  corridorCurrencyPair: string;
  baseFeePercentage: number;
  fxSpreadMarginPercentage: number;
  baseFeeAbsolute: number;
  fxSpreadAbsolute: number;
  latencyMs: number;
}

export interface RouteResultResponse {
  transactionId?: number;
  status: TransactionStatus;
  selectedPath: RouteHopDto[];
  optimizationPreference: OptimizationPreference;
  sourceCurrency: string;
  targetCurrency: string;
  amount: number;
  // FX
  baseFxRate?: number;
  fxRateSource?: string;
  fxSourceLabel?: string;   // e.g. "FINNHUB_WS", "ER-API"
  fxIsLiveTick?: boolean;
  effectiveFxRate?: number;
  fxSpreadCost?: number;
  convertedAmount?: number;
  // Fees
  feeBreakdown: FeeBreakdownDto[];
  totalFeePercentage?: number;
  totalFeeAbsolute?: number;
  totalFxImpact?: number;
  totalCost?: number;
  // Timing
  estimatedExecutionTimeMs?: number;
  estimatedSettlementTime?: string;
  // Stellar Network
  stellarFeeXlm?: number;
  stellarFeeUsd?: number;
  routingSource?: string;   // "STELLAR_TESTNET" | "MOCK_STELLAR_FALLBACK"
  // Liquidity Check
  liquidityCheckPassed?: boolean;
  liquidityCheckDetail?: string;
  // Status
  failureReason?: string;
  calculatedAt?: string;
}

export interface ApiErrorResponse {
  status: number;
  message: string;
  errorCode: string;
  fieldErrors?: Record<string, string>;
  timestamp: string;
}

// ─── UI State Types ────────────────────────────────────────────────────────────

export interface DashboardState {
  nodes: NetworkNodeDto[];
  routeResult: RouteResultResponse | null;
  isLoadingNodes: boolean;
  isCalculating: boolean;
  error: string | null;
}

export const CURRENCY_FLAGS: Record<string, string> = {
  USD: '🇺🇸',
  EUR: '🇪🇺',
  GBP: '🇬🇧',
  MXN: '🇲🇽',
  KES: '🇰🇪',
  INR: '🇮🇳',
};

export const COUNTRY_FLAGS: Record<string, string> = {
  US: '🇺🇸',
  DE: '🇩🇪',
  GB: '🇬🇧',
  MX: '🇲🇽',
  KE: '🇰🇪',
  IN: '🇮🇳',
};
