package com.game3cay.server.Model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    private Long userId;

    private String username;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String passwordHash;

    private String displayName;

    private String email;

    private String avatarUrl;

    private BigDecimal balance;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}