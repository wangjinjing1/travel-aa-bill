package com.travelbill.api.controller;

import com.travelbill.api.domain.AppUser;
import com.travelbill.api.dto.Requests.UpdateUserRoleRequest;
import com.travelbill.api.dto.Responses.AdminUserView;
import com.travelbill.api.repository.AppUserRepository;
import com.travelbill.api.service.ApiException;
import com.travelbill.api.service.AppUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_USER = "USER";

    private final AppUserRepository userRepository;
    private final AppUserService appUserService;

    public AdminUserController(AppUserRepository userRepository, AppUserService appUserService) {
        this.userRepository = userRepository;
        this.appUserService = appUserService;
    }

    @GetMapping
    public List<AdminUserView> users(HttpServletRequest request) {
        requireAdmin(request);
        return userRepository.findAll(Sort.by(Sort.Order.desc("createdAt"))).stream()
                .map(this::toUserView)
                .toList();
    }

    @PostMapping("/{userId}/role")
    public AdminUserView updateRole(
            @PathVariable String userId,
            @Valid @RequestBody UpdateUserRoleRequest request,
            HttpServletRequest httpRequest
    ) {
        requireAdmin(httpRequest);
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "用户不存在"));
        user.setRole(normalizeRole(request.role()));
        return toUserView(userRepository.save(user));
    }

    @PostMapping("/{userId}/delete")
    public Map<String, Boolean> deleteUser(@PathVariable String userId, HttpServletRequest httpRequest) {
        appUserService.deleteUserByAdmin(currentUserId(httpRequest), userId);
        return Map.of("success", true);
    }

    private void requireAdmin(HttpServletRequest request) {
        appUserService.requireAdmin(currentUserId(request));
    }

    private String currentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("authUserId");
        if (userId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        return userId.toString();
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请选择用户角色");
        }
        String normalizedRole = role.trim().toUpperCase();
        if (!ROLE_ADMIN.equals(normalizedRole) && !ROLE_USER.equals(normalizedRole)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "用户角色只能是管理员或普通用户");
        }
        return normalizedRole;
    }

    private AdminUserView toUserView(AppUser user) {
        return new AdminUserView(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}
