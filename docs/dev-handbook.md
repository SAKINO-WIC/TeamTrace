# TeamTrace 开发操作手册（工作区版）

适用对象：当前 TeamTrace 本地开发工作区。
工作区结构为“四目录协作”，其中代码主体仍是“三仓协作”。

## 1. 工作区结构

### 1.1 `AIDevRules`

- 用途：存放 AI 协作规则、上下文模板、统一开发日志、文档同步脚本与工作区文档索引。
- 职责：约束协作流程、记录每次会话完成内容、维护本地文档同步机制。
- 边界：不替代项目事实文档，不维护 PRD、系统设计、API 真值。

### 1.2 `team-trace-frontend`

- 用途：Vue 3 + Vite 前端代码仓。
- 职责：页面、交互、状态管理、路由、样式、前端联调说明。
- 事实文档入口：`README.md`、`docs/status/frontend_data_source_matrix.md`。
- 参考文档入口：`docs/reference/frontend_demo*.md`。

### 1.3 `team-trace-backend`

- 用途：Spring Boot + JPA 后端代码仓。
- 职责：认证、权限、业务逻辑、数据访问、联调脚本。
- 事实文档入口：`README.md`、`docs/landing-verification.md`、`docs/teacher-feedback-requirements.md`。
- 开发日志入口：统一使用 `AIDevRules/DEV_LOG.md`，后端旧独立日志仅保留归档。

### 1.4 `team-trace-project`

- 用途：项目治理仓。
- 职责：维护 PRD、系统设计、API 文档、数据库基线、流程规范。
- 关键文档：
  - `docs/api-spec-v1.2.md`
  - `docs/repository-map.md`
  - `docs/git-workflow.md`
  - `database/**`

## 2. 文档优先级

当多个文件信息重叠或冲突时，按以下顺序判断：

1. 当前会话中的明确指令
2. 协作规则文件（`AGENTS.md`、`DEV_RULES.md` 等）
3. 当前仓库的 README / 当前事实文档
4. 项目治理仓中的开发手册、API 文档
5. 配置文件与源码实现

补充：

- PRD、系统设计、数据库文档属于上游基线，不能用实现阶段的局部偏差直接回写覆盖。
- 接口真值应同步到 `team-trace-project/docs/api-spec-v1.2.md`。
- 页面接入真值应同步到 `team-trace-frontend/docs/status/frontend_data_source_matrix.md`。

## 3. 本地开发建议

### 3.1 前端

```sh
cd team-trace-frontend
cp .env.example .env
npm install
npm run dev
```

注意：

- 本地联调基线：前端代理到后端 `http://localhost:8082`。
- 云开发环境基线：后端端口 `8088`，云数据库 MySQL 端口 `3306`。
- 如果切到云环境联调，请在 `.env` 或 `.env.local` 中把 `VITE_API_PROXY_TARGET` 改到云环境对应地址和端口。

### 3.2 后端

```sh
cd team-trace-backend
cp .env.example .env
set -a && source .env && set +a && ./mvnw spring-boot:run
```

注意：

- 本地后端默认端口基线为 `8082`。
- 云开发环境当前后端端口为 `8088`，数据库端口为 `3306`。
- `.env` 不加载时，数据库和端口相关环境变量不会自动注入。

### 3.3 项目治理仓

通常不需要“运行”，重点是同步文档：

- API 变更时更新 `docs/api-spec-v1.2.md`
- 数据库结构变更时更新 `database/**`
- 流程或边界变化时更新 `docs/repository-map.md` / `docs/git-workflow.md`

## 4. 当前协作方式

### 4.1 前端默认边界

- 本地默认以 `team-trace-frontend` 开发为主。
- 前端页面是否已接通真实接口，以 `docs/status/frontend_data_source_matrix.md` 为准。
- 若后端真实接口已存在但前端仍在预览数据阶段，应优先记录在矩阵文档，而不是在 README 中笼统写“已完成联调”。

### 4.2 后端默认边界

- 后端接口事实以源码和 `team-trace-backend/README.md` 为准。
- 接口路径、请求参数、响应结构变更后，必须同步项目仓 API 文档。

### 4.3 文档同步最小闭环

接口或页面状态变化后，至少同步以下文件：

1. `team-trace-backend/README.md` 或相关后端说明
2. `team-trace-project/docs/api-spec-v1.2.md`
3. `team-trace-frontend/docs/status/frontend_data_source_matrix.md`
4. 必要时补充前端或项目 README / 手册

## 5. Git 协作原则

- `main` 应尽量保持可回滚。
- 代码变更和文档同步尽量在同一轮任务中完成。
- 接口变化不要只改前后端代码而不改项目仓文档。
- 数据库变化不要只改后端实体而不改项目仓数据库基线。

## 6. 当前阶段需要避免的误区

- 不要把历史 demo 文档当成当前实现真值。
- 不要把“后端已实现”误写成“前端已接通”。
- 不要把页面占位路由误写成“已有后端聚合接口”。
- 不要在没有需求确认的情况下改写 PRD、系统设计、数据库文档。

## 7. 常用真值入口

- 前端当前事实：`team-trace-frontend/README.md`
- 前端接通状态：`team-trace-frontend/docs/status/frontend_data_source_matrix.md`
- 后端实现范围：`team-trace-backend/README.md`
- API 当前真值：`team-trace-project/docs/api-spec-v1.2.md`
- 仓库与文档边界：`team-trace-project/docs/repository-map.md`
- AI 协作规则与日志：`AIDevRules/DEV_RULES.md`、`AIDevRules/DEV_LOG.md`、`AIDevRules/WORKSPACE_DOC_INDEX.md`
