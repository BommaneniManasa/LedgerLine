import { useState } from 'react';
import { Banner, ErrorBanner } from '../components/Banner.jsx';
import { TextField } from '../components/Field.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { api } from '../lib/api.js';
import * as v from '../lib/validators.js';
import { Link, Navigate, homeFor, useNavigate } from '../router.jsx';

function AuthShell({ title, subtitle, children, footer }) {
  return (
    <div className="auth-page">
      <div className="auth-card">
        <div className="brand brand-large"><span className="brand-mark" aria-hidden="true" />LedgerLine</div>
        <h1>{title}</h1>
        <p className="muted">{subtitle}</p>
        {children}
        <p className="auth-footer">{footer}</p>
      </div>
    </div>
  );
}

const NOTICES = {
  registered: 'Account created. You can now sign in.',
  'password-changed': 'Password changed. Please sign in again.',
};

export function LoginPage() {
  const { user, login } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ email: '', password: '' });
  const [errors, setErrors] = useState({});
  const [apiError, setApiError] = useState(null);
  const [busy, setBusy] = useState(false);
  const notice = NOTICES[new URLSearchParams(window.location.search).get('msg')];

  if (user) return <Navigate to={homeFor(user.role)} />;

  const set = (name) => (e) => setForm((f) => ({ ...f, [name]: e.target.value }));

  const submit = async (e) => {
    e.preventDefault();
    const found = { email: v.validateEmail(form.email), password: v.validateLoginPassword(form.password) };
    setErrors(found);
    setApiError(null);
    if (v.hasErrors(found)) return;
    setBusy(true);
    try {
      const me = await login(form.email, form.password);
      navigate(homeFor(me.role), { replace: true });
    } catch (err) {
      setApiError(err);
      setBusy(false);
    }
  };

  return (
    <AuthShell title="Sign in" subtitle="Access your accounts securely."
      footer={<>New to LedgerLine? <Link to="/register">Create an account</Link></>}>
      <Banner kind="success">{notice}</Banner>
      <ErrorBanner error={apiError} />
      <form onSubmit={submit} noValidate>
        <TextField label="Email" type="email" autoComplete="username" value={form.email}
          onChange={set('email')} error={errors.email} />
        <TextField label="Password" type="password" autoComplete="current-password" value={form.password}
          onChange={set('password')} error={errors.password} />
        <button className="btn btn-primary btn-block" disabled={busy}>{busy ? 'Signing in...' : 'Sign in'}</button>
      </form>
    </AuthShell>
  );
}

export function RegisterPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: '', email: '', phone: '', password: '', confirm: '' });
  const [errors, setErrors] = useState({});
  const [apiError, setApiError] = useState(null);
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to={homeFor(user.role)} />;
  const set = (name) => (e) => setForm((f) => ({ ...f, [name]: e.target.value }));

  const submit = async (e) => {
    e.preventDefault();
    const found = {
      fullName: v.validateFullName(form.fullName),
      email: v.validateEmail(form.email),
      phone: v.validatePhone(form.phone),
      password: v.validatePassword(form.password),
      confirm: v.validatePasswordMatch(form.password, form.confirm),
    };
    setErrors(found);
    setApiError(null);
    if (v.hasErrors(found)) return;
    setBusy(true);
    try {
      // No role field: the server always creates a CUSTOMER.
      await api.register({
        email: form.email.trim(), password: form.password, fullName: form.fullName.trim(),
        phone: form.phone.trim() || null,
      });
      navigate('/login?msg=registered', { replace: true });
    } catch (err) {
      setApiError(err);
      setBusy(false);
    }
  };

  return (
    <AuthShell title="Create your account" subtitle="It takes a minute."
      footer={<>Already registered? <Link to="/login">Sign in</Link></>}>
      <ErrorBanner error={apiError} />
      <form onSubmit={submit} noValidate>
        <TextField label="Full name" autoComplete="name" value={form.fullName} onChange={set('fullName')} error={errors.fullName} />
        <TextField label="Email" type="email" autoComplete="email" value={form.email} onChange={set('email')} error={errors.email} />
        <TextField label="Phone (optional)" type="tel" autoComplete="tel" value={form.phone} onChange={set('phone')} error={errors.phone} />
        <TextField label="Password" type="password" autoComplete="new-password" value={form.password} onChange={set('password')}
          error={errors.password} hint="At least 10 characters with a letter and a digit." />
        <TextField label="Confirm password" type="password" autoComplete="new-password" value={form.confirm}
          onChange={set('confirm')} error={errors.confirm} />
        <button className="btn btn-primary btn-block" disabled={busy}>{busy ? 'Creating...' : 'Create account'}</button>
      </form>
    </AuthShell>
  );
}

export function NotFoundPage() {
  const { user } = useAuth();
  return (
    <div className="auth-page">
      <div className="auth-card">
        <h1>Page not found</h1>
        <p className="muted">The page you are looking for does not exist.</p>
        <Link className="btn btn-primary" to={user ? homeFor(user.role) : '/login'}>Go to start</Link>
      </div>
    </div>
  );
}
