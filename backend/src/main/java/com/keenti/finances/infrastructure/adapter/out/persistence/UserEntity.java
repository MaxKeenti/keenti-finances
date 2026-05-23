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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true)
    public String username;

    @Column(name = "password_hash", nullable = true)
    public String passwordHash;

    @Column(name = "workos_id", unique = true)
    public String workosId;

    public static Optional<UserEntity> findByUsername(String username) {
        return find("username", username).firstResultOptional();
    }

    public static Optional<UserEntity> findByWorkosId(String workosId) {
        return find("workosId", workosId).firstResultOptional();
    }
}
