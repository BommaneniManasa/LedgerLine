import test from 'node:test';
import assert from 'node:assert/strict';
import * as v from './validators.js';

test('email validation', () => {
  assert.equal(v.validateEmail('a@b.io'), '');
  assert.notEqual(v.validateEmail(''), '');
  assert.notEqual(v.validateEmail('not-an-email'), '');
  assert.notEqual(v.validateEmail('a'.repeat(250) + '@b.io'), '');
});

test('password policy mirrors the backend (10+ chars, letter and digit, max 72 bytes)', () => {
  assert.notEqual(v.validatePassword('short1'), '');
  assert.notEqual(v.validatePassword('onlyletters'), '');
  assert.notEqual(v.validatePassword('1234567890'), '');
  assert.equal(v.validatePassword('abcdefghi1'), '');
  assert.notEqual(v.validatePassword('a1'.repeat(37)), ''); // 74 bytes
  assert.equal(v.validatePassword('a1'.repeat(36)), ''); // 72 bytes
});

test('phone is optional but validated when present', () => {
  assert.equal(v.validatePhone(''), '');
  assert.equal(v.validatePhone('+91 98765 43210'), '');
  assert.notEqual(v.validatePhone('abc'), '');
});

test('account number is normalised and validated', () => {
  assert.equal(v.normalizeAccountNumber('  ll-12345678 '), 'LL-12345678');
  assert.equal(v.validateAccountNumber('ll-12345678'), '');
  assert.notEqual(v.validateAccountNumber('x'), '');
  assert.notEqual(v.validateAccountNumber("1; DROP TABLE accounts"), '');
});

test('description, name, match and hasErrors', () => {
  assert.notEqual(v.validateDescription('x'.repeat(201)), '');
  assert.equal(v.validateDescription('x'.repeat(200)), '');
  assert.notEqual(v.validateFullName('   '), '');
  assert.notEqual(v.validatePasswordMatch('a', 'b'), '');
  assert.equal(v.hasErrors({ a: '', b: '' }), false);
  assert.equal(v.hasErrors({ a: '', b: 'x' }), true);
});
