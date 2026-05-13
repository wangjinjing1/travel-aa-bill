# Travel AA Bill

旅游 AA 记账 Web 应用。当前项目根目录就是 Spring Boot 项目，前端页面放在 `src/main/resources/static`，部署一个后端服务即可同时提供网页端和 API。

## 目录

- `src/main/java`: 后端代码
- `src/main/resources/static`: 手机网页端和 PC 端页面
- `src/main/resources/schema.sql`: 数据库结构参考
- `.env`: 后端运行配置
- `Dockerfile`: 后端镜像构建文件
- `docker-compose.yml`: 只启动后端服务，MySQL 和 Redis 使用外部服务

## 启动

先准备外部 MySQL 和 Redis，并按实际地址修改 `.env`。

根目录执行：

```bash
docker compose up -d --build
```

默认访问地址：

```text
http://localhost:24975/
```

默认超级管理员账号密码在 `.env` 的 `APP_ADMIN_USERNAME` 和 `APP_ADMIN_PASSWORD` 中配置。应用启动时会确保该账号角色为超级管理员；如果账号已存在，不会覆盖数据库里的密码。

## 功能

- 账号密码登录，不开放公开注册。
- 管理员可生成一次性邀请链接，被邀请用户通过链接注册。
- 创建旅游计划，支持文字和图片一起展示。
- 计划分享、成员申请加入、创建者审核成员。
- 成员记账、结算和 Excel 导出。
- IP 黑名单逻辑保持，Redis key 前缀为 `travel-aa-bill`。
