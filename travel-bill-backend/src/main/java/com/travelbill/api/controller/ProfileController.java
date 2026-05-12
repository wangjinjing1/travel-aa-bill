package com.travelbill.api.controller;

import com.travelbill.api.dto.Requests.UpdateProfileRequest;
import com.travelbill.api.dto.Responses.UserProfile;
import com.travelbill.api.service.ApiException;
import com.travelbill.api.service.AppUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class ProfileController {
    private final AppUserService appUserService;

    public ProfileController(AppUserService appUserService) {
        this.appUserService = appUserService;
    }

    @GetMapping
    public UserProfile me(jakarta.servlet.http.HttpServletRequest request) {
        return appUserService.profileOf(currentUser(request));
    }

    @PostMapping("/profile")
    public UserProfile updateProfile(
            jakarta.servlet.http.HttpServletRequest request,
            @Valid @RequestBody UpdateProfileRequest updateProfileRequest
    ) {
        return appUserService.updateProfile(
                currentUser(request),
                updateProfileRequest.displayName(),
                updateProfileRequest.avatarUrl()
        );
    }

    private String currentUser(jakarta.servlet.http.HttpServletRequest request) {
        Object userId = request.getAttribute("authUserId");
        if (userId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        return userId.toString();
    }
}
