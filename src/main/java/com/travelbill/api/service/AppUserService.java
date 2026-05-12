package com.travelbill.api.service;

import com.travelbill.api.domain.AppUser;
import com.travelbill.api.domain.PlanMember;
import com.travelbill.api.domain.RegistrationInvite;
import com.travelbill.api.domain.TravelExpense;
import com.travelbill.api.domain.TravelPlan;
import com.travelbill.api.dto.Requests.RegisterWithInviteRequest;
import com.travelbill.api.dto.Responses.AuthSession;
import com.travelbill.api.dto.Responses.InviteLink;
import com.travelbill.api.dto.Responses.UserProfile;
import com.travelbill.api.repository.AppUserRepository;
import com.travelbill.api.repository.PlanMemberRepository;
import com.travelbill.api.repository.RegistrationInviteRepository;
import com.travelbill.api.repository.TravelExpenseRepository;
import com.travelbill.api.repository.TravelPlanRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class AppUserService {
    private static final String DEFAULT_DISPLAY_NAME = "用户";

    private final AppUserRepository userRepository;
    private final PlanMemberRepository planMemberRepository;
    private final TravelPlanRepository travelPlanRepository;
    private final TravelExpenseRepository travelExpenseRepository;
    private final RegistrationInviteRepository inviteRepository;
    private final PasswordService passwordService;
    private final AuthTokenService authTokenService;

    public AppUserService(
            AppUserRepository userRepository,
            PlanMemberRepository planMemberRepository,
            TravelPlanRepository travelPlanRepository,
            TravelExpenseRepository travelExpenseRepository,
            RegistrationInviteRepository inviteRepository,
            PasswordService passwordService,
            AuthTokenService authTokenService
    ) {
        this.userRepository = userRepository;
        this.planMemberRepository = planMemberRepository;
        this.travelPlanRepository = travelPlanRepository;
        this.travelExpenseRepository = travelExpenseRepository;
        this.inviteRepository = inviteRepository;
        this.passwordService = passwordService;
        this.authTokenService = authTokenService;
    }

    @Transactional(readOnly = true)
    public AuthSession loginByPassword(String username, String password) {
        AppUser user = userRepository.findByUsername(normalizeUsername(username))
                .filter(item -> passwordService.matches(password, item.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "账号或密码错误"));
        return toAuthSession(user);
    }

    @Transactional
    public AuthSession registerWithInvite(RegisterWithInviteRequest request) {
        RegistrationInvite invite = inviteRepository.findById(request.inviteToken().trim())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "注册链接无效"));
        if (invite.getUsedAt() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "注册链接已被使用");
        }

        String username = normalizeUsername(request.username());
        userRepository.findByUsername(username).ifPresent(user -> {
            throw new ApiException(HttpStatus.CONFLICT, "账号已存在");
        });

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordService.hash(request.password()));
        user.setDisplayName(normalizeDisplayName(request.displayName()));
        user.setRole("USER");
        AppUser saved = userRepository.save(user);

        invite.setUsedAt(LocalDateTime.now());
        invite.setUsedBy(saved.getId());
        inviteRepository.save(invite);
        return toAuthSession(saved);
    }

    @Transactional
    public InviteLink createInvite(String currentUserId, String baseUrl) {
        AppUser admin = requireAdmin(currentUserId);
        RegistrationInvite invite = new RegistrationInvite();
        invite.setToken(UUID.randomUUID().toString().replace("-", ""));
        invite.setCreatedBy(admin.getId());
        inviteRepository.save(invite);

        String normalizedBaseUrl = StringUtils.hasText(baseUrl) ? baseUrl.trim().replaceAll("/+$", "") : "";
        return new InviteLink(invite.getToken(), normalizedBaseUrl + "/#/register?token=" + invite.getToken());
    }

    @Transactional(readOnly = true)
    public AppUser requireAdmin(String userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "请先登录"));
        if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "只有管理员可以执行此操作");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public String displayNameOf(String userId) {
        return userRepository.findById(userId)
                .map(AppUser::getDisplayName)
                .filter(StringUtils::hasText)
                .orElse(DEFAULT_DISPLAY_NAME);
    }

    @Transactional(readOnly = true)
    public UserProfile profileOf(String userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "用户不存在"));
        return new UserProfile(user.getId(), user.getDisplayName(), user.getAvatarUrl(), user.getUsername(), user.getRole());
    }

    @Transactional
    public UserProfile updateProfile(String userId, String displayName, String avatarUrl) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "用户不存在"));
        if (displayName == null && avatarUrl == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请至少修改一项个人资料");
        }

        boolean changed = false;
        if (displayName != null) {
            String normalizedDisplayName = normalizeDisplayName(displayName);
            if (!normalizedDisplayName.equals(user.getDisplayName())) {
                user.setDisplayName(normalizedDisplayName);
                syncDisplayNameAcrossPlans(user.getId(), normalizedDisplayName);
                changed = true;
            }
        }

        if (avatarUrl != null) {
            String normalizedAvatarUrl = normalizeAvatarUrl(avatarUrl);
            if (!Objects.equals(normalizedAvatarUrl, user.getAvatarUrl())) {
                user.setAvatarUrl(normalizedAvatarUrl);
                changed = true;
            }
        }

        AppUser saved = changed ? userRepository.save(user) : user;
        return new UserProfile(saved.getId(), saved.getDisplayName(), saved.getAvatarUrl(), saved.getUsername(), saved.getRole());
    }

    private AuthSession toAuthSession(AppUser user) {
        return new AuthSession(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getRole(),
                authTokenService.issue(user.getId())
        );
    }

    private String normalizeUsername(String username) {
        if (!StringUtils.hasText(username)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请输入账号");
        }
        return username.trim();
    }

    private String normalizeDisplayName(String displayName) {
        if (!StringUtils.hasText(displayName)) {
            return DEFAULT_DISPLAY_NAME;
        }
        return displayName.trim();
    }

    private String normalizeAvatarUrl(String avatarUrl) {
        if (!StringUtils.hasText(avatarUrl)) {
            return null;
        }
        return avatarUrl.trim();
    }

    private void syncDisplayNameAcrossPlans(String userId, String displayName) {
        List<PlanMember> memberships = planMemberRepository.findByUserIdOrderByJoinedAtDesc(userId);
        boolean memberChanged = false;
        for (PlanMember membership : memberships) {
            if (!displayName.equals(membership.getDisplayName())) {
                membership.setDisplayName(displayName);
                memberChanged = true;
            }
        }
        if (memberChanged) {
            planMemberRepository.saveAll(memberships);
        }

        List<TravelPlan> createdPlans = travelPlanRepository.findByCreatorId(userId);
        boolean creatorChanged = false;
        for (TravelPlan createdPlan : createdPlans) {
            if (!displayName.equals(createdPlan.getCreatorName())) {
                createdPlan.setCreatorName(displayName);
                creatorChanged = true;
            }
        }
        if (creatorChanged) {
            travelPlanRepository.saveAll(createdPlans);
        }

        List<TravelExpense> expenses = travelExpenseRepository.findByUserIdOrderByCreatedAtDesc(userId);
        boolean expenseChanged = false;
        for (TravelExpense expense : expenses) {
            if (!displayName.equals(expense.getPayerName())) {
                expense.setPayerName(displayName);
                expenseChanged = true;
            }
        }
        if (expenseChanged) {
            travelExpenseRepository.saveAll(expenses);
        }
    }
}
