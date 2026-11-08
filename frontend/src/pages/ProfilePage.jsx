import { useState } from 'react';
import { Banner, ErrorBanner } from '../components/Banner.jsx';
import { TextField } from '../components/Field.jsx';
import { PageHeader } from '../components/Layout.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { api } from '../lib/api.js';
import { formatDateTime } from '../lib/money.js';
import * as v from '../lib/validators.js';
import { useNavigate } from '../router.jsx';

function DetailsForm() {
  const { user, setUser } = useAuth();
  const [form, setForm] = useState({ fullName: user.fullName, phone: user.phone || '' });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState(null);
  const [done, setDone] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    const found = { fullName: v.validateFullName(form.fullName), phone: v.validatePhone(form.phone) };
    setErrors(found); setError(null); setDone('');
    if (v.hasErrors(found)) return;
    setBusy(true);
    try {
      // Only name and phone are sent. Email, role and status cannot be changed from here.
      const updated = await api.updateMe({ fullName: form.fullName.trim(), phone: form.phone.trim() || null });
      setUser(updated);
      setDone('Profile updated.');
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="card" aria-labelledby="profile-title">
      <h2 id="profile-title">Your details</h2>
      <dl className="summary">
        <dt>Email</dt><dd>{user.email}</dd>
        <dt>Role</dt><dd>{user.role}</dd>
        <dt>Member since</dt><dd>{formatDateTime(user.createdAt)}</dd>
      </dl>
      <form onSubmit={submit} noValidate>
        <ErrorBanner error={error} />
        <Banner kind="success">{done}</Banner>
        <TextField label="Full name" value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} error={errors.fullName} />
        <TextField label="Phone" type="tel" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} error={errors.phone} />
        <button className="btn btn-primary" disabled={busy}>{busy ? 'Saving...' : 'Save changes'}</button>
      </form>
    </section>
  );
}

function PasswordForm() {
  const { logout } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ current: '', next: '', confirm: '' });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    const found = {
      current: v.validateLoginPassword(form.current),
      next: v.validatePassword(form.next),
      confirm: v.validatePasswordMatch(form.next, form.confirm),
    };
    setErrors(found); setError(null);
    if (v.hasErrors(found)) return;
    setBusy(true);
    try {
      await api.changePassword(form.current, form.next);
      // The server invalidates every older token after a password change, so sign in again.
      await logout();
      navigate('/login?msg=password-changed', { replace: true });
    } catch (err) {
      setError(err);
      setBusy(false);
    }
  };

  return (
    <section className="card" aria-labelledby="pw-title">
      <h2 id="pw-title">Change password</h2>
      <form onSubmit={submit} noValidate>
        <ErrorBanner error={error} />
        <TextField label="Current password" type="password" autoComplete="current-password" value={form.current}
          onChange={(e) => setForm({ ...form, current: e.target.value })} error={errors.current} />
        <TextField label="New password" type="password" autoComplete="new-password" value={form.next}
          onChange={(e) => setForm({ ...form, next: e.target.value })} error={errors.next}
          hint="At least 10 characters with a letter and a digit." />
        <TextField label="Confirm new password" type="password" autoComplete="new-password" value={form.confirm}
          onChange={(e) => setForm({ ...form, confirm: e.target.value })} error={errors.confirm} />
        <button className="btn btn-primary" disabled={busy}>{busy ? 'Changing...' : 'Change password'}</button>
      </form>
    </section>
  );
}

export function ProfilePage() {
  return (
    <>
      <PageHeader title="Profile" subtitle="Manage your personal details and password." />
      <div className="two-col"><DetailsForm /><PasswordForm /></div>
    </>
  );
}
