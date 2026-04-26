# 测试证据与日志回归补强任务单

## 执行依据

- `plan/006-回放系统交付审阅/fix-steps.md`
- `plan/006-回放系统交付审阅/phases/phase-04.md`
- `tasks/todo-31-回放系统交付审阅.md`
- `plan/005-回放系统设计/005-final.md`

## 计划

- [x] 将 Phase 04 文档状态标记为进行中。
- [x] 新增 Spring 容器级 Mock 全链路测试，避免手动 new 全部服务替代容器装配。
- [x] 覆盖 create/start/pause/resume/rate/jump/stop 全链路消息流。
- [x] 补生命周期成功和失败日志字段断言。
- [x] 补控制成功日志字段断言。
- [x] 补协议解析失败、发布失败、调度查询失败等失败路径日志字段断言。
- [x] 使用 Maven clean 重新生成测试报告并检查 surefire dump。
- [x] 运行 Phase 04 定向测试、`mvn -pl replay-server -am test` 和 `mvn test`。
- [x] 回写 Phase 04 Review、修复索引状态和本任务单 Review。

## Review

- 新增 `replay-server/src/test/java/com/szzh/replayserver/integration/ReplaySpringFlowIntegrationTest.java`，以 Spring Boot 容器加载回放系统真实服务编排，并用 `@MockBean` 隔离 TDengine、RocketMQ 发送、动态订阅等外部依赖。
- 全链路验证覆盖 `broadcast-global` create/stop 与实例 start/pause/resume/rate/jump 控制消息，断言元信息发布、状态迁移、订阅清理、跳转水位和指标递增。
- 结构化日志断言覆盖生命周期成功与失败、控制成功、协议解析失败、全局监听业务异常、态势发布重试失败、调度 tick 查询失败，字段覆盖 `instanceId/topic/messageType/messageCode/senderId/currentReplayTime/lastDispatchedSimTime/rate/replayState/reason/attempt/simtime`。
- `mvn -pl replay-server -am "-Dtest=ReplaySpringFlowIntegrationTest" -DfailIfNoTests=false test` 通过：`Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`。
- `mvn -pl replay-server -am test` 通过：`Tests run: 112, Failures: 0, Errors: 0, Skipped: 1`；跳过项为默认关闭的真实环境测试。
- `mvn -pl replay-server -am clean test` 通过：`Tests run: 112, Failures: 0, Errors: 0, Skipped: 1`；跳过项为默认关闭的真实环境测试。
- `mvn clean test` 通过：根反应堆 `common`、`logger-server`、`replay-server` 均为 `BUILD SUCCESS`；报告统计为 `common 12/0/0/0`、`logger-server 59/0/0/2`、`replay-server 112/0/0/1`。
- surefire dump 处置结论：clean 后检查 `common/target/surefire-reports`、`logger-server/target/surefire-reports`、`replay-server/target/surefire-reports`，未发现 `.dump` 或 `.dumpstream`，无历史 dump 混入本阶段验收报告。
- 遗留风险：真实 TDengine/RocketMQ 仍由显式开关测试覆盖，Phase 04 不改变默认跳过策略。
