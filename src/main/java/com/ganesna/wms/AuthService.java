package com.ganesna.wms;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AuthService {

    public static final class User {
        public final String name;
        public final String role;

        User(String name, String role) {
            this.name = name;
            this.role = role;
        }

        public boolean isAdmin() {
            return "ADMIN".equals(role);
        }
    }

    private final Map<String, String[]> accounts = Map.of(
            "admin", new String[]{"admin123", "ADMIN"},
            "picker", new String[]{"picker123", "PICKER"});

    private final Map<String, User> sessions = new ConcurrentHashMap<>();

    /** Returns {token, role} or throws 401. */
    public String[] login(String username, String password) {
        String[] account = username == null ? null : accounts.get(username);
        if (account == null || !account[0].equals(password)) {
            throw new ApiException(401, "Invalid username or password");
        }
        String token = UUID.randomUUID().toString();
        sessions.put(token, new User(username, account[1]));
        return new String[]{token, account[1]};
    }

    public User authenticate(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new ApiException(401, "Missing or invalid token");
        }
        User user = sessions.get(authorizationHeader.substring("Bearer ".length()).trim());
        if (user == null) {
            throw new ApiException(401, "Missing or invalid token");
        }
        return user;
    }
}
