package com.travelbill.api.controller;

import com.travelbill.api.domain.AppUser;
import com.travelbill.api.service.AppUserService;
import com.travelbill.api.service.AuthTokenService;
import com.travelbill.api.service.WechatLoginService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AppUserService appUserService;
    private final WechatLoginService wechatLoginService;
    private final AuthTokenService tokenService;

    public AuthController(
            AppUserService appUserService,
            WechatLoginService wechatLoginService,
            AuthTokenService tokenService
    ) {
        this.appUserService = appUserService;
        this.wechatLoginService = wechatLoginService;
        this.tokenService = tokenService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        String openId = wechatLoginService.openIdByCode(request.code());
        AppUser user = appUserService.loginByOpenId(openId, request.displayName());
        return new LoginResponse(
                user.getId(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                tokenService.issue(user.getId())
        );
    }

    public record LoginRequest(
            @NotBlank(message = "缺少微信登录code") String code,
            @Size(max = 80, message = "昵称不能超过80个字") String displayName
    ) {
    }

    public record LoginResponse(String userId, String displayName, String avatarUrl, String token) {
    }
}
