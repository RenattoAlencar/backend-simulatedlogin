package com.backend.simulatedlogin.service;

import com.backend.simulatedlogin.dto.LoginRequest;
import com.backend.simulatedlogin.dto.LoginResponse;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    public LoginResponse authenticate(LoginRequest request) {

        if ("33806537801".equalsIgnoreCase(request.getUsername())) {
            return new LoginResponse(request.getUsername(), "BIO");
        } else {
            return new LoginResponse(request.getUsername(), "QRCODE");
        }
    }
}
