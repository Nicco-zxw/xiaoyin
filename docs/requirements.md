# 需求与实现对应

| 需求 | 实现入口 | 验证 |
| --- | --- | --- |
| R-01 技术职责 | frontend、backend、Store.java、compose.yaml | 两端构建、MySQL API 测试 |
| R-02 模板与分类 | V2__templates.sql、CatalogController、首页 | 浏览器分类切换及 HTTP 六模板检查 |
| R-03 上传校验 | FileStorage、UploadSlot、详情页 | 无效图片／数量测试、浏览器上传预览 |
| R-04 快速受理 | TaskService.create、TransactionTemplate、ThreadPoolExecutor | 受理耗时及非终态断言、HTTP smoke |
| R-05 三阶段状态 | Pipeline、TaskService.run、task_step_runs | 顺序测试、结果图片与终态查询 |
| R-06 指定失败／延迟 | Simulation、开发折叠区 | 每阶段失败并验证后续 SKIPPED |
| R-07 请求与任务状态 | api.ts、tracker.ts、制作页 | 查询失败保留任务状态测试 |
| R-08 多任务生命周期 | TaskTracker、TaskProvider、身份代际、AbortController | 多任务、终态、取消及旧身份响应测试 |
| R-09 历史与刷新 | /tasks、/tasks/active、历史和详情页 | 浏览器刷新恢复、HTTP 新会话恢复 |
| R-10 用户身份 | AuthController、SecurityConfig、各归属查询 | 注册登录、跨用户 404、密码修改失效测试 |
| R-11 视觉可用性 | globals.css、dialog、语义标签 | 桌面浏览器交互、小屏宽度检查、screenshots |
| R-12 CI与测试 | .github/workflows/ci.yml、两端测试 | 本地容器执行通过，远端 CI 未执行 |
| R-13 交付说明 | README、api-contract、implementation-design、verification | 完整源码、锁文件、SQL和文档均在本地目录 |
| R-14 容器 | 两端 Dockerfile、Compose、环境示例 | 镜像构建、三服务运行、整站 smoke |

所有“通过”对应本地容器或浏览器记录，不表示公网部署或 GitHub 托管流水线已运行。重启续跑、受理幂等、重试、清理无引用素材等限制见 README。
