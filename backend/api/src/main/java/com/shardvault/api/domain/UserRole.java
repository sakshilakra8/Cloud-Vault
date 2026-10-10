package com.shardvault.api.domain;

public enum UserRole {
    ROLE_USER,
    ROLE_ADMIN;

    public String getAuthority() {
        return name();
    }
}
