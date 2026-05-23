package com.keenti.finances.domain.model;

public class User {

    private Long id;
    private String username;
    private String passwordHash;
    private String workosId;

    public User(Long id, String username, String passwordHash, String workosId) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.workosId = workosId;
    }

    public User(Long id, String username, String passwordHash) {
        this(id, username, passwordHash, null);
    }

    public User(Long id, String username) {
        this(id, username, null, null);
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getWorkosId() { return workosId; }
}
