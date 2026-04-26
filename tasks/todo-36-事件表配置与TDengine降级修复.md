# 事件表配置与 TDengine 降级修复任务单

## 执行依据

- `plan/006-回放系统交付审阅/fix-steps.md`
- `plan/006-回放系统交付审阅/phases/phase-02.md`
- `tasks/todo-31-回放系统交付审阅.md`
- `plan/005-回放系统设计/005-final.md`

## 计划

- [x] 将 Phase 02 文档状态标记为进行中。
- [x] 确认事件消息默认配置来源。
- [x] 补充 `application.yml` 事件配置加载测试。
- [x] 补充事件表分类回归测试。
- [x] 补充 `time_control` 缺表降级测试。
- [x] 补充非兼容 TDengine 异常不降级测试。
- [x] 补齐生产事件消息配置。
- [x] 修复控制表缺失时的有限降级逻辑。
- [x] 运行 Phase 02 定向测试。
- [x] 运行 `mvn -pl replay-server -am test` 和必要全量回归。
- [x] 回写 Phase 02 Review、修复索引状态和本任务单 Review。

## Review

### 完成内容

- 已按 `005-final.md` 配置示例确认事件消息默认配置：`1001:[1,2,3]` 与 `1002:[8]`。
- 已在 `replay-server/src/main/resources/application.yml` 补齐 `replay-server.replay.event-messages`。
- 已修复 `ReplayTimeControlRepository` 的控制表缺失兼容逻辑：控制点无数据或 `time_control_{instanceId}` 表不存在时降级到态势表 `MIN/MAX(simtime)`；非缺表 TDengine 故障继续抛出。
- 已补充配置绑定、事件分类、缺表降级和非降级异常测试。

### TDD 证据

- 红灯命令：`mvn -pl replay-server -am "-Dtest=ReplayServerPropertiesTest,ReplayTableClassifierTest,ReplayTimeControlRepositoryTest" -DfailIfNoTests=false test`。
- 红灯结果：主配置事件配置缺失导致 `ReplayServerPropertiesTest` 失败；控制表不存在直接抛 `BadSqlGrammarException` 导致 `ReplayTimeControlRepositoryTest` 失败。

### 验证结果

- `mvn -pl replay-server -am "-Dtest=ReplayServerPropertiesTest,ReplayTableClassifierTest,ReplayTimeControlRepositoryTest" -DfailIfNoTests=false test`：通过，13 个测试，0 失败，0 错误，0 跳过。
- `mvn -pl replay-server -am test`：通过；replay-server 路径 109 个测试，0 失败，0 错误，1 跳过。
- `mvn clean test`：通过；clean 后 surefire XML 汇总为 48 个报告文件、180 个测试、0 失败、0 错误、2 跳过。
- 跳过项为默认不开启的真实环境测试：`RealEnvironmentFullFlowTest`、`ReplayRealEnvironmentTest`。

### 后续边界

- 本阶段未开启 `-Dreplay.real-env.test=true`，真实 TDengine 错误文本与 RocketMQ 全链路验证留给 Phase 03。
- 如果生产协议新增事件类消息，只需扩展 YAML 配置；分类逻辑保持按 `messageType + messageCode` 匹配。
