# Phase 05 批量发布与交付收尾

当前状态：已完成

## 1. 阶段目标

处理交付审阅的 P3 配置语义问题，并完成修复交付闭环：

- 让 `replay.publish.batch-size` 对连续回放和跳转发布路径产生实际约束或明确收敛为非配置项。
- 完成全量回归、真实环境验证、设计文档和任务记录回写。

## 2. 实现思路

优先选择让 `batch-size` 生效，而不是删除配置。实现可以先不引入 RocketMQ 批量发送 API，而是在服务层按 `batch-size` 分批处理帧列表，每批发布后检查会话状态、停止信号和失败状态。这样可以满足设计中的“每批发布后检查会话状态”和“发布节流”语义，同时保持发送端口简单。

收尾阶段需要把 Phase 00 至 Phase 04 的实际修复写回 `fix-steps.md`、各 phase Review、`tasks/todo-xx` 和必要的 `005-final.md`。如果实现对原设计做了有意识调整，必须同步设计文档，不能只让代码偏离文档。

## 3. 需要新增或修改的文件

| 文件 | 说明 |
| ---- | ---- |
| `replay-server/src/main/java/com/szzh/replayserver/service/ReplayScheduler.java` | 连续回放按 `batch-size` 分批发布并检查状态。 |
| `replay-server/src/main/java/com/szzh/replayserver/service/ReplayJumpService.java` | 跳转补偿和周期快照按 `batch-size` 分批发布并检查状态。 |
| `replay-server/src/main/java/com/szzh/replayserver/config/ReplayServerProperties.java` | 如保留配置，明确默认值和校验；如裁撤配置，同步删除。 |
| `replay-server/src/test/java/com/szzh/replayserver/service/ReplaySchedulerTest.java` | 新增 batch-size 分批发布测试。 |
| `replay-server/src/test/java/com/szzh/replayserver/service/ReplayJumpServiceTest.java` | 新增跳转分批发布和中途停止检查测试。 |
| `plan/006-回放系统交付审阅/fix-steps.md` | 更新各阶段状态。 |
| `plan/006-回放系统交付审阅/phases/phase-*.md` | 补充各阶段 Review。 |
| `plan/005-回放系统设计/005-final.md` | 如行为与设计文字有调整，回写最终语义。 |
| `tasks/todo-xx-*.md` | 记录修复执行和最终验证结果。 |

## 4. 待办条目

| 编号 | 待办 | 简要说明 | 前置依赖 |
| ---- | ---- | ---- | ---- |
| 05-01 | 明确 batch-size 修复策略 | 默认按配置分批处理帧列表，不直接改 RocketMQ 发送端口为批发送。 | Phase 03, Phase 04 |
| 05-02 | 新增连续回放分批测试 | 构造多帧窗口，设置小 batch-size，断言按批发布并在批间检查状态。 | 05-01 |
| 05-03 | 新增跳转分批测试 | 构造跳转事件大列表，断言按 batch-size 分批发布且失败不推进水位。 | 05-02 |
| 05-04 | 实现 batch-size 生效 | 在 `ReplayScheduler` 和 `ReplayJumpService` 中统一分批发布逻辑。 | 05-02, 05-03 |
| 05-05 | 运行阶段测试 | 运行调度、跳转、配置与集成测试。 | 05-04 |
| 05-06 | 运行默认全量测试 | 执行 `mvn test`，确认常规测试通过且真实环境测试默认跳过。 | 05-05 |
| 05-07 | 运行真实环境测试 | 执行显式真实环境测试并记录结果。 | 05-06 |
| 05-08 | 回写文档状态 | 更新 `fix-steps.md`、各 phase Review、必要的 `005-final.md` 和任务单。 | 05-07 |
| 05-09 | 最终交付审阅 | 对照 `todo-31` 逐项标记已修复、已验证或有设计裁定。 | 05-08 |

## 5. 验证要求

- `publish.batch-size` 不再是无效配置。
- 分批发布不能破坏回放帧顺序。
- 分批发布失败时仍遵守“不成功则不推进水位”。
- 默认全量测试和显式真实环境测试都有可复查证据。
- 006 修复计划和 005 总体设计不再互相矛盾。

## 6. 当前无需澄清的问题

本阶段默认选择“让 batch-size 生效”。如果执行阶段决定删除该配置而非实现分批发布，需要先回写设计文档并在 Review 中说明取舍。

## 7. Review

### 7.1 实际改动

- 保留 `replay-server.replay.publish.batch-size` 配置，并将其接入 `ReplayScheduler` 与 `ReplayJumpService` 的 Spring 构造路径。
- `ReplayScheduler` 连续回放发布改为按 `batch-size` 分批处理已归并帧列表；同一批次内保持帧顺序，批次之间检查 `RUNNING` 状态。若批次后会话已暂停或停止，仅推进到最后已成功发布帧的 `simtime`，不把窗口水位伪推进到 `toInclusive`。
- `ReplayJumpService` 跳转事件补偿和周期快照发布改为按 `batch-size` 分批处理；批次之间检查会话是否仍处于可跳转状态。若停止或失败发生在批次边界，立即终止后续发布，不执行最终时钟跳转、水位同步和成功跳转指标。
- 新增连续调度分批测试与跳转分批停止测试，证明 `batch-size` 不再是无效配置，并保持发布顺序与失败不推进水位语义。
- 在 `005-final.md` 中补充 Phase 05 落地说明，在 `todo-31` 中补充 Finding 1 至 Finding 10 的修复闭环。

### 7.2 验证结果

- TDD 失败证据：补测试后首次运行 `mvn -pl replay-server -am "-Dtest=ReplaySchedulerTest,ReplayJumpServiceTest" -DfailIfNoTests=false test` 失败于缺少 batch-size 构造路径。
- `mvn -pl replay-server -am "-Dtest=ReplaySchedulerTest,ReplayJumpServiceTest" -DfailIfNoTests=false test`：通过，`Tests run: 15, Failures: 0, Errors: 0, Skipped: 0`。
- `mvn -pl replay-server -am "-Dtest=ReplaySchedulerTest,ReplayJumpServiceTest,ReplayServerPropertiesTest,ReplayFlowIntegrationTest,ReplaySpringFlowIntegrationTest" -DfailIfNoTests=false test`：通过，`Tests run: 23, Failures: 0, Errors: 0, Skipped: 0`。
- `mvn -pl replay-server -am test`：通过，`Tests run: 114, Failures: 0, Errors: 0, Skipped: 1`；跳过项为默认关闭的真实环境测试。
- `mvn test`：通过，根反应堆 `common`、`logger-server`、`replay-server` 均为 `BUILD SUCCESS`；默认真实环境测试保持跳过策略。
- `mvn -pl replay-server -am "-Dtest=ReplayRealEnvironmentTest" "-Dreplay.real-env.test=true" -DfailIfNoTests=false test`：通过，`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`；真实 RocketMQ/TDengine 链路完成 create、jump、stop。
- surefire dump 检查：`common/target/surefire-reports`、`logger-server/target/surefire-reports`、`replay-server/target/surefire-reports` 中未发现 `.dump` 或 `.dumpstream`。

### 7.3 遗留风险

- 第一版仍未改用 RocketMQ 批量发送 API，`batch-size` 当前语义是服务层发布批次大小和批间状态检查粒度；这与本阶段方案一致，发送端口保持单帧同步发送。
