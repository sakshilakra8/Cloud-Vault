package com.shardvault.api.dto;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserDto user
) {
    public static AuthResponse of(String accessToken, long expiresIn, UserDto user) {
        return new AuthResponse(accessToken, "Bearer", expiresIn, user);
    }
}
