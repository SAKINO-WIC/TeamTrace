# TeamTrace 密钥与环境变量管理规范 v1.0

## 目标

- 防止密码、Token、私钥等敏感信息进入 Git 历史。
- 让本地/DevBox/线上环境配置可重复、可迁移。
- 出现泄露时可快速止损（轮换 + 回滚）。

## 1. 绝对禁止提交到 GitHub 的内容

- 数据库真实密码、连接串中包含的真实口令
- JWT/加密密钥、第三方 API Key、Webhook Secret
- 任何私钥文件（SSH Key、TLS Key 等）
- `.env` 真实配置文件（以及任何包含真实密钥的配置文件）
- 生产/真实业务数据导出（含隐私数据）

> 即使仓库是私有仓库，也不允许提交。因为 Git 历史会长期存在、成员会变动、权限可能外泄。

## 2. 推荐做法（标准落地）

### 2.1 代码仓库只保留“模板”

- 提交 `.env.example`（只含占位符）
- `.gitignore` 忽略 `.env` / `.env.*`，仅放行 `.env.example`
- 代码中通过环境变量读取配置（后端 `application.properties` / 前端 `.env.*`）

### 2.2 DevBox / 本地的真实配置存放位置

- 在仓库根目录创建 `.env`（本地/DevBox 私有文件，不提交）
- 使用 `source .env` 加载环境变量启动服务

### 2.3 发现误提交后的止损流程

1. 立刻轮换泄露的密码/密钥（数据库、Token、第三方 Key 等）
2. 修复仓库：将敏感值替换为占位符并提交
3. 通知团队成员重新拉取并更新本地 `.env`

> 不建议在协作仓库上强行改写历史（rewrite history）。优先轮换密钥止损。

## 3. 后端（Spring Boot）约定

建议使用环境变量命名：

- `DB_URL`
- `DB_USERNAME`
- `DB_PASSWORD`
- `SERVER_PORT`（可选）

并在 `application.properties` 中以 `${ENV_NAME}` 的形式引用。

## 4. 前端（Vite）约定

- 仅提交 `.env.example`（不含真实值）
- 若使用 Vite 环境变量，遵循 `VITE_` 前缀（例如 `VITE_API_BASE_URL`）

## 5. 审核清单（PR 必查）

- [ ] 是否出现 `password=` / `secret=` / `token=` 等可疑字段
- [ ] 是否新增了 `.env` 或包含敏感信息的配置文件
- [ ] `.env.example` 是否只含占位符
- [ ] 文档中是否写入了真实口令或可直接访问的敏感链接
