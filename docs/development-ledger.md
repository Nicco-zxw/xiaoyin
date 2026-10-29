# 开发记录

## 2026-10-29
- 用户授权按已有方案开发，在当前空仓库直接执行；仓库尚无首个 commit，未创建基于不存在 HEAD 的 worktree，保留原有 docs。
- 实施步骤见 superpowers/plans/2026-10-29-material-studio.md。
- Docker Desktop 初始未启动，已启动；所有项目安装和执行通过容器。
- 任务跟踪器先以空实现运行容器断言，观测到 0 !== 1 的预期失败；实现后多任务、查询异常与终态不回退断言通过。完整 Vitest 套件仍待依赖安装后运行。
- Ruling：MySQL 集成测试由 Compose／CI service 提供，不使用 Testcontainers，避免容器内嵌套 Docker；不降低真实 MySQL 验证范围。
- Ruling：沿用用户批准的执行授权在本会话直接开发，不增加实施方式确认或创建新聊天。
- 版本查询：Next.js 官方安装文档要求 Node >=20.9，React 与 Next.js 版本从 npm registry 核对并固定；MyBatis Starter 3.x 配合 Spring Boot 3.x。
- 初次依赖审计发现 Vitest 3.2.4 的开发依赖告警，计划更新后重新验证；生产依赖审计为零告警。

## 最终验证
- Java：2 个流水线测试 + 5 个真实 MySQL 8.4 API 集成测试，全部通过，verify 打包成功。
- 前端：npm ci、lint、typecheck、4 个 Vitest 测试、生产 build 全部通过；Vitest 已更新 5.0.5，审计零告警。
- 独立审查发现的旧身份 401 竞态已按 RED → GREEN 修复，加入请求代际、提交取消和缓存清理保护。
- docker compose build 两镜像成功，Compose 三服务启动；最终镜像上 HTTP smoke 全链路成功。
- 浏览器完成分类、注册、登录回跳、上传、连续提交、指定失败、刷新恢复、结果弹窗、账户弹窗与历史检查。小屏内容未横向溢出，截图保存在 docs/screenshots。
- Ruling：交付本地项目目录，不自动创建远端仓库、推送或发布；无需验收 commit。
- 构建过程中 Windows bind mount 上并行更新依赖出现文件重命名 EACCES；改用 Linux named volume 并执行干净 npm ci 后全套验证通过。
- Docker 镜像默认镜像源下载较慢，部分基础镜像通过 AWS ECR 公共官方镜像副本完成拉取；交付配置仍使用标准官方镜像名。
- 保留 Flyway 关于 MySQL 8.4 超出其验证范围的提示，实际迁移和集成测试通过，未隐藏提示。

无未修复的重要审查发现。范围外限制已列于 README，未声称远端 CI 或公网部署已验证。

## 边界测试补齐
- Java 新增队列闸门测试、并发配置快照、123 条同时间历史记录分页、文件缺失和上传限制、模拟参数端点；前端新增真实 Provider 假时钟与部分查询异常测试。
- 参数格式测试先复现 page=abc 返回 500；原因是框架参数绑定异常落入通用异常处理器。增加针对绑定／multipart 缺失的 400 处理后，完整 Java 15 个测试通过，前端 10 个测试与 lint/typecheck/build 通过。
- 独立 studio-restart-tests 环境创建成功、运行、排队任务；SIGKILL 后退出码 137；重启后中断状态、阶段、结果文件、历史、会话失效验证通过。流程加入 CI。
- Ruling：重启验收仅使用独立项目和文件卷，不重启现有演示服务，避免中断用户在页面创建的任务；因此当前演示容器需下一次重新构建部署才包含此次 400 映射修复。
- CI 仅配置和本地等价验证，远端执行仍未验证。

## 用户授权更新当前服务
- 重新构建前后端镜像，强制重建 backend、frontend 并等待就绪，保留 MySQL 和全部文件卷。
- 更新前后已有 FAILED 10、SUCCEEDED 10 条记录保持，完整 HTTP smoke 再次成功，含新增非法参数与缺失上传字段 400 检查；此次源码修复已在当前演示环境生效。
- 上一条“不重启演示服务”的临时决定由用户本次明确的重启指令取代。
