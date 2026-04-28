# todo-43 回放外部接口方案

## 任务目标

- [x] 阅读 `plan/007-回放系统外部接口/007-draft.md`，确认新增需求边界。
- [x] 对照 `plan/005-回放系统设计/005-final.md` 和现有 `replay-server` 代码，识别接口替换对现有链路的影响。
- [x] 先整理需要用户确认的不明确问题，不直接生成定稿。
- [x] 用户确认裁定后，生成 `plan/007-回放系统外部接口/007-final.md`。
- [x] 回写本任务单的执行结果和评审结论。

## 当前约束

- 本轮只做设计评审和方案收敛，不改生产代码。
- 方案应以最小改动为原则，只移除回放系统对 `broadcast-{instanceId}` 的控制订阅和元信息通知。
- 现有回放状态机、调度、TDengine 查询、跳转和发布逻辑默认保持不变。

## 执行记录

- 已读取 007 草案。
- 已确认 005 设计仍以 `broadcast-{instanceId}` 承载回放控制消息，并包含元信息通知。
- 已确认当前仓库存在 `replay-server` 模块，且已有回放控制、生命周期、动态订阅和元信息服务实现。
- 已确认 `ReplayLifecycleService#createReplay` 当前会调用 `ReplayTopicSubscriptionManager#subscribe` 并发布回放元信息。
- 已确认 `ReplayControlService` 已承载启动、暂停、继续、倍速、时间跳转的核心业务逻辑，新外部接口应优先复用该服务。
- 已确认 `replay-server` 当前未引入 `spring-boot-starter-web`，也没有现成 Controller。

## 待讨论问题

- 外部接口是否明确采用 HTTP REST；如采用 REST，需要新增 Web starter。
- 五个接口是否继续复用 `messageType=1200/messageCode=1/2/3/4/5` 的内部语义，还是 Controller 直接调用服务方法。
- 前端返回结构是否使用简单成功/失败 JSON，还是需要统一响应模型。
- `broadcast-{instanceId}` 被取消后，`ReplayTopicSubscriptionManager` 是否保留为空实现兼容旧测试，还是整体删除动态订阅类。
- 元信息通知取消后，前端是否需要通过新查询接口获取开始时间、结束时间和持续时间；当前草案只要求五个控制接口。

## 用户裁定

- 外部接口采用 HTTP REST。
- 5 个控制接口路径采用 `/api/replay/instances/{instanceId}/start|pause|resume|rate|jump`。
- 回放任务创建仍只来自 `broadcast-global` 的 `messageType=1,messageCode=0`。
- 额外新增 `GET /api/replay/instances/{instanceId}` 返回回放元信息。
- HTTP 控制入口复用现有 `ReplayControlService`。

## 评审结论

- 已生成 `plan/007-回放系统外部接口/007-final.md`。
- 定稿方案明确 007 是对 005 中实例级控制 topic 和元信息通知的修订。
- 后续实现应先补失败测试，再移除创建链路的动态订阅和元信息发布，并新增 Web Controller。
