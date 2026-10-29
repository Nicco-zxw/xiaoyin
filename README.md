# 素作 · Material Studio

源码仓库：https://github.com/Nicco-zxw/xiaoyin 。验收 commit 以交付消息提供的完整提交 ID 为准；CI 运行结果可在仓库 Actions 查看。

Next.js + Java 三阶段异步素材制作演示。无需 AI 凭据，模板与结果使用项目内原创几何图片。当前开发和验证进度见文末，不能把设计文档等同于运行验证。

评审入口：[项目说明与设计思路](docs/project-explanation.md) · [接口契约](docs/api-contract.md) · [验证记录](docs/verification.md)。

## 一键运行

安装 Docker Desktop（Linux containers）或 Docker Engine + Compose，复制 `.env.example` 为 `.env`（可选），在项目根目录执行：

```sh
docker compose up --build
```

访问 http://localhost:3000。首次镜像和依赖下载可能较慢；Java 启动时 Flyway 自动创建表并插入六个模板、三个分类，无需手动执行 SQL。数据库与上传文件分别持久化在 `mysql-data`、`file-data` 卷中。启动后自行注册，未内置真实账户或凭据。

## 技术与目录

| 项目 | 版本 |
| --- | --- |
| Java / Maven | 21 / 3.9.9 |
| Spring Boot | 3.5.7 |
| MyBatis Spring Boot Starter | 3.0.4 |
| MySQL | 8.4 |
| Node.js | 22 |
| Next.js / React | 16.4.3 / 19.3.1 |
| TypeScript | 5.9.3 |
| TanStack Query | 5.105.0 |
| Vitest | 5.0.5 |

前端完整依赖锁定在 `frontend/package-lock.json`；Java 依赖由 Maven 父 POM 和显式版本管理。

```text
frontend/src/app/          App Router 页面和样式
frontend/src/components/   身份、任务、上传和预览交互
frontend/src/lib/          API、类型和任务跟踪器
backend/src/main/java/     Java 接口、存储、认证和异步执行
backend/src/main/resources/db/migration/  数据库结构和种子数据
backend/src/main/resources/demo/          示例封面、结果和上传图片
backend/src/test/          顺序、中断及 MySQL API 集成测试
docs/                     实现方案、接口契约和执行计划
.github/workflows/ci.yml   push/PR 检查
```

Next.js 的 `/api/*` rewrite 转发至 Java；浏览器不直连 MySQL。Java 处理全部业务、身份、文件和持久化。MySQL 不向宿主机开放端口。

## 配置

| 配置 | 默认／作用 |
| --- | --- |
| PORT | 3000，网站宿主机端口 |
| DB_PASSWORD / DB_ROOT_PASSWORD | 本地非敏感演示密码，见 .env.example |
| SIMULATION_ENABLED | true，允许任务提交开发模拟参数 |
| COOKIE_SECURE | false，本地 HTTP；HTTPS 部署改 true |
| WORKER_THREADS / QUEUE_CAPACITY | 2 / 50，有界执行器 |
| DB_URL / DB_USER | Compose 中连接 mysql:3306/studio，用户 studio |
| STORAGE_PATH | 后端 /data/files |
| JAVA_API_URL | 前端构建时 rewrite 目的地，默认 http://backend:8080 |

修改已有数据库卷的密码需同步修改数据库用户，单改环境变量不会重置已初始化 MySQL。生产关闭模拟控制，使用 HTTPS 和独立密码。本项目以单实例演示交付，不包含生产运维平台。

## 构建和测试（容器）

前端：

```sh
docker run --rm -v "$PWD/frontend:/app" -w /app node:22-bookworm-slim sh -c 'npm ci && npm run lint && npm run typecheck && npm test && npm run build'
```

Java 单元测试与构建：

```sh
docker run --rm -v "$PWD/backend:/app" -v material-maven:/root/.m2 -w /app maven:3.9.9-eclipse-temurin-21 mvn -s maven-settings.xml -B verify
```

集成测试使用真实 MySQL，设置 `INTEGRATION_TEST=true`，并提供 DB_URL、DB_USER、DB_PASSWORD、STORAGE_PATH；CI 使用独立 MySQL service。未设置该变量时集成测试显式跳过，不代表已通过。容器验证入口见 `compose.test.yaml`。

```sh
docker compose -f compose.test.yaml up --build --abort-on-container-exit --exit-code-from tests
docker compose -f compose.test.yaml down
```

整站启动后运行真实 HTTP 联调（自动创建隔离的测试账户和记录）：

```sh
docker compose run --rm smoke
```

队列满、123 条历史分页、多任务配置隔离、请求取消和真实 SIGKILL 重启的完整测试说明见 [边界测试](docs/boundary-tests.md)。重启测试使用单独的 Compose 项目、数据库和文件卷，不操作演示环境。

`compose.test.yaml` 固定默认项目名为 studio-tests，与演示环境数据库隔离。测试服务结束后，执行对应配置的 down 清理测试容器。

以上挂载命令为 POSIX shell 写法；PowerShell 将 `$PWD` 替换为 `${PWD}`。Compose 命令跨平台一致。无需在宿主机安装 Java、Maven 或 Node。

## 业务演示

