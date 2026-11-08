import { useAuth } from '../context/AuthContext.jsx';
import { Link, useNavigate } from '../router.jsx';

// Navigation is chosen from the role the SERVER returned. Hiding links is only a convenience;
// the backend enforces the real permissions on every request.
const NAV = {
  CUSTOMER: [
    ['/dashboard', 'Dashboard'], ['/accounts', 'Accounts'], ['/transactions', 'History'],
    ['/deposit', 'Deposit'], ['/withdraw', 'Withdraw'], ['/transfer', 'Transfer'], ['/profile', 'Profile'],
  ],
  ADMIN: [['/admin', 'Administration'], ['/admin/audit', 'Audit trail'], ['/profile', 'Profile']],
  AUDITOR: [['/auditor', 'Audit & ledger'], ['/profile', 'Profile']],
};

export function Layout({ children }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const links = NAV[user.role] || [];

  const onLogout = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main">Skip to content</a>
      <header className="topbar">
        <div className="topbar-inner">
          <Link to="/" className="brand" aria-label="LedgerLine home"><span className="brand-mark" aria-hidden="true" />LedgerLine</Link>
          <div className="user-box">
            <span className="user-name">{user.fullName}</span>
            <span className="role-pill">{user.role}</span>
            <button type="button" className="btn btn-secondary btn-sm" onClick={onLogout}>Log out</button>
          </div>
        </div>
        <nav className="nav" aria-label="Main">
          {links.map(([to, label]) => <Link key={to} to={to}>{label}</Link>)}
        </nav>
      </header>
      <main id="main" className="content">{children}</main>
      <footer className="footer">LedgerLine - Money Movement with a Ledger You Can Prove</footer>
    </div>
  );
}

export function PageHeader({ title, subtitle, children }) {
  return (
    <div className="page-head">
      <div>
        <h1>{title}</h1>
        {subtitle ? <p className="muted">{subtitle}</p> : null}
      </div>
      {children}
    </div>
  );
}
