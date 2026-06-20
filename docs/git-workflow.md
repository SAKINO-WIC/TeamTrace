# TeamTrace Git 协作规范 v1.0

## 1. 分支策略

- 受保护分支：`main`
- 开发分支：从 `main` 拉出，按用途命名

命名规则：

- `feat/<module>-<short-desc>`
- `fix/<module>-<short-desc>`
- `docs/<topic>`
- `refactor/<module>-<short-desc>`

模块建议：

- `frontend` / `backend` / `db` / `docs` / `ops`

## 2. 日常开发流程

1. 同步主分支：`git pull --rebase origin main`
2. 创建分支：`git checkout -b feat/frontend-home-wireframe`
3. 小步提交：一次提交只做一件事
4. 推送分支：`git push -u origin <branch>`
5. 发起 PR 到 `main`
6. 评审通过并 CI 通过后合并
7. 删除已合并分支

## 3. 提交信息规范

推荐格式：

```text
type(scope): summary
```

示例：

- `feat(frontend): 搭建首页低保真演示模型`
- `fix(backend): 修复健康检查接口返回字段`
- `docs(db): 补充迁移与回滚规范`

## 4. Pull Request 最低要求

- 明确背景：为什么改
- 列出改动点：改了哪些文件/模块
- 验证方式：如何验证改动正确
- 风险说明：是否影响兼容性
- UI 改动附截图
- API 改动同步接口文档

## 5. 冲突处理

- 冲突时先拉取主分支并 rebase。
- 手工解决冲突后重新自测再推送。
- 禁止为省事直接覆盖他人改动。

## 6. 回滚策略

### 已推送并已合并到主分支

使用 `git revert <commit>` 回滚（推荐）。

### 仅本地/功能分支整理历史

可使用 `git reset` 或 `git rebase`，但不要改写他人依赖的公共历史。

## 7. 分支保护建议（GitHub 设置）

- Require pull request before merging
- Require at least 1 approval
- Dismiss stale approvals
- Require status checks to pass
- Restrict direct pushes to `main`
