# 任务：真实环境测试收敛

## 背景

根据 `plan/003-真实环境完整测试/draft.md`、`tasks/todo-13-真实环境完整测试.md`、`tasks/todo-14-排查真实环境rocketmq监听失败.md`，当前真实环境测试未通过，已知阻塞点集中在 `RealEnvironmentFullFlowTest` 启动完整 Spring 上下文时，`GlobalBroadcastListener` 的自动 RocketMQ 监听容器启动失败。需要在不破坏真实流程覆盖范围的前提下，完成根因分析、修正测试接入方式，并重新验证真实环境全流程。

## 计划

- [x] 复核真实环境测试链路涉及的监听器、生命周期服务、动态订阅与 TDengine 落库逻辑，确认最小修复面。
- [x] 先补充或调整能够锁定失败根因的测试配置，确保默认自动监听器不会阻塞真实环境测试启动。
- [x] 实现真实环境测试收敛方案，保持 `broadcast-global`、`broadcast-{instanceId}`、`situation-{instanceId}` 三类消息链路完整。
- [x] 执行目标真实环境测试与必要的辅助验证，记录结果、数据口径与剩余风险。
- [x] 回写本任务 review，并同步更新相关任务文档。

## Review

- 已确认上次真实环境测试失败并非单一问题，而是两个串联问题：
  - `RealEnvironmentFullFlowTest` 使用 `@SpringBootTest` 启动完整上下文时，`GlobalBroadcastListener` 对应的 `DefaultRocketMQListenerContainer` 在当前真实环境中会于容器注册阶段报 `RemotingConnectException: connect to null failed`，导致测试方法根本无法执行。
  - 在关闭自动全局监听器并改为测试内手工启动全局广播消费者后，真实根因进一步暴露为 TDengine `logger` 数据库不存在，`SimulationLifecycleService.handleCreate` 在建超表前即因 `auth failure: Database not exist` 获取连接失败。
- 已对 `src/test/java/com/szzh/loggerserver/integration/RealEnvironmentFullFlowTest.java` 做如下收敛：
  - 通过测试属性 `logger-server.rocketmq.enable-global-listener=false` 关闭自动 `broadcast-global` 容器，避免 Spring 上下文启动阶段被 RocketMQ 容器阻塞。
  - 在测试内手工创建全局广播消费者，并直接复用 `GlobalBroadcastListener` 的消息解析与生命周期委派逻辑，仍然保持 `broadcast-global` 消息链路真实生效。
  - 在发送创建消息前，基于 `application-local.yml` 中的 TDengine JDBC 配置推导出无库管理连接，执行 `CREATE DATABASE IF NOT EXISTS logger`，确保真实环境首次跑测时也能自动补齐数据库前置条件。
  - 将 TDengine 校验改为等待最终一致状态后再断言，避免真实异步链路带来的瞬时抖动。
- 已完成验证：
  - `mvn -q -Dlogger.real-env.test=true -Dtest=RealEnvironmentFullFlowTest test` 通过。
  - 本次通过实例：`real-it-1777090782653`
  - TDengine 校验结果：25 张子表，每张 11 条记录，实例超表总计 275 条记录。
  - `mvn -q test` 在 Java 8 环境下通过，说明默认测试套件未被真实环境测试污染。
- 当前结论：
  - 真实环境完整测试已经可以在当前 RocketMQ + TDengine 环境下执行成功。
  - `rocketmq-spring` 自动全局监听容器在该环境下仍存在启动不稳定问题，但已被测试内手工消费者方案稳定绕开；该问题若要在生产链路继续深挖，应另开任务专门分析 `DefaultRocketMQListenerContainer` 与当前 broker 路由交互差异。
