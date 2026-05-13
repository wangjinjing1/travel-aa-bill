package com.travelbill.api;

import com.travelbill.api.repository.AppUserRepository;
import com.travelbill.api.service.ApiException;
import com.travelbill.api.service.AuthTokenService;
import com.travelbill.api.service.IpSecurityService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

@Component
public class ApiGuardFilter extends OncePerRequestFilter {
    private static final Pattern SHARE_PREVIEW_PATH = Pattern.compile("^/api/plans/\\d+$");
    private static final Pattern INVITE_STATUS_PATH = Pattern.compile("^/api/auth/invites/[A-Za-z0-9]+/status$");

    private final AuthTokenService tokenService;
    private final IpSecurityService ipSecurityService;
    private final AppUserRepository userRepository;

    public ApiGuardFilter(AuthTokenService tokenService, IpSecurityService ipSecurityService, AppUserRepository userRepository) {
        this.tokenService = tokenService;
        this.ipSecurityService = ipSecurityService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        addSecurityHeaders(response);

        if (request.getRequestURI().startsWith("/api/")) {
            String ip = clientIp(request);

            // 先拦截已被拉黑的 IP，避免继续消耗后端资源。
            if (ipSecurityService.isBlacklisted(ip)) {
                response.setStatus(403);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"message\":\"当前 IP 已被限制访问，请 24 小时后再试\"}");
                return;
            }

            // 每个 IP 会累计 24 小时内的访问次数，超限后自动进入黑名单。
            if (ipSecurityService.recordAndCheckDailyLimit(ip)) {
                response.setStatus(403);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"message\":\"当前 IP 访问过于频繁，已加入 24 小时黑名单\"}");
                return;
            }

            // 分钟级限流继续保留，主要用于快速拦截突发流量。
            if (tooManyRequests(request, ip)) {
                response.setStatus(429);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"message\":\"请求过于频繁，请稍后再试\"}");
                return;
            }
        }

        if (requiresOptionalAuth(request) || requiresStrictAuth(request)) {
            try {
                String userId = resolveUserId(request, requiresStrictAuth(request));
                if (userId != null) {
                    request.setAttribute("authUserId", userId);
                }
            } catch (ApiException exception) {
                response.setStatus(exception.getStatus().value());
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"message\":\"" + exception.getMessage() + "\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean requiresStrictAuth(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/")) {
            return false;
        }
        if (uri.equals("/api/auth/login") || uri.equals("/api/auth/register")) {
            return false;
        }
        if ("GET".equalsIgnoreCase(request.getMethod()) && INVITE_STATUS_PATH.matcher(uri).matches()) {
            return false;
        }
        return !requiresOptionalAuth(request);
    }

    private boolean requiresOptionalAuth(HttpServletRequest request) {
        return "GET".equalsIgnoreCase(request.getMethod())
                && SHARE_PREVIEW_PATH.matcher(request.getRequestURI()).matches();
    }

    private String resolveUserId(HttpServletRequest request, boolean required) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            if (required) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "请先登录");
            }
            return null;
        }
        String userId = tokenService.verify(authorization.substring("Bearer ".length()));
        if (!userRepository.existsById(userId)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "用户不存在");
        }
        return userId;
    }

    private boolean tooManyRequests(HttpServletRequest request, String ip) {
        String authorization = request.getHeader("Authorization");
        String key = authorization == null || authorization.isBlank() ? ip : authorization;
        return ipSecurityService.exceedsMinuteLimit(key);
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void addSecurityHeaders(HttpServletResponse response) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Cache-Control", "no-store");
    }
}
