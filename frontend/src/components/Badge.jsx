const TONES = {
  ACTIVE: 'ok', COMPLETED: 'ok', SUCCESS: 'ok', CREDIT: 'ok', IN: 'ok',
  FROZEN: 'warn', SUSPENDED: 'warn', DENIED: 'warn',
  CLOSED: 'bad', DISABLED: 'bad', FAILED: 'bad', FAILURE: 'bad', OUT: 'neutral', DEBIT: 'neutral',
};
/** Text is rendered by React (escaped). Never put server text into dangerouslySetInnerHTML. */
export function Badge({ value }) {
  return <span className={`badge badge-${TONES[value] || 'neutral'}`}>{value}</span>;
}
