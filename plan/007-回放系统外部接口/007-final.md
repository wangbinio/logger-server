# 回放系统外部接口技术方案

## 1. 文档目标

本文档基于 `007-draft.md`、`../005-回放系统设计/005-final.md`、现有 `replay-server` 实现以及本轮讨论结论，给出回放系统外部接口改造方案。

本方案是对 `005-final.md` 中“实例级回放控制消息”和“回放元信息通知”部分的修订。`005-final.md` 中关于 TDengine 查询、回放时钟、连续调度、时间跳转、态势发布和全局任务管理消息的设计继续有效。

本文档重点回答以下问题：

- 回放控制为什么从 `broadcast-{instanceId}` 改为 HTTP REST 接口。
- 创建回放任务后为什么不再动态订阅 `broadcast-{instanceId}`。
- 创建回放任务后为什么不再向 `broadcast-{instanceId}` 发布 `messageType=1200,messageCode=9` 元信息通知。
- 前端如何通过 HTTP 接口启动、暂停、继续、倍速和时间跳转。
- 前端如何通过 HTTP 查询回放元信息。
- 现有 `ReplayControlService` 如何复用，避免重写回放状态机。
- 后续编码如何按最小改动和 TDD 落地。

## 2. 已确认设计决策

### 2.1 外部接口形式

前端控制回放使用 HTTP REST 接口。

`replay-server` 当前只有 `spring-boot-starter`，没有 Web 能力。实现时需要在 `replay-server/pom.xml` 中新增：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

### 2.2 全局任务管理入口保持不变

回放任务创建和停止仍来自 `broadcast-global`。

回放系统继续固定订阅 `broadcast-global`，并只处理：

| 字段 | 值 | 说明 |
| --- | --- | --- |
| `messageType` | `1` | 全局任务管理消息类型。 |
| `messageCode` | `0` | 创建回放任务。 |
| `messageCode` | `1` | 停止回放任务。 |

本次不新增创建回放任务 HTTP 接口，也不新增停止回放任务 HTTP 接口。

### 2.3 实例级控制入口改为 HTTP

回放系统不再订阅 `broadcast-{instanceId}` 来接收启动、暂停、继续、倍速和时间跳转控制。

`005-final.md` 中以下内容被本方案替代：

- `replay-server` 动态订阅 `broadcast-{instanceId}`。
- 实例级控制消息来自 `broadcast-{instanceId}`。
- 停止回放任务时取消订阅 `broadcast-{instanceId}`。

新的控制入口为：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/replay/instances/{instanceId}/start` | 启动回放。 |
| `POST` | `/api/replay/instances/{instanceId}/pause` | 暂停回放。 |
| `POST` | `/api/replay/instances/{instanceId}/resume` | 继续回放。 |
| `POST` | `/api/replay/instances/{instanceId}/rate` | 调整回放倍率。 |
| `POST` | `/api/replay/instances/{instanceId}/jump` | 跳转到指定回放时间。 |

### 2.4 元信息通知改为查询接口

创建回放任务后，回放系统不再向 `broadcast-{instanceId}` 发布：

| 字段 | 值 |
| --- | --- |
| `messageType` | `1200` |
| `messageCode` | `9` |

前端如需回放开始时间、结束时间和持续时间，改为调用：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/replay/instances/{instanceId}` | 查询回放元信息和当前会话状态。 |

### 2.5 控制业务逻辑复用现有服务

HTTP Controller 不重新实现回放状态机。

控制入口必须复用现有 `ReplayControlService` 中已经落地的逻辑：

- `READY -> RUNNING` 启动回放。
- `RUNNING -> PAUSED` 暂停回放。
- `PAUSED -> RUNNING` 继续回放。
- `RUNNING/PAUSED` 更新倍率。
- `READY/RUNNING/PAUSED/COMPLETED` 时间跳转。
- 跳转期间暂停调度，跳转成功后按原运行态恢复调度。
- 非法状态记录状态冲突指标。

HTTP 层只负责：

- 接收前端请求。
- 校验基础参数。
- 构造内部控制命令。
- 调用 `ReplayControlService`。
- 将处理结果包装为 HTTP 响应。

## 3. 修订后的整体链路

### 3.1 创建链路

创建回放任务仍由 `broadcast-global` 驱动：