1. 注册用户名（3–32 位字母、数字、下划线）和密码（8–64 位，UTF-8 不超过 72 字节），登录。
2. 在列表切换分类，进入模板；不同模板要求 1、2 或 3 张图片。
3. 制作页底部可打开本地示例 PNG，保存后上传；多素材模板应选择不同上传记录。支持预览、替换、移除，服务端再次校验。
4. 点击开始制作，接口立即受理，观察素材检查 → 模拟生成 → 结果整理，成功后点击卡片预览。
5. 展开“开发演示”，勾选自定义配置，把生成延迟调为 20000ms，连续提交多个任务；页面保持可用，任务各自更新。
6. 分别指定三个失败阶段，观察该阶段失败与后续停止；无随机失败，可重复触发。
7. 刷新、切换页面或关闭浏览器后回来，重新读取 Java 历史及活跃任务；关闭页面不会停止 Java 工作。
8. 处理中关闭网络，卡片保留上次状态并报告查询异常；恢复网络或点击恢复查询后继续。
9. A 退出、B 注册登录，B 看不到 A 的任务与素材；切回 A 可恢复记录。
10. 账户弹窗修改密码需当前密码，成功后全部会话失效，重新登录继续查看历史。

## 状态和执行边界

`QUEUED → RUNNING → SUCCEEDED / FAILED`，当前阶段单独保存。每个阶段开始和结束分别写 `task_step_runs`。失败保存阶段、错误码、原因，后续阶段 SKIPPED。成功结果复制内置 SVG 至任务专属文件，标明模拟结果，无真实生成或图像合成。

短事务创建任务和关联，事务完成后提交线程池；Worker 不占用长事务等待。队列满时把已受理任务标为 FAILED / EXECUTOR_REJECTED。查询只读，不能推进阶段。终态不可回退。

前端按任务 ID 合并状态，查询异常与任务失败分开保存；终态停止跟踪，没有活跃任务时停止计时轮询，重新聚焦／联网可重新核对。退出时取消请求、清空缓存和跟踪器，旧响应不得写入新用户数据。

主要接口、示例请求／响应、错误码和联调场景见 [接口契约](docs/api-contract.md)，架构见 [实现方案](docs/implementation-design.md)。

## 身份和个人记录

用户名归一为小写，BCrypt 保存密码，Java Session + HttpOnly Cookie，不将密码或登录 Token 放入 localStorage。写接口要求 CSRF Token，登录轮换会话 ID，退出销毁会话。素材、任务和结果按用户归属检查，不接受客户端传入用户 ID。

服务端重启后 Session 丢失，需要重新登录；MySQL 历史保持。启动时将遗留未完成任务标为 SERVICE_RESTARTED，说明服务重启中断；不自动续跑。

## 部署与回滚

1. 停止写入或安排维护窗口，备份 MySQL 和文件卷，保留配套镜像版本。
2. 修改配置，构建镜像并执行 `docker compose up -d --build`。
3. 查看 `docker compose logs backend` 的迁移日志及健康信息，验证登录、提交和结果。
4. 回滚时使用上个版本镜像；若迁移不向后兼容，需恢复匹配的数据库和文件备份。
5. `docker compose down` 保留卷。不要在需要保留记录时使用 `down -v`。

## 来源与 AI 工作流

项目从空仓库创建，未复用业务脚手架或第三方网站源码。使用的开源框架见依赖文件；封面 SVG 和示例 PNG 为本项目程序绘制的原创几何素材，无外部素材授权依赖。页面布局参考用户附件，未复用附件品牌、照片或题外付费功能。

使用 Codex 辅助开发：先将 R-01–R-14 拆为身份、模板上传、后台状态机、前端跟踪和交付模块；把设计及接口契约存入 docs 作为上下文；生成代码后在容器执行测试、类型检查和构建，根据失败修正。独立审查发现旧用户请求的 401 可能清除新用户登录状态，先用延迟响应断言复现，再增加身份代际、请求取消和缓存保护；回归测试通过。不提交私人聊天或账号信息。

## 完成状态与已知限制

R-01–R-14 的核心业务与工程交付已实现。容器内 Java 15 个测试、前端 10 个测试、lint、类型检查、生产构建以及整站 HTTP smoke 通过；Compose 镜像构建和启动通过。已补齐队列拒绝、123 条分页、文件缺失、输入边界和真实 SIGKILL 重启检查；同时修复非法参数误报 500。浏览器已检查主要页面与真实注册、上传、制作、失败和刷新恢复交互。详细证据与截图见 [验证记录](docs/verification.md)。首次功能交付的 [GitHub Actions](https://github.com/Nicco-zxw/xiaoyin/actions/runs/40827771643) 已全部通过，包含前端、后端和重启检查；后续提交状态以 Actions 为准。未做公网部署。

首轮开发和验证约 45 分钟，边界测试补齐另约 20 分钟，包含镜像、依赖下载和容器启动等待；不是人工独立开发工时统计。数据库迁移有 Flyway 对 MySQL 8.4 的版本验证范围提示，实际 MySQL 8.4 迁移与 API 测试已通过，提示详见验证记录。

不实现真实 AI、支付、订阅、复杂模板后台、邮件找回、第三方账号、自动重试、断点续跑、提交幂等、多实例或服务重启恢复执行。未引用上传文件暂不自动清理。提交网络中断可能已创建任务，客户端提示先查历史，不自动重发。单实例内存 Session，密码修改后的会话失效不支持集群。未提供公网部署。
