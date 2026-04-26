# 记录仿真停止时间

## 执行计划

- [x] 补充失败测试，覆盖 global stop 消息会写入仿真结束时间控制记录。
- [x] 补充失败测试，覆盖停止记录写入失败不阻断停止流程。
- [x] 调整生命周期停止流程，在停止现有会话时写入 `time_control_{instanceId}`。
- [x] 执行相关 Maven 测试，确认新增行为与既有控制记录逻辑不回归。

## 关键约束

- 任务停止消息来自 `broadcast-global`，不是实例级 `broadcast-{instanceId}`。
- 停止记录仍写入控制时间点表，保留 `sender_id`、`msgtype`、`msgcode`。
- 停止记录写入失败只打印日志，不阻断取消订阅、停止会话和移除会话。
- 不改动已有 `plan/001-总体设计/final.md` 与 `plan/005-回放系统设计/` 的未提交内容。

## Review

- 已在 `SimulationLifecycleService.handleStop(...)` 中接入停止时间点写入，写入内容复用 `TimeControlRecordCommand`，`rate=0`，并保留 stop 消息的 `sender_id`、`msgtype`、`msgcode`。
- 已保持停止记录写入失败非阻断：异常只记录 `time_control_stop_write_failed` 日志，不调用 `session.recordFailure(...)`，不阻断取消订阅、停止会话和移除会话。
- 已补充 `SimulationLifecycleServiceTest`，覆盖停止写入成功、停止写入失败不阻断、缺失会话不写入。
- 已补充 `SimulationFlowIntegrationTest`，覆盖从 `broadcast-global` stop 消息进入后产生第 4 条控制时间点记录。
- 验证通过：`mvn "-Dtest=SimulationLifecycleServiceTest,SimulationControlServiceTest,SimulationFlowIntegrationTest,GlobalBroadcastListenerTest" test`，共 18 个测试通过。
- 验证通过：`mvn test`，共 67 个测试，0 失败，1 个真实环境测试按开关跳过。
