package com.keenti.finances.infrastructure.adapter.out.persistence;

import com.keenti.finances.domain.model.User;
import com.keenti.finances.domain.port.out.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

@ApplicationScoped
public class PanacheUserRepository implements UserRepository {

    @Override
    public Optional<User> findByUsername(String username) {
        return UserEntity.findByUsername(username)
                .map(e -> new User(e.id, e.username, e.passwordHash));
    }
}
