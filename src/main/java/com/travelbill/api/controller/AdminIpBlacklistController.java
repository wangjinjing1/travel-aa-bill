package com.travelbill.api.controller;

import com.travelbill.api.service.ApiException;
import com.travelbill.api.service.AppUserService;
import com.travelbill.api.service.IpSecurityService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/ip-blacklist")
public class AdminIpBlacklistController {
    private final IpSecurityService ipSecurityService;
    private final AppUserService appUserService;

    public AdminIpBlacklistController(IpSecurityService ipSecurityService, AppUserService appUserService) {
        this.ipSecurityService = ipSecurityService;
        this.appUserService = appUserService;
    }

    @GetMapping
    public IpBlacklistPayload blacklist(HttpServletRequest request) {
        requireAdmin(request);
        return new IpBlacklistPayload(
                ipSecurityService.listBlacklist(),
                ipSecurityService.getMaxRequestsPerMinute(),
                ipSecurityService.getMaxRequestsPer24Hours()
        );
    }

    @PostMapping("/{ip}/remove")
    public Map<String, Boolean> removeBlacklist(@PathVariable String ip, HttpServletRequest request) {
        requireAdmin(request);
        return Map.of("removed", ipSecurityService.removeFromBlacklist(ip));
    }

    private void requireAdmin(HttpServletRequest request) {
        Object userId = request.getAttribute("authUserId");
        if (userId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        appUserService.requireAdmin(userId.toString());
    }

    public record IpBlacklistPayload(
            List<IpSecurityService.BlacklistView> items,
            int maxRequestsPerMinute,
            int maxRequestsPer24Hours
    ) {
    }
}
