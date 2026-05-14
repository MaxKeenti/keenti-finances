package com.keenti.finances.domain.port.out;

import com.keenti.finances.domain.model.User;
import java.util.Optional;

public interface UserRepository {
    Optional<User> findByUsername(String username);
}
