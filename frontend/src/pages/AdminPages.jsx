import { useState } from 'react';
import { AsyncView, Banner, ErrorBanner } from '../components/Banner.jsx';
import { AuditEvents } from '../components/AuditEvents.jsx';
import { Badge } from '../components/Badge.jsx';
import { SelectField, TextField } from '../components/Field.jsx';
import { PageHeader } from '../components/Layout.jsx';
import { Pager } from '../components/Pager.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { api } from '../lib/api.js';
import { CURRENCIES, formatDateTime, formatMoney } from '../lib/money.js';
import { useAsync } from '../lib/useAsync.js';
import * as v from '../lib/validators.js';
import { Link } from '../router.jsx';

const ROLES = ['CUSTOMER', 'AUDITOR', 'ADMIN'];
const USER_STATUSES = ['ACTIVE', 'SUSPENDED', 'DISABLED'];
const ACCOUNT_STATUSES = ['ACTIVE', 'FROZEN', 'CLOSED'];
const opts = (list) => list.map((x) => ({ value: x, label: x }));

function CreateUserForm({ onCreated }) {
  const [form, setForm] = useState({ fullName: '', email: '', password: '', role: 'CUSTOMER' });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState(null);
  const [done, setDone] = useState('');
  const [busy, setBusy] = useState(false);
  const set = (n) => (e) => setForm((f) => ({ ...f, [n]: e.target.value }));

  const submit = async (e) => {
    e.preventDefault();
    const found = {
      fullName: v.validateFullName(form.fullName), email: v.validateEmail(form.email),
      password: v.validatePassword(form.password),
    };
    setErrors(found); setError(null); setDone('');
    if (v.hasErrors(found)) return;
    setBusy(true);
    try {
      const created = await api.adminCreateUser({
        email: form.email.trim(), password: form.password, fullName: form.fullName.trim(), role: form.role,
      });
      setDone(`User ${created.email} created.`);
      setForm({ fullName: '', email: '', password: '', role: 'CUSTOMER' });
      onCreated();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="card" aria-labelledby="create-user">
      <h2 id="create-user">Create user</h2>
      <form onSubmit={submit} noValidate className="form-grid">
        <ErrorBanner error={error} />
        <Banner kind="success">{done}</Banner>
        <TextField label="Full name" value={form.fullName} onChange={set('fullName')} error={errors.fullName} />
        <TextField label="Email" type="email" value={form.email} onChange={set('email')} error={errors.email} />
        <TextField label="Initial password" type="password" autoComplete="new-password" value={form.password}
          onChange={set('password')} error={errors.password} />
        <SelectField label="Role" value={form.role} onChange={set('role')} options={opts(ROLES)} />
        <div><button className="btn btn-primary" disabled={busy}>{busy ? 'Creating...' : 'Create user'}</button></div>
      </form>
    </section>
  );
}

function UserRow({ user, isSelf, onChanged, onMessage }) {
  const [currency, setCurrency] = useState('INR');
  const [busy, setBusy] = useState(false);

  const run = async (label, action) => {
    setBusy(true);
    try {
      await action();
      onMessage({ kind: 'success', text: `${label} for ${user.email}.` });
      onChanged();
    } catch (err) {
      onMessage({ kind: 'error', error: err });
    } finally {
      setBusy(false);
    }
  };

  const changeStatus = (e) => {
    if (!window.confirm(`Change status of ${user.email} to ${e.target.value}?`)) return;
    run('Status updated', () => api.adminSetUserStatus(user.id, e.target.value));
  };
  const changeRole = (e) => {
    if (!window.confirm(`Change role of ${user.email} to ${e.target.value}?`)) return;
    run('Role updated', () => api.adminSetUserRole(user.id, e.target.value));
  };

  return (
    <tr>
      <td data-label="User">{user.fullName}<div className="muted small">{user.email}</div></td>
      <td data-label="Role">
        <select aria-label={`Role of ${user.email}`} value={user.role} onChange={changeRole} disabled={busy || isSelf}>
          {ROLES.map((r) => <option key={r}>{r}</option>)}
        </select>
      </td>
      <td data-label="Status">
        <select aria-label={`Status of ${user.email}`} value={user.status} onChange={changeStatus} disabled={busy || isSelf}>
          {USER_STATUSES.map((s) => <option key={s}>{s}</option>)}
        </select>
      </td>
      <td data-label="Joined">{formatDateTime(user.createdAt)}</td>
      <td data-label="Open account">
        {user.role === 'CUSTOMER' ? (
          <span className="row-actions">
            <select aria-label={`Currency for ${user.email}`} value={currency} onChange={(e) => setCurrency(e.target.value)}>
              {CURRENCIES.map((c) => <option key={c}>{c}</option>)}
            </select>
            <button type="button" className="btn btn-secondary btn-sm" disabled={busy}
              onClick={() => run('Account opened', () => api.adminCreateAccount(user.id, currency))}>Add</button>
          </span>
        ) : <span className="muted small">-</span>}
      </td>
    </tr>
  );
}

function UsersPanel() {
  const { user: me } = useAuth();
  const [page, setPage] = useState(0);
  const [message, setMessage] = useState(null);
  const users = useAsync(() => api.adminUsers(page, 10), [page]);

  return (
    <>
      <section className="card" aria-labelledby="users-title">
        <h2 id="users-title">Users</h2>
        {message?.kind === 'error' ? <ErrorBanner error={message.error} /> : <Banner kind="success">{message?.text}</Banner>}
        <AsyncView state={users}>
          {(data) => (
            <>
              <div className="table-wrap">
                <table>
                  <thead><tr><th>User</th><th>Role</th><th>Status</th><th>Joined</th><th>Open account</th></tr></thead>
                  <tbody>
                    {data.items.map((u) => (
                      <UserRow key={u.id} user={u} isSelf={u.id === me.id} onChanged={users.reload} onMessage={setMessage} />
                    ))}
                  </tbody>
                </table>
              </div>
              <Pager page={data.page} totalPages={data.totalPages} onChange={setPage} />
            </>
          )}
        </AsyncView>
      </section>
      <CreateUserForm onCreated={users.reload} />
    </>
  );
}

function AccountsPanel() {
  const [page, setPage] = useState(0);
  const [message, setMessage] = useState(null);
  const accounts = useAsync(() => api.adminAccounts(page, 10), [page]);

  const change = async (account, status) => {
    if (!window.confirm(`Set account ${account.accountNumber} to ${status}?`)) return;
    try {
      await api.adminSetAccountStatus(account.id, status);
      setMessage({ kind: 'success', text: `Account ${account.accountNumber} is now ${status}.` });
      accounts.reload();
    } catch (err) {
      setMessage({ kind: 'error', error: err });
    }
  };

  return (
    <section className="card" aria-labelledby="accounts-title">
      <h2 id="accounts-title">Accounts</h2>
      {message?.kind === 'error' ? <ErrorBanner error={message.error} /> : <Banner kind="success">{message?.text}</Banner>}
      <AsyncView state={accounts}>
        {(data) => (
          <>
            <div className="table-wrap">
              <table>
                <thead><tr><th>Account</th><th>Type</th><th className="num">Balance</th><th>Status</th><th>Change status</th></tr></thead>
                <tbody>
                  {data.items.map((a) => (
                    <tr key={a.id}>
                      <td data-label="Account" className="mono">{a.accountNumber}</td>
                      <td data-label="Type">{a.accountType}</td>
                      <td data-label="Balance" className="num amount">{formatMoney(a.balance, a.currency)}</td>
                      <td data-label="Status"><Badge value={a.status} /></td>
                      <td data-label="Change status">
                        {a.accountType === 'SYSTEM' ? <span className="muted small">system account</span> : (
                          <select aria-label={`Status of ${a.accountNumber}`} value={a.status} onChange={(e) => change(a, e.target.value)}>
                            {ACCOUNT_STATUSES.map((s) => <option key={s}>{s}</option>)}
                          </select>
                        )}
                      </td>
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

export function AdminDashboardPage() {
  const [tab, setTab] = useState('users');
  return (
    <>
      <PageHeader title="Administration" subtitle="Manage users and accounts. Every change is written to the audit trail.">
        <Link className="btn btn-secondary" to="/admin/audit">View audit trail</Link>
      </PageHeader>
      <div className="tabs" role="tablist" aria-label="Administration sections">
        {[['users', 'Users'], ['accounts', 'Accounts']].map(([key, label]) => (
          <button key={key} type="button" role="tab" aria-selected={tab === key} className={tab === key ? 'tab active' : 'tab'}
            onClick={() => setTab(key)}>{label}</button>
        ))}
      </div>
      {tab === 'users' ? <UsersPanel /> : <AccountsPanel />}
    </>
  );
}

export function AdminAuditPage() {
  return (
    <>
      <PageHeader title="Audit trail" subtitle="Read-only. Audit records cannot be edited or deleted." />
      <AuditEvents />
    </>
  );
}