```text
broadcast-global
  -> ReplayGlobalBroadcastListener
  -> ReplayLifecycleService#createReplay
  -> 查询 TDengine 控制时间点
  -> 发现并分类 TDengine 态势子表
  -> 创建 ReplaySession
  -> session.markReady()
```

创建成功后不再执行：

```text
ReplayTopicSubscriptionManager#subscribe(instanceId)
ReplayMetadataService#publishMetadata(session)
```

因此创建完成后的状态为：

- `ReplaySession` 已存在。
- 状态为 `READY`。
- 没有实例级 RocketMQ 控制消费者。
- 没有向 `broadcast-{instanceId}` 发布元信息。
- 前端通过 `GET /api/replay/instances/{instanceId}` 获取元信息。

### 3.2 控制链路

前端控制请求进入 HTTP Controller：

```text
HTTP POST /api/replay/instances/{instanceId}/start
  -> ReplayControlController
  -> 构造 ProtocolData(messageType=1200,messageCode=1,rawData={})
  -> ReplayControlService#handleStart
  -> ReplayScheduler#schedule
```

其他控制命令同理：

```text
pause  -> messageCode=2 -> ReplayControlService#handlePause
resume -> messageCode=3 -> ReplayControlService#handleResume
rate   -> messageCode=4 -> ReplayControlService#handleRate
jump   -> messageCode=5 -> ReplayControlService#handleJump
```

内部仍保留 `messageType=1200,messageCode=1/2/3/4/5` 的语义，原因是：

- 最小化 `ReplayControlService` 改动。
- 保持日志中的 `messageType/messageCode` 字段连续。
- 保持已有单元测试和业务语义可复用。
- 后续如果需要兼容旧 MQ 控制入口，可以低成本恢复适配层。

### 3.3 停止链路

停止回放任务仍由 `broadcast-global` 驱动：

```text
broadcast-global
  -> ReplayGlobalBroadcastListener
  -> ReplayLifecycleService#stopReplay
  -> ReplayScheduler#cancel
  -> ReplaySessionManager#stopSession
  -> ReplaySessionManager#removeSession
```

停止时不再需要释放 `broadcast-{instanceId}` 订阅。

如果实现时保留 `ReplayTopicSubscriptionManager` 类，也不得在停止链路调用 `unsubscribe(instanceId)` 作为业务必要步骤。

## 4. HTTP 接口设计

### 4.1 通用响应结构

建议新增统一响应 DTO：

```json
{
  "success": true,
  "code": "OK",
  "message": "成功",
  "data": {}
}
```

字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | `boolean` | 请求是否处理成功。 |
| `code` | `String` | 机器可读结果码。 |
| `message` | `String` | 人类可读说明。 |
| `data` | `Object` | 响应数据。无数据时为 `null`。 |

建议结果码：

| code | HTTP 状态 | 说明 |
| --- | --- | --- |
| `OK` | `200` | 处理成功。 |
| `BAD_REQUEST` | `400` | 请求参数错误。 |
| `REPLAY_SESSION_NOT_FOUND` | `404` | 指定实例不存在回放会话。 |
| `REPLAY_STATE_CONFLICT` | `409` | 当前状态不接受该控制命令。 |
| `INTERNAL_ERROR` | `500` | 非预期异常。 |

### 4.2 会话快照结构

控制成功和元信息查询建议返回统一会话快照：

```json
{
  "instanceId": "instance-001",
  "startTime": 1713952800000,
  "endTime": 1713956400000,
  "duration": 3600000,
  "state": "READY",
  "rate": 1.0,
  "currentReplayTime": 1713952800000,
  "lastDispatchedSimTime": 1713952799999
}
```

字段说明：

| 字段 | 来源 | 说明 |
| --- | --- | --- |
| `instanceId` | `ReplaySession#getInstanceId` | 回放实例 ID。 |
| `startTime` | `ReplaySession#getSimulationStartTime` | 回放起始仿真时间。 |
| `endTime` | `ReplaySession#getSimulationEndTime` | 回放结束仿真时间。 |
| `duration` | `ReplaySession#getDuration` | 回放持续时间。 |
| `state` | `ReplaySession#getState` | 当前回放状态。 |
| `rate` | `ReplaySession#getRate` | 当前回放倍率。 |
| `currentReplayTime` | `ReplaySession#getReplayClock().currentTime()` | 当前回放时钟时间。 |
| `lastDispatchedSimTime` | `ReplaySession#getLastDispatchedSimTime` | 已成功发布的回放水位。 |

