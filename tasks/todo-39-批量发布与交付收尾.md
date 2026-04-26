# 批量发布与交付收尾任务单

## 执行依据

- `plan/006-回放系统交付审阅/fix-steps.md`
- `plan/006-回放系统交付审阅/phases/phase-05.md`
- `plan/005-回放系统设计/005-final.md`
- `tasks/todo-31-回放系统交付审阅.md`
- `tasks/lessons.md`

## 计划

- [x] 将 Phase 05 文档状态标记为进行中。
- [x] 明确 `replay.publish.batch-size` 修复策略。
- [x] 先补连续回放分批发布失败测试。
- [x] 先补跳转分批发布和中途停止检查失败测试。
- [x] 实现连续调度和跳转发布按 `batch-size` 分批。
- [x] 运行阶段定向测试和模块默认回归。
- [x] 运行默认全量测试并记录真实环境默认跳过情况。
- [x] 尝试运行显式真实环境测试并记录结果。
- [x] 回写 Phase 05 Review、修复索引状态、设计文档和交付审阅任务单。

## Review

- 修复策略：保留 `replay-server.replay.publish.batch-size`，不引入 RocketMQ 批量发送 API；在 `ReplayScheduler` 和 `ReplayJumpService` 内按配置做服务层分批发布。
- TDD 失败证据：补测试后首次运行 `mvn -pl replay-server -am "-Dtest=ReplaySchedulerTest,ReplayJumpServiceTest" -DfailIfNoTests=false test`，失败于缺少 batch-size 构造路径。
- 连续调度新增 `shouldCheckSessionStateBetweenSchedulerPublishBatches`，断言 batch-size 为 2 时同一批内保持顺序发布，批次结束后检查状态，并只推进到最后成功发布帧水位。
- 时间跳转新增 `shouldStopJumpPublishingBetweenBatches`，断言 batch-size 为 2 时停止状态在批次边界中断后续事件和周期快照发布，不执行最终时钟跳转和水位同步。
- `ReplayScheduler` 已从配置读取 `publish.batch-size`，连续回放窗口归并后按批发布；发布异常仍进入 `FAILED` 且不推进水位。
- `ReplayJumpService` 已从配置读取 `publish.batch-size`，跳转补偿事件和周期快照均按批发布；停止或失败发生后不会继续伪装跳转成功。
- `mvn -pl replay-server -am "-Dtest=ReplaySchedulerTest,ReplayJumpServiceTest" -DfailIfNoTests=false test` 通过：`Tests run: 15, Failures: 0, Errors: 0, Skipped: 0`。
- `mvn -pl replay-server -am "-Dtest=ReplaySchedulerTest,ReplayJumpServiceTest,ReplayServerPropertiesTest,ReplayFlowIntegrationTest,ReplaySpringFlowIntegrationTest" -DfailIfNoTests=false test` 通过：`Tests run: 23, Failures: 0, Errors: 0, Skipped: 0`。
- `mvn -pl replay-server -am test` 通过：`Tests run: 114, Failures: 0, Errors: 0, Skipped: 1`；跳过项为默认关闭的真实环境测试。
- `mvn test` 通过：根反应堆 `common`、`logger-server`、`replay-server` 均为 `BUILD SUCCESS`。
- `mvn -pl replay-server -am "-Dtest=ReplayRealEnvironmentTest" "-Dreplay.real-env.test=true" -DfailIfNoTests=false test` 通过：`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`。
- surefire dump 处置结论：检查 `common/target/surefire-reports`、`logger-server/target/surefire-reports`、`replay-server/target/surefire-reports`，未发现 `.dump` 或 `.dumpstream`。
- 文档闭环：已回写 `fix-steps.md`、`phase-05.md`、`005-final.md` 和 `todo-31` 修复闭环。
