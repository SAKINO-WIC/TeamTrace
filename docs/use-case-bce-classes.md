# TeamTrace 全站用例参与类清单（BCE：边界类 / 控制类 / 实体类）

本文档基于当前仓库 **前后端分离** 实现，按面向对象分析与设计（OOAD）中常见的 **BCE（Boundary–Control–Entity）** 角色归类参与类，便于用例规约、顺序图与类图对齐。

## 1. 归类约定

| 角色 | 含义 | 在本项目中的典型落点 |
|------|------|----------------------|
| **边界类（Boundary）** | 与参与者或外部系统交互的接口：人机界面、API 契约、跨进程消息、安全与传输层适配 | Vue 视图/壳组件、前端 HTTP 封装、`@RestController`、请求/响应 DTO、过滤器、全局异常封装、JPA Repository（持久化对外边界） |
| **控制类（Control）** | 编排用例流程、事务边界、领域规则协调，通常不直接面向 HTTP 细节 | `*Service`、部分快照/聚合编排类、DTO 与实体之间的映射器 |
| **实体类（Entity）** | 核心业务信息及其生命周期，在持久化层有稳定标识 | `entity` 包下 JPA 实体 |

> **说明**：Spring Data `Repository` 在严格 RUP 中有时单独列为「实体访问」或「数据访问」边界；此处归入 **边界类（持久化边界）** 以便与 Controller/DTO 区分。

---

## 2. 按业务域的用例与参与类映射（概要）

以下按 **学生端 / 教师端 / 管理员端 / 公共** 归纳主要用例链路；完整类名见第 3、4 节清单。

### 2.1 认证与会话

| 用例（概要） | 边界类 | 控制类 | 实体类 |
|-------------|--------|--------|--------|
| 登录、注册、刷新令牌、登出 | `AuthView.vue`、`auth.js`、`http.js`、`AuthController`、`dto/auth/*` | `AuthService` | `User` |
| 路由守卫与角色切换 | `router/index.js`、`utils/auth`（若单独成模块） | — | — |
| JWT 解析与校验 | `JwtTokenProvider`（与 Controller/Filter 协作） | `AuthService`（签发场景） | — |

### 2.2 学生端（班级、任务、小组、互评、成绩、申诉、通知、个人）

| 用例（概要） | 边界类 | 控制类 | 实体类 |
|-------------|--------|--------|--------|
| 浏览班级/加入班级 | `StudentClassesView.vue`、`StudentClassDetailView.vue`、`student.js`、`StudentClassController`、`dto/student/*` | `StudentClassService`、`ClassMembershipService` 等 | `ClassEntity`、`ClassStudent`、`User` |
| 任务列表与详情、子任务提交/审核流 | `StudentTask*.vue`、`student.js`、`StudentTaskController`、`StudentSubtaskController`、`dto/subtask/*` | `StudentTaskService`、`SubtaskService` | `Task`、`Subtask`、`TaskGroup`、`GroupMember` 等 |
| 学期/任务小组创建与加入 | `StudentClassGroupsView.vue`、`StudentTaskGroupController`、`dto/group/*` | `StudentSemesterGroupService`、`TeacherTaskGroupService`（跨角色场景视调用链） | `TaskGroup`、`GroupMember` |
| 互评 | `StudentTaskPeerReviewsView.vue`、`StudentPeerReviewController`、`dto/peerreview/*` | `PeerReviewService` | `PeerReview` |
| 成绩汇总（学生视角） | `StudentTaskScoreSummaryView.vue`、`StudentScoreSummaryController`、`dto/score/*` | `ScoreSummaryService` | `TeacherScore`、`CourseScore` 等（视查询） |
| 申诉 | `StudentAppealsView.vue`、`StudentTaskAppealsView.vue`、`StudentAppealController`、`dto/appeal/*`、`CreateStudentAppealRequest` | `AppealService` | `Appeal` |
| 通知 | `StudentNotificationsView.vue`、`NotificationController`、`dto/notification/*` | `NotificationService` | `Notification` |
| 个人资料与注销 | `StudentProfileView.vue`、`StudentProfileController`、`dto/student/*` | `StudentProfileService` | `User` |
| 学生跨模块任务/申诉列表聚合 | `StudentHomeView.vue` 等、`StudentAggregateController` | `StudentTaskService`、`AppealService` | `Task`、`Appeal` 等（视查询） |

### 2.3 教师端（班级、任务、子任务、互评、评分、课程分、申诉、分析、邀请码、日志）