原 `messageType=1200,messageCode=9` 元信息 payload 只包含 `startTime/endTime/duration`。HTTP 查询接口可以返回更多运行态字段，便于前端展示和控制按钮状态判断。

### 4.3 查询元信息

请求：

```http
GET /api/replay/instances/{instanceId}
```

成功响应：

```json
{
  "success": true,
  "code": "OK",
  "message": "成功",
  "data": {
    "instanceId": "instance-001",
    "startTime": 1713952800000,
    "endTime": 1713956400000,
    "duration": 3600000,
    "state": "READY",
    "rate": 1.0,
    "currentReplayTime": 1713952800000,
    "lastDispatchedSimTime": 1713952799999
  }
}
```

会话不存在响应：

```json
{
  "success": false,
  "code": "REPLAY_SESSION_NOT_FOUND",
  "message": "回放会话不存在",
  "data": null
}
```

### 4.4 启动回放

请求：

```http
POST /api/replay/instances/{instanceId}/start
```

请求体为空。

内部命令：

| 字段 | 值 |
| --- | --- |
| `senderId` | `0` |
| `messageType` | `1200` |
| `messageCode` | `1` |
| `rawData` | `{}` |

成功后预期状态：

| 原状态 | 目标状态 |
| --- | --- |
| `READY` | `RUNNING` |
| `PAUSED` | `RUNNING` |

### 4.5 暂停回放

请求：

```http
POST /api/replay/instances/{instanceId}/pause
```

请求体为空。

内部命令：

| 字段 | 值 |
| --- | --- |
| `senderId` | `0` |
| `messageType` | `1200` |
| `messageCode` | `2` |
| `rawData` | `{}` |

成功后预期状态：

| 原状态 | 目标状态 |
| --- | --- |
| `RUNNING` | `PAUSED` |

### 4.6 继续回放

请求：

```http
POST /api/replay/instances/{instanceId}/resume
```

请求体为空。

内部命令：

| 字段 | 值 |
| --- | --- |
| `senderId` | `0` |
| `messageType` | `1200` |
| `messageCode` | `3` |
| `rawData` | `{}` |

成功后预期状态：

| 原状态 | 目标状态 |
| --- | --- |
| `PAUSED` | `RUNNING` |

### 4.7 倍速回放

请求：

```http
POST /api/replay/instances/{instanceId}/rate
Content-Type: application/json

{
  "rate": 2.0
}
```

字段说明：

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `rate` | `double` | 必填，必须大于 `0`。 |

内部命令：

| 字段 | 值 |
| --- | --- |
| `senderId` | `0` |
| `messageType` | `1200` |
| `messageCode` | `4` |
| `rawData` | `{"rate":2.0}` |

接受状态：

| 原状态 | 目标状态 |
| --- | --- |
| `RUNNING` | `RUNNING`，倍率更新。 |
| `PAUSED` | `PAUSED`，倍率更新但时间继续冻结。 |

非法入参：

- 缺少 `rate`。
- `rate <= 0`。
- `rate` 不是数值。

非法入参返回 `400 BAD_REQUEST`。

### 4.8 时间跳转

请求：

```http
POST /api/replay/instances/{instanceId}/jump
Content-Type: application/json

{
  "time": 1713952800000
}
```

字段说明：

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `time` | `long` | 必填，目标仿真时间，毫秒值。 |

内部命令：

| 字段 | 值 |
| --- | --- |
| `senderId` | `0` |
| `messageType` | `1200` |
| `messageCode` | `5` |
| `rawData` | `{"time":1713952800000}` |

接受状态：

| 原状态 | 行为 |
| --- | --- |
| `READY` | 执行跳转，发布目标时间所需补偿数据。 |
| `RUNNING` | 先暂停连续调度，跳转成功后恢复调度。 |
| `PAUSED` | 执行跳转，保持暂停。 |
| `COMPLETED` | 执行跳转，跳转后回到可继续控制状态。 |

时间边界仍由 `ReplayJumpService` 和 `ReplayClock` 负责限制到 `[simulationStartTime, simulationEndTime]`。

非法入参：

- 缺少 `time`。
- `time` 不是数值。

