package com.game3cay.server.Dto.Response;

import com.game3cay.server.Model.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String message;
    private String accessToken;
    private String tokenType = "Bearer";
    private User user;

    public LoginResponse(String message, String accessToken, User user) {
        this.message = message;
        this.accessToken = accessToken;
        this.tokenType = "Bearer";
        this.user = user;
    }
}
