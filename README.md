# Campus Integrated Service Platform Scaffold

This repository contains a graduation-project-ready scaffold built from your plan.

## Architecture

- Backend: Spring Boot 2.7 multi-module (microservice style)
- Discovery: Eureka server
- Gateway: Spring Cloud Gateway
- Services:
  - user-service (auth, RBAC entry)
  - lost-found-service (P0)
  - activity-service (P0)
  - market-service (P1 with WebSocket + recommendation endpoint)
  - stats-service (dashboard data API)
- Frontend: Vue3 + Vite + Element Plus
- Infra: MySQL 8, Redis 7, RabbitMQ, Nginx

## Quick Start

1. Start middleware:
   - `docker compose up -d`
2. Start backend with single Java startup entry (recommended):
   - Main class: `com.campus.launcher.DevLauncherApplication`
   - File path: `backend/dev-launcher/src/main/java/com/campus/launcher/DevLauncherApplication.java`
   - CLI alternative (from `backend/`): `mvn -pl dev-launcher spring-boot:run`
3. Start frontend:
   - `cd frontend`
   - `npm install`
   - `npm run dev`

## Manual Backend Startup (Fallback)

If you want to start modules manually, run from `backend/`:

- `mvn -pl service-discovery spring-boot:run`
- `mvn -pl user-service spring-boot:run`
- `mvn -pl lost-found-service spring-boot:run`
- `mvn -pl activity-service spring-boot:run`
- `mvn -pl market-service spring-boot:run`
- `mvn -pl stats-service spring-boot:run`
- `mvn -pl api-gateway spring-boot:run`

## Activity Module API

Gateway prefix: `/api/activities`

- 首次切换到新活动模块表结构时，先执行：`sql/migrate_activity_module.sql`
- `POST /api/activities`: 发布活动（默认进入待审核）
- `GET /api/activities`: 活动列表（支持 `keyword/categoryId/status/publisherUserId` 筛选）
- `GET /api/activities/{id}`: 活动详情
- `GET /api/activities/categories`: 活动分类列表
- `POST /api/activities/categories`: 新增活动分类
- `POST /api/activities/{id}/apply`: 活动报名（支持名额限制与可选报名审核）
- `POST /api/activities/{id}/apply/cancel`: 取消报名
- `GET /api/activities/{id}/applies`: 查看报名列表（发布者）
- `PATCH /api/activities/{id}/applies/{applyId}/review`: 报名审核（发布者）
- `GET /api/activities/audit/pending`: 待审批活动列表（管理员）
- `PATCH /api/activities/{id}/audit`: 活动审批（管理员）
- `GET /api/activities/{id}/audits`: 活动审批记录

## Ports

- 8761: discovery
- 8080: gateway
- 9001: user-service
- 9002: lost-found-service
- 9003: activity-service
- 9004: market-service
- 9005: stats-service
- 5173: frontend dev server

## Database

- Schema file: `sql/init.sql`
- Includes 28 tables covering user/RBAC, lost-found, activities, market, and system module.

## Suggested Milestones

1. P0 week 1-2: user + lost-found CRUD + auth skeleton
2. P0 week 3-4: activity module + audit flow
3. P1 week 5-6: market module + websocket chat
4. P1 week 7: recommendation and dashboard integration
5. Week 8: test, optimize, and defense demo scripts
