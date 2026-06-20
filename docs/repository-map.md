# TeamTrace 仓库与工作区地图

用于统一说明当前本地工作区结构、三仓职责边界，以及哪些文档属于事实说明、哪些属于设计基线。

## 1. 当前本地工作区

| 目录 | 类型 | 主要内容 | 是否独立 Git 仓库 |
| --- | --- | --- | --- |
| `AIDevRules` | 本地协作规则目录 | AI 协作规则、统一开发日志、自动同步脚本、工作区文档索引 | 否 |
| `team-trace-frontend` | 前端代码仓 | Vue 前端代码、页面、样式、前端联调文档 | 是 |
| `team-trace-backend` | 后端代码仓 | Spring Boot 后端代码、接口实现、脚本、后端说明 | 是 |
| `team-trace-project` | 项目治理仓 | PRD、系统设计、API 文档、数据库、流程规范 | 是 |

说明：

- 代码层面的主仓仍然是三仓结构。
- `AIDevRules` 是本地开发协作辅助目录，不承载产品真值，不替代 `team-trace-project/docs`。

## 2. 文档真值边界

| 文档位置 | 主要职责 | 更新原则 |
| --- | --- | --- |
| `team-trace-project/docs/product-prd-v1.1.md` | 产品需求基线 | 非需求变更场景不改 |
| `team-trace-project/docs/system-design-v1.1.md` | 系统功能设计基线 | 非设计变更场景不改 |
| `team-trace-project/database/**` | 数据库基线与迁移 | 非数据库变更场景不改 |
| `team-trace-project/docs/api-spec-v1.2.md` | 当前后端接口事实 | 接口实现变化时同步 |
| `team-trace-frontend/README.md` | 前端当前事实与联调说明 | 以前端现状为准 |
| `team-trace-frontend/docs/status/frontend_data_source_matrix.md` | 页面级数据来源与接通状态 | 以前端实际接入为准 |
| `team-trace-backend/README.md` | 后端实现范围与联调命令 | 以后端源码为准 |
| `AIDevRules/DEV_LOG.md` | 统一阶段性开发日志 | 前后端与项目仓开发结束后统一追加 |
| `AIDevRules/WORKSPACE_DOC_INDEX.md` | 工作区文档路径总索引 | 由同步脚本自动刷新 |
| `AIDevRules/LAST_DOC_SYNC.md` | 最近一次文档同步状态 | 由 hook / 同步脚本自动刷新 |
| `AIDevRules/*.md` | AI 协作规则与辅助说明 | 只维护规则、流程、记录，不重复项目事实 |

## 3. 职责边界

- 前端仓：页面、交互、状态管理、路由、前端联调说明。
- 后端仓：接口、鉴权、业务逻辑、数据访问、后端脚本与验收说明。
- 项目仓：PRD、系统设计、API 文档、数据库基线、协作流程。
- AIDevRules：AI 开发规则、项目上下文模板、阶段性开发日志。

## 4. 交叉变更规则

- 涉及接口实现变化：
  - 更新 `team-trace-backend` 代码与说明。
  - 同步 `team-trace-project/docs/api-spec-v1.2.md`。
  - 同步 `team-trace-frontend/docs/status/frontend_data_source_matrix.md` 和必要的前端 README 描述。
- 涉及数据库结构变化：
  - 先走 `team-trace-project/database/**`。
  - 再同步后端实体、仓库、服务逻辑。
- 涉及页面数据来源变化：
  - 优先更新 `team-trace-frontend/docs/status/frontend_data_source_matrix.md`。
  - 如果影响启动方式、联调入口或整体说明，再更新前端 README。

## 5. 当前版本基线

- PRD：V1.1
- 系统功能设计：V1.1
- API 接口文档：V1.2（2026-04-25 实现对齐）
- 数据库设计：V2.0.2

> 说明：版本基线更新时，需要同步修改本文件。
