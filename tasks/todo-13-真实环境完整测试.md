# 任务：真实环境完整测试

## 背景

根据 `plan/003-真实环境完整测试/draft.md`，当前需要基于已经配置好的真实 RocketMQ 与 TDengine 环境，补齐一条完整的端到端测试链路：启动 `logger-server`，发送创建、启动、暂停、继续、停止控制消息，并持续向 `situation-{instanceId}` 发送真实态势消息，最终校验 TDengine 中各子表的落库情况。

## 执行项

- [x] 复核 `draft.md`、总体设计与现有真实环境测试基础，明确消息节奏、控制流程和校验口径。
- [x] 设计真实环境测试用例，确保默认测试套件不会误跑真实环境测试。
- [x] 实现完整真实环境测试代码，包括 RocketMQ 生产、状态等待、TDengine 查询与结果断言。
- [x] 运行目标测试并记录执行结果。
- [x] 如有必要，回写相关文档与本任务 review。

## 验收标准

- 存在可独立触发的真实环境完整测试用例。
- 测试覆盖创建、启动、暂停、继续、停止和 `situation-{instanceId}` 连续发送流程。
- 测试停止后能够查询并校验 TDengine 中相关表的数据情况。
- 默认 `mvn test` 不会被真实环境依赖用例污染。

## Review

- 已新增 `src/test/java/com/szzh/loggerserver/integration/RealEnvironmentFullFlowTest.java`，通过真实 Spring Boot 上下文 + 真实 RocketMQ + 真实 TDengine 的方式，覆盖创建、启动、暂停、继续、停止、60 秒态势消息发送以及停止后逐表计数校验。
- 已将 `TopicSubscriptionManagerTest` 中原有真实 RocketMQ 用例改为仅在显式设置 `-Dlogger.real-env.test=true` 时执行，避免默认测试套件继续耦合真实环境。
- 已验证默认测试套件仍然可用：在 Java 8 环境下执行 `mvn -q test` 通过。
  - `JAVA_HOME=C:\Users\summer\.jdks\corretto-1.8.0_482`
- 已尝试执行目标真实环境测试：
  - 命令：`mvn -q -Dlogger.real-env.test=true -Dtest=RealEnvironmentFullFlowTest test`
  - 结果：失败，阻塞点不在测试代码本身，而在当前 RocketMQ 真实环境。
- 当前真实环境阻塞结论：
  - `Test-NetConnection 192.168.233.109 -Port 9876` 成功，说明 namesrv 端口可达。
  - 但测试预热 `broadcast-global` topic 时，RocketMQ 返回 `Can not find Message Queue for this topic, broadcast-global`，底层原因为 `RemotingConnectException: connect to null failed`。
  - 这表明当前 namesrv 可访问，但 broker 路由或 topic 可用性仍不满足真实环境测试启动条件，导致 `logger-server` 的全局监听器无法正常启动。