| 用例（概要） | 边界类 | 控制类 | 实体类 |
|-------------|--------|--------|--------|
| 班级 CRUD、学生名单、仪表盘 | `TeacherClassesView.vue`、`TeacherClassDetailView.vue`、`teacher.js`、`TeacherClassController`、`dto/teacher/*` | `TeacherClassService`、`DashboardService` | `ClassEntity`、`ClassStudent`、`User` |
| 任务与子任务管理 | `TeacherTasksView.vue`、`TeacherTaskDetailView.vue`、`TeacherTaskProgressView.vue`、`TeacherTaskController`、`TeacherSubtaskController` | `TeacherTaskService`、`SubtaskService` | `Task`、`Subtask` |
| 分组与锁定 | `TeacherGroupsView.vue`、`TeacherClassGroupController`、`dto/group/*` | `TeacherTaskGroupService` | `TaskGroup`、`GroupMember` |
| 互评审阅 | `TeacherPeerReviewsView.vue`、`TeacherPeerReviewController` | `PeerReviewService` | `PeerReview` |
| 教师评分、课程成绩 | `TeacherScoreSummariesView.vue`、`TeacherScoreController`、`CourseScoreController`、`dto/teacher/*` | `TeacherScoreService`、`CourseScoreService`、`ScoreSummaryService` | `TeacherScore`、`CourseScore` |
| 申诉处理 | `TeacherAppealsView.vue`、`TeacherAppealController`、`ResolveAppealRequest` 等 | `AppealService` | `Appeal` |
| 数据分析 | `TeacherDashboardView.vue`、`TeacherAnalyticsController` | `DashboardService` 及分析相关 Service | 多实体统计 |
| 班级邀请码（教师侧） | `TeacherClassInviteCodeController`、`dto/teacher/ClassInviteCodeResponse` 等 | `TeacherClassInviteCodeService` | `ClassInviteCode` |
| 操作日志（教师） | `TeacherNotificationLogsView.vue`、`TeacherOperationLogController`、`dto/audit/*` | `TeacherOperationLogService` | `OperationLog` |
| 个人资料 | `TeacherProfileView.vue`、`TeacherProfileController` | `TeacherProfileService` | `User` |
| 任务/评分/报告/申诉中心（聚合页） | `Teacher*CenterView.vue`、`teacherCenter.js` | 各 Snapshot Service（`TeacherWorkbenchSnapshotService`、`TeacherCenterSnapshotService` 等） | 只读聚合 |

### 2.4 管理员端

| 用例（概要） | 边界类 | 控制类 | 实体类 |
|-------------|--------|--------|--------|
| 用户与状态、重置密码 | `AdminUsersView.vue`、`admin.js`、`AdminController`、`dto/admin/*` | `AdminService` | `User` |
| 教师邀请码批处理 | `AdminInviteCodesView.vue`、`AdminController` | `AdminService` | `TeacherInviteCode` |
| 系统监控 | `AdminMonitorView.vue`、`AdminMonitorController`、`dto/admin/monitor/*` | `AdminMonitorService` | 多实体统计 |
| 管理员操作日志 | `AdminLogsView.vue`、`AdminOperationLogController` | `AdminOperationLogService` | `OperationLog` |
| 安全设置（改密等） | `AdminSecurityView.vue`、`AdminController` | `AdminService` | `User` |

### 2.5 横切能力

| 用例（概要） | 边界类 | 控制类 | 实体类 |
|-------------|--------|--------|--------|
| 健康检查 | `TestController`（`/api/health`） | — | — |
| HTTPS 强制（认证相关） | `AuthHttpsEnforcementFilter` | — | — |
| 操作审计记录 | `OperationLogFilter`、`OperationLogService`（写入侧） | `OperationLogService` | `OperationLog` |
| 统一错误响应 | `GlobalExceptionHandler` | — | — |
| Spring Security 规则 | `SecurityConfig` | — | — |

---

## 3. 边界类清单（完整枚举）

### 3.1 前端（人机边界）

**路由**

- `team-trace-frontend/src/router/index.js`

**页面视图（`views`）**

- `AuthView.vue`、`DashboardView.vue`
- 学生：`StudentHomeView.vue`、`StudentClassesView.vue`、`StudentClassDetailView.vue`、`StudentClassGroupsView.vue`、`StudentClassTasksView.vue`、`StudentTasksView.vue`、`StudentTaskDetailView.vue`、`StudentTaskPeerReviewsView.vue`、`StudentTaskScoreSummaryView.vue`、`StudentTaskAppealsView.vue`、`StudentAppealsView.vue`、`StudentNotificationsView.vue`、`StudentProfileView.vue`
- 教师：`TeacherHomeView.vue`、`TeacherClassesView.vue`、`TeacherClassDetailView.vue`、`TeacherTasksView.vue`、`TeacherTaskDetailView.vue`、`TeacherTaskProgressView.vue`、`TeacherPeerReviewsView.vue`、`TeacherScoreSummariesView.vue`、`TeacherAppealsView.vue`、`TeacherStudentsView.vue`、`TeacherGroupsView.vue`、`TeacherDashboardView.vue`、`TeacherTaskCenterView.vue`、`TeacherScoresCenterView.vue`、`TeacherReportsCenterView.vue`、`TeacherAppealsCenterView.vue`、`TeacherNotificationLogsView.vue`、`TeacherHelpView.vue`、`TeacherProfileView.vue`
- 管理员：`AdminHomeView.vue`、`AdminInviteCodesView.vue`、`AdminUsersView.vue`、`AdminSecurityView.vue`、`AdminMonitorView.vue`、`AdminLogsView.vue`

**布局壳与导航（`components`）**