非法入参返回 `400 BAD_REQUEST`。

## 5. 服务层复用方案

### 5.1 Controller 不直接操作领域对象

`ReplayControlController` 不应直接调用以下领域方法：

- `ReplaySession#start`
- `ReplaySession#pause`
- `ReplaySession#resume`
- `ReplaySession#updateRate`
- `ReplaySession#jumpTo`
- `ReplayScheduler#schedule`
- `ReplayScheduler#cancel`

这些动作必须继续由 `ReplayControlService` 编排。

### 5.2 内部命令构造

建议新增一个轻量适配器，例如 `ReplayHttpCommandFactory`，统一构造 `ProtocolData`：

```java
ProtocolData command = new ProtocolData();
command.setSenderId(0);
command.setMessageType(messageConstants.getInstanceControlMessageType());
command.setMessageCode(messageCode);
command.setRawData(rawData);
```

其中：

- 启动、暂停、继续的 `rawData` 使用 `{}`。
- 倍速的 `rawData` 使用前端传入的 `{"rate":...}`。
- 跳转的 `rawData` 使用前端传入的 `{"time":...}`。

### 5.3 控制结果返回

当前 `ReplayControlService#handleStart/handlePause/handleResume/handleRate/handleJump` 返回 `void`，且非法状态下只记录指标。

为了让 HTTP 接口能向前端返回准确状态，建议在不改变业务逻辑的前提下做轻量提取：

1. 新增内部结果对象 `ReplayControlResult`。
2. 将现有 `handle*` 方法中的核心逻辑提取为返回 `ReplayControlResult` 的方法。
3. 原 MQ 端口方法继续保留 `void` 签名，调用新方法后保持原有日志和指标语义。
4. HTTP Controller 调用返回结果的方法，根据结果映射 `200/404/409/400`。

这样可以避免 Controller 复制状态机判断，也能让前端明确知道命令是否真正生效。

建议结果对象字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `accepted` | `boolean` | 命令是否被业务接受。 |
| `code` | `String` | 业务结果码。 |
| `message` | `String` | 说明。 |
| `session` | `ReplaySession` | 命令处理后的会话对象，可为空。 |

如果实现阶段为了进一步减少改动，也可以先让 Controller 调用现有 `handle*` 方法，再读取会话快照返回。但该做法无法精确区分状态冲突，不作为优先方案。

## 6. 现有代码改动点

### 6.1 `replay-server/pom.xml`

新增 Web starter：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

### 6.2 `ReplayLifecycleService`

创建回放任务时删除以下动作：

```java
subscriptionManager.subscribe(instanceId);
session.setBroadcastConsumerHandle(TopicConstants.buildInstanceBroadcastTopic(instanceId));
metadataService.publishMetadata(session);
```

停止回放任务时删除以下动作：

```java
subscriptionManager.unsubscribe(instanceId);
```

构造函数中不再需要注入：

- `ReplayTopicSubscriptionManager`
- `ReplayMetadataService`

创建成功日志中的 `topic` 字段建议从 `broadcast-{instanceId}` 改为 `-`，避免日志暗示仍存在实例控制 topic。

### 6.3 `ReplayTopicSubscriptionManager`

本方案下生产链路不再使用 `ReplayTopicSubscriptionManager`。

处理方式有两种：

| 方案 | 说明 | 建议 |
| --- | --- | --- |
| 保留类但不被生命周期服务调用 | 改动小，但存在废弃代码。 | 本次优先采用。 |
| 删除类及相关测试 | 代码更干净，但改动面更大。 | 后续清理时再做。 |

本次以最小改动为原则，保留类和测试可以接受，但必须新增测试证明创建回放任务不会调用 `subscribe(instanceId)`。

### 6.4 `ReplayInstanceBroadcastMessageHandler`

本方案下生产控制入口不再使用 `ReplayInstanceBroadcastMessageHandler`。

为最小化改动，可以暂时保留该类。后续如果确认不需要兼容旧 MQ 控制入口，再统一删除：

- `ReplayInstanceBroadcastMessageHandler`
- `ReplayControlCommandPort` 的 MQ 适配用途
- `ReplayRocketMqConsumerFactory#createInstanceBroadcastConsumer`
- `ReplayTopicSubscriptionManager`
- 对应测试

本次不要求删除。

### 6.5 `ReplayMetadataService`

