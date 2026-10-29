# 边界测试补齐

目标：覆盖此前未验证的队列容量、进程重启、分页和前端查询生命周期，不扩展产品功能。

执行：本会话直接补充已有行为测试；发现缺陷时先保留失败测试再修正代码。所有执行在容器中，测试数据库独立于演示环境。

- [x] 抽取共享 API 测试夹具，加入有界队列拒绝、并发配置隔离、123 条历史分页、输入和文件缺失检查。
- [x] 加入 TaskProvider 假时钟测试，覆盖终态／空队列停止轮询、请求异常恢复、卸载与用户切换取消旧请求。
- [x] 加入独立 Compose 重启验收：成功任务、运行中任务、排队任务 → SIGKILL Java → 启动 → 新会话恢复记录及中断原因。
- [x] 将重启验收加入 CI，完整运行 Java、前端 lint/typecheck/test/build、容器重启检查。
- [x] 更新 README、验证记录和实际未验证范围。

文件：backend/src/test/java/studio/ApiTestSupport.java、BoundaryIntegrationTest.java、SimulationValidationTest.java；frontend/src/components/tasks.test.tsx、src/lib/tracker.test.ts；compose.restart-test.yaml、infra/restart-smoke.mjs、.github/workflows/ci.yml。
