import { useRef, useState } from 'react';
import { AsyncView, Banner, ErrorBanner } from '../components/Banner.jsx';
import { SelectField, TextField } from '../components/Field.jsx';
import { PageHeader } from '../components/Layout.jsx';
import { api } from '../lib/api.js';
import { createKeyHolder, shouldRenewKey } from '../lib/idempotency.js';
import { formatMoney, parseAmount } from '../lib/money.js';
import { useAsync } from '../lib/useAsync.js';
import * as v from '../lib/validators.js';
import { Link } from '../router.jsx';

/**
 * Shared submit logic for deposit / withdraw / transfer.
 * One Idempotency-Key per attempt: a retry after a network error re-sends the SAME key, so the server
 * returns the first result instead of moving money twice. The key is renewed after success or a definite rejection.
 */
function useMoneySubmit(send) {
  const keys = useRef(createKeyHolder());
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [result, setResult] = useState(null);

  const submit = async (payload) => {
    setBusy(true); setError(null); setResult(null);
    try {
      const { data, replayed } = await send(payload, keys.current.current());
      keys.current.reset();
      setResult({ transaction: data, replayed });
      return true;
    } catch (err) {
      if (shouldRenewKey(err)) keys.current.reset();
      setError(err);
      return false;
    } finally {
      setBusy(false);
    }
  };
  // Editing the form means it is a different request: use a new key.
  const touch = () => keys.current.reset();
  return { busy, error, result, submit, touch, clear: () => { setResult(null); setError(null); } };
}

function ResultBanner({ result }) {
  if (!result) return null;
  const t = result.transaction;
  return (
    <Banner kind="success">
      {result.replayed ? 'This request was already processed. ' : ''}
      Done: {t.type.toLowerCase()} of {formatMoney(t.amount, t.currency)} (reference <span className="mono">{t.reference}</span>).
    </Banner>
  );
}

function useActiveAccounts() {
  const state = useAsync(() => api.accounts(), []);
  return { state, active: (state.data || []).filter((a) => a.status === 'ACTIVE') };
}

function accountOptions(accounts) {
  return accounts.map((a) => ({ value: a.id, label: `${a.accountNumber} - ${formatMoney(a.balance, a.currency)}` }));
}

function NoAccounts() {
  return <p className="empty">You need an active account first. <Link to="/dashboard">Open one on the dashboard</Link>.</p>;
}

export function DepositWithdrawPage({ kind }) {
  const isDeposit = kind === 'deposit';
  const { state, active } = useActiveAccounts();
  const [form, setForm] = useState({ accountId: '', amount: '', description: '' });
  const [errors, setErrors] = useState({});
  const money = useMoneySubmit((payload, key) => (isDeposit ? api.deposit(payload, key) : api.withdraw(payload, key)));

  const accountId = form.accountId || active[0]?.id || '';
  const account = active.find((a) => a.id === accountId);
  const set = (name) => (e) => { money.touch(); setForm((f) => ({ ...f, [name]: e.target.value })); };

  const submit = async (e) => {
    e.preventDefault();
    const amount = parseAmount(form.amount);
    const found = {
      accountId: account ? '' : 'Choose an account',
      amount: amount.error || '',
      description: v.validateDescription(form.description),
    };
    setErrors(found);
    if (v.hasErrors(found)) return;
    // currency always comes from the chosen account
    const ok = await money.submit({
      accountId, amount: amount.value, currency: account.currency, description: form.description.trim() || null,
    });
    if (ok) { setForm((f) => ({ ...f, amount: '', description: '' })); state.reload(); }
  };

  return (
    <>
      <PageHeader title={isDeposit ? 'Deposit' : 'Withdraw'}
        subtitle={isDeposit ? 'Add money to one of your accounts.' : 'Take money out of one of your accounts.'} />
      <section className="card narrow">
        <AsyncView state={state}>
          {() => (active.length === 0 ? <NoAccounts /> : (
            <form onSubmit={submit} noValidate>
              <ErrorBanner error={money.error} />
              <ResultBanner result={money.result} />
              <SelectField label="Account" value={accountId} onChange={set('accountId')} options={accountOptions(active)} error={errors.accountId} />
              <TextField label={`Amount (${account?.currency || ''})`} inputMode="decimal" value={form.amount}
                onChange={set('amount')} error={errors.amount} placeholder="0.00" autoComplete="off" />
              <TextField label="Description (optional)" value={form.description} onChange={set('description')}
                error={errors.description} maxLength={200} />
              <button className="btn btn-primary btn-block" disabled={money.busy}>
                {money.busy ? 'Processing...' : isDeposit ? 'Deposit' : 'Withdraw'}
              </button>
            </form>
          ))}
        </AsyncView>
      </section>
    </>
  );
}