创建回放任务后不再调用 `ReplayMetadataService#publishMetadata`。

`ReplayMetadataPayload` 仍可复用为元信息响应的一部分，但不再通过 RocketMQ 发布。

如果后续实现中 `ReplayMetadataService` 只剩 MQ 发布职责，可以保留但不使用；也可以新增 `ReplayInstanceQueryService` 专门负责 HTTP 查询会话快照。

### 6.6 新增 Controller 与 DTO

建议新增包：

```text
com.szzh.replayserver.controller
com.szzh.replayserver.model.api
```

建议新增类：

| 类名 | 职责 |
| --- | --- |
| `ReplayControlController` | 暴露 5 个控制接口和 1 个元信息查询接口。 |
| `ReplayApiResponse<T>` | 统一 HTTP 响应结构。 |
| `ReplaySessionResponse` | 会话元信息和当前状态快照。 |
| `ReplayRateRequest` | 倍速请求体。 |
| `ReplayJumpRequest` | 时间跳转请求体。 |
| `ReplayHttpCommandFactory` | 构造内部 `ProtocolData` 控制命令。 |
| `ReplayControlResult` | 服务层控制结果。 |

## 7. 接口状态语义

### 7.1 会话不存在

前端调用控制接口时，如果 `ReplaySessionManager` 中不存在对应 `instanceId`，返回：

```http
404 Not Found
```

响应：

```json
{
  "success": false,
  "code": "REPLAY_SESSION_NOT_FOUND",
  "message": "回放会话不存在",
  "data": null
}
```

### 7.2 状态冲突

当前状态不接受控制命令时，返回：

```http
409 Conflict
```

示例：

- `READY` 状态调用暂停。
- `RUNNING` 状态调用继续。
- `STOPPED` 状态调用任意控制命令。
- `FAILED` 状态调用任意控制命令。

响应：

```json
{
  "success": false,
  "code": "REPLAY_STATE_CONFLICT",
  "message": "当前回放状态不接受该控制命令",
  "data": {
    "instanceId": "instance-001",
    "state": "READY"
  }
}
```

### 7.3 幂等语义

HTTP 接口不额外扩大幂等范围，优先保持现有 `ReplayControlService` 语义。

建议在实现 `ReplayControlResult` 时明确：

| 场景 | 建议语义 |
| --- | --- |
| `READY` 调用启动 | 成功。 |
| `PAUSED` 调用启动 | 按现有逻辑恢复运行，成功。 |
| `RUNNING` 重复启动 | 状态冲突，或后续单独裁定为幂等成功。 |
| `PAUSED` 重复暂停 | 状态冲突，或后续单独裁定为幂等成功。 |
| `RUNNING` 重复继续 | 状态冲突，或后续单独裁定为幂等成功。 |

为避免本次接口改造扩大行为边界，默认按当前服务实现处理：不主动新增幂等成功语义。

## 8. 配置调整

### 8.1 YAML 注释调整

`replay-server/src/main/resources/application.yml` 中当前注释仍描述：

```yaml
# broadcast-{ScenarioInstanceID} 回放控制指令
```

应调整为：

```yaml
# HTTP 外部接口复用的回放控制指令语义
```

`metadata-message-code` 不再用于 RocketMQ 发布。可以保留配置字段以兼容 `ReplayMessageConstants`，但注释应改为：

```yaml
# 历史回放元信息通知消息码，HTTP 元信息查询替代后不再主动发布
metadata-message-code: 9
```

### 8.2 RocketMQ 配置

`replay-server.rocketmq.enable-global-listener` 继续有效。

实例级控制 topic 不再需要新增配置开关，因为本方案直接停止调用动态订阅管理器。

## 9. 测试策略

后续编码按 TDD 实施，先补失败测试，再改实现。

### 9.1 单元测试

#### `ReplayLifecycleServiceTest`

新增或修改测试：

- 创建回放任务后会话进入 `READY`。
- 创建回放任务后不调用 `ReplayTopicSubscriptionManager#subscribe`。
- 创建回放任务后不调用 `ReplayMetadataService#publishMetadata`。
- 停止回放任务后不调用 `ReplayTopicSubscriptionManager#unsubscribe`。
- 创建失败时不再需要回滚实例控制 topic 订阅。

#### `ReplayControlControllerTest`

新增测试覆盖：

