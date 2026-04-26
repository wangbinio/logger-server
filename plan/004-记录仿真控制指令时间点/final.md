# 记录仿真控制指令时间点详细设计

## 1. 背景

后续回放系统需要根据仿真运行过程中的控制命令还原仿真时间轴，包括开始、暂停、继续，以及未来可能支持的倍速调整。

当前系统已经在 `broadcast-{instanceId}` 实例级广播主题中处理开始、暂停、继续命令，并由 `SimulationControlService` 驱动 `SimulationClock` 与会话状态迁移。现阶段需要在这些控制命令实际生效时，把控制时间点写入 TDengine，供后续回放系统查询。

## 2. 目标

- 在每个仿真实例下新增一张控制时间点记录表。
- 在开始、暂停、继续命令实际改变仿真状态时记录一条控制时间点。
- 记录字段需要支持后续回放计算，并保留基础审计信息。
- 控制记录写入失败时只打印日志，不影响原本控制流程继续执行。
- TDengine 建表与写入逻辑继续封装在 `TdengineSchemaService` 和 `TdengineWriteService` 中。

## 3. 非目标

- 本次不实现倍速控制消息的协议解析、消息分发和时钟倍率调整。
- 本次不新增 `action` 字段区分开始、暂停、继续；开始和继续统一通过 `rate=1` 表达。
- 本次不改变 RocketMQ 消费确认语义。
- 本次不改变态势数据记录表结构。
- 本次不为控制记录写入失败增加重试队列、补偿任务或告警系统。

## 4. 已确认设计决策

- 控制记录写入失败时，控制流程继续执行，只记录错误日志。
- 控制表加入 `sender_id`、`msgtype`、`msgcode` 三个审计字段。
- `start` 与 `resume` 均记录 `rate=1`，不额外增加 `action` 字段。
- `pause` 记录 `rate=0`。
- `ts` 采用服务处理时间，允许忽略服务处理时间与真实消息发送时间之间的误差。
- 倍速预留字段使用可表达小数倍率的数据类型。

## 5. 数据模型

### 5.1 表命名

每个仿真实例创建一张控制时间点表：

```sql
time_control_{instanceId}
```

`instanceId` 需要复用现有 TDengine 表名清洗规则，把非法字符替换为下划线，避免 SQL 对象名非法。

示例：

```sql
time_control_instance_001
```

### 5.2 表结构

```sql
CREATE TABLE IF NOT EXISTS time_control_{instanceId} (
    ts TIMESTAMP,
    simtime BIGINT,
    rate DOUBLE,
    sender_id INT,
    msgtype INT,
    msgcode INT
)
```

字段含义：

字段类型含义`tsTIMESTAMP`服务处理控制命令并写入记录的墙钟时间。`simtimeBIGINT`控制命令生效后的仿真时间，单位为毫秒。`rateDOUBLE`控制命令生效后的回放倍率语义。`1` 表示正常运行，`0` 表示暂停，未来可记录 `0.5`、`2.0` 等倍速。`sender_idINT`原始控制消息发送方 ID。`msgtypeINT`原始控制消息类型。`msgcodeINT`原始控制消息编号。

### 5.3 `rate` 语义

`rate` 是回放时间轴的控制语义，不完全等同于 `SimulationClock.speed`。

- 开始命令实际生效后记录 `rate=1`。
- 暂停命令实际生效后记录 `rate=0`。
- 继续命令实际生效后记录 `rate=1`。
- 未来倍速命令实际生效后记录对应倍率，例如 `0.5`、`2.0`。

`SimulationClock.updateSpeed(double)` 要求倍率大于 0，因此 `rate=0` 只表示回放时间轴暂停，不应直接作为 `updateSpeed(0)` 的参数。

## 6. 建表设计

### 6.1 建表时机

控制时间点表应在仿真实例创建时创建，与现有态势记录超级表创建流程保持一致。

当前实例创建流程位于 `SimulationLifecycleService.handleCreate(...)`：

1. 创建 `SimulationSession`。
2. 调用 `TdengineSchemaService.createStableIfAbsent(instanceId)` 创建态势记录超级表。
3. 订阅实例级广播和态势主题。
4. 将会话状态更新为 `READY`。

