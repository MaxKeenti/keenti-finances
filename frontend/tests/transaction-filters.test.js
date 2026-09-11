// @ts-nocheck
import { describe, expect, test } from 'bun:test';
import { matchesTransaction, readTransactionFilters } from '../src/lib/transaction-filters';
const tx = { description: 'Groceries', categoryName: 'Food', contactName: 'Market', accountName: 'Cash', transactionDate: '2026-09-07', accountId: 1, categoryId: 2, direction: 'EGRESS' };
describe('transaction history filters', () => {
 test('combines text, inclusive dates, account, category and direction', () => {
  expect(matchesTransaction(tx, readTransactionFilters(new URLSearchParams('q=MARKET&from=2026-09-07&to=2026-09-07&account=1&category=2&direction=EGRESS')))).toBe(true);
  for (const query of ['q=missing', 'from=2026-09-08', 'to=2026-09-06', 'account=9', 'category=9', 'direction=INGRESS']) {
   expect(matchesTransaction(tx, readTransactionFilters(new URLSearchParams(query)))).toBe(false);
  }
 });
 test('matches beyond the first page before pagination and tolerates null labels', () => {
  const rows = Array.from({length: 31}, (_, id) => ({...tx, description: id === 30 ? 'Needle' : null, contactName: null}));
  const filtered = rows.filter(row => matchesTransaction(row, readTransactionFilters(new URLSearchParams('q=needle'))));
  expect(filtered).toHaveLength(1);
  expect(filtered.slice(0, 10)[0].description).toBe('Needle');
 });
});
