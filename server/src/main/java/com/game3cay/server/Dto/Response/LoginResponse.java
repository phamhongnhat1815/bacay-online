package com.game3cay.server.Dto.Response;

import com.game3cay.server.Model.User;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {

    private String message;
    private User user;
}