新增控制时间点表后，建议流程调整为：

1. 创建 `SimulationSession`。
2. 调用 `TdengineSchemaService.createStableIfAbsent(instanceId)` 创建态势记录超级表。
3. 调用 `TdengineSchemaService.createTimeControlTableIfAbsent(instanceId)` 创建控制时间点表。
4. 订阅实例级广播和态势主题。
5. 将会话状态更新为 `READY`。

这样可以确保实例进入 `READY` 后，后续开始命令不会因为控制表不存在而写入失败。

### 6.2 建表 SQL 封装

`TdengineConstants` 增加控制表命名与 SQL 构造能力：

- `TIME_CONTROL_PREFIX = "time_control_"`
- `CREATE_TIME_CONTROL_TABLE_SQL_TEMPLATE`
- `buildTimeControlTableName(String instanceId)`
- `buildCreateTimeControlTableSql(String instanceId)`

`TdengineSchemaService` 增加：

```java
/**
 * 按实例创建控制时间点表。
 *
 * @param instanceId 仿真实例 ID。
 */
public void createTimeControlTableIfAbsent(String instanceId)
```

## 7. 写入设计

### 7.1 写入 DTO

新增独立 DTO，避免复用态势记录写入命令。

建议类名：

```java
com.szzh.loggerserver.model.dto.TimeControlRecordCommand
```

字段：

- `instanceId`
- `simTime`
- `rate`
- `senderId`
- `messageType`
- `messageCode`

校验规则：

- `instanceId` 不能为空。
- `rate` 不能为负数。
- `simTime` 使用控制命令生效后的仿真时间。

说明：

- 本 DTO 不需要 `rawData`。
- `rate=0` 是合法值，用于表示暂停。
- 未来倍速命令可以复用该 DTO 写入小数倍率。

### 7.2 写入 SQL

`TdengineConstants` 增加：

```sql
INSERT INTO time_control_{instanceId}
VALUES (NOW, ?, ?, ?, ?, ?)
```

参数顺序建议：

1. `simtime`
2. `rate`
3. `sender_id`
4. `msgtype`
5. `msgcode`

使用 `NOW` 表示服务处理时间，符合当前设计决策。

### 7.3 写入服务

`TdengineWriteService` 增加：

```java
/**
 * 写入仿真控制时间点。
 *
 * @param command 控制时间点写入命令。
 */
public void writeTimeControl(TimeControlRecordCommand command)
```

写入失败处理：

- `TdengineWriteService.writeTimeControl(...)` 内部可以沿用现有写入重试次数。
- 如果重试后仍失败，可以抛出 `BusinessException.tdengineWrite(...)`。
- `SimulationControlService` 调用方必须捕获写入异常并只记录日志，不能阻断控制流程。

## 8. 控制流程集成

### 8.1 记录原则

只记录实际生效的控制命令。

需要记录：

- `READY -> RUNNING` 的开始命令。
- `RUNNING -> PAUSED` 的暂停命令。
- `PAUSED -> RUNNING` 的继续命令。
- 当前实现中，`PAUSED` 状态下收到开始命令会按继续处理，此时如果继续实际生效，应记录 `rate=1`。

不记录：

- 会话不存在的控制命令。
- 运行态下重复开始。
- 非运行态下暂停。
- 运行态下重复继续。
- 非暂停态下继续。
- 未知控制消息。
- 解析失败消息。

### 8.2 写入时机

建议在状态迁移成功后记录控制时间点。

原因：

- `simtime` 应表达命令生效后的仿真时间。
- 暂停命令需要先调用 `SimulationClock.pause()` 固化暂停时刻，再读取 `currentSimTimeMillis()`。
- 继续命令需要先调用 `SimulationClock.resume()` 恢复时钟基准，再读取当前仿真时间。
- 写入失败不影响控制流程，因此没有必要在状态迁移前写入。

流程示意：

