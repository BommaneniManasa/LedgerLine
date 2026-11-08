import test from 'node:test';
import assert from 'node:assert/strict';
import { parseAmount, formatMoney, MAX_AMOUNT } from './money.js';

test('parseAmount accepts valid amounts', () => {
  assert.equal(parseAmount('10').value, 10);
  assert.equal(parseAmount('0.01').value, 0.01);
  assert.equal(parseAmount(' 1250.50 ').value, 1250.5);
  assert.equal(parseAmount(String(MAX_AMOUNT)).value, MAX_AMOUNT);
});

test('parseAmount rejects invalid amounts', () => {
  for (const bad of ['', '0', '0.00', '-5', 'abc', '1.234', '1e3', '1,000', '1000000.01', '99999999']) {
    assert.ok(parseAmount(bad).error, `should reject "${bad}"`);
  }
});

test('formatMoney formats and survives bad input', () => {
  assert.match(formatMoney(1234.5, 'USD'), /1,234\.50/);
  assert.equal(formatMoney(null, 'USD'), '-');
  assert.equal(typeof formatMoney(5, 'NOPE!'), 'string');
});
