# ISO 20022 Multi-Currency Payment Routing & Liquidity Engine

A pitch-ready institutional payment routing prototype built with **Spring Boot 3 / Java 21** (backend) and **React 18 / TypeScript / Tailwind CSS** (frontend).

## Features

- **ISO 20022-aligned** payment instruction model (pacs.008 FIToFICustmrCdtTrf)
- **Dijkstra's algorithm** for optimal correspondent banking route selection
- **COST vs SPEED** optimization modes
- **Live FX rates** from Frankfurter API (ECB), with mock fallback
- **Network outage simulator** — toggle nodes off and watch routes recalculate
- **Liquidity feasibility checks** — rejects routes when any hop has insufficient balance
- **3 distinct paths** to each of MXN, KES, INR
- **Enterprise dark-mode dashboard** with route visualization and fee breakdown chart

---

## Data Sources

| System Component | Data Source / Implementation Strategy |
|------------------|----------------------------------------|
| FX Base Rates | **3-Tier Live Engine**<br>• Tier A: Frankfurter API (Daily ECB Reference)<br>• Tier B: Finnhub WebSockets (Live Ticks for Major Pairs)<br>• Tier C: ExchangeRate-API (Exotics & Emerging Markets)<br><br>+ Deterministic Mock Fallback |
| Node / Bank Network | Stellar Testnet (Dynamic Proxy via Horizon API) |
| Routing Fees / Spreads | Real-time Stellar Testnet Data (Dynamic DEX spreads and blockchain network fees converted into correspondent banking equivalents) |
| Latency Values | Synthetic Mock Data (Simulated clearance and settlement times) |
| Liquidity Balances | Synthetic Mock Data (Simulated internal Nostro/Vostro account balances) |
| ISO 20022 Structure | Real ISO 20022 field naming and message structure (e.g., `pacs.008`) |

---

## Prerequisites

- **Java 21+** (`java -version`)
- **Maven 3.9+** (`mvn -version`)
- **Node.js 20+** (`node -version`)
- **PostgreSQL** running locally on port 5432

---

## 1. Database Setup

Create the database before starting the backend:

```sql
-- Connect to PostgreSQL as superuser
psql -U postgres

CREATE DATABASE payment_routing;
-- (optional) create dedicated user:
CREATE USER routing_user WITH PASSWORD 'routing_pass';
GRANT ALL PRIVILEGES ON DATABASE payment_routing TO routing_user;
```

Update credentials in `backend/src/main/resources/application.properties` if needed:
```properties
spring.datasource.username=postgres
spring.datasource.password=postgres
```

---

## 2. Start the Backend (Spring Boot)

```bash
cd backend

# First time: download dependencies and compile
mvn clean install -DskipTests

# Start the server (auto-seeds mock data on startup)
mvn spring-boot:run
```

The backend will start on **http://localhost:8080**.

On first startup, `MockDataSeeder` automatically populates:
- 14 correspondent nodes (US, EU, UK, MXN, KES, INR)
- 14 routing edges with 3 paths per destination currency
- Liquidity balances for all nodes
- FX rate snapshots (baseline mock values)

---

## 3. Start the Frontend (React + Vite)

```bash
cd frontend

# Install dependencies
npm install

# Start Vite dev server
npm run dev
```

The dashboard opens at **http://localhost:5173**.

---

## 4. Run Tests

```bash
cd backend

# All tests (unit + integration)
mvn test

# Unit tests only
mvn test -Dtest=RoutingEngineTest,LiquidityServiceTest

# Integration tests only
mvn test -Dtest=NetworkControllerIT
```

---

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/network/nodes` | All nodes with edges and liquidity |
| POST | `/api/network/toggle-node/{id}` | Toggle node active/inactive |
| POST | `/api/routing/calculate` | Stateless route calculation |
| POST | `/api/transactions/submit` | Create + route transaction |
| GET | `/api/transactions/{id}` | Retrieve stored transaction |

### Example: Calculate Route

```bash
curl -X POST http://localhost:8080/api/routing/calculate \
  -H "Content-Type: application/json" \
  -d '{
    "sourceCurrency": "USD",
    "targetCurrency": "INR",
    "amount": 50000,
    "optimizationPreference": "COST"
  }'
```

---

## Demo Scenarios

1. **Happy Path**: USD → INR, $50,000, COST mode → 3-hop route via Deutsche Bank
2. **Speed Mode**: USD → MXN, $10,000, SPEED mode → different path selected
3. **Network Outage**: Toggle off "Deutsche Bank Frankfurt" → engine re-routes around it
4. **Liquidity Failure**: Enter very large amount (e.g., $900M) → InsufficientLiquidityException
5. **Unsupported Currency**: Change target to JPY → 400 UNSUPPORTED_CURRENCY_PAIR

---

## Architecture

```
transaction-routing/
├── backend/                     # Spring Boot 3 / Java 21
│   ├── src/main/java/com/routing/
│   │   ├── config/              # AppConfig (CORS, WebClient), MockDataSeeder
│   │   ├── controller/          # NetworkController, RoutingController, TransactionController
│   │   ├── domain/
│   │   │   ├── dto/             # Request/Response DTOs
│   │   │   ├── entity/          # JPA Entities (CorrespondentNode, RoutingEdge, ...)
│   │   │   └── enums/           # OptimizationPreference, TransactionStatus, FxRateSource
│   │   ├── exception/           # GlobalExceptionHandler + domain exceptions
│   │   ├── repository/          # Spring Data JPA repositories
│   │   └── service/             # RoutingEngine (Dijkstra), FxRateService, LiquidityService, PaymentService
│   └── src/test/                # Unit + Integration tests
│
└── frontend/                    # React 18 + TypeScript + Tailwind CSS
    ├── src/
    │   ├── api/client.ts        # Axios API client
    │   ├── components/          # PaymentInitiator, NetworkSimulator, RoutingVisualization,
    │   │                        # FinancialLedger, TransactionDetail
    │   ├── types/index.ts       # TypeScript interfaces
    │   └── Dashboard.tsx        # Main 3-column layout
    └── package.json
```

---

## ISO 20022 Alignment

| ISO 20022 Field | Implementation |
|-----------------|----------------|
| `GrpHdr.IntrBkSttlmDt` | `PaymentTransaction.createdAt` |
| `CdtTrfTxInf.IntrBkSttlmAmt` | `amount` + `sourceCurrency` |
| `CdtTrfTxInf.CdtrAgt` | `targetCurrency` node endpoint |
| `CdtTrfTxInf.PmtTpInf.SvcLvl` | `optimizationPreference` |
| `CdtTrfTxInf.ExchangeRate` | `FxRateSnapshot` + `fxSpreadMargin` |
| `IntermediaryAgent1..3` | `RoutingEdge` hops in path |
| `FinInstnId.Nm` | `CorrespondentNode.bankName` |
