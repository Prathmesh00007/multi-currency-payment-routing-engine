import axios, { AxiosError } from 'axios';
import type {
  NetworkNodeDto,
  PaymentInstructionRequest,
  RouteResultResponse,
  ApiErrorResponse,
} from '../types';

/**
 * Axios API client for the ISO 20022 Payment Routing Engine backend.
 * Base URL points to Spring Boot on port 8080.
 * Vite proxy forwards /api/* requests to http://localhost:8080.
 */
const apiClient = axios.create({
  baseURL: '/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
  },
});

// Response interceptor for clean error handling
apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiErrorResponse>) => {
    if (error.response?.data) {
      return Promise.reject(error.response.data);
    }
    return Promise.reject({
      status: 0,
      message: 'Unable to connect to the routing engine. Ensure the backend is running on port 8080.',
      errorCode: 'CONNECTION_ERROR',
      timestamp: new Date().toISOString(),
    } as ApiErrorResponse);
  }
);

// ─── FX Ticker API ────────────────────────────────────────────────────────────

/**
 * GET /api/fx/rates
 * Returns a snapshot of current rates from all 3 tiers.
 */
export async function getAllFxRates(): Promise<Record<string, { rate: number | null; source: string; live: boolean }>> {
  const response = await apiClient.get('/fx/rates');
  return response.data;
}

// ─── Network API ──────────────────────────────────────────────────────────────

/**
 * GET /api/network/nodes
 * Fetches all correspondent nodes with edges and liquidity.
 */
export async function getNetworkNodes(): Promise<NetworkNodeDto[]> {
  const response = await apiClient.get<NetworkNodeDto[]>('/network/nodes');
  return response.data;
}

/**
 * POST /api/network/toggle-node/{id}
 * Toggles the active state of a node. Returns updated node.
 */
export async function toggleNode(nodeId: number): Promise<NetworkNodeDto> {
  const response = await apiClient.post<NetworkNodeDto>(`/network/toggle-node/${nodeId}`);
  return response.data;
}

// ─── Routing API ──────────────────────────────────────────────────────────────

/**
 * POST /api/routing/calculate
 * Stateless route calculation (does not persist a transaction).
 */
export async function calculateRoute(
  request: PaymentInstructionRequest
): Promise<RouteResultResponse> {
  const response = await apiClient.post<RouteResultResponse>('/routing/calculate', request);
  return response.data;
}

// ─── Transaction API ──────────────────────────────────────────────────────────

/**
 * POST /api/transactions/submit
 * Creates and routes a payment transaction, persists to DB.
 */
export async function submitTransaction(
  request: PaymentInstructionRequest
): Promise<RouteResultResponse> {
  const response = await apiClient.post<RouteResultResponse>('/transactions/submit', request);
  return response.data;
}

/**
 * GET /api/transactions/{id}
 * Retrieves a stored transaction by ID.
 */
export async function getTransaction(id: number): Promise<RouteResultResponse> {
  const response = await apiClient.get<RouteResultResponse>(`/transactions/${id}`);
  return response.data;
}
