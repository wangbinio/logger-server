# logger-server 调查

## 入口与职责

`logger-server` 是记录侧 Spring Boot 服务，固定订阅 `broadcast-global`，按实例动态订阅 `broadcast-{instanceId}` 和 `situation-{instanceId}`。

## 主链路

- `GlobalBroadcastListener` 解析全局消息并委派 `SimulationLifecycleService`。
- `SimulationLifecycleService.handleCreate` 创建会话、创建 `situation_{instanceId}` 超级表、创建 `time_control_{instanceId}` 控制表、动态订阅实例 Topic，最后进入 `READY`。
- `InstanceBroadcastMessageHandler` 处理实例控制消息并委派 `SimulationControlService`。
- `SimulationControlService` 处理 start、pause、resume，并将控制时间点写入 TDengine。控制时间点写入失败只记录日志，不阻断已经生效的控制状态。
- `SituationMessageHandler` 处理态势消息并委派 `SituationRecordService`。
- `SituationRecordService` 只在会话为 `RUNNING` 时写入态势，其他状态丢弃并计数。

## TDengine 写入

- `TdengineSchemaService` 负责建表。
- `TdengineWriteService` 负责态势写入、控制时间点写入和标准 JDBC batch。
- 当前不依赖 `TSWSPreparedStatement`，低版本 TDengine 使用标准 JDBC batch。

## 状态语义

- `READY`：资源已创建，等待启动。
- `RUNNING`：允许写入态势。
- `PAUSED`：时钟冻结，态势不入库。
- `STOPPED`：停止并释放订阅。
- `FAILED`：创建或运行出现不可恢复异常。

