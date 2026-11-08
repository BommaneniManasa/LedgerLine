import { useState } from 'react';
import { AsyncView, Banner, ErrorBanner } from '../components/Banner.jsx';
import { Badge } from '../components/Badge.jsx';
import { SelectField } from '../components/Field.jsx';
import { PageHeader } from '../components/Layout.jsx';
import { Pager } from '../components/Pager.jsx';
import { TransactionTable } from '../components/TransactionTable.jsx';
import { api } from '../lib/api.js';
import { CURRENCIES, formatDateTime, formatMoney } from '../lib/money.js';
import { useAsync } from '../lib/useAsync.js';
import { Link, useNavigate, useParams } from '../router.jsx';

function AccountCard({ account }) {
  return (
    <Link to={`/accounts/${account.id}`} className="account-card">
      <div className="account-top"><span className="mono">{account.accountNumber}</span><Badge value={account.status} /></div>
      <div className="account-balance amount">{formatMoney(account.balance, account.currency)}</div>
      <div className="muted small">{account.currency} account</div>
    </Link>
  );
}

function CreateAccountForm({ onCreated }) {
  const [currency, setCurrency] = useState('INR');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const [done, setDone] = useState('');

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true); setError(null); setDone('');
    try {
      const account = await api.createAccount(currency);
      setDone(`Account ${account.accountNumber} opened.`);
      onCreated();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="inline-form" onSubmit={submit}>
      <SelectField label="Open a new account" value={currency} onChange={(e) => setCurrency(e.target.value)}
        options={CURRENCIES.map((c) => ({ value: c, label: c }))} />
      <button className="btn btn-primary" disabled={busy}>{busy ? 'Opening...' : 'Open account'}</button>
      <ErrorBanner error={error} />
      <Banner kind="success">{done}</Banner>
    </form>
  );
}

export function DashboardPage() {
  const accounts = useAsync(() => api.accounts(), []);
  const recent = useAsync(() => api.transactions({ page: 0, size: 5 }), []);

  return (
    <>
      <PageHeader title="Dashboard" subtitle="Your balances and latest activity.">
        <div className="actions">
          <Link className="btn btn-primary" to="/transfer">Send money</Link>
          <Link className="btn btn-secondary" to="/deposit">Deposit</Link>
        </div>
      </PageHeader>

      <section className="card" aria-labelledby="acc-title">
        <h2 id="acc-title">Accounts</h2>
        <AsyncView state={accounts}>
          {(list) => (
            <>
              {list.length === 0 ? <p className="empty">You have no accounts yet. Open one below.</p> : (
                <>
                  <div className="totals">
                    {Object.entries(list.reduce((acc, a) => {
                      acc[a.currency] = (acc[a.currency] || 0) + Number(a.balance);
                      return acc;
                    }, {})).map(([cur, total]) => (
                      <div key={cur} className="total"><span className="muted small">Total {cur}</span><strong className="amount">{formatMoney(total, cur)}</strong></div>
                    ))}
                  </div>
                  <div className="account-grid">{list.map((a) => <AccountCard key={a.id} account={a} />)}</div>
                </>
              )}
              <CreateAccountForm onCreated={accounts.reload} />
            </>
          )}
        </AsyncView>
      </section>

      <section className="card" aria-labelledby="recent-title">
        <div className="card-head"><h2 id="recent-title">Recent transactions</h2><Link to="/transactions">View all</Link></div>
        <AsyncView state={recent}>{(page) => <TransactionTable items={page.items} />}</AsyncView>
      </section>
    </>
  );
}

export function AccountsPage() {
  const accounts = useAsync(() => api.accounts(), []);
  return (
    <>
      <PageHeader title="Accounts" subtitle="All of your accounts." />
      <section className="card">
        <AsyncView state={accounts}>
          {(list) => (
            <>
              {list.length === 0 ? <p className="empty">No accounts yet.</p>
                : <div className="account-grid">{list.map((a) => <AccountCard key={a.id} account={a} />)}</div>}
              <CreateAccountForm onCreated={accounts.reload} />
            </>
          )}
        </AsyncView>
      </section>
    </>
  );
}

export function AccountDetailPage() {
  const { id } = useParams();
  const [page, setPage] = useState(0);
  const account = useAsync(() => api.account(id), [id]);
  const ledger = useAsync(() => api.ledger(id, page, 10), [id, page]);

  return (
    <>
      <PageHeader title="Account overview"><Link className="btn btn-secondary" to="/accounts">Back to accounts</Link></PageHeader>
      <AsyncView state={account}>
        {(a) => (
          <section className="card">
            <div className="detail-grid">
              <div><span className="muted small">Account number</span><div className="mono big">{a.accountNumber}</div></div>
              <div><span className="muted small">Balance</span><div className="big amount">{formatMoney(a.balance, a.currency)}</div></div>
              <div><span className="muted small">Status</span><div><Badge value={a.status} /></div></div>
              <div><span className="muted small">Opened</span><div>{formatDateTime(a.createdAt)}</div></div>
            </div>
          </section>
        )}
      </AsyncView>
      <section className="card" aria-labelledby="ledger-title">
        <h2 id="ledger-title">Ledger entries</h2>
        <AsyncView state={ledger}>
          {(data) => (
            <>
              {data.items.length === 0 ? <p className="empty">No ledger entries yet.</p> : (
                <div className="table-wrap">
                  <table>
                    <thead><tr><th>Date</th><th>Entry</th><th className="num">Amount</th><th className="num">Balance after</th></tr></thead>
                    <tbody>
                      {data.items.map((e) => (
                        <tr key={e.id}>
                          <td data-label="Date">{formatDateTime(e.createdAt)}</td>
                          <td data-label="Entry"><Badge value={e.entryType} /></td>
                          <td data-label="Amount" className="num amount">{formatMoney(e.amount, e.currency)}</td>
                          <td data-label="Balance after" className="num amount">{formatMoney(e.balanceAfter, e.currency)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              <Pager page={data.page} totalPages={data.totalPages} onChange={setPage} />
            </>
          )}
        </AsyncView>
      </section>
    </>
  );
}

export function HistoryPage() {
  const [accountId, setAccountId] = useState('');
  const [page, setPage] = useState(0);
  const accounts = useAsync(() => api.accounts(), []);
  const txns = useAsync(() => api.transactions({ accountId, page, size: 10 }), [accountId, page]);
  const navigate = useNavigate();

  return (
    <>
      <PageHeader title="Transaction history" subtitle="Everything that touched your accounts." />
      <section className="card">
        <div className="card-head">
          <label className="inline-filter">
            <span>Account</span>
            <select value={accountId} onChange={(e) => { setAccountId(e.target.value); setPage(0); }}>
              <option value="">All accounts</option>
              {(accounts.data || []).map((a) => <option key={a.id} value={a.id}>{a.accountNumber} ({a.currency})</option>)}
            </select>
          </label>
          <button type="button" className="btn btn-secondary btn-sm" onClick={() => navigate('/transfer')}>New transfer</button>
        </div>
        <AsyncView state={txns}>
          {(data) => (
            <>
              <TransactionTable items={data.items} />
              <Pager page={data.page} totalPages={data.totalPages} onChange={setPage} />
            </>
          )}
        </AsyncView>
      </section>
    </>
  );
}
