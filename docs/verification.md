# 验证记录

环境：2026-10-29，Windows 上的 Docker Desktop Linux 容器。未在宿主机直接安装或执行项目依赖脚本。

| 检查 | 当前结果 |
| --- | --- |
| Compose / Compose test 配置解析 | 通过 |
| Java 编译及流水线单元测试 | 通过，2 个 JUnit 测试 |
| 独立流水线 RED → GREEN | 空实现出现顺序断言失败，补齐顺序循环后成功，并验证每阶段中断 |
| MySQL 8.4 初始化 | Flyway V1、V2 通过，六模板、三分类 |
| API 集成与边界最终轮 | 11 个通过，加流水线与模拟配置单元测试 4 个；Java 共 15 个，零失败、零跳过 |
| 前端核心与生命周期套件 | 10 个通过，包含真实 Provider 轮询停止、异常恢复、卸载取消、用户切换、部分查询失败及旧身份 401 |
| 前端 lint / TypeScript | 通过 |
| 前端生产构建 | lint、typecheck、10 测试、Next.js build 全部通过；新增测试后的镜像构建亦通过 |
| 镜像构建 | docker compose build 成功，前后端镜像均已生成 |
| 真实 HTTP | 通过 Next.js 代理和真实 Java/MySQL 的登录、CSRF、上传、异步成功／失败、隔离、历史恢复、退出 smoke 检查 |
| 浏览器 UI | 分类、注册登录回跳、PNG 上传预览、连续提交、指定阶段失败、刷新恢复、结果预览、账户弹窗、历史均已操作验证 |
| 小屏 | 390px 视口检查制作与历史页面，DOM 内容宽度未超出视口；恢复桌面视口 |
| 真实重启 | 在独立 Compose 中 SIGKILL Java，退出码 137；重启后运行／排队任务为 SERVICE_RESTARTED，成功结果、历史保持，旧 Session 失效 |
| 队列与分页 | 闸门固定队列拒绝顺序；123 条同时间记录跨页无重复遗漏，模板与用户过滤正确 |
| 文件及输入 | 文件缺失不回退终态；超限图片、异常尺寸、路径文件名、分页格式和字段缺失均验证 |

页面截图保存在 screenshots/。最终 Compose 三服务已启动，外部端口 3000；本地浏览器没有捕获到页面 JS 错误或警告。

后端配置 TCP 就绪健康检查，前端依赖其健康状态启动；独立测试 Compose 使用 studio-tests 项目名，避免覆盖演示数据库。

独立静态审查发现旧身份请求的 401 可清除新身份状态：先复现 true !== false，再加入认证代际校验、提交 AbortController 和缓存清理保护，复现检查与 Vitest 测试通过。

依赖审计：Vitest 3.2.4 的开发依赖存在告警，已更新至 5.0.5；npm 安装后的审计为 0 vulnerabilities。

已知工具提示：Flyway 随 Spring Boot 管理的版本对 MySQL 8.4 输出“新于测试版本”的提示，当前实际 V1/V2 迁移和 API MySQL 集成测试通过；不隐藏此提示。Mockito 测试自附加 agent 在 Java 21 输出未来兼容性提示，不影响本次测试。

本记录描述本地容器验证；没有声称 GitHub 托管 CI 已实际运行或完成公网部署。

## 边界补齐（2026-10-29）

新增边界测试发现非数字分页参数被通用 Exception handler 映射为 500。先运行复现用例，得到 `expected 400 but was 500`；随后为参数类型不匹配、请求字段缺失和 multipart 部件缺失添加专用 400 / VALIDATION_ERROR 处理。完整 Java 15 个测试通过，重启测试还通过真实 HTTP 验证该错误映射。

新增 CI `restart-boundary` job，使用同一套隔离 Compose 与 before／after 检查；本地完整流程已执行，远端仍未执行。复现与运行命令见 boundary-tests.md。边界测试阶段没有重启端口 3000 演示环境；其后用户明确授权重启，部署情况见下。

## 演示环境更新与重启（2026-10-29）

- 根据用户指令执行 docker compose build，重新生成当前源码的前后端镜像；随后强制重建并启动 backend、frontend，等待健康检查通过。MySQL 和原有数据卷保留。
- 重启前与重启后、运行新 smoke 之前，数据库均为 FAILED 10 条、SUCCEEDED 10 条，原 20 条任务记录保持。
- 在更新后的端口 3000 环境执行真实 HTTP smoke：注册登录、CSRF、PNG 上传、快速受理、成功／指定阶段失败、结果图片、跨用户隔离、重新登录恢复历史、退出均通过。
- 此次 smoke 已增加 page=abc、size 整数溢出及缺少上传字段的 400 / VALIDATION_ERROR 检查，确认最新异常映射修复已经部署生效。
- 后端、前端和 MySQL 均处于运行状态；内存 Session 在 Java 重启后失效，原用户需刷新并重新登录。
