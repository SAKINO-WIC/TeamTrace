# TeamTrace（摸鱼终结者）API 接口设计文档（V1.2）

文档版本：V1.2
修订日期：2026-04-25
状态：实现对齐版

> 本文件只记录当前后端已经落地的接口事实，用于前后端联调和文档同步。
> PRD、系统功能设计文档中的规划能力仍保留其产品意义，但如果尚未落地，不在本文件中继续按“已实现接口”描述。

---

## 一、通用规范

### 1.1 基础信息

- Base URL：`/api`
- 认证方式：JWT，Header 为 `Authorization: Bearer <token>`
- 统一响应结构：

```json
{
  "success": true,
  "code": "OK",
  "message": "操作成功",
  "data": {}
}
```

### 1.2 分页约定

分页接口统一使用：

- `page`
- `size`

分页结果通常位于 `data` 中，兼容字段包括：

- `list`
- `page`
- `size`
- `total`
- `pages`
- `hasNext`

### 1.3 当前与旧版文档的关键差异

- 站内通知统一走 `/api/notifications/**`，不再区分 `/api/student/notifications/**`。
- 学生申诉接口为 `/api/student/tasks/{taskId}/appeals`。
- 学生任务详情接口为 `/api/student/classes/{classId}/tasks/{taskId}`。
- 教师任务详情接口为 `/api/teacher/classes/{classId}/tasks/{taskId}`。
- 教师申诉处理接口为 `/api/teacher/classes/{classId}/tasks/{taskId}/appeals`。
- 当前没有忘记密码、WebSocket、通用文件上传、教师总报告中心等正式接口。

---

## 二、认证模块

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/auth/student/register` | 公开 | 学生注册 |
| POST | `/api/auth/teacher/register` | 公开 | 教师注册 |
| POST | `/api/auth/login` | 公开 | 登录，返回 `data.token`、`role`、`user` |
| POST | `/api/auth/refresh` | 已登录 | 刷新 token |
| POST | `/api/auth/logout` | 已登录 | 登出并吊销当前 token |

补充说明：

- 教师注册需要教师邀请码；注册成功后邀请码进入 `使用中`。
- 登录返回的 token 字段为 `data.token`，不是 `accessToken`。
- 当前认证模块默认启用 HTTPS 约束，`localhost/127.0.0.1` 本地调试除外。

---

## 三、管理员模块

### 3.1 教师邀请码

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/admin/teacher-invite-codes?expireDays=30` | 生成单个教师邀请码 |
| POST | `/api/admin/teacher-invite-codes/batch` | 批量生成邀请码 |
| GET | `/api/admin/teacher-invite-codes` | 分页查询邀请码 |
| POST | `/api/admin/teacher-invite-codes/{code}/revoke` | 停止使用单个邀请码 |
| POST | `/api/admin/teacher-invite-codes/{code}/resume` | 继续使用单个邀请码 |
| DELETE | `/api/admin/teacher-invite-codes/{code}` | 删除单个未使用邀请码 |
| POST | `/api/admin/teacher-invite-codes/revoke-batch` | 按 code 列表批量撤销 |
| POST | `/api/admin/teacher-invite-codes/revoke-by-query` | 按查询条件批量撤销 |

当前支持的常见过滤参数：

- `code`
- `status`
- `expired`
- `expireFrom`
- `expireTo`

当前邀请码状态语义：

- `0` 未使用
- `1` 使用中（已绑定教师）
- `2` 已停用

### 3.2 用户管理

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/users` | 分页查询用户 |
| PUT | `/api/admin/users/{id}/status` | 启用/禁用用户 |
| POST | `/api/admin/users/{id}/reset-password` | 重置用户密码 |
| DELETE | `/api/admin/users/{id}` | 注销教师账号，并释放其绑定的邀请码 |
| PUT | `/api/admin/me/password` | 管理员修改本人密码 |

当前常见筛选参数：

- `role`
- `phone`
- `name`
- `status`
- `isDeleted`

### 3.3 监控与审计

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/monitor/overview` | 监控总览 |
| GET | `/api/admin/monitor/classes` | 班级分页监控 |
| GET | `/api/admin/monitor/tasks` | 任务分页监控 |
| GET | `/api/admin/operation-logs` | 分页查询全局操作日志 |
| GET | `/api/admin/operation-logs/export` | 导出操作日志 CSV |

操作日志筛选参数：

- `userId`
- `role`
- `pathContains`
- `createdFrom`
- `createdTo`

---

## 四、站内通知模块

