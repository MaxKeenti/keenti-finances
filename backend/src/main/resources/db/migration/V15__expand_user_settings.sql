ALTER TABLE app_user ADD COLUMN locale VARCHAR(2) NOT NULL DEFAULT 'es';
ALTER TABLE app_user ADD COLUMN transaction_page_size SMALLINT NOT NULL DEFAULT 25;
ALTER TABLE app_user ADD COLUMN transaction_sort_by VARCHAR(50) NOT NULL DEFAULT 'transactionDate';
ALTER TABLE app_user ADD COLUMN transaction_sort_direction VARCHAR(4) NOT NULL DEFAULT 'desc';
ALTER TABLE app_user ADD COLUMN mobile_pinned_nav_items VARCHAR(160) NOT NULL DEFAULT '/transactions,/subscriptions,/debts';
ALTER TABLE app_user ADD COLUMN dock_magnification BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE app_user ADD CONSTRAINT app_user_locale_valid
    CHECK (locale IN ('en', 'es'));
ALTER TABLE app_user ADD CONSTRAINT app_user_transaction_page_size_valid
    CHECK (transaction_page_size IN (10, 25, 50, 100));
ALTER TABLE app_user ADD CONSTRAINT app_user_transaction_sort_by_valid
    CHECK (transaction_sort_by IN (
        'transactionDate',
        'amount',
        'direction',
        'description',
        'categoryName',
        'contactName'
    ));
ALTER TABLE app_user ADD CONSTRAINT app_user_transaction_sort_direction_valid
    CHECK (transaction_sort_direction IN ('asc', 'desc'));
