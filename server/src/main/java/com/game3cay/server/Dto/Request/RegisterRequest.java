package com.game3cay.server.Dto.Request;

import lombok.Data;

@Data
public class RegisterRequest {

    private String username;

    private String password;

    private String displayName;

    private String email;
}
