export type TransactionFilters = { q: string; from: string; to: string; account: string; category: string; direction: string };
export const FILTER_KEYS = ['q', 'from', 'to', 'account', 'category', 'direction'] as const;
export function readTransactionFilters(params: URLSearchParams): TransactionFilters {
 return Object.fromEntries(FILTER_KEYS.map(key => [key, (params.get(key) ?? '').trim()])) as TransactionFilters;
}
export function hasTransactionFilters(filters: TransactionFilters): boolean {
 return FILTER_KEYS.some(key => Boolean(filters[key]));
}
export function matchesTransaction(tx: { description: string | null; categoryName: string | null; contactName: string | null; accountName: string | null; transactionDate: string; accountId: number | null; categoryId: number; direction: string }, filters: TransactionFilters): boolean {
 const text = [tx.description, tx.categoryName, tx.contactName, tx.accountName].filter(Boolean).join(' ').toLocaleLowerCase();
 return (!filters.q || text.includes(filters.q.toLocaleLowerCase())) &&
  (!filters.from || tx.transactionDate >= filters.from) && (!filters.to || tx.transactionDate <= filters.to) &&
  (!filters.account || String(tx.accountId) === filters.account) &&
  (!filters.category || String(tx.categoryId) === filters.category) &&
  (!filters.direction || tx.direction === filters.direction);
}