export function TransferPage() {
  const { state, active } = useActiveAccounts();
  const [form, setForm] = useState({ sourceAccountId: '', destination: '', amount: '', description: '' });
  const [errors, setErrors] = useState({});
  const [reviewing, setReviewing] = useState(false);
  const money = useMoneySubmit((payload, key) => api.transfer(payload, key));

  const sourceId = form.sourceAccountId || active[0]?.id || '';
  const source = active.find((a) => a.id === sourceId);
  const set = (name) => (e) => { money.touch(); setReviewing(false); setForm((f) => ({ ...f, [name]: e.target.value })); };

  const review = (e) => {
    e.preventDefault();
    const amount = parseAmount(form.amount);
    const found = {
      sourceAccountId: source ? '' : 'Choose a source account',
      destination: v.validateAccountNumber(form.destination),
      amount: amount.error || '',
      description: v.validateDescription(form.description),
    };
    if (!found.destination && source && v.normalizeAccountNumber(form.destination) === source.accountNumber) {
      found.destination = 'Source and destination must be different accounts';
    }
    setErrors(found);
    money.clear();
    setReviewing(!v.hasErrors(found));
  };

  const confirm = async () => {
    const amount = parseAmount(form.amount);
    const ok = await money.submit({
      sourceAccountId: sourceId,
      destinationAccountNumber: v.normalizeAccountNumber(form.destination),
      amount: amount.value,
      currency: source.currency,
      description: form.description.trim() || null,
    });
    if (ok) { setReviewing(false); setForm((f) => ({ ...f, destination: '', amount: '', description: '' })); state.reload(); }
  };

  return (
    <>
      <PageHeader title="Send money" subtitle="Transfer between accounts in the same currency." />
      <section className="card narrow">
        <AsyncView state={state}>
          {() => (active.length === 0 ? <NoAccounts /> : (
            <>
              <ErrorBanner error={money.error} />
              <ResultBanner result={money.result} />
              {reviewing ? (
                <div className="review">
                  <h2>Confirm transfer</h2>
                  <dl className="summary">
                    <dt>From</dt><dd className="mono">{source.accountNumber}</dd>
                    <dt>To</dt><dd className="mono">{v.normalizeAccountNumber(form.destination)}</dd>
                    <dt>Amount</dt><dd className="amount">{formatMoney(parseAmount(form.amount).value, source.currency)}</dd>
                    {form.description.trim() ? (<><dt>Note</dt><dd>{form.description.trim()}</dd></>) : null}
                  </dl>
                  <div className="actions">
                    <button type="button" className="btn btn-primary" onClick={confirm} disabled={money.busy}>
                      {money.busy ? 'Sending...' : 'Confirm and send'}
                    </button>
                    <button type="button" className="btn btn-secondary" onClick={() => setReviewing(false)} disabled={money.busy}>Edit</button>
                  </div>
                </div>
              ) : (
                <form onSubmit={review} noValidate>
                  <SelectField label="From account" value={sourceId} onChange={set('sourceAccountId')}
                    options={accountOptions(active)} error={errors.sourceAccountId} />
                  <TextField label="Destination account number" value={form.destination} onChange={set('destination')}
                    error={errors.destination} placeholder="e.g. LL123456789012" autoComplete="off" />
                  <TextField label={`Amount (${source?.currency || ''})`} inputMode="decimal" value={form.amount}
                    onChange={set('amount')} error={errors.amount} placeholder="0.00" autoComplete="off" />
                  <TextField label="Description (optional)" value={form.description} onChange={set('description')}
                    error={errors.description} maxLength={200} />
                  <button className="btn btn-primary btn-block">Review transfer</button>
                </form>
              )}
            </>
          ))}
        </AsyncView>
      </section>
    </>
  );
}