- `GET /api/replay/instances/{instanceId}` 返回 `startTime/endTime/duration/state/rate/currentReplayTime/lastDispatchedSimTime`。
- 会话不存在时返回 `404`。
- 启动接口能调用 `ReplayControlService` 并返回会话快照。
- 暂停接口能调用 `ReplayControlService` 并返回会话快照。
- 继续接口能调用 `ReplayControlService` 并返回会话快照。
- 倍速接口能传入 `rate` 并返回更新后的倍率。
- 倍速缺少 `rate` 或 `rate <= 0` 返回 `400`。
- 跳转接口能传入 `time` 并返回更新后的水位。
- 跳转缺少 `time` 返回 `400`。
- 状态冲突返回 `409`。

#### `ReplayPayloadTest`

保留现有 `ReplayRatePayload` 和 `ReplayJumpPayload` 测试。

如果新增 HTTP 请求 DTO，应补充：

- `ReplayRateRequest` 只接受大于 `0` 的数值。
- `ReplayJumpRequest` 必须包含 `time`。

### 9.2 集成测试

#### `ReplayFlowIntegrationTest`

现有测试需要调整：

- 删除“创建后发送元信息到 `broadcast-{instanceId}`”的断言。
- 删除“创建后动态订阅 `broadcast-{instanceId}`”的断言。
- 控制动作从 `ReplayInstanceBroadcastMessageHandler#handle` 改为 HTTP Controller 或 Controller 下层适配器。
- 保留启动、暂停、继续、倍速、向前跳转、向后跳转、停止的业务断言。

#### `ReplaySpringFlowIntegrationTest`

如该测试启动 Spring 上下文，应补充 Web 接口路径验证：

- 创建任务仍通过 `ReplayGlobalBroadcastListener` 或模拟全局消息进入。
- 创建完成后通过 HTTP 启动、暂停、继续、倍速和跳转。
- 停止仍通过全局 stop 消息进入。

### 9.3 真实环境测试

`ReplayRealEnvironmentTest` 当前通过真实 `broadcast-{instanceId}` 控制 topic 驱动回放，需要调整为：

- 真实 `broadcast-global` 创建回放任务。
- 不再创建或等待 `broadcast-{instanceId}` 动态订阅。
- 使用 HTTP 接口执行时间跳转或启动控制。
- 继续从真实 `situation-{instanceId}` topic 验证回放发布。
- 真实 `broadcast-global` 停止回放任务。

真实环境测试仍使用显式开关：

```powershell
mvn test -Dreplay.real-env.test=true
```

常规 CI 不依赖真实 RocketMQ 和 TDengine。

## 10. 实施步骤

### 10.1 先写失败测试

1. 修改 `ReplayLifecycleServiceTest`，断言创建后不订阅、不发布元信息。
2. 新增 `ReplayControlControllerTest`，覆盖 5 个控制接口和 1 个查询接口。
3. 修改 `ReplayFlowIntegrationTest`，移除实例控制 topic 和元信息通知断言。
4. 如时间允许，调整 `ReplayRealEnvironmentTest` 的控制入口。

### 10.2 引入 Web 能力

1. `replay-server/pom.xml` 增加 `spring-boot-starter-web`。
2. 确认 `ReplayServerApplication` 正常启动 Web 上下文。
3. 保持 Java 8 兼容，不使用 Java 9+ API。

### 10.3 改造生命周期服务

1. `ReplayLifecycleService#createReplay` 删除动态订阅。
2. `ReplayLifecycleService#createReplay` 删除元信息发布。
3. `ReplayLifecycleService#stopReplay` 删除动态取消订阅。
4. 更新相关日志中的 topic 字段。
5. 更新构造函数和测试夹具。

### 10.4 增加 HTTP 控制入口

1. 新增统一响应 DTO。
2. 新增会话快照 DTO。
3. 新增倍速和跳转请求 DTO。
4. 新增命令构造适配器。
5. 新增 `ReplayControlController`。
6. 复用 `ReplayControlService` 执行业务动作。

### 10.5 补齐服务结果

1. 从 `ReplayControlService` 提取返回 `ReplayControlResult` 的内部方法。
2. 原 `ReplayControlCommandPort` 的 `handle*` 方法继续存在，保持 MQ 适配能力。
3. HTTP Controller 使用结果对象映射响应。
4. 非法状态继续记录 `ReplayMetrics#recordStateConflict`。

