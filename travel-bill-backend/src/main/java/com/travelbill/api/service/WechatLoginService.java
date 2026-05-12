package com.travelbill.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@Service
public class WechatLoginService {
    private final boolean mockLogin;
    private final String appId;
    private final String appSecret;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WechatLoginService(
            @Value("${app.wechat.mock-login:true}") boolean mockLogin,
            @Value("${app.wechat.app-id:}") String appId,
            @Value("${app.wechat.app-secret:}") String appSecret
    ) {
        this.mockLogin = mockLogin;
        this.appId = appId;
        this.appSecret = appSecret;
    }

    public String openIdByCode(String code) {
        if (code == null || code.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "缺少微信登录 code");
        }
        if (mockLogin) {
            return "dev_" + sha256(code).substring(0, 32);
        }
        if (appId.isBlank() || appSecret.isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "微信登录配置缺失");
        }

        String url = UriComponentsBuilder.fromHttpUrl("https://api.weixin.qq.com/sns/jscode2session")
                .queryParam("appid", appId)
                .queryParam("secret", appSecret)
                .queryParam("js_code", code)
                .queryParam("grant_type", "authorization_code")
                .toUriString();

        try {
            String raw = restTemplate.getForObject(url, String.class);
            if (raw == null || raw.isBlank()) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "微信登录失败：响应为空");
            }
            Map<String, Object> response = objectMapper.readValue(raw, new TypeReference<>() {});
            Object openid = response.get("openid");
            if (openid != null) {
                return openid.toString();
            }
            Object errorCode = response.get("errcode");
            Object errorMessage = response.get("errmsg");
            String message = errorCode == null
                    ? "微信登录失败"
                    : "微信登录失败：" + errorCode + (errorMessage == null ? "" : " " + errorMessage);
            throw new ApiException(HttpStatus.UNAUTHORIZED, message);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "微信登录失败：" + exception.getMessage());
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : digest) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot hash code", exception);
        }
    }
}
