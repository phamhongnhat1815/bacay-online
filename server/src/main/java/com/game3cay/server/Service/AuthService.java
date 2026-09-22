package com.game3cay.server.Service;

import com.game3cay.server.Dto.Request.LoginRequest;
import com.game3cay.server.Dto.Response.LoginResponse;
import com.game3cay.server.Dto.Request.RegisterRequest;
import com.game3cay.server.Model.User;
import com.game3cay.server.Repository.UserRepository;
import com.game3cay.server.Security.JwtUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtUtils jwtUtils
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
    }

    // ============================================================
    // REGISTER
    // ============================================================

    public void register(RegisterRequest request) {

        if (request.getUsername() == null ||
                request.getUsername().isBlank()) {

            throw new IllegalArgumentException(
                    "Username không được để trống"
            );
        }

        if (request.getPassword() == null ||
                request.getPassword().isBlank()) {

            throw new IllegalArgumentException(
                    "Password không được để trống"
            );
        }

        if (request.getDisplayName() == null ||
                request.getDisplayName().isBlank()) {

            throw new IllegalArgumentException(
                    "Display name không được để trống"
            );
        }

        // Kiểm tra username
        if (userRepository
                .findByUsername(request.getUsername())
                .isPresent()) {

            throw new IllegalArgumentException(
                    "Username đã tồn tại"
            );
        }

        // Kiểm tra email
        if (request.getEmail() != null &&
                !request.getEmail().isBlank() &&
                userRepository.findByEmail(request.getEmail()).isPresent()) {

            throw new IllegalArgumentException(
                    "Email đã tồn tại"
            );
        }

        User user = new User();

        user.setUsername(request.getUsername());

        // KHÔNG lưu password gốc
        user.setPasswordHash(
                passwordEncoder.encode(
                        request.getPassword()
                )
        );

        user.setDisplayName(
                request.getDisplayName()
        );

        user.setEmail(
                request.getEmail()
        );

        user.setAvatarUrl(null);

        user.setBalance(
                BigDecimal.ZERO
        );

        user.setStatus(
                "ACTIVE"
        );

        try {

            userRepository.insert(user);

        } catch (DuplicateKeyException e) {

            // Phòng trường hợp 2 request đăng ký đồng thời
            throw new IllegalArgumentException(
                    "Username hoặc email đã tồn tại"
            );
        }
    }


    // ============================================================
    // LOGIN
    // ============================================================

    public LoginResponse login(LoginRequest request) {

        User user = userRepository
                .findByUsername(request.getUsername())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Username hoặc password không đúng"
                        )
                );

        // Kiểm tra tài khoản bị khóa
        if (!"ACTIVE".equals(user.getStatus())) {

            throw new IllegalArgumentException(
                    "Tài khoản đã bị khóa"
            );
        }

        // Kiểm tra password
        boolean matches = passwordEncoder.matches(
                request.getPassword(),
                user.getPasswordHash()
        );

        if (!matches) {

            throw new IllegalArgumentException(
                    "Username hoặc password không đúng"
            );
        }

        // Sinh JWT token
        String token = jwtUtils.generateToken(user);

        return new LoginResponse(
                "Đăng nhập thành công",
                token,
                user
        );
    }
}