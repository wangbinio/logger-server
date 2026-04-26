# Phase 05 控制命令与时间跳转执行

## 背景

按照 `plan/005-回放系统设计/005-final.md`、`development-steps.md` 与 `phases/phase-05.md` 执行 Phase 05，实现 `replay-server` 的生命周期服务、控制服务、跳转服务和元信息发布。

本阶段完成后，回放系统应具备创建、停止、开始、暂停、继续、倍速、向前跳转、向后跳转和周期快照发布能力。

## 执行计划

- [x] 检查 `005-final.md`、Phase 05 计划和现有 Phase 02/03/04 接口。
- [x] 将 Phase 05 状态标记为进行中。
- [x] 先补 `ReplayPayloadTest` 并确认失败。
- [x] 先补 `ReplayMetadataServiceTest` 并确认失败。
- [x] 先补 `ReplayLifecycleServiceTest` 并确认失败。
- [x] 先补 `ReplayControlServiceTest` 并确认失败。
- [x] 先补 `ReplayJumpServiceTest` 并确认失败。
- [x] 实现 payload DTO、元信息服务、生命周期服务、控制服务和跳转服务。
- [x] 将实例控制消息处理器接入 `ReplayControlService`。
- [x] 运行 Phase 05 定向测试。
- [x] 运行 `mvn -pl replay-server -am test` 验证阶段模块。
- [x] 运行完整 `mvn test` 回归。
- [x] 回写 Phase 05 文档、开发步骤索引和本任务单 Review。

## Review

- TDD 失败确认：首次运行 Phase 05 定向测试时，因 `ReplayControlService`、`ReplayJumpService`、`ReplayLifecycleService`、`ReplayMetadataService` 等生产类缺失导致编译失败，符合先测后实现预期。
- 代码完成：新增回放创建/停止、元信息、倍率、跳转 DTO；新增元信息发布、生命周期编排、控制命令处理和跳转补偿服务；`ReplaySession` 增加跳转场景需要的水位同步方法。
- 接入完成：`ReplayLifecycleService` 实现 `ReplayLifecycleCommandPort`，`ReplayControlService` 实现 `ReplayControlCommandPort`，现有全局监听器和实例控制处理器会通过 Spring 自动注入对应端口。
- 跳转语义完成：向后跳转查询 `[simulationStartTime, targetTime]` 事件，向前跳转查询 `(currentTime, targetTime]` 事件，原地跳转不补发事件；三种跳转都发布周期表目标时间前最后一帧。
- 失败语义完成：跳转发布失败时在发布完成前不移动时钟、不推进 `lastDispatchedSimTime`；运行状态跳转失败会恢复运行状态并由控制层恢复调度。
- 阶段验证通过：Phase 05 定向测试 19 个通过。
- 模块验证通过：`mvn -pl replay-server -am test`，`common` 12 个测试通过，`replay-server` 84 个测试通过。
- 全量验证通过：`mvn test`，`common` 12 个测试通过，`logger-server` 59 个测试中 1 个真实环境开关测试跳过，`replay-server` 84 个测试通过。
- 范围边界：本阶段未做真实 RocketMQ/TDengine 联调，也不发布状态重置协议；这些验收项按计划留给 Phase 06。
