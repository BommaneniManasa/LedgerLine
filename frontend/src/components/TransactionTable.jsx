import { formatDateTime, formatMoney } from '../lib/money.js';
import { Badge } from './Badge.jsx';

/** Works for customers (direction IN/OUT) and auditors (no direction, shows both account numbers). */
export function TransactionTable({ items, onSelect }) {
  if (!items || items.length === 0) return <p className="empty">No transactions yet.</p>;
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            <th>Date</th><th>Reference</th><th>Type</th><th>From</th><th>To</th>
            <th className="num">Amount</th><th>Status</th>
          </tr>
        </thead>
        <tbody>
          {items.map((t) => {
            const sign = t.direction === 'OUT' ? '-' : t.direction === 'IN' ? '+' : '';
            return (
              <tr key={t.id}>
                <td data-label="Date">{formatDateTime(t.createdAt)}</td>
                <td data-label="Reference">
                  {onSelect
                    ? <button type="button" className="link-button" onClick={() => onSelect(t)}>{t.reference}</button>
                    : t.reference}
                  {t.description ? <div className="muted small">{t.description}</div> : null}
                </td>
                <td data-label="Type">{t.type}</td>
                <td data-label="From" className="mono">{t.sourceAccountNumber}</td>
                <td data-label="To" className="mono">{t.destinationAccountNumber}</td>
                <td data-label="Amount" className={`num amount ${t.direction === 'OUT' ? 'neg' : t.direction === 'IN' ? 'pos' : ''}`}>
                  {sign}{formatMoney(t.amount, t.currency)}
                </td>
                <td data-label="Status"><Badge value={t.status} /></td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
