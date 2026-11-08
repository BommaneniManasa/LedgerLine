export function Pager({ page, totalPages, onChange }) {
  if (!totalPages || totalPages <= 1) return null;
  return (
    <nav className="pager" aria-label="Pagination">
      <button type="button" className="btn btn-secondary" disabled={page <= 0} onClick={() => onChange(page - 1)}>Previous</button>
      <span>Page {page + 1} of {totalPages}</span>
      <button type="button" className="btn btn-secondary" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>Next</button>
    </nav>
  );
}