师生共用同一组通知接口，数据按 JWT 当前用户隔离。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/notifications` | 分页查询通知 |
| GET | `/api/notifications/unread-count` | 查询未读数 |
| PUT | `/api/notifications/{id}/read` | 单条标记已读 |
| PUT | `/api/notifications/read-all` | 全部标记已读 |
| PUT | `/api/notifications/read-by-type` | 按类型批量标记已读 |
| PUT | `/api/notifications/read-by-related` | 按关联对象批量标记已读 |

常见查询参数：

- `page`
- `size`
- `type`
- `isRead`

---

## 五、教师模块

### 5.1 班级

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/teacher/classes` | 创建班级 |
| GET | `/api/teacher/classes` | 查询教师自己的班级列表 |
| GET | `/api/teacher/classes/{classId}` | 查询班级详情 |
| GET | `/api/teacher/classes/{classId}/students` | 查询班级学生分页 |
| PUT | `/api/teacher/classes/{classId}/grouping-lock` | 分组锁定/解锁 |
| DELETE | `/api/teacher/classes/{classId}/students/{studentId}` | 移除班级学生 |
| DELETE | `/api/teacher/classes/{classId}` | 软删除班级 |
| POST | `/api/teacher/classes/{classId}/restore` | 恢复班级 |

### 5.2 班级邀请码

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/teacher/classes/{classId}/invite-codes` | 生成或刷新班级邀请码 |

### 5.3 分组

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/teacher/classes/{classId}/groups` | 教师创建学期小组 |
| GET | `/api/teacher/classes/{classId}/groups` | 查询班级学期小组 |
| PUT | `/api/teacher/classes/{classId}/groups/{fromGroupId}/members/{studentId}/move` | 跨组移动成员 |
| POST | `/api/teacher/classes/{classId}/groups/{targetGroupId}/members/{studentId}/add` | 将未分组学生加入小组 |

### 5.4 任务

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/teacher/classes/{classId}/tasks` | 发布任务 |
| GET | `/api/teacher/classes/{classId}/tasks` | 查询任务列表 |
| GET | `/api/teacher/classes/{classId}/tasks/{taskId}` | 查询任务详情 |
| PUT | `/api/teacher/classes/{classId}/tasks/{taskId}` | 更新任务 |
| DELETE | `/api/teacher/classes/{classId}/tasks/{taskId}` | 软删除任务 |

### 5.5 Dashboard

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/teacher/classes/{classId}/dashboard` | 班级级 dashboard |
| GET | `/api/teacher/classes/{classId}/tasks/{taskId}/dashboard` | 任务级 dashboard |

### 5.6 子任务与进度

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/teacher/classes/{classId}/tasks/{taskId}/groups/{groupId}/subtasks/progress` | 查询小组与成员进度 |
| GET | `/api/teacher/classes/{classId}/tasks/{taskId}/groups/{groupId}/subtasks` | 查询子任务列表 |
| POST | `/api/teacher/classes/{classId}/tasks/{taskId}/groups/{groupId}/subtasks` | 教师创建子任务 |

### 5.7 互评与成绩汇总

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/teacher/classes/{classId}/tasks/{taskId}/groups/{groupId}/peer-reviews` | 查询组内互评列表 |
| GET | `/api/teacher/classes/{classId}/tasks/{taskId}/groups/{groupId}/score-summaries` | 查询组内成绩汇总 |

### 5.8 申诉处理

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/teacher/classes/{classId}/tasks/{taskId}/appeals` | 查询当前任务申诉列表 |
| PUT | `/api/teacher/classes/{classId}/tasks/{taskId}/appeals/{appealId}` | 处理申诉 |

### 5.9 教师操作日志

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/teacher/operation-logs` | 查询教师本人操作日志 |
| GET | `/api/teacher/operation-logs/export` | 导出教师本人操作日志 CSV |

常见过滤参数：

- `classId`

### 5.10 教师个人资料

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/teacher/profile` | 查询教师个人资料 |
| PUT | `/api/teacher/profile` | 更新教师姓名 |
| PUT | `/api/teacher/me/password` | 教师修改本人密码 |

### 5.11 课程总评

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/teacher/classes/{classId}/course-scores` | 查询班级课程总评列表 |
| POST | `/api/teacher/classes/{classId}/course-scores` | 保存/更新学生课程总评分数 |

### 5.12 教师任务评分（PRD FR-26）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/teacher/classes/{classId}/tasks/{taskId}/scores` | 教师对任务中的学生打分（写入 `teacher_scores` 表） |
| GET | `/api/teacher/classes/{classId}/tasks/{taskId}/scores` | 查看任务全部学生评分列表 |

请求体（POST）：
```json
{ "studentId": 37, "score": 92 }
```

响应（POST/GET）：
```json
{ "studentId": 37, "studentName": "测试学生", "score": 92.0, "scoredBy": 36, "scoredAt": "..." }
```

说明：同一学生对同一任务重复 POST 会更新分数（upsert）；分数写入后 `score-summaries` 的 `teacherScore` 与 `weightedTotal100` 自动反映。

---

## 六、学生模块

