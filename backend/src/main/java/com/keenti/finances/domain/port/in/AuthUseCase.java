package com.keenti.finances.domain.port.in;

import com.keenti.finances.domain.model.User;
import java.util.Optional;

public interface AuthUseCase {
    Optional<User> login(String username, String password);
}
