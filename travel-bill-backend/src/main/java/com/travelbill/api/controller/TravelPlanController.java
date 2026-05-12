package com.travelbill.api.controller;

import com.travelbill.api.dto.Requests.ClosePlanRequest;
import com.travelbill.api.dto.Requests.CreateExpenseRequest;
import com.travelbill.api.dto.Requests.CreatePlanRequest;
import com.travelbill.api.dto.Requests.UpdateExpenseRequest;
import com.travelbill.api.dto.Responses.ExpensePage;
import com.travelbill.api.dto.Responses.PlanDetail;
import com.travelbill.api.dto.Responses.PlanPage;
import com.travelbill.api.dto.Responses.PlanSummary;
import com.travelbill.api.service.ApiException;
import com.travelbill.api.service.TravelPlanService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/plans")
public class TravelPlanController {
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("^[A-Za-z0-9_.:-]{1,80}$");

    private final TravelPlanService service;

    public TravelPlanController(TravelPlanService service) {
        this.service = service;
    }

    @PostMapping
    public PlanDetail createPlan(
            jakarta.servlet.http.HttpServletRequest httpRequest,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @Valid @RequestBody CreatePlanRequest request
    ) {
        return service.createPlan(currentUser(httpRequest), request, optionalRequestId(requestId));
    }

    @GetMapping("/my")
    public List<PlanSummary> myPlans(jakarta.servlet.http.HttpServletRequest httpRequest) {
        return service.myPlans(currentUser(httpRequest));
    }

    @GetMapping("/my-page")
    public PlanPage myPlansPage(
            jakarta.servlet.http.HttpServletRequest httpRequest,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword
    ) {
        return service.myPlans(currentUser(httpRequest), page, size, keyword);
    }

    @GetMapping("/manage-page")
    public PlanPage managePlansPage(
            jakarta.servlet.http.HttpServletRequest httpRequest,
            @RequestParam(defaultValue = "created") String scope,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) String creatorName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdDate
    ) {
        return service.managePlans(currentUser(httpRequest), scope, page, size, destination, creatorName, createdDate);
    }

    @GetMapping("/{id}")
    public PlanDetail getPlan(
            @PathVariable Long id,
            jakarta.servlet.http.HttpServletRequest httpRequest,
            @RequestParam(value = "shareToken", required = false) String shareToken
    ) {
        return service.getPlan(id, optionalCurrentUser(httpRequest), shareToken);
    }

    @PostMapping("/{id}/join")
    public PlanDetail joinPlan(
            @PathVariable Long id,
            jakarta.servlet.http.HttpServletRequest httpRequest,
            @RequestParam(value = "shareToken", required = false) String shareToken
    ) {
        return service.requestJoin(id, currentUser(httpRequest), shareToken);
    }

    @PostMapping("/{id}/members/{memberId}/approve")
    public PlanDetail approveMember(
            @PathVariable Long id,
            @PathVariable Long memberId,
            jakarta.servlet.http.HttpServletRequest httpRequest
    ) {
        return service.approveMember(id, memberId, currentUser(httpRequest));
    }

    @PostMapping("/{id}/members/{memberId}/reject")
    public PlanDetail rejectMember(
            @PathVariable Long id,
            @PathVariable Long memberId,
            jakarta.servlet.http.HttpServletRequest httpRequest
    ) {
        return service.rejectMember(id, memberId, currentUser(httpRequest));
    }

    @PostMapping("/{id}/expenses")
    public PlanDetail addExpense(
            @PathVariable Long id,
            jakarta.servlet.http.HttpServletRequest httpRequest,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @Valid @RequestBody CreateExpenseRequest request
    ) {
        return service.addExpense(id, currentUser(httpRequest), request, optionalRequestId(requestId));
    }

    @PostMapping("/{id}/expenses/{expenseId}/update")
    public PlanDetail updateExpense(
            @PathVariable Long id,
            @PathVariable Long expenseId,
            jakarta.servlet.http.HttpServletRequest httpRequest,
            @Valid @RequestBody UpdateExpenseRequest request
    ) {
        return service.updateExpense(id, expenseId, currentUser(httpRequest), request);
    }

    @GetMapping("/{id}/expenses")
    public ExpensePage expenses(
            @PathVariable Long id,
            jakarta.servlet.http.HttpServletRequest httpRequest,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return service.expenses(id, currentUser(httpRequest), page, size);
    }

    @GetMapping("/{id}/expenses/export")
    public ResponseEntity<byte[]> exportExpenses(
            @PathVariable Long id,
            jakarta.servlet.http.HttpServletRequest httpRequest
    ) {
        byte[] content = service.exportExpenses(id, currentUser(httpRequest));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=travel-expenses.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(content);
    }

    @PostMapping("/{id}/close")
    public PlanDetail closePlan(
            @PathVariable Long id,
            jakarta.servlet.http.HttpServletRequest httpRequest,
            @Valid @RequestBody ClosePlanRequest request
    ) {
        return service.closePlan(id, currentUser(httpRequest), request);
    }

    @PostMapping("/{id}/reopen")
    public PlanDetail reopenPlan(
            @PathVariable Long id,
            jakarta.servlet.http.HttpServletRequest httpRequest
    ) {
        return service.reopenPlan(id, currentUser(httpRequest));
    }

    @PostMapping("/{id}/delete")
    public Map<String, Boolean> deletePlan(
            @PathVariable Long id,
            jakarta.servlet.http.HttpServletRequest httpRequest
    ) {
        service.deletePlan(id, currentUser(httpRequest));
        return Map.of("success", true);
    }

    private String currentUser(jakarta.servlet.http.HttpServletRequest request) {
        Object userId = request.getAttribute("authUserId");
        if (userId == null) {
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "请先登录");
        }
        return requiredUser(userId.toString());
    }

    private String optionalCurrentUser(jakarta.servlet.http.HttpServletRequest request) {
        Object userId = request.getAttribute("authUserId");
        return userId == null ? null : requiredUser(userId.toString());
    }

    private String requiredUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "缺少用户标识");
        }
        return userId;
    }

    private String optionalRequestId(String requestId) {
        if (!StringUtils.hasText(requestId)) {
            return null;
        }
        if (!SAFE_REQUEST_ID.matcher(requestId).matches()) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "请求标识格式不正确");
        }
        return requestId;
    }
}
