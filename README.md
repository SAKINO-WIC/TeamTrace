# TeamTrace

TeamTrace（摸鱼终结者）是一个围绕小组协作过程可视化的课程项目。系统把任务拆解、成员推进、提交交接、教师评分、通知公告和申诉反馈放到同一条可追踪链路里，让协作过程不只停留在最终结果。

当前仓库是最终版合仓快照，由原来的前端、后端、项目文档三仓合并而来。

## 产品线

### 课堂协作模式

面向教师组织的课程小组任务。

- 教师创建班级、生成邀请码、管理学生和分组
- 教师发布任务、拆解子任务、查看进度
- 学生加入班级、领取/提交子任务、查看任务状态
- 支持小组互评、教师评分、课程总评
- 支持申诉、通知、系统公告、邮件验证码
- 管理员可管理账号、邀请码、邮件、公告和系统数据

### 自由协作模式 Beta

面向没有教师监管场景的自组织团队协作。

- 学生用户可创建自由协作空间
- 通过邀请码邀请成员加入
- 发起项目并拆解子任务
- 支持指定负责人、成员认领、提交结果、打回重做
- 支持任务交接节点、依赖关系、附件和协作痕迹
- 通过看板、甘特式进度视图和活动日志呈现团队推进状态

自由协作模式的核心目标不是绩效考核，而是把“谁在推进、哪里卡住、下一步交给谁”自动可视化。

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 前端 | Vue 3, Vite, Pinia, Vue Router, Axios |
| 后端 | Spring Boot 4, Spring Data JPA, Spring Validation |
| 数据库 | MySQL |
| 认证 | JWT, BCrypt |
| 邮件 | Brevo HTTP API / SMTP 配置能力 |
| 部署 | Sealos DevBox / Sealos 应用管理 / Docker |

## 仓库结构

```text
TeamTrace/
  frontend/     Vue 前端应用
  backend/      Spring Boot 后端服务
  docs/         PRD、系统设计、API、协作规范
  database/     数据库结构、迁移和回滚说明
  ops/          Sealos 运维说明
```

## 本地启动

### 1. 后端

```bash
cd backend
cp .env.example .env
# 编辑 .env，填写数据库、JWT、管理员、邮件等配置
./mvnw spring-boot:run
```

默认后端端口由 `SERVER_PORT` 控制，常用为 `8081` 或 `8082`。

### 2. 前端

```bash
cd frontend
npm install
npm run dev
```

前端会通过 Vite 代理访问后端 API。需要切换后端目标时，可参考 `frontend/package.json` 中的脚本和 `frontend/vite.config.js`。

## 环境变量

后端真实运行配置必须通过环境变量或 `.env` 注入。仓库只保留 example 文件。

关键变量包括：

```text
DB_URL
DB_USERNAME
DB_PASSWORD
JWT_SECRET
JWT_EXPIRATION_SECONDS
ADMIN_PHONE
ADMIN_PASSWORD
ADMIN_NAME
ALLOWED_ORIGINS
EMAIL_PROVIDER
BREVO_API_KEY
MAIL_FROM
MAIL_FROM_NAME
```

不要把真实 `.env`、数据库导出、生产备份、邮箱密钥或 GitHub token 提交到仓库。

## 构建

前端：

```bash
cd frontend
npm run build
```

后端：

```bash
cd backend
./mvnw package
```

Docker 构建：

```bash
cd backend
docker build -t teamtrace-backend .
```

运行镜像时必须传入数据库、JWT、管理员和 CORS 等环境变量。

## 主要文档

- `docs/product-prd-v1.1.md`：课堂协作主线 PRD
- `docs/system-design-v1.1.md`：系统设计
- `docs/api-spec-v1.2.md`：接口说明
- `docs/class-diagram.md`：类图与模块关系
- `docs/secrets-policy.md`：密钥管理规范
- `database/schema/`：数据库结构说明
- `ops/devbox-sealos-runbook.md`：Sealos 运维说明

## 安全说明

本仓库已经排除真实运行配置、构建产物、日志、上传文件和数据库备份。

如果你从旧仓库迁移而来，建议检查并轮换历史中可能暴露过的密钥，包括：

- 数据库密码
- JWT secret
- 管理员默认密码
- 邮件服务 API key

## 状态

当前版本是课程项目的最终收尾快照：

- 前端来源：`team-trace-frontend` main `c2bd62c`
- 后端来源：`team-trace-backend` main `4b15e6d`
- 文档来源：`team-trace-project` main `5d149fa`

旧三仓建议保留为 private 或 archive，用于必要时回溯。
