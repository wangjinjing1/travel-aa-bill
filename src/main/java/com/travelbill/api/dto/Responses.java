package com.travelbill.api.dto;

import com.travelbill.api.domain.MemberStatus;
import com.travelbill.api.domain.PlanStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class Responses {
    private Responses() {
    }

    public record UserProfile(
            String userId,
            String displayName,
            String avatarUrl,
            String username,
            String role
    ) {
    }

    public record AuthSession(
            String userId,
            String username,
            String displayName,
            String avatarUrl,
            String role,
            String token
    ) {
    }

    public record InviteLink(
            String token,
            String url
    ) {
    }

    public record PlanSummary(
            Long id,
            String destination,
            LocalDate startDate,
            LocalDate endDate,
            PlanStatus status,
            String creatorName,
            boolean creator,
            LocalDateTime createdAt
    ) {
    }

    public record PlanDetail(
            Long id,
            String destination,
            LocalDate startDate,
            LocalDate endDate,
            String description,
            List<PlanImageView> images,
            String creatorId,
            String creatorName,
            String shareToken,
            PlanStatus status,
            Integer participantCount,
            boolean creator,
            boolean joined,
            boolean approved,
            boolean canViewExpenses,
            boolean canAddExpense,
            MemberStatus membershipStatus,
            List<MemberView> members,
            List<MemberView> pendingMembers,
            List<ExpenseView> expenses,
            List<SettlementView> settlements
    ) {
    }

    public record PlanImageView(
            Long id,
            String filename,
            String contentType,
            String url
    ) {
    }

    public record MemberView(
            Long id,
            String userId,
            String displayName,
            MemberStatus status,
            LocalDateTime joinedAt,
            LocalDateTime reviewedAt
    ) {
    }

    public record MemberPage(
            List<MemberView> items,
            int page,
            int size,
            long total,
            int totalPages,
            boolean hasNext
    ) {
    }

    public record ExpenseView(
            Long id,
            String userId,
            String payerName,
            BigDecimal amount,
            String note,
            LocalDate spentAt
    ) {
    }

    public record SettlementView(
            String userId,
            String payerName,
            BigDecimal paidTotal,
            BigDecimal perPersonTotal
    ) {
    }

    public record ExpensePage(
            List<ExpenseView> items,
            int page,
            int size,
            long total,
            int totalPages,
            boolean hasNext
    ) {
    }

    public record PlanPage(
            List<PlanSummary> items,
            int page,
            int size,
            long total,
            int totalPages,
            boolean hasNext
    ) {
    }
}
