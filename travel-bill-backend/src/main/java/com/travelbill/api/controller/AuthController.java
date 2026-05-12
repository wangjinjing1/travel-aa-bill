package com.travelbill.api.controller;

import com.travelbill.api.domain.AppUser;
import com.travelbill.api.dto.Requests.PasswordLoginRequest;
import com.travelbill.api.dto.Requests.RegisterWithInviteRequest;
import com.travelbill.api.dto.Responses.AuthSession;
import com.travelbill.api.dto.Responses.InviteLink;
import com.travelbill.api.service.AppUserService;
import com.travelbill.api.service.AuthTokenService;
import com.travelbill.api.service.WechatLoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @PostMapping("/wechat-login")
    public AuthSession wechatLogin(@Valid @RequestBody WechatLoginRequest request) {
        String openId = wechatLoginService.openIdByCode(request.code());
        AppUser user = appUserService.loginByOpenId(openId, request.displayName());
        return new AuthSession(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getRole(),
                tokenService.issue(user.getId())
        );
    }

    private String currentUser(HttpServletRequest request) {
        Object userId = request.getAttribute("authUserId");
        if (userId == null) {
            throw new com.travelbill.api.service.ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "请先登录");
        }
        return userId.toString();
    }

    private boolean isDefaultPort(HttpServletRequest request) {
        int port = request.getServerPort();
        return ("http".equals(request.getScheme()) && port == 80)
                || ("https".equals(request.getScheme()) && port == 443);
    }

    public record WechatLoginRequest(
            @NotBlank(message = "缺少微信登录code") String code,
            @Size(max = 80, message = "昵称不能超过80个字") String displayName
    ) {
    }
}
