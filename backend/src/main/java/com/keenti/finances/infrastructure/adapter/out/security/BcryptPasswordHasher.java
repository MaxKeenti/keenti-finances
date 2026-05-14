package com.keenti.finances.infrastructure.adapter.out.security;

import com.keenti.finances.domain.port.out.PasswordHasher;
import jakarta.enterprise.context.ApplicationScoped;
import org.mindrot.jbcrypt.BCrypt;

@ApplicationScoped
public class BcryptPasswordHasher implements PasswordHasher {

    @Override
    public boolean verify(String plaintext, String hash) {
        return BCrypt.checkpw(plaintext, hash);
    }
}
