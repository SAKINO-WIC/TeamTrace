# Sealos DevBox / 云数据库运行手册（简版）

## 1. 环境说明

- 开发环境：Sealos DevBox
- 数据库：Sealos 云 MySQL
- 代码托管：GitHub（前端、后端、项目三仓）
- 当前云后端端口：`8088`
- 当前云数据库端口：`3306`

## 2. 日常启动建议

### 前端

```bash
cd frontend
npm install
npm run dev
```

如需代理到云后端，请在前端 `.env` / `.env.local` 中显式设置：

```bash
VITE_API_PROXY_TARGET=http://<your-host>:8088
```
### 后端

```bash
cd backend
./mvnw spring-boot:run
```

约定：

- 本地默认端口基线：`8082`
- 当前云开发环境端口基线：`8088`
- 云数据库 MySQL 端口：`3306`

## 3. 配置管理

- 真实配置放环境变量，不放 Git。
- 仓库中仅保留配置模板（如 `.env.example`）。
- 禁止将数据库密码写入公开文档与代码仓库。

更详细的密钥管理要求见：`docs/secrets-policy.md`。

## 4. 数据库操作规范

- 结构变更必须先写 SQL 脚本再执行。
- 执行后立即提交 migration 与 rollback 文件。
- 不允许仅在控制台手工改库而不留版本记录。

## 5. 应急处理

### 发现错误提交到主分支

- 使用 `git revert` 回滚，保留历史。

### DevBox 资源重建

1. 重新克隆三仓
2. 按 README 重装依赖
3. 导入数据库基线与迁移脚本

## 6. 备份建议

- 文档与脚本：依赖 GitHub 版本化
- 数据库：按周期做逻辑备份（`mysqldump`）并安全存储
