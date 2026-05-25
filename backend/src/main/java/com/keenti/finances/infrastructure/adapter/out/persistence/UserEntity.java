package com.keenti.finances.infrastructure.adapter.out.persistence;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

    public static Optional<UserEntity> findByUsername(String username) {
        return find("username", username).firstResultOptional();
    }

    public static Optional<UserEntity> findByWorkosId(String workosId) {
        return find("workosId", workosId).firstResultOptional();
    }
}
