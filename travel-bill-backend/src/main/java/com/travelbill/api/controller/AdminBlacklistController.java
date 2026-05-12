package com.travelbill.api.controller;

import com.travelbill.api.service.IpSecurityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/api")
public class AdminBlacklistController {
    private static final String ADMIN_SESSION_KEY = "travelBillAdminLoggedIn";

    private final IpSecurityService ipSecurityService;
    private final String adminUsername;
    private final String adminPassword;

    public AdminBlacklistController(
            IpSecurityService ipSecurityService,
            @Value("${app.admin.username:admin}") String adminUsername,
            @Value("${app.admin.password:admin}") String adminPassword
    ) {
        this.ipSecurityService = ipSecurityService;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody AdminLoginRequest request, HttpServletRequest httpRequest) {
        // 后台页面只做最小登录态管理，避免把管理入口暴露给未授权用户。
        if (!StringUtils.hasText(request.username()) || !StringUtils.hasText(request.password())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名和密码不能为空");
        }
        if (!adminUsername.equals(request.username()) || !adminPassword.equals(request.password())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        httpRequest.getSession(true).setAttribute(ADMIN_SESSION_KEY, Boolean.TRUE);
        return sessionPayload();
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }

    @GetMapping("/session")
    public Map<String, Object> session(HttpServletRequest httpRequest) {
        boolean loggedIn = isLoggedIn(httpRequest);
        return Map.of(
                "loggedIn", loggedIn,
                "maxRequestsPerMinute", ipSecurityService.getMaxRequestsPerMinute(),
                "maxRequestsPer24Hours", ipSecurityService.getMaxRequestsPer24Hours()
        );
    }

    @GetMapping("/blacklist")
    public List<IpSecurityService.BlacklistView> blacklist(HttpServletRequest httpRequest) {
        requireLogin(httpRequest);
        return ipSecurityService.listBlacklist();
    }

    @PostMapping("/blacklist/{ip}/remove")
    public Map<String, Object> removeBlacklist(@PathVariable String ip, HttpServletRequest httpRequest) {
        requireLogin(httpRequest);
        boolean removed = ipSecurityService.removeFromBlacklist(ip);
        return Map.of("removed", removed);
    }

    private void requireLogin(HttpServletRequest httpRequest) {
        if (!isLoggedIn(httpRequest)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录后台管理");
        }
    }

    private boolean isLoggedIn(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        return session != null && Boolean.TRUE.equals(session.getAttribute(ADMIN_SESSION_KEY));
    }

    private Map<String, Object> sessionPayload() {
        return Map.of(
                "loggedIn", true,
                "maxRequestsPerMinute", ipSecurityService.getMaxRequestsPerMinute(),
                "maxRequestsPer24Hours", ipSecurityService.getMaxRequestsPer24Hours()
        );
    }

    public record AdminLoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {
    }
}
