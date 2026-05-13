package com.travelbill.api.controller;

import com.travelbill.api.dto.Requests.PasswordLoginRequest;
import com.travelbill.api.dto.Requests.RegisterWithInviteRequest;
import com.travelbill.api.dto.Responses.AuthSession;
import com.travelbill.api.dto.Responses.InviteLink;
import com.travelbill.api.dto.Responses.InviteStatus;
import com.travelbill.api.service.ApiException;
import com.travelbill.api.service.AppUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AppUserService appUserService;

    public AuthController(AppUserService appUserService) {
        this.appUserService = appUserService;
    }

    @PostMapping("/login")
    public AuthSession passwordLogin(@Valid @RequestBody PasswordLoginRequest request) {
        return appUserService.loginByPassword(request.username(), request.password());
    }

    @PostMapping("/register")
    public AuthSession register(@Valid @RequestBody RegisterWithInviteRequest request) {
        return appUserService.registerWithInvite(request);
    }

    @PostMapping("/invites")
    public InviteLink createInvite(HttpServletRequest request) {
        String baseUrl = request.getScheme() + "://" + request.getServerName()
                + (isDefaultPort(request) ? "" : ":" + request.getServerPort());
        return appUserService.createInvite(currentUser(request), baseUrl);
    }

    @GetMapping("/invites/{token}/status")
    public InviteStatus inviteStatus(@PathVariable String token) {
        return appUserService.inviteStatus(token);
    }

    private String currentUser(HttpServletRequest request) {
        Object userId = request.getAttribute("authUserId");
        if (userId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        return userId.toString();
    }

    private boolean isDefaultPort(HttpServletRequest request) {
        int port = request.getServerPort();
        return ("http".equals(request.getScheme()) && port == 80)
                || ("https".equals(request.getScheme()) && port == 443);
    }
}
