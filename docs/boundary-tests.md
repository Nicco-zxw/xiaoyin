# 边界测试与执行方法

本次补充验证已有业务边界；测试数据库和重启环境均与端口 3000 的演示环境隔离。

## Java / MySQL

`BoundaryIntegrationTest`：

- 1 个线程、1 个队列位：用 CountDownLatch 阻塞真实结果整理，确定性构造运行、排队和拒绝。拒绝任务持久化 EXECUTOR_REJECTED，所有未开始阶段 SKIPPED；释放闸门后前两个任务仍可成功。
- 两个任务使用不同延迟和失败配置，分别成功／失败，检查配置快照、结果与失败阶段互不影响。
- 数据库建立 123 条记录并设置相同创建时间，验证按 ID 的稳定次序、五页无重复无遗漏、尾页／空页、模板过滤总数、其他用户不可见以及分页上下界。
- 删除测试自己创建的结果文件后返回 404，已成功任务仍为成功；素材文件缺失时拒绝提交且不创建孤立任务。
- 超过 5MB、超宽图片被拒绝；原文件名包含路径时仍使用服务器随机路径。
- 非数字、分数、整数溢出的分页参数，以及缺少模板／上传文件，返回 400 / VALIDATION_ERROR。

`SimulationValidationTest`：验证 0、60000ms 端点和全部指定失败阶段；拒绝负数、超上限、缺少／未知阶段、空值。

运行全部 Java 测试（含既有 API 与流水线测试）：

```sh
docker compose -f compose.test.yaml up --abort-on-container-exit --exit-code-from tests
docker compose -f compose.test.yaml down
```

未设置 INTEGRATION_TEST=true 的普通镜像构建会跳过数据库集成测试；不能把这种构建中的跳过记录算作通过。

## 前端生命周期

`tasks.test.tsx` 使用真实 TaskProvider 和 TaskTracker，仅替换网络边界，用假时钟断言：

- 活跃列表为空时不设置持续轮询。
- 一个任务终止后不再单独查询它；最后一个任务终止后停止计时查询。
- 网络异常保留 RUNNING 状态、暂停轮询，手动恢复后成功更新。
- 用户切换取消旧 AbortSignal，旧响应不会污染新用户；卸载清理计时器、focus 和 online 订阅。

`tracker.test.ts` 另外验证部分任务查询失败不影响其他任务的终态、独立恢复，以及请求不重叠。

```sh
docker run --rm -v "$PWD/frontend:/app" -w /app node:22-bookworm-slim sh -c 'npm ci && npm test && npm run lint && npm run typecheck && npm run build'
```

## 真实 Java 进程重启

`compose.restart-test.yaml` 使用独立项目 studio-restart-tests，不发布网站端口。数据库、素材、检查点分别使用独立卷。以下 SIGKILL 只作用于该测试环境的 backend 服务：

```sh
docker compose -f compose.restart-test.yaml up -d --build --wait mysql backend frontend
docker compose -f compose.restart-test.yaml run --rm --no-deps checks before
docker compose -f compose.restart-test.yaml kill -s SIGKILL backend
docker compose -f compose.restart-test.yaml up -d --wait backend
docker compose -f compose.restart-test.yaml run --rm --no-deps checks after
docker compose -f compose.restart-test.yaml --profile tools down -v
```

before 阶段创建一个成功任务、一个素材检查中的长任务、一个排队任务并保存检查点。强制杀死进程，避免正常关闭逻辑提前标记失败。

after 阶段验证旧 Session 失效，重新登录后成功记录和图片仍在；运行／排队任务均为 FAILED / SERVICE_RESTARTED，分别保留正确阶段和 SKIPPED 状态；历史总数为 3，活跃列表为空，后续查询不会回退状态。此测试验证“重启后明确中断”，没有实现或声称断点续跑。

CI 新增 restart-boundary job 执行相同流程，并始终清理隔离测试环境。远端 CI 尚未实际运行；本地结果见 verification.md。
