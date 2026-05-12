# Travel AA Bill

旅游 AA 记账 Web 应用。前端已合并到 Spring Boot 后端静态资源目录中，部署一个后端镜像即可同时提供网页端和 API。

## 目录

- `travel-bill-backend`: Spring Boot 后端和 Web 前端
- `travel-bill-backend/src/main/resources/static`: 手机网页端和 PC 端页面
- `travel-bill-backend/src/main/resources/schema.sql`: 数据库结构参考
- `.env`: Docker Compose 和本地 IDEA 启动共用的配置
- `docker-compose.yml`: MySQL、Redis、后端一键启动

## 启动

根目录执行：

```bash
docker compose up -d --build
```

默认访问地址：

```text
http://localhost:24975/
```

同一份 `.env` 同时支持两种场景：

- IDEA 直接启动 `TravelBillApplication`：后端读取 `.env`，连接 `127.0.0.1:24976` 的 MySQL 和 `127.0.0.1:24977` 的 Redis。
- Docker Compose 启动：Compose 读取 `.env`，后端容器会覆盖数据库和 Redis 地址为 Docker 网络内的 `mysql:3306` 和 `redis:6379`。

默认管理员账号密码在 `.env` 的 `APP_ADMIN_USERNAME` 和 `APP_ADMIN_PASSWORD` 中配置。应用启动时只会在数据库中不存在该管理员账号时创建；如果已存在，不会覆盖数据库里的密码。

## 功能

- 账号密码登录，不开放公开注册。
- 管理员可生成一次性邀请链接，被邀请用户通过链接注册。
- 创建旅游计划，支持文字和图片一起展示。
- 计划分享、成员申请加入、创建者审核成员。
- 成员记账、结算和 Excel 导出。
- IP 黑名单逻辑保持，Redis key 前缀为 `travel-aa-bill`。
