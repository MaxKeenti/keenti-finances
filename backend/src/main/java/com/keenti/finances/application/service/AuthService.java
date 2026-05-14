package com.keenti.finances.application.service;

import com.keenti.finances.domain.model.User;
import com.keenti.finances.domain.port.in.AuthUseCase;
import com.keenti.finances.domain.port.out.PasswordHasher;
import com.keenti.finances.domain.port.out.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import org.jboss.logging.Logger;

@ApplicationScoped
public class AuthService implements AuthUseCase {

    private static final Logger LOG = Logger.getLogger(AuthService.class);

    @Inject
    UserRepository userRepository;

    @Inject
    PasswordHasher passwordHasher;

    @Override
    public Optional<User> login(String username, String password) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            LOG.infof("auth.login username=%s success=false reason=user_not_found", username);
            return Optional.empty();
        }
        User user = userOpt.get();
        boolean valid = passwordHasher.verify(password, user.getPasswordHash());
        LOG.infof("auth.login username=%s success=%b", username, valid);
        return valid ? Optional.of(user) : Optional.empty();
    }
}
