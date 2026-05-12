package com.travelbill.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class Requests {
    private Requests() {
    }

    public record PasswordLoginRequest(
            @NotBlank(message = "请输入账号")
            @Size(max = 80, message = "账号不能超过80个字")
            String username,
            @NotBlank(message = "请输入密码")
            @Size(max = 120, message = "密码不能超过120个字")
            String password
    ) {
    }

    public record RegisterWithInviteRequest(
            @NotBlank(message = "注册链接无效")
            String inviteToken,
            @NotBlank(message = "请输入账号")
            @Size(max = 80, message = "账号不能超过80个字")
            String username,
            @NotBlank(message = "请输入密码")
            @Size(max = 120, message = "密码不能超过120个字")
            String password,
            @NotBlank(message = "请输入昵称")
            @Size(max = 80, message = "昵称不能超过80个字")
            String displayName
    ) {
    }

    public record CreatePlanRequest(
            @NotBlank(message = "请输入旅游地点")
            @Size(max = 120, message = "旅游地点不能超过120个字")
            String destination,
            @NotNull(message = "请选择开始日期")
            LocalDate startDate,
            @NotNull(message = "请选择结束日期")
            LocalDate endDate,
            @NotBlank(message = "请输入旅游详细计划")
            @Size(max = 2000, message = "旅游详细计划不能超过2000个字")
            String description,
            @Size(max = 80, message = "昵称不能超过80个字")
            String creatorName
    ) {
    }

    public record CreateExpenseRequest(
            @NotBlank(message = "请输入付款人名称")
            @Size(max = 80, message = "付款人名称不能超过80个字")
            String payerName,
            @NotNull(message = "请输入花费金额")
            @DecimalMin(value = "0.01", message = "花费金额必须大于0")
            @Digits(integer = 10, fraction = 2, message = "花费金额最多10位整数和2位小数")
            BigDecimal amount,
            @NotBlank(message = "请输入花费用途")
            @Size(max = 255, message = "花费用途不能超过255个字")
            String note,
            @NotNull(message = "请选择花费日期")
            LocalDate spentAt
    ) {
    }

    public record ClosePlanRequest(
            @NotNull(message = "请输入分摊总人数")
            @Min(value = 1, message = "分摊总人数必须大于0")
            Integer participantCount
    ) {
    }

    public record UpdateExpenseRequest(
            @NotNull(message = "请输入花费金额")
            @DecimalMin(value = "0.01", message = "花费金额必须大于0")
            @Digits(integer = 10, fraction = 2, message = "花费金额最多10位整数和2位小数")
            BigDecimal amount,
            @NotBlank(message = "请输入花费用途")
            @Size(max = 255, message = "花费用途不能超过255个字")
            String note
    ) {
    }

    public record UpdateProfileRequest(
            @Size(max = 80, message = "昵称不能超过80个字")
            String displayName,
            @Size(max = 500, message = "头像地址不能超过500个字")
            String avatarUrl
    ) {
    }
}
