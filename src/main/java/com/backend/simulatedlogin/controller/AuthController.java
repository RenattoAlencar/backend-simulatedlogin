package com.backend.simulatedlogin.controller;

import com.backend.simulatedlogin.dto.LoginRequest;
import com.backend.simulatedlogin.dto.LoginResponse;
import com.backend.simulatedlogin.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @CrossOrigin("http://localhost:4200")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        log.info("Received login request for user: {}", request.getUsername());
        LoginResponse response = authService.authenticate(request);
        log.info("User {} authenticated with type", response);
        return ResponseEntity.ok(response);
    }
}
