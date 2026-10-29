# 素材制作网站 Implementation Plan

执行方式：当前会话直接实施。用户已授权基于方案开始开发。

**Goal:** 完成可容器运行的注册登录、上传、三阶段任务、历史与多任务前端。

**Architecture:** 同源 Next.js 转发 Java，MyBatis/MySQL 持久化；事务后有界线程池执行，阶段独立落库。

**Spec:** ../../implementation-design.md 与 ../../api-contract.md

## Global Constraints
- Java 执行业务与持久化；不接真实 AI。
- Next.js App Router、React、TypeScript；MySQL、MyBatis。
- 运行和验证使用容器；不直接在宿主机执行项目脚本。

## Review Focus
- 提交结果未知不得自动重发，查询异常不冒充执行失败。
- 事务未提交不能被 worker 读取；队列拒绝不得永久等待。
- 用户切换须取消旧请求，结果与素材校验归属。
- 阶段失败停止后续阶段，终态不回退。
- 服务重启遗留任务明确失败，不隐含断点恢复。

## Task 1：后台工程与任务核心
- [x] 创建 backend/pom.xml、application.yml、Flyway migration、Dockerfile。
- [x] 先写 PipelineTest 验证顺序与各阶段中断，在容器执行失败测试。
- [x] 实现 Pipeline、TaskService、Mapper，短事务受理、提交后执行。
- [x] 容器执行 Java 测试与 package。

## Task 2：身份、素材与接口
- [x] 实现 SecurityConfig、AuthController、文件与模板接口。
- [x] 加入身份隔离、上传内容校验、持久化集成测试。
- [x] 使用 MySQL 容器验证迁移与接口。

## Task 3：前端闭环
- [x] 创建 frontend 配置与 App Router 页面。
- [x] 先写任务存储测试：多任务独立、查询错误保留、终态停止。
- [x] 实现 API 客户端、身份上下文、任务跟踪及上传组件。
- [x] 实现深色模板列表、双栏制作页、历史、账户弹窗。
- [x] 容器执行 lint、typecheck、test、build。

## Task 4：交付验证
- [x] Compose、CI、环境示例、素材来源与 README。
- [x] 完整容器启动，演示注册、上传、成功与失败、多任务及恢复。
- [x] 记录已验证与未验证项，同步接口文档。
