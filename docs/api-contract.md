# 前后端联调接口契约

更新日期：2026-10-29。状态：已实现的联调契约，与 implementation-design.md 配套；运行验证范围见 verification.md，实现变更须同步更新本文。

## 1. 通用约定

- 基址 `/api`，浏览器同源请求，由 Next.js 转发至 Java。
- JSON 使用 camelCase、UTF-8；ID 一律为字符串，时间为 UTC ISO 8601。
- 成功直接返回业务对象；删除会话和修改密码成功返回 204，无响应体。
- Cookie 会话认证，fetch 使用 credentials: "same-origin"。
- 写请求携带 `X-CSRF-TOKEN`，Token 由 CSRF 接口获取；登录及退出后重新获取。
- 图片接口返回二进制；上传为 multipart/form-data，不手动设置 multipart Content-Type。
- 分页 page 从 0 开始，size 默认 20，最大 100；历史按 createdAt DESC、id DESC 排序。
- 私有任务、素材和结果不存在或不属于当前用户时均返回 404。
- 任务状态仅来自后端；HTTP 请求失败不等于任务 FAILED。
- 非数字／整数溢出的分页参数、缺少必要的上传参数，统一返回 400 / VALIDATION_ERROR，不归为服务端 500。

统一错误：

```json
{
  "code": "VALIDATION_ERROR",
  "message": "请检查提交内容",
  "fieldErrors": {},
  "requestId": "request-uuid"
}
```

| HTTP | 错误码示例 | 含义 |
| --- | --- | --- |
| 400 | VALIDATION_ERROR / INVALID_SIMULATION | 字段或参数错误 |
| 401 | UNAUTHENTICATED / INVALID_CREDENTIALS | 未登录或凭据错误 |
| 403 | CSRF_INVALID / SIMULATION_DISABLED | CSRF 或开发控制禁止 |
| 404 | TEMPLATE_NOT_FOUND / ASSET_NOT_FOUND / TASK_NOT_FOUND | 不存在或无权访问 |
| 409 | USERNAME_TAKEN / RESULT_NOT_READY | 冲突或结果尚未就绪 |
| 413 | FILE_TOO_LARGE | 文件超过限制 |
| 415 | UNSUPPORTED_IMAGE_TYPE | 非支持的实际图片类型 |
| 500 | INTERNAL_ERROR | 未预期服务端错误，不返回堆栈 |

## 2. 注册与会话

### GET /auth/csrf

无需登录。200：

```json
{"token":"opaque-token","headerName":"X-CSRF-TOKEN"}
```

### POST /auth/register

```json
{"username":"demo_user","password":"example-password","confirmPassword":"example-password"}
```

用户名 3–32 位 ASCII 字母、数字、下划线，转小写后唯一；密码 8–64 位，UTF-8 不超过 72 字节，不裁剪密码。201 返回用户对象，不自动登录，前端跳转登录页。

### POST /auth/login

```json
{"username":"demo_user","password":"example-password"}
```

200 设置 Session Cookie 并返回用户对象；用户名不存在和密码错误使用相同错误信息。

### GET /auth/me

200 用户对象；无有效会话返回 401。

```json
{"id":"user-uuid","username":"demo_user","createdAt":"2026-10-29T02:00:00Z"}
```

### POST /auth/logout

登录且携带 CSRF Token，204。前端取消请求并清空用户缓存。

### PUT /auth/password

```json
{"currentPassword":"example-password","newPassword":"another-password","confirmPassword":"another-password"}
```

204，使该用户所有现有会话失效，前端返回登录页；当前密码错误返回 400 / CURRENT_PASSWORD_INVALID。

## 3. 分类与模板

分类和模板可公开浏览，其余业务接口需要登录。

### GET /categories

```json
{"items":[{"id":"creative","name":"创意图片","sortOrder":1}]}
```

### GET /templates?categoryId=creative

省略 categoryId 表示全部；未知分类返回空 items。200：

```json
{
  "items":[{
    "id":"demo-postcard",
    "title":"旅行明信片",
    "categoryId":"creative",
    "coverUrl":"/api/demo-files/postcard-cover.jpg",
    "description":"上传图片，模拟生成一张明信片",
    "requiredImageCount":2,
    "acceptedMimeTypes":["image/jpeg","image/png"],
    "maxFileSizeBytes":5242880
  }]
}
```

### GET /templates/{id}

200 返回上述单个模板对象；不存在返回 404。GET /demo-files/{name} 仅按服务端白名单提供随项目打包的公开示例图片，不接受任意文件路径。

## 4. 素材

### POST /assets

multipart 字段：templateId（字符串）、file（一个文件）。每次上传一个槽位。

201：

```json
{
  "id":"asset-uuid",
  "previewUrl":"/api/assets/asset-uuid/content",
  "mimeType":"image/png",
  "sizeBytes":123456,
  "width":1200,
  "height":800,
  "createdAt":"2026-10-29T02:01:00Z"
}
```

后端校验模板限制及实际解码类型；图片单边建议不超过 8192 像素，总像素不超过 4000 万，最终常量在实现与本文同步。替换／移除上传位只改变前端选择，不删除已上传文件；未引用文件清理不在首版范围。

