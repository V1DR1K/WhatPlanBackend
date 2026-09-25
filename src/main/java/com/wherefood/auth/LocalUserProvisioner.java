package com.wherefood.auth;

import com.wherefood.domain.Role;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.Users;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LocalUserProvisioner {
    private final Users users;
    private final Role defaultRole;

    @org.springframework.beans.factory.annotation.Autowired
    public LocalUserProvisioner(Users users, @Value("${app.auth-default-role}") String defaultRole) {
        this.users = users;
        Role configuredRole;
        try {
            configuredRole = Role.valueOf(defaultRole.trim().toUpperCase());
        } catch (Exception ex) {
            throw new IllegalArgumentException("AUTH_DEFAULT_ROLE must be USER", ex);
        }
        if (configuredRole != Role.USER) {
            throw new IllegalArgumentException("AUTH_DEFAULT_ROLE must be USER; administrator promotion is an audited operation");
        }
        this.defaultRole = configuredRole;
    }

    @Transactional
    public User provision(UUID authUserId, String username) {
        if (authUserId == null || username == null || username.isBlank()) {
            throw new IllegalArgumentException("Central user identity is incomplete");
        }
        String normalizedUsername = username.trim();
        User user = users.findByAuthUserId(authUserId).orElse(null);
        if (user == null) {
            if (users.findByUsernameIgnoreCase(normalizedUsername).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "La cuenta local requiere una vinculación de identidad verificada");
            }
            user = create(authUserId, normalizedUsername);
        } else {
            Long localUserId = user.id;
            if (users.findByUsernameIgnoreCase(normalizedUsername)
                    .filter(existing -> !existing.id.equals(localUserId)).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "El nombre de usuario ya está en uso");
            }
        }
        user.authUserId = authUserId;
        user.username = normalizedUsername;
        if (user.role == null) user.role = defaultRole;
        user.passwordHash = null;
        return users.save(user);
    }

    private User create(UUID authUserId, String username) {
        User user = new User();
        user.authUserId = authUserId;
        user.username = username.trim();
        user.role = defaultRole;
        return user;
    }
}