1. 校验会话存在和当前状态合法。
2. 执行 `SimulationClock` 动作。
3. 更新 `SimulationSessionState`。
4. 读取 `session.getSimulationClock().currentSimTimeMillis()`。
5. 构造 `TimeControlRecordCommand`。
6. 调用 `TdengineWriteService.writeTimeControl(...)`。
7. 如果写入失败，捕获异常并记录错误日志。
8. 按原逻辑输出控制成功日志并返回。

### 8.3 `SimulationControlService` 依赖调整

`SimulationControlService` 当前只依赖：

- `SimulationSessionManager`
- `LoggerMetrics`

新增控制时间点记录后，需要增加：

- `TdengineWriteService`

为了保持已有单元测试易用性，可以保留测试构造路径，或者提供可注入空实现策略。推荐直接调整测试，使用 Mockito mock `TdengineWriteService`，这样能覆盖写入行为。

### 8.4 非阻断写入方法

`SimulationControlService` 内部建议增加私有方法：

```java
/**
 * 记录控制时间点，写入失败只记录日志，不阻断控制流程。
 *
 * @param session 仿真实例会话。
 * @param protocolData 协议数据。
 * @param rate 控制命令生效后的回放倍率。
 */
private void recordTimeControlQuietly(SimulationSession session,
                                      ProtocolData protocolData,
                                      double rate)
```

该方法职责：

- 读取当前仿真时间。
- 构造 `TimeControlRecordCommand`。
- 调用 `TdengineWriteService.writeTimeControl(...)`。
- 捕获 `RuntimeException`。
- 记录包含 `instanceId`、`messageType`、`messageCode`、`senderId`、`simtime`、`rate`、失败原因的错误日志。

该方法不应：

- 修改会话状态。
- 调用 `session.recordFailure(...)`。
- 重新抛出异常。
- 触发 RocketMQ 重试。

## 9. 异常与日志语义

### 9.1 写入失败

控制记录写入失败属于控制记录缺失风险，但不影响仿真控制命令本身执行。

建议日志级别：

```text
ERROR
```

建议日志字段：

- `result=time_control_write_failed`
- `instanceId`
- `topic=-`
- `messageType`
- `messageCode`
- `senderId`
- `simtime`
- `rate`
- `sessionState`
- `reason`

### 9.2 写入成功

可以记录 `INFO` 或 `DEBUG`。为避免控制消息日志过多，建议使用 `DEBUG`，核心控制成功日志仍由现有 `start_success`、`pause_success`、`resume_success` 保留。

建议日志字段：

- `result=time_control_write_success`
- `instanceId`
- `messageType`
- `messageCode`
- `senderId`
- `simtime`
- `rate`

### 9.3 会话状态

控制记录写入失败不调用 `session.recordFailure(...)`，避免把仿真会话标记为业务失败或污染最后错误信息。

## 10. 测试设计

本任务按 TDD 实施。先补失败测试，再实现代码。

### 10.1 `TdengineConstantsTest`

新增测试：

- 应能构造控制时间点表名，并复用实例 ID 清洗规则。
- 应能构造控制时间点建表 SQL。
- 应能构造控制时间点写入 SQL。
- 空白实例 ID 应被拒绝。

重点断言：

- 表名前缀为 `time_control_`。
- SQL 包含 `simtime BIGINT`。
- SQL 包含 `rate DOUBLE`。
- SQL 包含 `sender_id INT`、`msgtype INT`、`msgcode INT`。

### 10.2 `TdengineSchemaServiceTest`

新增测试：

- `createTimeControlTableIfAbsent("instance-001")` 会调用 `JdbcTemplate.execute(...)`。
- SQL 包含 `CREATE TABLE IF NOT EXISTS time_control_instance_001`。
- 非法实例 ID 会被拒绝，且不调用 JDBC。

### 10.3 `TdengineWriteServiceTest`

新增测试：

- `writeTimeControl(...)` 会调用 `JdbcTemplate.update(...)`。
- 参数顺序为 `simtime`、`rate`、`sender_id`、`msgtype`、`msgcode`。
- 写入失败时按配置重试。
- 重试后仍失败时抛出 TDengine 写入业务异常。
- `rate=0` 合法。
- 负数 `rate` 被 DTO 拒绝。

