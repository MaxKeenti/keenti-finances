package com.keenti.finances.infrastructure.adapter.out.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Optional;

@Entity
@Table(name = "app_user")
public class UserEntity extends PanacheEntityBase {

    /**
     * Defaults for the per-User personalization columns. These are also encoded
     * in {@code V13__user_preferences.sql} as DB-level DEFAULTs for existing
     * rows backfilled by the migration. Hibernate includes every column on
     * INSERT, so it bypasses the SQL DEFAULT for new rows — these constants are
     * the single source of truth for that path. If you change a default,
     * change both this constant and the migration line.
     */
    public static final int DEFAULT_PRIMARY_HUE = 91;
    public static final String DEFAULT_HEADING_FONT = "Fraunces";
    public static final String DEFAULT_BODY_FONT = "Geist";
    public static final String DEFAULT_LOCALE = "es";
    public static final int DEFAULT_TRANSACTION_PAGE_SIZE = 25;
    public static final String DEFAULT_TRANSACTION_SORT_BY = "transactionDate";
    public static final String DEFAULT_TRANSACTION_SORT_DIRECTION = "desc";
    public static final String DEFAULT_MOBILE_PINNED_NAV_ITEMS = "/transactions,/subscriptions,/debts";
    public static final boolean DEFAULT_DOCK_MAGNIFICATION = true;
    public static final String DEFAULT_TIME_ZONE = "America/Mexico_City";
    public static final String DEFAULT_THEME_MODE = "system";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true)
    public String username;

    @Column(name = "password_hash", nullable = true)
    public String passwordHash;

    @Column(name = "workos_id", unique = true)
    public String workosId;

    @Column(name = "primary_hue", nullable = false)
    public int primaryHue = DEFAULT_PRIMARY_HUE;

    @Column(name = "heading_font", nullable = false, length = 50)
    public String headingFont = DEFAULT_HEADING_FONT;

    @Column(name = "body_font", nullable = false, length = 50)
    public String bodyFont = DEFAULT_BODY_FONT;

    @Column(name = "locale", nullable = false, length = 2)
    public String locale = DEFAULT_LOCALE;

    @Column(name = "transaction_page_size", nullable = false)
    public int transactionPageSize = DEFAULT_TRANSACTION_PAGE_SIZE;

    @Column(name = "transaction_sort_by", nullable = false, length = 50)
    public String transactionSortBy = DEFAULT_TRANSACTION_SORT_BY;

    @Column(name = "transaction_sort_direction", nullable = false, length = 4)
    public String transactionSortDirection = DEFAULT_TRANSACTION_SORT_DIRECTION;

    @Column(name = "mobile_pinned_nav_items", nullable = false, length = 160)
    public String mobilePinnedNavItems = DEFAULT_MOBILE_PINNED_NAV_ITEMS;

    @Column(name = "dock_magnification", nullable = false)
    public boolean dockMagnification = DEFAULT_DOCK_MAGNIFICATION;

    @Column(name = "time_zone", nullable = false, length = 64)
    public String timeZone = DEFAULT_TIME_ZONE;

    @Column(name = "theme_mode", nullable = false, length = 6)
    public String themeMode = DEFAULT_THEME_MODE;

    @Column(name = "account_tracking_activated_at")
    public LocalDate accountTrackingActivatedAt;

    @Column(name = "account_tracking_required", nullable = false)
    public boolean accountTrackingRequired;

    public static Optional<UserEntity> findByWorkosId(String workosId) {
        return find("workosId", workosId).firstResultOptional();
    }
}
