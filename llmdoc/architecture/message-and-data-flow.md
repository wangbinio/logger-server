# 消息与数据流

## 平台协议

协议解析由 `ProtocolMessageUtil` 负责，使用小端序。关键字段为 `senderId`、`messageType`、`messageCode`、`rawData`。解析失败抛出 `ProtocolParseException`，消息入口应记录日志和指标后安全结束当前消息。

## Topic 约定

| Topic | 记录侧 | 回放侧 |
| --- | --- | --- |
| `broadcast-global` | 固定订阅，处理仿真创建和停止。 | 固定订阅，处理回放创建和停止。 |
| `broadcast-{instanceId}` | 动态订阅，处理记录侧实例控制。 | 当前生产控制入口不是该 Topic。 |
| `situation-{instanceId}` | 动态订阅，消费原始态势并写入 TDengine。 | 发布回放态势，不消费。 |

## 记录链路

```text
broadcast-global
  -> GlobalBroadcastListener
  -> SimulationLifecycleService
  -> SimulationSessionManager
  -> TdengineSchemaService
  -> TopicSubscriptionManager
```

```text
broadcast-{instanceId}
  -> InstanceBroadcastMessageHandler
  -> SimulationControlService
  -> SimulationClock
  -> TdengineWriteService.writeTimeControl
```

```text
situation-{instanceId}
  -> SituationMessageHandler
  -> SituationRecordService
  -> TdengineWriteService.write
  -> TDengine
```

## 回放链路

```text
broadcast-global
  -> ReplayGlobalBroadcastListener
  -> ReplayLifecycleService
  -> ReplayTimeControlRepository
  -> ReplayTableDiscoveryRepository
  -> ReplayTableClassifier
  -> ReplaySessionManager
```

```text
HTTP /api/replay/instances/{instanceId}/...
  -> ReplayControlController
  -> ReplayHttpCommandFactory
  -> ReplayControlService
  -> ReplayScheduler 或 ReplayJumpService
```

```text
ReplayScheduler / ReplayJumpService
  -> ReplayFrameRepository
  -> ReplayFrameMergeService
  -> ReplaySituationPublisher
  -> situation-{instanceId}
```

## 状态和水位

- 记录侧只在 `RUNNING` 写态势。
- 回放侧连续调度使用 `(lastDispatchedSimTime, currentReplayTime]`。
- 回放帧只有发布成功后才推进 `lastDispatchedSimTime`。
- 跳转在会话锁内执行，跳转期间暂停连续调度，补偿帧发布成功后才同步时钟和水位。

