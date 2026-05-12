package com.travelbill.api.controller;

import com.travelbill.api.domain.AppUser;
import com.travelbill.api.repository.PlanMemberRepository;
import com.travelbill.api.repository.AppUserRepository;
import com.travelbill.api.repository.TravelPlanRepository;
import com.travelbill.api.service.PasswordService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final AppUserRepository userRepository;
    private final TravelPlanRepository planRepository;
    private final PlanMemberRepository memberRepository;
    private final PasswordService passwordService;

    public UserController(
            AppUserRepository userRepository,
            TravelPlanRepository planRepository,
            PlanMemberRepository memberRepository,
            PasswordService passwordService
    ) {
        this.userRepository = userRepository;
        this.planRepository = planRepository;
        this.memberRepository = memberRepository;
        this.passwordService = passwordService;
    }

    @PostMapping("/identify")
    public UserResponse identify(@Valid @RequestBody IdentifyRequest request) {
        String displayName = request.displayName().trim();
        AppUser user = userRepository.findByDisplayName(displayName)
                .orElseGet(() -> {
                    AppUser created = new AppUser();
                    existingUserId(displayName).ifPresent(created::setId);
                    created.setUsername("legacy_" + created.getId());
                    created.setPasswordHash(passwordService.hash(java.util.UUID.randomUUID().toString()));
                    created.setRole("USER");
                    created.setDisplayName(displayName);
                    return userRepository.save(created);
                });
        return new UserResponse(user.getId(), user.getDisplayName());
    }

    private java.util.Optional<String> existingUserId(String displayName) {
        return planRepository.findFirstByCreatorNameOrderByCreatedAtAsc(displayName)
                .map(plan -> java.util.Optional.of(plan.getCreatorId()))
                .orElseGet(() -> memberRepository.findFirstByDisplayNameOrderByJoinedAtAsc(displayName)
                        .map(member -> member.getUserId()));
    }

    public record IdentifyRequest(
            @NotBlank(message = "请输入昵称或真实姓名")
            @Size(max = 80, message = "昵称或真实姓名不能超过80个字")
            String displayName
    ) {
    }

    public record UserResponse(String userId, String displayName) {
    }
}