- `components/student/StudentShell.vue`
- `components/teacher/TeacherShell.vue`、`TeacherClassNav.vue`、`TeacherSubviewShell.vue`
- `components/admin/AdminShell.vue`、`AdminToolbar.vue`
- `components/common/EmptyState.vue`、`RuntimeModeBanner.vue`、`DataSourceNotice.vue`

**HTTP / API 客户端边界（`services`）**

- `auth.js`、`http.js`、`runtime.js`、`mock.js`
- `student.js`、`teacher.js`、`teacherCenter.js`、`teacherLocal.js`、`admin.js`

### 3.2 后端 API 与传输边界

**REST 控制器（`controller`）**

- `AuthController`、`StudentProfileController`、`StudentClassController`、`StudentTaskController`、`StudentSubtaskController`、`StudentTaskGroupController`、`StudentPeerReviewController`、`StudentScoreSummaryController`、`StudentAppealController`、`StudentOperationLogController`、`StudentAggregateController`、`NotificationController`
- `TeacherClassController`、`TeacherClassGroupController`、`TeacherClassInviteCodeController`、`TeacherTaskController`、`TeacherSubtaskController`、`TeacherPeerReviewController`、`TeacherScoreSummaryController`、`TeacherScoreController`、`CourseScoreController`、`TeacherAppealController`、`TeacherDashboardController`、`TeacherAnalyticsController`、`TeacherProfileController`、`TeacherOperationLogController`
- `AdminController`、`AdminMonitorController`、`AdminOperationLogController`
- `TestController`

**安全与 Web 过滤器**

- `security/JwtTokenProvider`
- `config/SecurityConfig`
- `web/AuthHttpsEnforcementFilter`、`web/OperationLogFilter`

**全局异常与响应包装**

- `exception/GlobalExceptionHandler`
- `dto/common/ApiResponse.java`

**DTO（请求/响应契约，`dto` 包，共 71 个文件）**

- `dto/auth/*`、`dto/student/*`、`dto/teacher/*`、`dto/subtask/*`、`dto/group/*`、`dto/peerreview/*`、`dto/score/*`、`dto/notification/*`、`dto/appeal/*`、`dto/audit/*`、`dto/admin/*`、`dto/admin/monitor/*`、`dto/common/ApiResponse.java`

### 3.3 持久化边界（Repository）

- `UserRepository`、`ClassRepository`、`ClassStudentRepository`、`ClassInviteCodeRepository`
- `TaskRepository`、`SubtaskRepository`、`TaskGroupRepository`、`GroupMemberRepository`
- `PeerReviewRepository`、`TeacherScoreRepository`、`CourseScoreRepository`
- `NotificationRepository`、`AppealRepository`、`OperationLogRepository`
- `TeacherInviteCodeRepository`

---

## 4. 控制类清单

**应用服务（`service` 包，主源码）**

- `AuthService`、`AdminService`、`AdminMonitorService`、`AdminOperationLogService`
- `StudentProfileService`、`StudentClassService`、`StudentTaskService`、`StudentSemesterGroupService`、`StudentOperationLogService`
- `TeacherClassService`、`TeacherClassInviteCodeService`、`TeacherTaskService`、`TeacherTaskGroupService`、`TeacherProfileService`、`TeacherOperationLogService`
- `SubtaskService`、`PeerReviewService`、`TeacherScoreService`、`CourseScoreService`、`ScoreSummaryService`
- `AppealService`、`NotificationService`、`DashboardService`
- `ClassMembershipService`、`OperationLogService`
- **快照/聚合编排**：`TeacherWorkbenchSnapshotService`、`TeacherCenterSnapshotService`

**映射器（控制类辅助：用例级 DTO 组装）**

- `TaskSummaryMapper`

---

## 5. 实体类清单（领域 + 持久化映射）

包路径：`com.teamtrace.backend.entity`

- `User`
- `ClassEntity`、`ClassStudent`、`ClassInviteCode`
- `Task`、`Subtask`、`TaskGroup`、`GroupMember`
- `PeerReview`、`TeacherScore`、`CourseScore`
- `Notification`、`Appeal`、`OperationLog`
- `TeacherInviteCode`

---

## 6. 小结

- **边界**：前端 Vue 视图/壳/`services` 与后端 `Controller`、DTO、Filter、`JwtTokenProvider`、`GlobalExceptionHandler`、以及 **Repository** 共同构成系统对外与对内的接口层。
- **控制**：以 `*Service` 为主轴编排用例；快照类 Service 承担 **只读聚合用例** 的控制职责。
- **实体**：`entity` 包内 JPA 实体承载核心业务状态，经 Repository 持久化。

若需要在 UML 工具中落地，建议：**每个主要用例 1 个控制对象（对应 Service 或明确的事务脚本边界）**，边界侧为 **Actor ↔ Vue** 与 **Vue ↔ Controller**，实体侧仅暴露领域状态与不变式。

---

*文档生成依据：仓库 `team-trace-backend` 与 `team-trace-frontend` 源码结构扫描（控制器、服务、实体、DTO、前端路由与服务模块）。*
