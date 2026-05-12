package com.travelbill.api.service;

import com.travelbill.api.domain.AppUser;
import com.travelbill.api.domain.PlanMember;
import com.travelbill.api.domain.TravelExpense;
import com.travelbill.api.domain.TravelPlan;
import com.travelbill.api.dto.Responses.UserProfile;
import com.travelbill.api.repository.AppUserRepository;
import com.travelbill.api.repository.PlanMemberRepository;
import com.travelbill.api.repository.TravelExpenseRepository;
import com.travelbill.api.repository.TravelPlanRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Service
public class AppUserService {
    private static final String DEFAULT_DISPLAY_NAME = "微信用户";

    private final AppUserRepository userRepository;
    private final PlanMemberRepository planMemberRepository;
    private final TravelPlanRepository travelPlanRepository;
    private final TravelExpenseRepository travelExpenseRepository;

    public AppUserService(
            AppUserRepository userRepository,
            PlanMemberRepository planMemberRepository,
            TravelPlanRepository travelPlanRepository,
            TravelExpenseRepository travelExpenseRepository
    ) {
        this.userRepository = userRepository;
        this.planMemberRepository = planMemberRepository;
        this.travelPlanRepository = travelPlanRepository;
        this.travelExpenseRepository = travelExpenseRepository;
    }

    @Transactional
    public AppUser loginByOpenId(String openId, String displayName) {
        String normalizedDisplayName = normalizeDisplayName(displayName);
        return userRepository.findByOpenId(openId)
                .map(this::preserveExistingProfileOnLogin)
                .orElseGet(() -> bindLegacyUserOrCreate(openId, normalizedDisplayName));
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
        return new UserProfile(user.getId(), user.getDisplayName(), user.getAvatarUrl());
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
        return new UserProfile(saved.getId(), saved.getDisplayName(), saved.getAvatarUrl());
    }

    private AppUser preserveExistingProfileOnLogin(AppUser user) {
        if (!StringUtils.hasText(user.getDisplayName())) {
            user.setDisplayName(DEFAULT_DISPLAY_NAME);
            AppUser saved = userRepository.save(user);
            syncDisplayNameAcrossPlans(saved.getId(), saved.getDisplayName());
            return saved;
        }
        return user;
    }

    private AppUser bindLegacyUserOrCreate(String openId, String displayName) {
        AppUser user = userRepository.findByDisplayName(displayName)
                .filter(existing -> !StringUtils.hasText(existing.getOpenId()))
                .orElseGet(AppUser::new);
        user.setOpenId(openId);
        user.setDisplayName(displayName);
        return userRepository.save(user);
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
