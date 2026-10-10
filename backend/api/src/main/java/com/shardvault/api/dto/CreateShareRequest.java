package com.shardvault.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

public record CreateShareRequest(
        @NotBlank(message = "Resource type is required")
        @Pattern(regexp = "^(FILE|FOLDER)$", message = "Resource type must be either FILE or FOLDER")
        String resourceType,

        @NotBlank(message = "Resource ID is required")
        String resourceId,

        @Pattern(regexp = "^(READ|WRITE)$", message = "Permission must be READ or WRITE")
        String permission,

        Instant expiresAt
) {}