### 6.1 班级

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/student/classes` | 查询学生加入的所有班级列表 |
| POST | `/api/student/classes/join` | 通过邀请码加入班级 |
| GET | `/api/student/classes/{classId}` | 查询当前学生在该班的班级详情与我的小组状态 |

### 6.2 任务

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/student/tasks` | 查询学生全部任务聚合列表（跨班级） |
| GET | `/api/student/classes/{classId}/tasks` | 查询当前班级任务列表 |
| GET | `/api/student/classes/{classId}/tasks/{taskId}` | 查询当前班级任务详情 |

### 6.3 学期小组

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/student/classes/{classId}/groups` | 查询当前班级学期小组 |
| POST | `/api/student/classes/{classId}/groups` | 学生自建小组 |
| POST | `/api/student/classes/{classId}/groups/join` | 通过邀请码加入小组 |
| GET | `/api/student/classes/{classId}/groups/{groupId}/join-pending` | 组长查看待审批入组 |
| PUT | `/api/student/classes/{classId}/groups/{groupId}/members/{userId}/approve` | 组长通过申请 |
| PUT | `/api/student/classes/{classId}/groups/{groupId}/members/{userId}/reject` | 组长拒绝申请 |
| POST | `/api/student/classes/{classId}/groups/{groupId}/invite-code/refresh` | 刷新小组邀请码 |
| DELETE | `/api/student/classes/{classId}/groups/{groupId}/members/{userId}` | 组长移除组员 |

### 6.4 子任务与进度

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/student/classes/{classId}/tasks/{taskId}/groups/{groupId}/subtasks/progress` | 查询小组与个人进度 |
| GET | `/api/student/classes/{classId}/tasks/{taskId}/groups/{groupId}/subtasks` | 查询子任务列表 |
| POST | `/api/student/classes/{classId}/tasks/{taskId}/groups/{groupId}/subtasks` | 组长创建子任务 |
| POST | `/api/student/classes/{classId}/tasks/{taskId}/groups/{groupId}/subtasks/{subtaskId}/claim` | 认领子任务 |
| PUT | `/api/student/classes/{classId}/tasks/{taskId}/groups/{groupId}/subtasks/{subtaskId}/submit` | 提交子任务 |
| PUT | `/api/student/classes/{classId}/tasks/{taskId}/groups/{groupId}/subtasks/{subtaskId}/review` | 组长审批子任务 |
| PUT | `/api/student/classes/{classId}/tasks/{taskId}/groups/{groupId}/subtasks/{subtaskId}/send-back` | 组长打回已完成子任务 |
| PUT | `/api/student/classes/{classId}/tasks/{taskId}/groups/{groupId}/subtasks/{subtaskId}/reassign` | 组长重指派子任务 |

### 6.5 互评与成绩汇总

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/student/classes/{classId}/tasks/{taskId}/groups/{groupId}/peer-reviews` | 提交互评 |
| GET | `/api/student/classes/{classId}/tasks/{taskId}/groups/{groupId}/peer-reviews` | 查询组内互评列表 |
| GET | `/api/student/classes/{classId}/tasks/{taskId}/groups/{groupId}/score-summary` | 查询本人成绩汇总 |

### 6.6 学生申诉

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/student/appeals` | 查询学生全部申诉聚合列表（跨任务） |
| POST | `/api/student/tasks/{taskId}/appeals` | 发起任务申诉 |
| GET | `/api/student/tasks/{taskId}/appeals` | 查询本人在该任务下的申诉记录 |

### 6.7 个人资料

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/student/profile` | 查询学生个人资料 |
| PUT | `/api/student/profile` | 更新学生个人资料（当前仅支持修改姓名） |

### 6.8 学生操作日志

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/student/operation-logs` | 查询学生本人操作日志 |
| GET | `/api/student/operation-logs/export` | 导出学生本人操作日志 CSV |

常见过滤参数：

- `classId`
- `pathContains`

---

## 七、健康检查

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/health` | 服务健康检查 |

---

## 八、当前未落地能力

以下能力曾在旧版接口文档或规划文档中出现，但当前后端并未以正式接口落地，因此不应继续按"现有接口"使用：

- `/api/auth/forgot-password`
- `/api/student/notifications/**`
- `/api/teacher/tasks/{taskId}` 旧版教师任务详情路径
- `/api/teacher/appeals` 教师全局申诉接口
- `/api/teacher/tasks/{taskId}/report`
- `/api/teacher/classes/{classId}/course-report`
- `/api/common/uploads`
- `/ws` WebSocket 实时接口

---

## 九、文档维护规则

- 当前实现变更后，优先同步本文件、`team-trace-backend/README.md` 与 `team-trace-frontend/docs/status/frontend_data_source_matrix.md`。
- PRD、系统功能设计文档、数据库文档属于上游设计文档，不因为实现阶段的局部接口调整而直接改写。
