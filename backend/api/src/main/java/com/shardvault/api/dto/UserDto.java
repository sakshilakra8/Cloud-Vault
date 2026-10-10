package com.shardvault.api.dto;

public record UserDto(
        String id,
        String email,
        String role
) {}
