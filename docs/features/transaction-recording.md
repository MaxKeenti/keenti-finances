# Recording and finding Transactions

Income and expense are direct choices in the create/edit dialog. Account identifies where money moved, Category its purpose, and optional Box Funding the reserved money used. The funding summary groups the Transaction amount, funded amount, unallocated remainder and projected Available to Spend.

A Box's Record expense action opens `/transactions?expenseFromBox=<id>` with EGRESS and that active Box selected. The initial allocation is zero, so the User must enter an amount or remove it. It never chooses a Financial Account or saves anything. Existing atomic Transaction create/update/delete and funding reversal remain unchanged. Negative Available to Spend does not reject an unfunded real expense.

Search, inclusive date bounds, Financial Account, Category and Direction filter the full `activityTransactions` collection before sorting and pagination. That collection already comes from the unpaginated Transactions endpoint. Filters persist in the URL and sorting links, and applying them resets the page. Active filters show Transactions only; clearing them restores the combined Transaction/Transfer ledger. Failed activity loading reports unavailable with Retry. This does not add a backend filter API or change its legacy array/paged response contract.

The existing full-history loading strategy remains a scalability limit for very large histories. A future backend-filtered combined ledger must preserve Transfer visibility, count consistency and user isolation.
