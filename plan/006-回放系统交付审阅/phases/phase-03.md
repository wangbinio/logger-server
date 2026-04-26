# Phase 03 真实环境入口与内容验证补强

当前状态：已完成

## 1. 阶段目标

补齐真实环境测试的验收价值：

- 通过真实 `broadcast-global` 发送创建和停止消息，验证生产全局监听入口。
- 通过真实 `broadcast-{instanceId}` 发送控制消息，验证动态实例订阅入口。
- 从真实 `situation-{instanceId}` 消费回放结果，并校验内容、协议字段、顺序、重复和 tag 映射。
- 让 `application-real.yml` 被真实环境测试实际激活。

## 2. 实现思路

真实环境测试继续使用显式开关 `-Dreplay.real-env.test=true`，但不能绕过生产消息入口。测试应启动完整 Spring 上下文和 RocketMQ 注解监听器，向 `broadcast-global` 发送 create/stop 平台协议包；创建成功后再向实例广播 topic 发送 start、pause、rate、resume、jump 等控制消息。

TDengine 准备数据时应保留期望列表，消费态势消息后逐条断言 `senderId`、`messageType`、`messageCode`、`rawData` 中的 `simTime`，并断言顺序和无重复。

## 3. 需要新增或修改的文件

| 文件 | 说明 |
| ---- | ---- |
| `replay-server/src/test/java/com/szzh/replayserver/integration/ReplayRealEnvironmentTest.java` | 改为真实全局入口驱动，并补内容断言。 |
| `replay-server/src/test/resources/application-real.yml` | 作为真实环境测试 profile 被实际激活。 |
| `replay-server/src/test/resources/application-test.yml` | 保持常规测试不依赖真实 RocketMQ/TDengine。 |
| `tasks/todo-xx-*.md` | 执行阶段记录真实环境测试命令、日志和结果。 |

## 4. 待办条目

| 编号 | 待办 | 简要说明 | 前置依赖 |
| ---- | ---- | ---- | ---- |
| 03-01 | 调整真实环境 profile 测试 | 将真实环境测试切换为 `@ActiveProfiles("real")` 或等价显式加载，断言实际配置已生效。 | Phase 01, Phase 02 |
| 03-02 | 新增全局 create 入口测试 | 通过真实 RocketMQ 向 `broadcast-global` 发送 create，等待 `ReplaySession` 进入 `READY`。 | 03-01 |
| 03-03 | 新增全局 stop 入口测试 | 通过真实 RocketMQ 向 `broadcast-global` 发送 stop，断言实例订阅取消并移除会话。 | 03-02 |
| 03-04 | 补齐内容期望模型 | 将写入 TDengine 的帧封装为期望对象，记录 sender、type、code、simTime 和 rawData。 | 03-02 |
| 03-05 | 校验真实回放内容 | 消费 `situation-{instanceId}` 后逐条断言协议字段、rawData、顺序、无重复、无额外消息。 | 03-04 |
| 03-06 | 覆盖跳转真实路径 | 在真实环境中至少验证一次向前或向后跳转，确保事件补偿和周期快照按配置生效。 | 03-05 |
| 03-07 | 运行真实环境测试 | 执行 `mvn -pl replay-server -am -Dtest=ReplayRealEnvironmentTest -Dreplay.real-env.test=true -DfailIfNoTests=false test`。 | 03-06 |
| 03-08 | 归档真实环境证据 | 在任务单 Review 中记录命令、结果、关键日志和 surefire 报告状态。 | 03-07 |

## 5. 验证要求

- 默认 `mvn test` 仍跳过真实环境测试。
- 开启 `-Dreplay.real-env.test=true` 后，真实测试不再绕过 `broadcast-global`。
- 真实测试能证明 `ReplayGlobalBroadcastListener`、动态实例消费者、TDengine 查询和 RocketMQ 发布共同工作。
- 测试断言不只校验数量，还校验内容、顺序和无重复。

## 6. 当前无需澄清的问题

真实环境地址沿用当前 YAML 配置；如执行阶段环境不可用，应记录为环境阻塞，而不是修改测试降级为伪验证。

## 7. Review

- 已将 `ReplayRealEnvironmentTest` 改为显式激活 `dev + real` profile，并断言真实环境配置已加载 `ReplayGlobalBroadcastListener`。
- 已移除测试中的 `ReplayLifecycleService.createReplay/stopReplay` 直调路径，改为通过真实 `broadcast-global` 发送 create/stop，通过真实 `broadcast-{instanceId}` 发送 jump。
- 已在真实 TDengine 写入事件帧和周期帧期望模型，并从真实 `situation-{instanceId}` 消费后校验 `senderId`、`messageType`、`messageCode`、`rawData`、`rawData.simTime`、发布顺序和重复帧。
- 已修复 `application-real.yml` 在 profile-specific 配置中使用 `spring.profiles.include` 导致 Spring Boot 拒绝加载的问题，并将真实环境全局监听开关设为 `true`。
- 红灯记录：`mvn -pl replay-server -am "-Dtest=ReplayRealEnvironmentTest" "-Dreplay.real-env.test=true" -DfailIfNoTests=false test` 首次失败，错误为 `application-real.yml` 中 `spring.profiles.include` 在 profile-specific resource 内非法。
- 真实环境验证：同一命令修复后通过，`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`；关键日志包含 `broadcast-global` 全局监听容器启动、`replay_create_success`、`replay_jump_success messageType=1200 messageCode=5 currentReplayTime=2600 lastDispatchedSimTime=2600`、`replay_stop_success`。
- 默认跳过验证：`mvn -pl replay-server -am "-Dtest=ReplayRealEnvironmentTest" -DfailIfNoTests=false test` 通过，`Tests run: 1, Skipped: 1`。
- 模块回归：`mvn -pl replay-server -am test` 通过，`Tests run: 109, Failures: 0, Errors: 0, Skipped: 1`。
- 全量回归：`mvn test` 通过。
- 空白检查：`git diff --check` 通过，仅保留当前 Windows 工作区的 CRLF 换行提示。
