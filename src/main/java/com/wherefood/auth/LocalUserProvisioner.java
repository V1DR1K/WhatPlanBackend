package com.wherefood.auth;

import com.wherefood.domain.Role;
import com.wherefood.domain.User;
import com.wherefood.repo.Repositories.Users;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocalUserProvisioner {
    private final Users users;
    private final Role defaultRole;

    public LocalUserProvisioner(Users users, @Value("${app.auth-default-role}") String defaultRole) {
        this.users = users;
        try {
            this.defaultRole = Role.valueOf(defaultRole.trim().toUpperCase());
        } catch (Exception ex) {
            throw new IllegalArgumentException("AUTH_DEFAULT_ROLE must be USER or ADMIN", ex);
        }
    }

    @Transactional
    public User provision(UUID authUserId, String username) {
        if (authUserId == null || username == null || username.isBlank()) {
            throw new IllegalArgumentException("Central user identity is incomplete");
        }
        User user = users.findByAuthUserId(authUserId).orElseGet(() -> users.findByUsernameIgnoreCase(username)
                .map(existing -> bind(existing, authUserId)).orElseGet(() -> create(authUserId, username)));
        user.authUserId = authUserId;
        user.username = username.trim();
        if (user.role == null) user.role = defaultRole;
        user.passwordHash = null;
        return users.save(user);
    }

    private User bind(User user, UUID authUserId) {
        if (user.authUserId != null && !user.authUserId.equals(authUserId)) {
            throw new IllegalStateException("Local username is linked to another central user");
        }
        return user;
    }

    private User create(UUID authUserId, String username) {
        User user = new User();
        user.authUserId = authUserId;
        user.username = username.trim();
        user.role = defaultRole;
        return user;
    }
}
