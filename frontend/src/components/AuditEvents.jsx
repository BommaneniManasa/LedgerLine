import { useState } from 'react';
import { api } from '../lib/api.js';
import { formatDateTime } from '../lib/money.js';
import { useAsync } from '../lib/useAsync.js';
import { AsyncView } from './Banner.jsx';
import { Badge } from './Badge.jsx';
import { Pager } from './Pager.jsx';

const ACTIONS = [
  'LOGIN_SUCCESS', 'LOGIN_FAILURE', 'LOGOUT', 'USER_REGISTERED', 'USER_CREATED_BY_ADMIN', 'ADMIN_BOOTSTRAPPED',
  'PROFILE_UPDATED', 'PASSWORD_CHANGED', 'USER_STATUS_CHANGED', 'USER_ROLE_CHANGED', 'ACCOUNT_CREATED',
  'ACCOUNT_STATUS_CHANGED', 'DEPOSIT', 'WITHDRAWAL', 'TRANSFER', 'TRANSACTION_REJECTED', 'AUTHZ_DENIED',
  'AUDIT_LOG_VIEWED',
];

/** Read-only view of the audit trail. There are no edit or delete controls: the API has none either. */
export function AuditEvents() {
  const [action, setAction] = useState('');
  const [page, setPage] = useState(0);
  const state = useAsync(() => api.auditEvents({ action, page, size: 15 }), [action, page]);

  return (
    <section className="card" aria-labelledby="audit-title">
      <div className="card-head">
        <h2 id="audit-title">Audit trail</h2>
        <label className="inline-filter">
          <span>Action</span>
          <select value={action} onChange={(e) => { setAction(e.target.value); setPage(0); }}>
            <option value="">All actions</option>
            {ACTIONS.map((a) => <option key={a} value={a}>{a}</option>)}
          </select>
        </label>
      </div>
      <AsyncView state={state}>
        {(data) => (
          <>
            {data.items.length === 0 ? <p className="empty">No audit events.</p> : (
              <div className="table-wrap">
                <table>
                  <thead><tr><th>Time</th><th>Actor</th><th>Action</th><th>Outcome</th><th>Target</th><th>IP</th><th>Details</th></tr></thead>
                  <tbody>
                    {data.items.map((e) => (
                      <tr key={e.id}>
                        <td data-label="Time">{formatDateTime(e.occurredAt)}</td>
                        <td data-label="Actor">{e.actorEmail || 'anonymous'}{e.actorRole ? <div className="muted small">{e.actorRole}</div> : null}</td>
                        <td data-label="Action" className="mono small">{e.action}</td>
                        <td data-label="Outcome"><Badge value={e.outcome} /></td>
                        <td data-label="Target" className="small">{e.targetType}{e.targetId ? <div className="mono muted">{e.targetId}</div> : null}</td>
                        <td data-label="IP" className="mono small">{e.ipAddress}</td>
                        <td data-label="Details" className="mono small wrap">{e.metadata}</td>
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
  );
}