### 10.4 `SimulationLifecycleServiceTest`

新增或调整测试：

- 创建实例时同时创建态势记录超级表和控制时间点表。
- 如果控制时间点表创建失败，实例创建流程应失败，保持与现有建表失败语义一致。

说明：

- 创建阶段的建表失败仍属于实例初始化失败，和运行期控制记录写入失败不同。

### 10.5 `SimulationControlServiceTest`

新增或调整测试：

- `READY -> RUNNING` 后写入一条 `rate=1` 的控制时间点。
- `RUNNING -> PAUSED` 后写入一条 `rate=0` 的控制时间点，`simtime` 为暂停后的冻结仿真时间。
- `PAUSED -> RUNNING` 后写入一条 `rate=1` 的控制时间点。
- `PAUSED` 状态下收到开始命令并按继续处理时，写入 `rate=1`。
- 重复开始、非法暂停、重复继续、缺失会话不写入控制时间点。
- 控制时间点写入失败时，控制状态仍然迁移成功。
- 控制时间点写入失败时，不向外抛出异常。

### 10.6 回归测试

需要运行：

```powershell
mvn test
```

如果本地 Java 版本存在差异，需要优先使用项目要求的 Java 8 环境执行验证。

## 11. 实施步骤

 1. 新增 `TimeControlRecordCommand` DTO 及单元测试。
 2. 扩展 `TdengineConstants`，增加控制时间点表名、建表 SQL、写入 SQL 构造方法。
 3. 扩展 `TdengineSchemaService`，增加 `createTimeControlTableIfAbsent(...)`。
 4. 调整 `SimulationLifecycleService.handleCreate(...)`，创建实例时同步创建控制时间点表。
 5. 扩展 `TdengineWriteService`，增加 `writeTimeControl(...)`。
 6. 调整 `SimulationControlService` 构造函数依赖，引入 `TdengineWriteService`。
 7. 在开始、暂停、继续实际生效后调用非阻断控制时间点记录方法。
 8. 补齐单元测试和必要的集成测试。
 9. 执行 `mvn test` 验证。
10. 根据测试结果修正实现，直到所有相关测试通过。

## 12. 风险与取舍

### 12.1 控制记录可能缺失

运行期控制记录写入失败只打印日志，不阻断控制流程。这符合当前裁定，但意味着回放系统未来可能遇到缺失控制时间点的情况。

后续如果回放一致性要求提升，可以增加补偿队列或失败记录表。

### 12.2 `start` 与 `resume` 不可直接从 `rate` 区分

当前设计中，开始和继续都记录 `rate=1`，不增加 `action` 字段。因此仅看控制表无法区分某条 `rate=1` 是开始还是继续。

如果后续回放系统需要区分，可通过第一条 `rate=1` 视为开始，暂停后的 `rate=1` 视为继续。也可以在未来增加 `action` 字段或由 `msgcode` 辅助判断。

### 12.3 服务处理时间存在误差

`ts=NOW` 记录的是服务处理时间，不是消息生产时间。当前已确认忽略该误差。

对回放更关键的是 `simtime` 和 `rate`，`ts` 主要用于审计和排序辅助。

### 12.4 控制消息并发

当前控制服务依赖会话状态判断和 `SimulationClock` 同步方法保证基本安全。本次设计不额外引入控制消息串行化机制。

如果未来出现同一实例控制消息并发消费导致顺序不稳定，需要在消费端或会话层增加实例级串行化策略。

## 13. 验收标准

- 创建仿真实例时会创建 `time_control_{instanceId}` 表。
- 开始命令实际生效后写入 `rate=1`。
- 暂停命令实际生效后写入 `rate=0`。
- 继续命令实际生效后写入 `rate=1`。
- 控制记录包含 `simtime`、`rate`、`sender_id`、`msgtype`、`msgcode`。
- 控制记录写入失败时，开始、暂停、继续的原有状态迁移仍然成功。
- 重复或非法控制命令不会产生控制时间点记录。
- 所有新增和既有相关测试通过。
