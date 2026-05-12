# Travel Bill

旅游费用小程序，包含微信小程序前端和 Spring Boot + MySQL 后端。

## 目录

- `travel-bill-frontend`: 微信小程序前端
- `travel-bill-backend`: Java 后端
- `travel-bill-backend/src/main/resources/schema.sql`: 创建数据库和表的 SQL

## 本地启动

1. 先创建数据库：

```sql
CREATE DATABASE IF NOT EXISTS `travel-bill`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
```

后端启动时会自动创建/更新表结构。完整建库建表 SQL 仍保留在 `travel-bill-backend/src/main/resources/schema.sql`，方便手动初始化或部署时使用。

2. 修改后端数据库账号密码：

```yaml
# travel-bill-backend/src/main/resources/application.yml
spring:
  datasource:
    username: root
    password: root
```

3. 启动后端：

```bash
cd travel-bill-backend
mvn spring-boot:run
```

4. 用微信开发者工具打开 `travel-bill-frontend`。

前端默认请求 `http://localhost:8080/api`，配置在 `miniprogram/utils/request.ts`。

## 功能

- 创建旅游计划：地点、开始/结束日期、详细计划描述
- 创建者可分享计划链接到微信群
- 只有通过分享链接进入的用户会加入可见成员列表
- 成员可新增自己的旅游花费
- 所有可见成员可查看每条花费和人均分摊金额
- 创建者可在旅游结束后输入分摊总人数并关闭计划

## 后端防护

- 数据访问使用 Spring Data JPA 参数化查询，不拼接用户输入 SQL。
- 业务接口需要 `Authorization: Bearer <固定128位token>`，后端和小程序配置的 token 不一致时直接返回 401。
- 当前小程序使用本地生成的 `X-User-Id` 标识用户，前提是固定 token 先校验通过。
- 请求 DTO 对必填、长度、金额范围和金额位数做校验。
- POST 请求支持 `X-Request-Id` 幂等键，创建计划和新增花费可防止同一次请求重复落库。
- API 增加基础限流：同一用户或 IP 每分钟最多 120 次请求。
- 响应增加 `X-Content-Type-Options`、`X-Frame-Options`、`Referrer-Policy`、`Cache-Control` 等安全头。
- 固定 token 配置在后端 `app.auth.static-token` 和前端 `miniprogram/utils/request.ts` 的 `STATIC_TOKEN`，两边必须保持一致。
- 用户首次进入小程序会输入昵称或真实姓名，后端通过 `/api/users/identify` 创建或查找用户 id；之后业务接口用这个 id 访问数据。
- 为兼容旧数据，识别用户时会优先复用同名旧计划创建者或同名成员的历史 user id。
