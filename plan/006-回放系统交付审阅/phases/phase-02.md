# Phase 02 事件表配置与 TDengine 降级修复

当前状态：已完成

## 1. 阶段目标

修复生产配置和历史数据兼容问题：

- 补齐 `replay-server.replay.event-messages` 生产配置，避免所有表默认被识别为周期表。
- 修复 `time_control_{instanceId}` 表不存在时无法降级到态势表 `MIN/MAX(simtime)` 的问题。

## 2. 实现思路

事件表配置必须和 `005-final.md` 中的设计示例保持一致，至少提供一组生产默认配置或在 `application-dev.yml` 中明确配置当前项目事件消息类型。测试需要证明 YAML 绑定后 `ReplayTableClassifier` 能把配置命中的表识别为事件表。

TDengine 降级不能把所有查询异常都吞掉。建议在 Repository 层明确区分“控制表不存在或控制点无数据”与“连接失败、SQL 语法错误、权限错误”等真实故障。只有前者允许降级到态势表，后者应继续抛出，避免掩盖生产故障。

## 3. 需要新增或修改的文件

| 文件 | 说明 |
| ---- | ---- |
| `replay-server/src/main/resources/application.yml` | 补齐 `replay.event-messages` 配置。 |
| `replay-server/src/main/resources/application-dev.yml` | 如事件消息与环境相关，在 dev 配置中补齐或覆盖。 |
| `replay-server/src/main/java/com/szzh/replayserver/repository/ReplayTimeControlRepository.java` | 修复控制表缺失时的降级逻辑。 |
| `replay-server/src/test/java/com/szzh/replayserver/config/ReplayServerPropertiesTest.java` | 新增 YAML 风格事件消息绑定测试。 |
| `replay-server/src/test/java/com/szzh/replayserver/service/ReplayTableClassifierTest.java` | 新增配置命中事件表的回归测试。 |
| `replay-server/src/test/java/com/szzh/replayserver/repository/ReplayTimeControlRepositoryTest.java` | 新增 `time_control` 缺表降级测试和非降级异常测试。 |
| `replay-server/src/test/resources/application-test.yml` | 必要时补齐测试 profile 的事件消息配置。 |

## 4. 待办条目

| 编号 | 待办 | 简要说明 | 前置依赖 |
| ---- | ---- | ---- | ---- |
| 02-01 | 明确事件消息默认配置 | 从 `005-final.md` 设计示例和当前业务配置中确认事件消息类型与编号，写入配置计划。 | Phase 00 |
| 02-02 | 新增事件配置绑定失败测试 | 验证当前 `application.yml` 缺少事件配置时分类结果不满足跳转事件补偿语义。 | 02-01 |
| 02-03 | 新增控制表缺失降级测试 | 模拟 TDengine 返回控制表不存在异常，断言继续查询 `MIN/MAX(simtime)`。 | 02-02 |
| 02-04 | 补齐事件表配置 | 在主配置或 dev 配置中增加 `replay.event-messages`，保持与设计示例一致。 | 02-02 |
| 02-05 | 修复控制表缺失降级 | 只对表不存在或无控制点的场景降级，其他 TDengine 故障继续抛出。 | 02-03 |
| 02-06 | 补分类与降级回归测试 | 覆盖事件表命中、全部周期表、缺控制表起止时间降级、真实故障不降级。 | 02-04, 02-05 |
| 02-07 | 运行阶段测试 | 运行配置、分类和时间范围 Repository 测试。 | 02-06 |
| 02-08 | 回写阶段 Review | 记录事件消息配置来源、降级边界和测试结果。 | 02-07 |

## 5. 验证要求

- 默认或 dev 配置加载后，至少有设计要求的事件类消息配置。
- 命中 `messageType + messageCode` 的子表被识别为 `EVENT`。
- `time_control` 表不存在且态势表有数据时，能从态势表解析起止时间。
- TDengine 连接失败、权限失败等非兼容场景不得被误判为“无控制点”。

## 6. 当前无需澄清的问题

如果事件消息类型必须以真实业务协议为准，执行阶段应先核对当前部署配置；当前文档阶段暂无阻塞性疑问。

## Review

### 实际改动

- 事件消息配置来源采用 `005-final.md` 中的设计示例：`messageType=1001,messageCode=[1,2,3]` 与 `messageType=1002,messageCode=[8]`。
- `replay-server/src/main/resources/application.yml` 已补齐 `replay-server.replay.event-messages`，避免生产默认配置下事件配置为空导致所有子表被识别为周期表。
- `ReplayTimeControlRepository` 已区分控制表兼容缺失与真实 TDengine 故障：控制表无数据或控制表不存在时返回 `null` 并降级查询 `situation_{instanceId}` 的 `MIN/MAX(simtime)`；连接失败、权限失败、非缺表 SQL 异常继续抛出。
- `ReplayServerPropertiesTest` 新增主配置 YAML 加载断言，`ReplayTableClassifierTest` 新增设计默认事件配置命中断言，`ReplayTimeControlRepositoryTest` 新增控制表缺失降级和连接失败不降级断言。

### TDD 证据

- 红灯命令：`mvn -pl replay-server -am "-Dtest=ReplayServerPropertiesTest,ReplayTableClassifierTest,ReplayTimeControlRepositoryTest" -DfailIfNoTests=false test`。
- 红灯结果：`ReplayServerPropertiesTest.shouldLoadDesignEventMessagesFromApplicationYaml` 因主配置缺少事件配置失败；`ReplayTimeControlRepositoryTest.shouldFallbackToSituationRangeWhenTimeControlTableMissing` 因控制表不存在直接抛 `BadSqlGrammarException` 失败。

### 验证结果

- `mvn -pl replay-server -am "-Dtest=ReplayServerPropertiesTest,ReplayTableClassifierTest,ReplayTimeControlRepositoryTest" -DfailIfNoTests=false test`：通过，13 个测试，0 失败，0 错误，0 跳过。
- `mvn -pl replay-server -am test`：通过，common 与 replay-server Reactor 均 SUCCESS；replay-server 路径 109 个测试，0 失败，0 错误，1 个真实环境测试按开关跳过。
- `mvn clean test`：通过，四模块 Reactor 均 SUCCESS；clean 后 surefire XML 汇总为 48 个报告文件、180 个测试、0 失败、0 错误、2 跳过。
- 跳过项为显式开关控制的真实环境测试：`RealEnvironmentFullFlowTest` 与 `ReplayRealEnvironmentTest`，本阶段未开启真实 TDengine/RocketMQ 验证。

### 遗留风险

- 缺表识别依赖 TDengine/JDBC 异常消息包含表名、`table` 或常见 `not exist/no such table` 语义；后续真实环境若出现不同厂商或版本错误文本，需要在 Phase 03 真实环境验证中补充对应样本。
- 事件消息默认配置目前按设计文档示例落地。如果生产业务协议新增事件类 `messageType/messageCode`，需要通过配置扩展，不应改动分类代码。