### GET /assets/{id}/content

校验当前用户归属后返回图片，Content-Type 为实际类型，私有响应 Cache-Control: private, no-store。

## 5. 任务枚举与字段

| 字段 | 值 |
| --- | --- |
| status | QUEUED、RUNNING、SUCCEEDED、FAILED |
| currentStage | null、MATERIAL_CHECK、MOCK_GENERATION、RESULT_PACKAGING |
| steps[].status | PENDING、RUNNING、SUCCEEDED、FAILED、SKIPPED |

QUEUED 的 currentStage 为 null；执行失败保留失败阶段；成功保留 RESULT_PACKAGING。未执行的后续阶段在失败后置为 SKIPPED。调度失败或启动中断可能没有失败阶段，failure.stage 允许 null。

任务完整对象：

```json
{
  "id":"task-uuid",
  "templateId":"demo-postcard",
  "templateTitle":"旅行明信片",
  "status":"RUNNING",
  "currentStage":"MOCK_GENERATION",
  "steps":[
    {"stage":"MATERIAL_CHECK","status":"SUCCEEDED","startedAt":"2026-10-29T02:02:00Z","finishedAt":"2026-10-29T02:02:01Z"},
    {"stage":"MOCK_GENERATION","status":"RUNNING","startedAt":"2026-10-29T02:02:01Z","finishedAt":null},
    {"stage":"RESULT_PACKAGING","status":"PENDING","startedAt":null,"finishedAt":null}
  ],
  "failure":null,
  "resultUrl":null,
  "createdAt":"2026-10-29T02:02:00Z",
  "startedAt":"2026-10-29T02:02:00Z",
  "finishedAt":null
}
```

失败时 failure 示例：

```json
{"stage":"MOCK_GENERATION","code":"SIMULATED_STAGE_FAILURE","message":"模拟生成阶段按配置失败"}
```

成功时 resultUrl 为 `/api/tasks/{id}/result`；仅 SUCCEEDED 返回非空结果地址。

## 6. 创建与查询任务

### POST /tasks

```json
{
  "templateId":"demo-postcard",
  "assetIds":["asset-uuid-1","asset-uuid-2"],
  "simulation":{
    "stageDelayMs":{"MATERIAL_CHECK":1000,"MOCK_GENERATION":6000,"RESULT_PACKAGING":1000},
    "failAtStage":null
  }
}
```

assetIds 顺序对应槽位，数量必须准确，禁止重复，均须属于当前用户并符合模板要求。无 simulation 时使用后端默认配置。

simulation 仅开发配置开启时允许；各阶段耗时为 0–60000 整数毫秒，failAtStage 为三种阶段之一或 null，不接受未知字段。生产传入 simulation 返回 403。

202 返回任务完整对象并设置 Location: /api/tasks/{id}。任务可能已经开始或结束，客户端按实际 status 处理，不假设必为 QUEUED。队列拒绝时仍返回已受理的任务对象，其状态为 FAILED，failure.code 为 EXECUTOR_REJECTED。

同素材可连续创建多个任务；不提供受理幂等保证，前端仅在提交期间禁止重复点击。网络断开时提交结果可能未知，应提示刷新历史确认，不自动重发。

### GET /tasks/{id}

200 返回任务完整对象。接口只读取，不推进阶段；终态不回退。

### GET /tasks?templateId=demo-postcard&page=0&size=20

只返回当前用户记录；templateId 可省略。200：

```json
{"items":[],"page":0,"size":20,"totalElements":0,"totalPages":0}
```

items 为任务完整对象数组。

### GET /tasks/active

```json
{"items":[]}
```

返回当前用户全部 QUEUED/RUNNING 任务，不分页，按创建时间升序。前端用一个活跃查询驱动轮询；曾活跃但此次消失的任务单独获取终态。无活跃任务时停止计时查询。

### GET /tasks/{id}/result

成功返回图片二进制；未成功返回 409 / RESULT_NOT_READY；非本人或不存在返回 404。文件意外丢失返回 404 / RESULT_FILE_MISSING，不把已成功任务改回处理中。Cache-Control: private, no-store。

## 7. 联调场景

1. 获取 CSRF → 注册 → 登录 → 重新获取 CSRF → me。
2. 查询分类和模板，按模板要求逐个上传，使用素材 ID 创建任务。
3. 立即收到 202，观察等待及三个独立阶段，最终预览结果。
4. failAtStage 依次指定各阶段，验证后续 SKIPPED、原因持久化。
5. 调大延迟，连续创建任务并刷新页面，验证恢复和独立更新。
6. 暂停网络，验证保留已知任务信息并显示查询异常；恢复后继续获取。
7. A 用户创建后退出，B 用户不能查询 A 的素材、任务、结果；旧请求不污染 B 页面。
8. 修改密码使旧会话失效，重新登录后历史仍在。
9. 后端重启，遗留任务标记 SERVICE_RESTARTED，重新登录可查历史；不自动续跑。

前端不得将网络错误、401 或 5xx 转换为任务 FAILED。401 触发会话失效流程；其他查询错误提供恢复按钮。业务失败仅以任务对象 status=FAILED 为准。
