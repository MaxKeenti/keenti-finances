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
                .map(e -> new User(e.id, e.username, e.passwordHash, e.workosId));
    }

    @Override
    public Optional<User> findByWorkosId(String workosId) {
        return UserEntity.findByWorkosId(workosId)
                .map(e -> new User(e.id, e.username, e.passwordHash, e.workosId));
    }

    @Override
    public User save(User user) {
        UserEntity e = new UserEntity();
        e.username = user.getUsername();
        e.passwordHash = user.getPasswordHash();
        e.workosId = user.getWorkosId();
        e.persist();
        return new User(e.id, e.username, e.passwordHash, e.workosId);
    }
}
