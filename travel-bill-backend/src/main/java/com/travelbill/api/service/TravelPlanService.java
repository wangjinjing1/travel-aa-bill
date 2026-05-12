package com.travelbill.api.service;

import com.travelbill.api.domain.MemberStatus;
import com.travelbill.api.domain.PlanMember;
import com.travelbill.api.domain.PlanStatus;
import com.travelbill.api.domain.TravelExpense;
import com.travelbill.api.domain.TravelPlan;
import com.travelbill.api.domain.TravelPlanImage;
import com.travelbill.api.dto.Requests.ClosePlanRequest;
import com.travelbill.api.dto.Requests.CreateExpenseRequest;
import com.travelbill.api.dto.Requests.CreatePlanRequest;
import com.travelbill.api.dto.Requests.UpdateExpenseRequest;
import com.travelbill.api.dto.Responses.ExpensePage;
import com.travelbill.api.dto.Responses.ExpenseView;
import com.travelbill.api.dto.Responses.MemberView;
import com.travelbill.api.dto.Responses.PlanDetail;
import com.travelbill.api.dto.Responses.PlanImageView;
import com.travelbill.api.dto.Responses.PlanPage;
import com.travelbill.api.dto.Responses.PlanSummary;
import com.travelbill.api.dto.Responses.SettlementView;
import com.travelbill.api.repository.PlanMemberRepository;
import com.travelbill.api.repository.TravelExpenseRepository;
import com.travelbill.api.repository.TravelPlanImageRepository;
import com.travelbill.api.repository.TravelPlanRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TravelPlanService {
    private final TravelPlanRepository planRepository;
    private final PlanMemberRepository memberRepository;
    private final TravelExpenseRepository expenseRepository;
    private final TravelPlanImageRepository imageRepository;
    private final AppUserService appUserService;

    public TravelPlanService(
            TravelPlanRepository planRepository,
            PlanMemberRepository memberRepository,
            TravelExpenseRepository expenseRepository,
            TravelPlanImageRepository imageRepository,
            AppUserService appUserService
    ) {
        this.planRepository = planRepository;
        this.memberRepository = memberRepository;
        this.expenseRepository = expenseRepository;
        this.imageRepository = imageRepository;
        this.appUserService = appUserService;
    }

    @Transactional
    public PlanDetail createPlan(String userId, CreatePlanRequest request, String requestId) {
        if (StringUtils.hasText(requestId)) {
            Optional<TravelPlan> existing = planRepository.findByCreatorIdAndRequestId(userId, requestId);
            if (existing.isPresent()) {
                return detailFor(existing.get(), userId, false);
            }
        }
        if (request.endDate().isBefore(request.startDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "结束日期不能早于开始日期");
        }

        TravelPlan plan = new TravelPlan();
        plan.setDestination(request.destination().trim());
        plan.setStartDate(request.startDate());
        plan.setEndDate(request.endDate());
        plan.setDescription(request.description().trim());
        plan.setCreatorId(userId);
        plan.setCreatorName(StringUtils.hasText(request.creatorName()) ? request.creatorName().trim() : appUserService.displayNameOf(userId));
        plan.setShareToken(UUID.randomUUID().toString().replace("-", ""));
        plan.setRequestId(requestId);
        planRepository.save(plan);

        ensureCreatorMembership(plan, userId, plan.getCreatorName());
        return detailFor(plan, userId, false);
    }

    @Transactional
    public PlanDetail addPlanImage(Long planId, String userId, MultipartFile file) {
        TravelPlan plan = ownedPlan(planId, userId);
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请选择要上传的图片");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "只能上传图片文件");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "图片不能超过5MB");
        }
        try {
            TravelPlanImage image = new TravelPlanImage();
            image.setPlan(plan);
            image.setFilename(StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "plan-image");
            image.setContentType(contentType);
            image.setData(file.getBytes());
            imageRepository.save(image);
            return detailFor(plan, userId, false);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "图片上传失败");
        }
    }

    @Transactional(readOnly = true)
    public TravelPlanImage getPlanImage(Long planId, Long imageId, String userId, String shareToken) {
        TravelPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "旅游计划不存在"));
        boolean sharedPreview = StringUtils.hasText(shareToken);
        if (!canPreviewPlan(plan, userId, sharedPreview)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "无权查看该图片");
        }
        if (sharedPreview && !plan.getShareToken().equals(shareToken)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "分享链接无效");
        }
        return imageRepository.findById(imageId)
                .filter(image -> image.getPlan().getId().equals(planId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "图片不存在"));
    }

    @Transactional(readOnly = true)
    public List<PlanSummary> myPlans(String userId) {
        return myPlans(userId, null);
    }

    @Transactional(readOnly = true)
    public List<PlanSummary> myPlans(String userId, String keyword) {
        return visiblePlans(userId, keyword).stream()
                .map(plan -> toPlanSummary(plan, userId))
                .toList();
    }

    @Transactional(readOnly = true)
    public PlanPage myPlans(String userId, int page, int size) {
        return myPlans(userId, page, size, null);
    }

    @Transactional(readOnly = true)
    public PlanPage myPlans(String userId, int page, int size, String keyword) {
        return buildPlanPage(myPlans(userId, keyword), page, size);
    }

    @Transactional(readOnly = true)
    public PlanPage managePlans(
            String userId,
            String scope,
            int page,
            int size,
            String destination,
            String creatorName,
            LocalDate createdDate
    ) {
        List<TravelPlan> plans = visiblePlansByScope(userId, scope).stream()
                .filter(plan -> matchesDestination(plan, destination))
                .filter(plan -> matchesCreatorName(plan, creatorName))
                .filter(plan -> matchesCreatedDate(plan, createdDate))
                .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
                .toList();
        List<PlanSummary> summaries = plans.stream()
                .map(plan -> toPlanSummary(plan, userId))
                .toList();
        return buildPlanPage(summaries, page, size);
    }

    @Transactional(readOnly = true)
    public PlanDetail getPlan(Long id, String userId, String shareToken) {
        TravelPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "旅游计划不存在"));
        boolean sharedPreview = StringUtils.hasText(shareToken);
        if (sharedPreview && !plan.getShareToken().equals(shareToken)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "分享链接无效");
        }
        if (!canPreviewPlan(plan, userId, sharedPreview)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "请先打开创建者分享的链接");
        }
        return detailFor(plan, userId, sharedPreview);
    }

    @Transactional
    public PlanDetail requestJoin(Long id, String userId, String shareToken) {
        TravelPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "旅游计划不存在"));
        if (!StringUtils.hasText(shareToken) || !plan.getShareToken().equals(shareToken)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "分享链接无效");
        }
        if (plan.getCreatorId().equals(userId)) {
            ensureCreatorMembership(plan, userId, plan.getCreatorName());
            return detailFor(plan, userId, true);
        }

        PlanMember membership = memberRepository.findByPlanIdAndUserId(plan.getId(), userId).orElseGet(() -> {
            PlanMember created = new PlanMember();
            created.setPlan(plan);
            created.setUserId(userId);
            created.setDisplayName(appUserService.displayNameOf(userId));
            created.setStatus(MemberStatus.PENDING);
            return created;
        });
        membership.setDisplayName(appUserService.displayNameOf(userId));
        if (membership.getStatus() == MemberStatus.REJECTED) {
            membership.setStatus(MemberStatus.PENDING);
            membership.setReviewedAt(null);
            membership.setReviewedBy(null);
        }
        memberRepository.save(membership);
        return detailFor(plan, userId, true);
    }

    @Transactional
    public PlanDetail approveMember(Long planId, Long memberId, String currentUserId) {
        TravelPlan plan = ownedPlan(planId, currentUserId);
        PlanMember member = memberRepository.findById(memberId)
                .filter(item -> item.getPlan().getId().equals(plan.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "成员申请不存在"));
        member.setStatus(MemberStatus.APPROVED);
        member.setReviewedAt(LocalDateTime.now());
        member.setReviewedBy(currentUserId);
        memberRepository.save(member);
        return detailFor(plan, currentUserId, false);
    }

    @Transactional
    public PlanDetail rejectMember(Long planId, Long memberId, String currentUserId) {
        TravelPlan plan = ownedPlan(planId, currentUserId);
        PlanMember member = memberRepository.findById(memberId)
                .filter(item -> item.getPlan().getId().equals(plan.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "成员申请不存在"));
        member.setStatus(MemberStatus.REJECTED);
        member.setReviewedAt(LocalDateTime.now());
        member.setReviewedBy(currentUserId);
        memberRepository.save(member);
        return detailFor(plan, currentUserId, false);
    }

    @Transactional
    public PlanDetail addExpense(Long id, String userId, CreateExpenseRequest request, String requestId) {
        TravelPlan plan = findExpenseAccessiblePlan(id, userId);
        if (plan.getStatus() == PlanStatus.CLOSED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "旅游计划已关闭，不能继续新增花费");
        }
        if (StringUtils.hasText(requestId)) {
            Optional<TravelExpense> existing = expenseRepository.findByPlanIdAndUserIdAndRequestId(id, userId, requestId);
            if (existing.isPresent()) {
                return detailFor(plan, userId, false);
            }
        }

        TravelExpense expense = new TravelExpense();
        expense.setPlan(plan);
        expense.setUserId(userId);
        expense.setPayerName(appUserService.displayNameOf(userId));
        expense.setAmount(request.amount().setScale(2, RoundingMode.HALF_UP));
        expense.setNote(request.note().trim());
        expense.setSpentAt(request.spentAt());
        expense.setRequestId(requestId);
        expenseRepository.save(expense);

        return detailFor(plan, userId, false);
    }

    @Transactional
    public PlanDetail closePlan(Long id, String userId, ClosePlanRequest request) {
        TravelPlan plan = ownedPlan(id, userId);
        plan.setStatus(PlanStatus.CLOSED);
        plan.setParticipantCount(request.participantCount());
        plan.setClosedAt(LocalDateTime.now());
        planRepository.save(plan);
        return detailFor(plan, userId, false);
    }

    @Transactional
    public PlanDetail updateExpense(Long planId, Long expenseId, String userId, UpdateExpenseRequest request) {
        TravelPlan plan = findExpenseAccessiblePlan(planId, userId);
        TravelExpense expense = expenseRepository.findById(expenseId)
                .filter(item -> item.getPlan().getId().equals(plan.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "花费记录不存在"));
        if (!plan.getCreatorId().equals(userId) && !expense.getUserId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "只有计划创建人或花费创建人可以修改该条明细");
        }
        expense.setAmount(request.amount().setScale(2, RoundingMode.HALF_UP));
        expense.setNote(request.note().trim());
        expenseRepository.save(expense);
        return detailFor(plan, userId, false);
    }

    @Transactional
    public PlanDetail reopenPlan(Long id, String userId) {
        TravelPlan plan = ownedPlan(id, userId);
        plan.setStatus(PlanStatus.OPEN);
        plan.setParticipantCount(null);
        plan.setClosedAt(null);
        planRepository.save(plan);
        return detailFor(plan, userId, false);
    }

    @Transactional
    public void deletePlan(Long id, String userId) {
        TravelPlan plan = ownedPlan(id, userId);
        expenseRepository.deleteByPlan(plan);
        imageRepository.deleteByPlan(plan);
        memberRepository.deleteByPlan(plan);
        planRepository.delete(plan);
    }

    @Transactional(readOnly = true)
    public ExpensePage expenses(Long id, String userId, int page, int size) {
        TravelPlan plan = findExpenseAccessiblePlan(id, userId);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(50, Math.max(1, size));
        var result = expenseRepository.findByPlan(
                plan,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.desc("spentAt"), Sort.Order.desc("createdAt")))
        );
        return new ExpensePage(
                result.getContent().stream().map(this::toExpenseView).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    @Transactional(readOnly = true)
    public byte[] exportExpenses(Long id, String userId) {
        TravelPlan plan = findExpenseAccessiblePlan(id, userId);
        List<TravelExpense> expenses = expenseRepository.findByPlanOrderBySpentAtDescCreatedAtDesc(plan);
        int divisor = settlementDivisor(plan);

        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("travel-expenses");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("付款人");
            header.createCell(1).setCellValue("金额");
            header.createCell(2).setCellValue("花费日期");
            header.createCell(3).setCellValue("用途");
            header.createCell(4).setCellValue("人均金额");

            for (int i = 0; i < expenses.size(); i++) {
                TravelExpense expense = expenses.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(expense.getPayerName());
                row.createCell(1).setCellValue(expense.getAmount().doubleValue());
                row.createCell(2).setCellValue(expense.getSpentAt().toString());
                row.createCell(3).setCellValue(expense.getNote());
                row.createCell(4).setCellValue(perPerson(expense.getAmount(), divisor).doubleValue());
            }

            for (int i = 0; i < 5; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "导出 Excel 失败");
        }
    }

    private List<TravelPlan> visiblePlans(String userId, String keyword) {
        String normalizedKeyword = normalizeKeyword(keyword);
        Map<Long, TravelPlan> plans = new LinkedHashMap<>();
        planRepository.findByCreatorId(userId).forEach(plan -> plans.put(plan.getId(), plan));
        memberRepository.findByUserIdOrderByJoinedAtDesc(userId).stream()
                .filter(member -> member.getStatus() == MemberStatus.APPROVED)
                .map(PlanMember::getPlan)
                .forEach(plan -> plans.put(plan.getId(), plan));
        return new ArrayList<>(plans.values()).stream()
                .filter(plan -> matchesKeyword(plan, normalizedKeyword))
                .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
                .toList();
    }

    private List<TravelPlan> visiblePlansByScope(String userId, String scope) {
        if ("joined".equalsIgnoreCase(scope)) {
            return memberRepository.findByUserIdOrderByJoinedAtDesc(userId).stream()
                    .filter(member -> member.getStatus() == MemberStatus.APPROVED)
                    .map(PlanMember::getPlan)
                    .filter(plan -> !userId.equals(plan.getCreatorId()))
                    .distinct()
                    .toList();
        }
        return planRepository.findByCreatorId(userId);
    }

    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return keyword.trim().toLowerCase();
    }

    private boolean matchesKeyword(TravelPlan plan, String keyword) {
        if (keyword == null) {
            return true;
        }
        return plan.getDestination() != null && plan.getDestination().toLowerCase().contains(keyword);
    }

    private boolean matchesDestination(TravelPlan plan, String destination) {
        String normalizedDestination = normalizeKeyword(destination);
        if (normalizedDestination == null) {
            return true;
        }
        return plan.getDestination() != null && plan.getDestination().toLowerCase().contains(normalizedDestination);
    }

    private boolean matchesCreatorName(TravelPlan plan, String creatorName) {
        String normalizedCreatorName = normalizeKeyword(creatorName);
        if (normalizedCreatorName == null) {
            return true;
        }
        return plan.getCreatorName() != null && plan.getCreatorName().toLowerCase().contains(normalizedCreatorName);
    }

    private boolean matchesCreatedDate(TravelPlan plan, LocalDate createdDate) {
        if (createdDate == null || plan.getCreatedAt() == null) {
            return createdDate == null;
        }
        return createdDate.equals(plan.getCreatedAt().toLocalDate());
    }

    private PlanPage buildPlanPage(List<PlanSummary> summaries, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(20, Math.max(1, size));
        int from = Math.min(safePage * safeSize, summaries.size());
        int to = Math.min(from + safeSize, summaries.size());
        int totalPages = summaries.isEmpty() ? 0 : (int) Math.ceil((double) summaries.size() / safeSize);
        return new PlanPage(summaries.subList(from, to), safePage, safeSize, summaries.size(), totalPages, to < summaries.size());
    }

    private TravelPlan ownedPlan(Long id, String userId) {
        TravelPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "旅游计划不存在"));
        if (!plan.getCreatorId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "只有创建者可以执行此操作");
        }
        return plan;
    }

    private TravelPlan findExpenseAccessiblePlan(Long id, String userId) {
        TravelPlan plan = planRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "旅游计划不存在"));
        if (!canViewExpenses(plan, userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "审核通过后才能查看记账详情");
        }
        return plan;
    }

    private boolean canPreviewPlan(TravelPlan plan, String userId, boolean sharedPreview) {
        if (userId != null && isCreatorOrApprovedMember(plan, userId)) {
            return true;
        }
        return sharedPreview;
    }

    private boolean canViewExpenses(TravelPlan plan, String userId) {
        return userId != null && isCreatorOrApprovedMember(plan, userId);
    }

    private boolean canAddExpense(TravelPlan plan, String userId) {
        return plan.getStatus() == PlanStatus.OPEN && canViewExpenses(plan, userId);
    }

    private boolean isCreatorOrApprovedMember(TravelPlan plan, String userId) {
        if (plan.getCreatorId().equals(userId)) {
            return true;
        }
        return memberRepository.findByPlanIdAndUserId(plan.getId(), userId)
                .map(member -> member.getStatus() == MemberStatus.APPROVED)
                .orElse(false);
    }

    private void ensureCreatorMembership(TravelPlan plan, String userId, String displayName) {
        PlanMember membership = memberRepository.findByPlanIdAndUserId(plan.getId(), userId).orElseGet(() -> {
            PlanMember created = new PlanMember();
            created.setPlan(plan);
            created.setUserId(userId);
            created.setJoinedAt(LocalDateTime.now());
            return created;
        });
        membership.setDisplayName(displayName);
        membership.setStatus(MemberStatus.APPROVED);
        membership.setReviewedAt(membership.getReviewedAt() == null ? LocalDateTime.now() : membership.getReviewedAt());
        membership.setReviewedBy(userId);
        memberRepository.save(membership);
    }

    private PlanSummary toPlanSummary(TravelPlan plan, String userId) {
        return new PlanSummary(
                plan.getId(),
                plan.getDestination(),
                plan.getStartDate(),
                plan.getEndDate(),
                plan.getStatus(),
                plan.getCreatorName(),
                plan.getCreatorId().equals(userId),
                plan.getCreatedAt()
        );
    }

    private PlanDetail detailFor(TravelPlan plan, String userId, boolean sharedPreview) {
        boolean creator = userId != null && plan.getCreatorId().equals(userId);
        Optional<PlanMember> currentMembership = userId == null
                ? Optional.empty()
                : memberRepository.findByPlanIdAndUserId(plan.getId(), userId);

        boolean joined = currentMembership.isPresent();
        MemberStatus membershipStatus = currentMembership.map(PlanMember::getStatus).orElse(null);
        boolean approved = creator || membershipStatus == MemberStatus.APPROVED;
        boolean canViewExpenses = approved;
        boolean canAddExpense = canAddExpense(plan, userId);

        List<PlanMember> approvedMembers = memberRepository.findByPlanAndStatusOrderByJoinedAtAsc(plan, MemberStatus.APPROVED);
        List<MemberView> members = approvedMembers.stream()
                .map(this::toMemberView)
                .toList();

        List<MemberView> pendingMembers = creator
                ? memberRepository.findByPlanAndStatusOrderByJoinedAtAsc(plan, MemberStatus.PENDING).stream()
                .map(this::toMemberView)
                .toList()
                : List.of();

        List<ExpenseView> expenses = canViewExpenses
                ? expenseRepository.findByPlanOrderBySpentAtDescCreatedAtDesc(plan).stream()
                .limit(5)
                .map(this::toExpenseView)
                .toList()
                : List.of();

        List<SettlementView> settlements = canViewExpenses
                ? buildSettlements(plan)
                : List.of();

        return new PlanDetail(
                plan.getId(),
                plan.getDestination(),
                plan.getStartDate(),
                plan.getEndDate(),
                plan.getDescription(),
                imageRepository.findByPlanOrderByCreatedAtAsc(plan).stream()
                        .map(image -> toPlanImageView(plan, image))
                        .toList(),
                plan.getCreatorId(),
                plan.getCreatorName(),
                plan.getShareToken(),
                plan.getStatus(),
                plan.getParticipantCount(),
                creator,
                joined,
                approved,
                canViewExpenses,
                canAddExpense,
                membershipStatus,
                members,
                pendingMembers,
                expenses,
                settlements
        );
    }

    private PlanImageView toPlanImageView(TravelPlan plan, TravelPlanImage image) {
        return new PlanImageView(
                image.getId(),
                image.getFilename(),
                image.getContentType(),
                "/api/plans/" + plan.getId() + "/images/" + image.getId()
        );
    }

    private MemberView toMemberView(PlanMember member) {
        return new MemberView(
                member.getId(),
                member.getUserId(),
                member.getDisplayName(),
                member.getStatus(),
                member.getJoinedAt(),
                member.getReviewedAt()
        );
    }

    private ExpenseView toExpenseView(TravelExpense expense) {
        return new ExpenseView(
                expense.getId(),
                expense.getUserId(),
                expense.getPayerName(),
                expense.getAmount(),
                expense.getNote(),
                expense.getSpentAt()
        );
    }

    private List<SettlementView> buildSettlements(TravelPlan plan) {
        List<TravelExpense> expenses = expenseRepository.findByPlanOrderBySpentAtDescCreatedAtDesc(plan);
        int divisor = settlementDivisor(plan);
        Map<String, List<TravelExpense>> byUser = expenses.stream()
                .collect(Collectors.groupingBy(TravelExpense::getUserId, LinkedHashMap::new, Collectors.toList()));
        return byUser.entrySet().stream()
                .map(entry -> {
                    List<TravelExpense> userExpenses = entry.getValue();
                    BigDecimal paidTotal = userExpenses.stream()
                            .map(TravelExpense::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    String payerName = userExpenses.isEmpty() ? appUserService.displayNameOf(entry.getKey()) : userExpenses.get(0).getPayerName();
                    return new SettlementView(entry.getKey(), payerName, paidTotal, perPerson(paidTotal, divisor));
                })
                .toList();
    }

    private int settlementDivisor(TravelPlan plan) {
        if (plan.getParticipantCount() != null && plan.getParticipantCount() > 0) {
            return plan.getParticipantCount();
        }
        int approvedCount = memberRepository.findByPlanAndStatusOrderByJoinedAtAsc(plan, MemberStatus.APPROVED).size();
        return Math.max(1, approvedCount);
    }

    private BigDecimal perPerson(BigDecimal amount, int divisor) {
        return amount.divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP);
    }
}
