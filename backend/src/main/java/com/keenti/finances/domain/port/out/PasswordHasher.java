package com.keenti.finances.domain.port.out;

public interface PasswordHasher {
    boolean verify(String plaintext, String hash);
}
