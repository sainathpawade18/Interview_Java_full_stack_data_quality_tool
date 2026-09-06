package com.dqplatform.controller;

import com.dqplatform.dto.*;
import com.dqplatform.entity.AppUser;
import com.dqplatform.repository.AppUserRepository;
import com.dqplatform.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final AppUserRepository users;
    private final JwtService jwtService;

    public AuthController(AuthenticationManager authenticationManager,
                          AppUserRepository users, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        AppUser user = users.findByUsername(request.username()).orElseThrow();
        String token = jwtService.generateToken(
                User.withUsername(user.getUsername()).password(user.getPassword())
                        .roles(user.getRole().name()).build());
        return new LoginResponse(token, user.getUsername(), user.getRole().name());
    }
}
