import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const ACCOUNT_SVC = __ENV.ACCOUNT_SVC || 'http://account-service:8081';
const PAYMENT_SVC = __ENV.PAYMENT_SVC || 'http://payment-service:8082';

// Custom metrics
const paymentErrors  = new Counter('payment_errors');
const paymentSuccess = new Counter('payment_success');
const insufficientFunds = new Counter('insufficient_funds');
const paymentDuration   = new Trend('payment_duration_ms', true);
const successRate = new Rate('payment_success_rate');

export const options = {
  scenarios: {
    ramp_up: {
      executor: 'ramping-vus',
      startVUs: 1,
      stages: [
        { duration: '30s', target: 10 },
        { duration: '1m',  target: 50 },
        { duration: '30s', target: 50 },
        { duration: '30s', target: 0  },
      ],
    },
  },
  thresholds: {
    payment_success_rate:    ['rate>0.95'],
    payment_duration_ms:     ['p(95)<2000'],
    http_req_failed:         ['rate<0.05'],
  },
};

// setup() runs once before the test — creates accounts and funds wallets
export function setup() {
  const accounts = [];

  for (let i = 0; i < 20; i++) {
    const ts = Date.now() + i;

    // 1. Create account
    const accRes = http.post(`${ACCOUNT_SVC}/accounts`,
      JSON.stringify({ name: `LoadUser${i}`, email: `loaduser${ts}@test.com` }),
      { headers: { 'Content-Type': 'application/json' } }
    );
    if (accRes.status !== 201) {
      console.error(`[setup] account ${i} creation failed: status=${accRes.status} body=${accRes.body}`);
      continue;
    }
    const account = JSON.parse(accRes.body);
    if (!account || !account.id) {
      console.error(`[setup] account ${i} response has no id: ${accRes.body}`);
      continue;
    }

    // 2. Verify KYC
    http.put(`${ACCOUNT_SVC}/accounts/${account.id}/kyc/verify`, null);

    // 3. Get USD wallet
    const walletsRes = http.get(`${ACCOUNT_SVC}/accounts/${account.id}/wallets`);
    let wallets = [];
    try { wallets = JSON.parse(walletsRes.body); } catch (_) {}
    const usdWallet = wallets.find(w => w.currency === 'USD');
    if (!usdWallet) {
      console.error(`[setup] no USD wallet for account ${account.id}`);
      continue;
    }

    // 4. Deposit $50,000
    http.post(`${ACCOUNT_SVC}/wallets/${usdWallet.id}/deposit`,
      JSON.stringify({ amount: 50000 }),
      { headers: { 'Content-Type': 'application/json' } }
    );

    accounts.push({ accountId: account.id, walletId: usdWallet.id });
  }

  if (accounts.length < 2) {
    throw new Error(`Setup failed: only ${accounts.length} accounts ready, need at least 2`);
  }
  console.log(`Setup complete: ${accounts.length} accounts ready`);
  return { accounts };
}

// default() runs for every VU on every iteration
export default function (data) {
  const accounts = data.accounts;

  // Pick random sender and receiver (different)
  const senderIdx   = Math.floor(Math.random() * accounts.length);
  let   receiverIdx = Math.floor(Math.random() * accounts.length);
  while (receiverIdx === senderIdx) {
    receiverIdx = Math.floor(Math.random() * accounts.length);
  }

  const sender   = accounts[senderIdx];
  const receiver = accounts[receiverIdx];
  const amount   = (Math.random() * 99 + 1).toFixed(2);          // $1–$100
  const key      = `${sender.walletId.substring(0,8)}-${receiver.walletId.substring(0,8)}-${Date.now()}`;

  const start = Date.now();
  const res = http.post(`${PAYMENT_SVC}/payments`,
    JSON.stringify({
      fromWalletId:   sender.walletId,
      toWalletId:     receiver.walletId,
      amount:         parseFloat(amount),
      currency:       'USD',
      idempotencyKey: key,
    }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  paymentDuration.add(Date.now() - start);

  const ok = res.status === 200 || res.status === 201;
  const insuffFunds = res.status === 500 && res.body.includes('Insufficient');

  check(res, {
    'payment accepted': () => ok || insuffFunds,
    'payment 2xx':      () => ok,
  });

  if (ok) {
    paymentSuccess.add(1);
    successRate.add(true);
  } else if (insuffFunds) {
    insufficientFunds.add(1);
    successRate.add(true);   // not a service error
  } else {
    paymentErrors.add(1);
    successRate.add(false);
    console.error(`Payment failed [${res.status}]: ${res.body.substring(0, 200)}`);
  }

  sleep(0.1);
}
