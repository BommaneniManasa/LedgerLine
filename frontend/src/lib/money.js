// Money helpers. Amounts are typed as text and checked with a regex: no floating-point arithmetic is
// done in the browser. The backend is the real authority (BigDecimal, NUMERIC(19,2)).
export const CURRENCIES = ['INR', 'USD', 'EUR'];
// Keep in sync with ledgerline.money.max-transaction-amount (1,000,000.00). At this size a JSON number is exact.
export const MAX_AMOUNT = 1000000;

const AMOUNT_RE = /^\d{1,7}(\.\d{1,2})?$/;

/** Returns {value, error}. value is a Number with at most 2 decimals (safe up to MAX_AMOUNT). */
export function parseAmount(text) {
  const raw = String(text ?? '').trim();
  if (!raw) return { error: 'Amount is required' };
  if (!AMOUNT_RE.test(raw)) return { error: 'Enter a valid amount with at most 2 decimal places' };
  const value = Number(raw);
  if (value < 0.01) return { error: 'Amount must be at least 0.01' };
  if (value > MAX_AMOUNT) return { error: 'Amount must not exceed 1,000,000.00' };
  return { value };
}

export function formatMoney(amount, currency) {
  if (amount === null || amount === undefined || Number.isNaN(Number(amount))) return '-';
  try {
    return new Intl.NumberFormat(undefined, { style: 'currency', currency, minimumFractionDigits: 2 }).format(Number(amount));
  } catch {
    return `${Number(amount).toFixed(2)} ${currency ?? ''}`.trim();
  }
}

export function formatDateTime(iso) {
  if (!iso) return '-';
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? '-' : d.toLocaleString();
}
