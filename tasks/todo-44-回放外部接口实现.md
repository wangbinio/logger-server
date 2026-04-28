# todo-44 回放外部接口实现

## 任务目标

- [x] 根据 `plan/007-回放系统外部接口/007-final.md` 实现回放外部 HTTP 接口。
- [x] 保持 `broadcast-global` 创建和停止回放任务入口不变。
- [x] 创建回放任务后不再订阅 `broadcast-{instanceId}`。
- [x] 创建回放任务后不再发布 `messageType=1200,messageCode=9` 元信息通知。
- [x] 新增 5 个控制接口和 1 个元信息查询接口。
- [x] 控制业务逻辑复用 `ReplayControlService`，不在 Controller 中复制状态机。
- [x] 按 TDD 补测试并运行模块级验证。

## 执行计划

- [x] 先修改/新增测试，覆盖设计文档中的入口替换行为。
- [x] 引入 Web starter 和 HTTP API DTO。
- [x] 为 `ReplayControlService` 增加可返回结果的控制方法，保留原 MQ 端口方法。
- [x] 修改 `ReplayLifecycleService`，移除动态订阅和元信息发布依赖。
- [x] 新增 `ReplayControlController` 和内部命令构造适配器。
- [x] 更新 YAML 注释和平台文档。
- [x] 执行 `mvn -q -pl replay-server -am test`。

## 当前约束

- Java 版本保持 1.8。
- 不删除旧 MQ 控制适配类，仅停止生产创建/停止链路调用。
- 不新增创建或停止回放任务 HTTP 接口。
- 不默认新增全放行 CORS。

## 执行记录

- 已读取 `007-final.md`。
- 已读取 `tasks/lessons.md`，确认需使用 Java 8 验证并保持函数声明中文注释。
- 已先补 `ReplayControlControllerTest`、`ReplayLifecycleServiceTest`、回放集成测试和真实环境测试入口改造，并用缺失 Web 依赖和新类的编译失败确认测试先失败。
- 已在 `replay-server/pom.xml` 引入 `spring-boot-starter-web`。
- 已新增 `ReplayControlController`，暴露 `GET /api/replay/instances/{instanceId}` 与 `POST /start`、`/pause`、`/resume`、`/rate`、`/jump`。
- 已新增 `ReplayApiResponse`、`ReplaySessionResponse`、`ReplayRateRequest`、`ReplayJumpRequest`、`ReplayControlResult` 和 `ReplayHttpCommandFactory`。
- 已改造 `ReplayControlService`，保留原 MQ 端口方法，并新增返回 `ReplayControlResult` 的 HTTP 复用方法。
- 已改造 `ReplayLifecycleService`，创建回放后只创建 READY 会话，不再订阅 `broadcast-{instanceId}`，不再发布元信息；停止回放不再取消实例级回放控制订阅。
- 已更新 `application.yml`、`README.md`、`ARCHITECTURE.md`，明确回放实例级控制改为 HTTP，元信息改为查询接口。

## 验证记录

- [x] `mvn -q -pl replay-server -am "-Dtest=ReplayLifecycleServiceTest,ReplayControlControllerTest" -DfailIfNoTests=false test`
- [x] `mvn -q -pl replay-server -am test`
- [x] `mvn -q test`

## Review 记录

- 常规模块测试和根工程测试均通过，测试日志显示使用 Java 1.8。
- 真实环境测试 `ReplayRealEnvironmentTest` 已改为通过 MockMvc 调用 HTTP 控制接口，但真实 RocketMQ/TDengine 链路仍按显式开关 `-Dreplay.real-env.test=true` 单独运行，本次未开启真实环境验证。
- 旧 `ReplayInstanceBroadcastMessageHandler`、`ReplayTopicSubscriptionManager`、`ReplayMetadataService` 按方案保留，生产创建/停止链路不再调用动态订阅和元信息发布。
