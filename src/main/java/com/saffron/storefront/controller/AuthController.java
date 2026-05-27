package com.saffron.storefront.controller;

import com.saffron.storefront.repository.AdminUserRepository;
import com.saffron.storefront.security.AuthHelper;
import com.saffron.storefront.service.AuthService;
import com.saffron.storefront.web.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AdminUserRepository users;

    public AuthController(AuthService authService, AdminUserRepository users) {
        this.authService = authService;
        this.users = users;
    }

    public record LoginRequest(String email, String password) {}
    public record ChangePasswordRequest(String currentPassword, String newPassword) {}

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest req, HttpServletRequest http) {
        return authService.login(req.email(), req.password(), http);
    }

    @PostMapping("/change-password")
    public Map<String, Object> changePassword(@RequestBody ChangePasswordRequest req, HttpServletRequest http) {
        return authService.changePassword(req.currentPassword(), req.newPassword(),
                AuthHelper.currentUser(), http);
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        var user = users.findById(AuthHelper.currentUser().id())
                .orElseThrow(() -> new NotFoundException("User not found"));
        return Map.of("user", AuthService.userJson(user));
    }
}
