# Phase 06 联调验收与交付收尾

## 1. 阶段目标

完成回放系统第一版交付前的验证和文档收敛，包括集成测试、真实环境测试开关、日志指标、构建验证、设计文档同步和验收清单。

## 2. 实现思路

先用 Mock Repository 和 Mock RocketMQ 打通回放全链路集成测试，再提供显式开关启用真实 RocketMQ 与 TDengine 环境测试。真实环境测试默认不进入常规 CI，避免外部依赖导致本地和流水线不稳定。

本阶段还需要回看 `005-final.md` 和所有 phase 文档，确保实际实现与设计一致。

## 3. 需要新增或调整的文件

| 文件 | 说明 |
| ---- | ---- |
| `replay-server/src/main/java/.../support/metric/ReplayMetrics.java` | 回放内存级指标。 |
| `replay-server/src/test/java/.../integration/ReplayFlowIntegrationTest.java` | Mock 全链路集成测试。 |
| `replay-server/src/test/java/.../integration/ReplayRealEnvironmentTest.java` | 真实环境可选测试。 |
| `replay-server/src/test/resources/application-test.yml` | 测试配置。 |
| `replay-server/src/test/resources/application-real.yml` | 真实环境测试配置模板。 |
| `plan/005-回放系统设计/005-final.md` | 按实现结果同步更新。 |
| `plan/005-回放系统设计/development-steps.md` | 更新阶段状态。 |
| `plan/005-回放系统设计/phases/phase-*.md` | 补充每阶段 Review。 |
| `ARCHITECTURE.md` | 如项目拆分已经落地，补充新架构说明。 |

## 4. 待办条目

| 编号 | 待办 | 简要说明 | 前置依赖 |
| ---- | ---- | ---- | ---- |
| 06-01 | 新增指标测试 | 验证活跃会话数、发布成功数、发布失败数、查询失败数、跳转次数和状态冲突计数。 | Phase 05 |
| 06-02 | 实现 `ReplayMetrics` | 先采用内存级计数器，保持与记录系统指标风格一致。 | 06-01 |
| 06-03 | 新增 Mock 集成测试 | 覆盖创建、元信息发布、启动、暂停、继续、倍速、向前跳转、向后跳转、停止。 | 06-02 |
| 06-04 | 打通 `ReplayFlowIntegrationTest` | 使用 Mock Repository 和 Mock Publisher 验证服务编排，不依赖真实外部服务。 | 06-03 |
| 06-05 | 新增真实环境测试开关 | 通过 `-Dreplay.real-env.test=true` 才启用真实 RocketMQ 和 TDengine 测试。 | 06-04 |
| 06-06 | 实现真实环境测试 | 使用已记录数据创建回放任务，验证控制 topic、发布 topic 和 TDengine 查询。 | 06-05 |
| 06-07 | 补齐结构化日志 | 确保关键日志包含 `result`、`instanceId`、`topic`、`messageType`、`messageCode`、`currentReplayTime`、`lastDispatchedSimTime`、`rate`。 | 06-02 |
| 06-08 | 执行构建验证 | 在 Java 8 环境运行 `mvn test` 和必要的模块级测试命令。 | 06-07 |
| 06-09 | 更新设计文档 | 根据实际实现同步 `005-final.md`、`development-steps.md` 和各 phase Review。 | 06-08 |
| 06-10 | 更新架构文档 | 如果多模块拆分已经完成，更新 `ARCHITECTURE.md` 中的服务边界和部署说明。 | 06-09 |

## 5. 验证要求

- Mock 集成测试覆盖完整回放主链路。
- 真实环境测试默认跳过，显式开启时能运行。
- `mvn test` 在 Java 8 环境下通过。
- 文档与实际实现一致，不保留已经过期的阶段状态。
- 回放服务不依赖 Redis。

## 6. 当前无需澄清的问题

本阶段没有阻塞性疑问。

## 7. Review

### 7.1 实际改动

- 新增 `ReplayMetrics` 内存级指标，覆盖活跃会话数、发布成功数、发布失败数、查询失败数、跳转次数和状态冲突数。
- 将指标接入回放发布、连续调度查询失败、跳转查询失败和控制状态冲突路径。
- 新增 `ReplayFlowIntegrationTest`，使用 Mock Repository 与 Mock RocketMQ Sender 覆盖创建、元信息发布、启动、暂停、继续、倍速、向前跳转、向后跳转和停止。
- 新增 `ReplayRealEnvironmentTest`，通过 `-Dreplay.real-env.test=true` 显式启用真实环境测试；测试直接使用当前 YAML 的 TDengine 与 RocketMQ 配置，写入真实 TDengine 控制点和态势数据，再通过真实控制 topic 驱动回放并验证真实态势 topic 发布。
- 新增 `application-test.yml` 与 `application-real.yml` 测试资源，真实环境测试默认禁用自动全局监听器。
- 补齐生命周期、控制、调度失败和发布失败路径的结构化日志字段。
- 更新 `005-final.md`、`development-steps.md` 与 `ARCHITECTURE.md`，同步 Phase 06 实际落地状态。

### 7.2 验证结果

- `mvn -pl replay-server -am "-Dtest=ReplayMetricsTest,ReplaySituationPublisherTest,ReplaySchedulerTest,ReplayControlServiceTest,ReplayJumpServiceTest" -DfailIfNoTests=false test` 通过，21 个测试成功。
- `mvn -pl replay-server -am "-Dtest=ReplayFlowIntegrationTest" -DfailIfNoTests=false test` 通过，Mock 全链路集成测试成功。
- `mvn -pl replay-server -am "-Dtest=ReplayRealEnvironmentTest" -DfailIfNoTests=false test` 通过，真实环境测试默认跳过。
- `mvn -pl replay-server -am "-Dtest=ReplayRealEnvironmentTest" "-Dreplay.real-env.test=true" -DfailIfNoTests=false test` 通过，真实 TDengine 与 RocketMQ 回放链路成功。
- `mvn test` 通过，全工程 91 个常规测试成功，1 个真实环境测试按默认策略跳过。

### 7.3 遗留风险

- 真实环境测试会在当前 TDengine `logger` 库中留下 `replay-real-it-*` 测试表，便于后续复查真实回放数据。
- 第一版仍不支持 `sourceInstanceId` 与 `replayInstanceId` 分离，同一实例不能并发记录和回放。
- 第一版未引入外部指标系统，`ReplayMetrics` 仍是内存级计数器。
