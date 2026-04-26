# 回放系统交付审阅任务单

## 执行依据

- `plan/005-回放系统设计/005-final.md`
- `plan/005-回放系统设计/development-steps.md`
- `plan/005-回放系统设计/phases/phase-00.md` 至 `phase-06.md`
- `tasks/lessons.md`
- `replay-server/src/main/java`
- `replay-server/src/test/java`
- `replay-server/target/surefire-reports`

## 计划

- [x] 审阅总体设计、开发步骤与阶段 Review，提取验收约束。
- [x] 审阅 `replay-server` 生产实现，核对消息入口、订阅生命周期、TDengine 查询、状态机、调度、跳转、发布与指标。
- [x] 审阅 `replay-server` 测试与 surefire 报告，核对单元、集成、真实环境验证证据。
- [x] 对照设计识别缺口、偏差、边界风险和验证不足点。
- [x] 在本任务单末尾补充 Review，记录审阅范围、发现项和验证结果。

## Review

### 审阅范围

- 已审阅 `005-final.md`、`development-steps.md`、`phase-06.md` 与 Phase 06 交付记录。
- 已审阅 `replay-server/src/main/java` 下回放生命周期、控制、调度、跳转、发布、TDengine 查询、动态订阅、配置和指标实现。
- 已审阅 `replay-server/src/test/java` 下单元测试、Mock 集成测试、真实环境测试和 `target/surefire-reports`。

### 验证结果

- `mvn -pl replay-server -am test`：通过，`common` 与 `replay-server` 常规测试成功；`ReplayRealEnvironmentTest` 默认跳过。
- `mvn test`：通过，全工程 91 个常规测试成功，1 个真实环境测试按默认策略跳过。
- 当前环境 Maven 使用 Java 8：`Java version: 1.8.0_482`。

### 主要发现

- P1：`ReplayScheduler.tick()` 查询、发布和推进水位没有持有会话锁；`ReplayControlService.handleJump()` 只取消 future，不等待已经执行中的 tick 结束。跳转补偿发布可能和连续回放窗口并发发布，违反同一 `instanceId` 控制命令串行、跳转与调度互斥的设计要求。
- P1：`ReplayJumpService.jump()` 中跳转发布失败后只恢复运行态并抛出异常，没有将会话标记为 `FAILED`；外层 `ReplayControlService` 还可能重新调度。该行为偏离发布失败后会话进入 `FAILED`、不伪装成功的设计。
- P1：`ReplaySession` 初始 `lastDispatchedSimTime` 被设为 `simulationStartTime`，而设计要求启动时为 `simulationStartTime - 1`。连续回放窗口使用 `(lastDispatchedSimTime, currentReplayTime]`，因此 `simtime == simulationStartTime` 的第一帧会被漏发。
- P2：生产配置没有配置 `replay-server.replay.event-messages`，默认事件配置为空，导致所有表都会被归类为周期表；向前/向后跳转不会补发事件类表数据，只发布周期快照。
- P2：`ReplaySessionManager.stopSession()` 对 `COMPLETED` 或 `FAILED` 终态直接返回，`removeSession()` 又只移除 `STOPPED`；自然完成或失败后的停止命令不会移除会话对象，与停止后释放会话的设计不一致。
- P2：设计要求自然完成后保留 `broadcast-{instanceId}` 订阅，允许用户时间跳转后重新查看；当前 `COMPLETED` 被 `ReplaySessionState.isTerminal()` 视为终态，`ReplayControlService`、`ReplayJumpService` 和 `ReplaySession.jumpTo()` 都不接受 `COMPLETED` 跳转。
- P2：`ReplayTimeControlRepository` 只捕获 `EmptyResultDataAccessException`。如果历史实例完全没有 `time_control_{instanceId}` 表，查询异常不会降级到 `situation_{instanceId}` 的 `MIN/MAX(simtime)`。
- P2：真实环境测试禁用了全局监听器，并直接调用 `lifecycleService.createReplay()` 和 `stopReplay()`，没有验证生产入口 `broadcast-global` 的 RocketMQ 注解监听链路。
- P2：`ReplayRealEnvironmentTest` 标注 `@ActiveProfiles("dev")`，未激活 `application-real.yml`；该真实环境配置模板目前没有测试证明。
- P2：真实环境测试只断言收到 3 条消息和发布成功指标，没有校验 `senderId/messageType/messageCode/rawData`、顺序、重复和 tag 映射。
- P2：Mock 全链路测试不是 Spring 容器级集成测试，Repository、订阅管理器和调度线程均被 mock 或手动替代，不能证明真实 Bean 装配和异步调度边界。
- P2：结构化日志字段没有自动化断言，日志验收仍依赖人工声明。
- P3：`replay.publish.batch-size` 配置存在但未被任何生产路径使用，连续回放和跳转均逐帧调用发布。
- P3：`replay-server/target/surefire-reports` 中留有历史 fork/process checker dump，当前验收记录未解释这些异常测试产物。

### 总体结论

当前交付物已经覆盖回放系统主骨架：独立 `replay-server`、不消费 `situation-{instanceId}`、创建后订阅 `broadcast-{instanceId}`、停止时取消实例广播订阅、TDengine 查询、协议重组、连续调度、跳转和基础指标均有实现与常规测试。

但从上线审阅角度看，仍不应直接判定为“设计完全一致”。核心风险集中在并发一致性、跳转失败状态、事件表生产配置、终态停止清理和真实环境验证证据。建议先修复 P1 和 P2 中影响业务语义的项，再补真实全入口验证与日志/配置回归测试。

## 修复闭环

- Finding 1：已在 Phase 00 修复 `ReplaySession` 初始水位，首次连续回放窗口覆盖 `simulationStartTime`。
- Finding 2：已在 Phase 01 修复连续调度与跳转互斥，`ReplayScheduler.tick()` 与 `ReplayJumpService.jump()` 对同一会话串行。
- Finding 3：已在 Phase 01 修复跳转发布失败语义，发布失败进入 `FAILED` 且不恢复调度。
- Finding 4：已在 Phase 02 补齐生产事件消息配置，事件表分类不再依赖空默认配置。
- Finding 5：已在 Phase 00 修复终态停止清理，`COMPLETED/FAILED` 会话可由 stop 命令释放。
- Finding 6：已在 Phase 00 修复 `COMPLETED` 跳转语义，自然完成后仍可跳转查看。
- Finding 7：已在 Phase 02 修复 `time_control_{instanceId}` 缺表降级，历史数据可回退到态势表 `MIN/MAX(simtime)`。
- Finding 8：已在 Phase 03 补强真实 `broadcast-global` 入口验证，真实环境测试不再绕过生产监听入口。
- Finding 9：已在 Phase 03 补强真实回放内容验证，覆盖顺序、协议字段和 tag 映射。
- Finding 10：已在 Phase 05 修复 `replay.publish.batch-size` 无效配置，连续回放与跳转发布均按配置分批并在批间检查状态。
- 交付审阅验证缺口：已在 Phase 04 补 Spring 容器级 Mock 集成测试、结构化日志字段断言和 clean surefire dump 处置。
- 最终验证：Phase 05 已通过阶段定向测试、`mvn -pl replay-server -am test`、`mvn test` 和显式真实环境 `ReplayRealEnvironmentTest`。
