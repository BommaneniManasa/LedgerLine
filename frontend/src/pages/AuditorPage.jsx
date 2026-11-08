import { useState } from 'react';
import { AsyncView, Banner, ErrorBanner, Spinner } from '../components/Banner.jsx';
import { AuditEvents } from '../components/AuditEvents.jsx';
import { Badge } from '../components/Badge.jsx';
import { PageHeader } from '../components/Layout.jsx';
import { Pager } from '../components/Pager.jsx';
import { TransactionTable } from '../components/TransactionTable.jsx';
import { api } from '../lib/api.js';
import { formatDateTime, formatMoney } from '../lib/money.js';
import { useAsync } from '../lib/useAsync.js';

function IntegrityPanel() {
  const [report, setReport] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const run = async () => {
    setBusy(true); setError(null);
    try {
      setReport(await api.auditorIntegrity());
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="card" aria-labelledby="integrity-title">
      <div className="card-head">
        <h2 id="integrity-title">Ledger integrity</h2>
        <button type="button" className="btn btn-primary btn-sm" onClick={run} disabled={busy}>{busy ? 'Checking...' : 'Run check'}</button>
      </div>
      <p className="muted">Recomputes debits, credits and every account balance from the raw ledger entries.</p>
      <ErrorBanner error={error} />
      {busy && !report ? <Spinner label="Checking ledger" /> : null}
      {report ? (
        <>
          <Banner kind={report.balanced ? 'success' : 'error'}>
            {report.balanced ? 'Ledger is balanced: debits equal credits and every balance matches its entries.'
              : 'Ledger problems found. See details below.'}
          </Banner>
          <dl className="summary">
            <dt>Total debits</dt><dd className="amount">{formatMoney(report.totalDebits, 'INR')}</dd>
            <dt>Total credits</dt><dd className="amount">{formatMoney(report.totalCredits, 'INR')}</dd>
            <dt>Unbalanced transactions</dt><dd>{report.unbalancedTransactions.length}</dd>
            <dt>Mismatched accounts</dt><dd>{report.mismatchedAccounts.length}</dd>
            <dt>Checked at</dt><dd>{formatDateTime(report.checkedAt)}</dd>
          </dl>
          <p className="muted small">Totals are the sum of raw amounts across all currencies.</p>
          {report.mismatchedAccounts.length > 0 ? (
            <ul>{report.mismatchedAccounts.map((m) => (
              <li key={m.accountId}><span className="mono">{m.accountNumber}</span>: stored {String(m.storedBalance)}, ledger {String(m.ledgerBalance)}</li>
            ))}</ul>
          ) : null}
        </>
      ) : null}
    </section>
  );
}

function AccountsPanel() {
  const [page, setPage] = useState(0);
  const accounts = useAsync(() => api.auditorAccounts(page, 10), [page]);
  return (
    <section className="card" aria-labelledby="aud-acc">
      <h2 id="aud-acc">Accounts (read-only)</h2>
      <AsyncView state={accounts}>
        {(data) => (
          <>
            <div className="table-wrap">
              <table>
                <thead><tr><th>Account</th><th>Type</th><th>Currency</th><th className="num">Balance</th><th>Status</th><th>Opened</th></tr></thead>
                <tbody>
                  {data.items.map((a) => (
                    <tr key={a.id}>
                      <td data-label="Account" className="mono">{a.accountNumber}</td>
                      <td data-label="Type">{a.accountType}</td>
                      <td data-label="Currency">{a.currency}</td>
                      <td data-label="Balance" className="num amount">{formatMoney(a.balance, a.currency)}</td>
                      <td data-label="Status"><Badge value={a.status} /></td>
                      <td data-label="Opened">{formatDateTime(a.createdAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pager page={data.page} totalPages={data.totalPages} onChange={setPage} />
          </>
        )}
      </AsyncView>
    </section>
  );
}

function TransactionsPanel() {
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState(null);
  const txns = useAsync(() => api.auditorTransactions(page, 10), [page]);
  const entries = useAsync(() => (selected ? api.auditorTransactionLedger(selected.id) : Promise.resolve([])), [selected?.id]);

  return (
    <section className="card" aria-labelledby="aud-txn">
      <h2 id="aud-txn">Transactions (read-only)</h2>
      <AsyncView state={txns}>
        {(data) => (
          <>
            <TransactionTable items={data.items} onSelect={setSelected} />
            <Pager page={data.page} totalPages={data.totalPages} onChange={setPage} />
          </>
        )}
      </AsyncView>
      {selected ? (
        <div className="subcard" aria-live="polite">
          <h3>Ledger entries for <span className="mono">{selected.reference}</span></h3>
          <AsyncView state={entries}>
            {(list) => (
              <ul className="entry-list">
                {list.map((e) => (
                  <li key={e.id}><Badge value={e.entryType} /> <span className="amount">{formatMoney(e.amount, e.currency)}</span>{' '}
                    <span className="muted small">balance after {formatMoney(e.balanceAfter, e.currency)}</span></li>
                ))}
              </ul>
            )}
          </AsyncView>
        </div>
      ) : null}
    </section>
  );
}

export function AuditorDashboardPage() {
  const [tab, setTab] = useState('integrity');
  const tabs = [['integrity', 'Ledger integrity'], ['accounts', 'Accounts'], ['transactions', 'Transactions'], ['audit', 'Audit events']];
  return (
    <>
      <PageHeader title="Auditor dashboard" subtitle="Read-only view of financial records and the audit trail." />
      <div className="tabs" role="tablist" aria-label="Auditor sections">
        {tabs.map(([key, label]) => (
          <button key={key} type="button" role="tab" aria-selected={tab === key} className={tab === key ? 'tab active' : 'tab'}
            onClick={() => setTab(key)}>{label}</button>
        ))}
      </div>
      {tab === 'integrity' ? <IntegrityPanel /> : null}
      {tab === 'accounts' ? <AccountsPanel /> : null}
      {tab === 'transactions' ? <TransactionsPanel /> : null}
      {tab === 'audit' ? <AuditEvents /> : null}
    </>
  );
}
