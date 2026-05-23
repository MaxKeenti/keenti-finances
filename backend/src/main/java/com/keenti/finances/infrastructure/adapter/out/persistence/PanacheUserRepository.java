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
                .map(this::toDomain);
    }

    @Override
    public Optional<User> findByWorkosId(String workosId) {
        return UserEntity.findByWorkosId(workosId)
                .map(this::toDomain);
    }

    @Override
    public User save(User user) {
        UserEntity entity = new UserEntity();
        entity.username = user.getUsername();
        entity.passwordHash = user.getPasswordHash();
        entity.workosId = user.getWorkosId();
        entity.persist();
        return toDomain(entity);
    }

    private User toDomain(UserEntity entity) {
        return new User(entity.id, entity.username, entity.passwordHash, entity.workosId);
    }
}
