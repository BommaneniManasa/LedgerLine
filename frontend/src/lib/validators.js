// Client-side validation: gives fast feedback and MIRRORS the backend rules (DTO annotations + PasswordPolicy).
// It is only a convenience. The backend re-validates everything and is the real gatekeeper.
// Each function returns an error message, or '' when the value is fine.
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PHONE_RE = /^\+?[0-9 ()-]{7,20}$/;
const ACCOUNT_NUMBER_RE = /^[A-Z0-9-]{6,20}$/;

export function validateEmail(value) {
  const v = String(value ?? '').trim();
  if (!v) return 'Email is required';
  if (v.length > 254) return 'Email is too long';
  if (!EMAIL_RE.test(v)) return 'Enter a valid email address';
  return '';
}

export function validatePassword(value) {
  const v = String(value ?? '');
  if (v.length < 10) return 'Password must be at least 10 characters long';
  if (new TextEncoder().encode(v).length > 72) return 'Password must be at most 72 bytes long';
  if (!/\p{L}/u.test(v) || !/\p{Nd}/u.test(v)) return 'Password must contain at least one letter and one digit';
  return '';
}

export function validateLoginPassword(value) {
  return value ? '' : 'Password is required';
}

export function validateFullName(value) {
  const v = String(value ?? '').trim();
  if (!v) return 'Full name is required';
  if (v.length > 100) return 'Full name must be at most 100 characters';
  return '';
}

export function validatePhone(value) {
  const v = String(value ?? '').trim();
  if (!v) return ''; // optional
  return PHONE_RE.test(v) ? '' : 'Enter a valid phone number (7-20 digits, may start with +)';
}

export function validateDescription(value) {
  return String(value ?? '').length > 200 ? 'Description must be at most 200 characters' : '';
}

export function normalizeAccountNumber(value) {
  return String(value ?? '').trim().toUpperCase();
}

export function validateAccountNumber(value) {
  const v = normalizeAccountNumber(value);
  if (!v) return 'Destination account number is required';
  return ACCOUNT_NUMBER_RE.test(v) ? '' : 'Account numbers use capital letters, digits and "-" (6-20 characters)';
}

export function validatePasswordMatch(password, confirm) {
  return password === confirm ? '' : 'Passwords do not match';
}

export function hasErrors(errors) {
  return Object.values(errors).some(Boolean);
}