### 10.6 更新配置和文档

1. 更新 `application.yml` 中控制消息配置注释。
2. 更新 `README.md` 或 `ARCHITECTURE.md` 中回放控制入口说明。
3. 明确 `broadcast-{instanceId}` 已不再作为回放控制入口。
4. 明确元信息通过 `GET /api/replay/instances/{instanceId}` 查询。

### 10.7 验证

建议至少执行：

```powershell
mvn -q -pl replay-server -am test
```

如涉及父工程回归，再执行：

```powershell
mvn -q test
```

真实环境验证单独执行：

```powershell
mvn test -Dreplay.real-env.test=true
```

如果全量测试中仍出现外部 RocketMQ 或 TDengine 环境问题，需要区分普通测试回归和真实环境依赖失败，不应混淆。

## 11. 风险与边界

### 11.1 前端跨域

本方案只定义 HTTP 接口，不强制新增 CORS 配置。

如果前端在浏览器中跨域直连 `replay-server`，应由部署网关统一处理 CORS；如果没有网关，再在 `replay-server` 中增加明确白名单配置。不要默认全放行。

### 11.2 创建和控制的时序

前端必须先等待回放任务创建完成，再调用控制接口。

如果创建消息刚进入 `broadcast-global`，但 `ReplaySession` 尚未进入 `READY`，控制接口可能返回：

- `404 REPLAY_SESSION_NOT_FOUND`
- 或 `409 REPLAY_STATE_CONFLICT`

前端应在创建后轮询 `GET /api/replay/instances/{instanceId}`，直到状态为 `READY` 再启用控制按钮。

### 11.3 旧 MQ 控制入口兼容

本次需求明确取消 `broadcast-{instanceId}` 控制订阅。

因此生产链路不保证旧 MQ 实例控制消息继续有效。保留相关类只是为了减少一次性删除风险，不代表该入口仍受支持。

### 11.4 元信息推送语义变化

原方案是创建成功后主动推送元信息。

新方案改为前端主动查询。前端必须调整为：

1. 发送或等待外部系统发送全局创建消息。
2. 调用 `GET /api/replay/instances/{instanceId}` 获取元信息。
3. 根据返回状态启用启动、暂停、继续、倍速和跳转按钮。

### 11.5 状态冲突返回值

当前 `ReplayControlService` 对非法状态的处理偏 MQ 消费模型：记录指标后返回。

HTTP 接口需要更明确的客户端反馈。本方案通过 `ReplayControlResult` 收敛该问题，但实现时必须避免在 Controller 中复制状态机规则。

## 12. 验收标准

本任务完成后应满足：

- `replay-server` 暴露 5 个回放控制 HTTP 接口。
- `replay-server` 暴露 1 个回放元信息查询 HTTP 接口。
- `broadcast-global` 创建和停止回放任务保持不变。
- 创建回放任务后不再订阅 `broadcast-{instanceId}`。
- 创建回放任务后不再向 `broadcast-{instanceId}` 发布 `messageType=1200,messageCode=9`。
- 启动、暂停、继续、倍速和时间跳转继续复用现有 `ReplayControlService` 的业务逻辑。
- 原有回放时钟、调度、跳转、TDengine 查询和态势发布逻辑不被重写。
- 单元测试和集成测试覆盖入口替换后的主链路。
- 常规 Maven 测试通过。

## 13. 最终结论

回放系统的实例级控制入口应从 RocketMQ `broadcast-{instanceId}` 切换为 HTTP REST 接口。

全局任务管理仍保留在 `broadcast-global`，因为创建和停止回放任务属于后端任务编排消息，不在本次前端控制接口范围内。

创建回放任务后，`replay-server` 只维护本地 `ReplaySession` 并等待前端 HTTP 控制，不再创建实例级控制消费者，也不再主动发布元信息通知。前端通过 `GET /api/replay/instances/{instanceId}` 查询元信息，通过 5 个 `POST` 接口驱动启动、暂停、继续、倍速和时间跳转。

实现时应以最小改动为原则：去掉生命周期服务中的动态订阅和元信息发布，新增 Web Controller 和少量 DTO，控制动作继续复用 `ReplayControlService`。这样能满足新需求，同时避免重写已经通过测试验证的回放状态机和调度逻辑。
