# 记录侧控制配置命名收敛任务单

## 执行依据

- `logger-server/src/main/resources/application.yml`
- `logger-server/src/main/java/com/szzh/loggerserver/config/LoggerServerProperties.java`
- `logger-server/src/main/java/com/szzh/loggerserver/support/constant/MessageConstants.java`
- `replay-server/src/main/resources/application.yml`
- `tasks/lessons.md`

## 计划

- [x] 增加配置结构测试，先证明 `messages.instance` 仍存在且不符合目标结构。
- [x] 将 `logger-server.protocol.messages.instance` 重命名为 `logger-server.protocol.messages.control`。
- [x] 将 `LoggerServerProperties.Messages` 的绑定对象从 `Instance` 改为 `Control`。
- [x] 将 `MessageConstants` 和相关测试切换到 `messages.control`。
- [x] 更新 README 与 ARCHITECTURE 中的 logger-server 配置示例。
- [x] 运行 logger 侧定向测试与全量测试。
- [x] 记录验证结果。

## Review

- TDD 失败验证：先新增 `ApplicationProfileConfigurationTest.shouldUseControlNodeForInstanceControlMessages`，执行 `mvn -pl logger-server -am "-Dtest=ApplicationProfileConfigurationTest" -DfailIfNoTests=false test`，失败点为旧配置 `logger-server.protocol.messages.instance.message-type` 仍返回 `1100`，证明原配置节点尚未收敛。
- 配置迁移：`logger-server/src/main/resources/application.yml` 已将 `logger-server.protocol.messages.instance` 改为 `logger-server.protocol.messages.control`，与 `replay-server` 的 `control` 命名保持一致。
- 代码迁移：`LoggerServerProperties.Messages` 已将绑定对象从 `instance` 改为 `control`，内部配置类从 `Instance` 改为 `Control`；`MessageConstants` 改为从 `messages.getControl()` 读取记录侧实例控制消息类型和消息码。
- 测试迁移：`InstanceBroadcastMessageHandlerTest` 与 `MessageConstantsTest` 已改用 `getControl()` 构造配置；新增配置测试保留对旧 `logger-server.protocol.messages.instance.message-type` 的空值断言，作为防回归保护。
- 文档同步：`README.md` 与 `ARCHITECTURE.md` 中 logger-server 的协议配置示例已改为 `control`。
- 定向测试通过：`mvn -pl logger-server -am "-Dtest=ApplicationProfileConfigurationTest,MessageConstantsTest,InstanceBroadcastMessageHandlerTest" -DfailIfNoTests=false test`，结果 `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`。
- logger 模块回归通过：`mvn -pl logger-server -am test`，结果 `Tests run: 60, Failures: 0, Errors: 0, Skipped: 1`。
- 根工程回归通过：`mvn test`，结果 `Tests run: 114, Failures: 0, Errors: 0, Skipped: 1`；跳过项为默认关闭的真实环境测试，符合既有约定。
- surefire dump 检查：`logger-server/target/surefire-reports/2026-04-26T21-51-08_779-jvmRun1.dump` 存在但长度为 `0`，未包含异常堆栈或失败信息，不影响上述测试结论。
- 残留检查：`messages.instance` / `protocol.messages.instance` / `getMessages().getInstance` 仅剩新增测试中的旧路径空值断言；`logger-server/src`、`README.md`、`ARCHITECTURE.md` 未发现 YAML `instance:` 配置节点残留。
