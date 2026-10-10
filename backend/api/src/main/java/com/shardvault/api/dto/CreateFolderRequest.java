package com.shardvault.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateFolderRequest(
        @NotBlank(message = "Folder name is required")
        @Size(min = 1, max = 255, message = "Folder name must be between 1 and 255 characters")
        @Pattern(regexp = "^(?![.]{1,2}$)[^/\\\\:*?\"<>|]+$", message = "Folder name contains invalid characters or path traversal elements")
        String name,

        String parentId
) {}
