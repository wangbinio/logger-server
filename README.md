# logger-platform

`logger-platform` 是一个基于 Spring Boot、RocketMQ 与 TDengine 的仿真记录与回放平台。项目由 `common`、`logger-server`、`replay-server` 三个 Maven 模块组成：记录服务负责消费仿真控制与态势消息并写入 TDengine，回放服务负责从 TDengine 读取已记录数据并重新发布回放态势。

当前发布版本：`v0.2`

## 当前能力

### logger-server

- 固定订阅 `broadcast-global`，处理仿真实例创建与停止。
- 根据实例创建消息动态订阅 `broadcast-{instanceId}` 和 `situation-{instanceId}`。
- 支持实例 `start`、`pause`、`resume`、`stop` 控制。
- 维护 `SimulationSession` 与 `SimulationClock`，态势入库时记录当前仿真时间。
- 写入 TDengine `situation_{instanceId}` 超级表和按消息维度拆分的子表。
- 记录控制时间点到 `time_control_{instanceId}`，其中 `start`、`resume` 记 `rate=1`，`pause`、`stop` 记 `rate=0`。
- 控制时间点写入失败时只记录日志，不阻断主控制流程。

### replay-server

- 独立 Spring Boot 服务，固定订阅 `broadcast-global` 中的回放任务消息。
- 创建回放任务后动态订阅 `broadcast-{instanceId}`，不消费 `situation-{instanceId}`。
- 读取 TDengine `time_control_{instanceId}` 与 `situation_{instanceId}`，发现态势子表并按配置区分事件表、周期表。
- 支持回放 `create`、`stop`、`start`、`pause`、`resume`、`rate`、`jump`。
- 连续回放按 `(lastDispatchedSimTime, currentReplayTime]` 查询并发布，发布成功后推进水位。
- 时间跳转支持向前事件补偿、向后事件补偿，以及周期表目标时刻前最后一帧补偿。
- `replay-server.replay.publish.batch-size` 作用于服务层分批发布，连续调度和跳转发布都会在批次边界检查状态。

### common

- 平台协议解析与构包。
- JSON 序列化工具。
- RocketMQ Topic 命名。
- TDengine 表名清洗与命名。
- 通用业务异常与协议解析异常。

## 技术栈

- Java 8
- Spring Boot 2.7.12
- RocketMQ Spring Boot Starter 2.2.3
- TDengine Java Connector 3.8.0
- Spring JDBC 与 HikariCP
- Lombok
- JUnit 5 与 Mockito

## 模块结构

```text
logger-platform
├── common          # 公共协议、JSON、Topic、TDengine 命名和异常
├── logger-server   # RocketMQ 到 TDengine 的仿真记录服务
├── replay-server   # TDengine 到 RocketMQ 的仿真回放服务
├── plan            # 设计、开发步骤和交付审阅文档
├── tasks           # 本地任务单和交付记录
├── pom.xml         # Maven 父工程
├── README.md
└── ARCHITECTURE.md
```

## Topic 与职责

| Topic | logger-server | replay-server |
| --- | --- | --- |
| `broadcast-global` | 固定订阅，消费仿真创建与停止消息。 | 固定订阅，消费回放创建与停止消息。 |
| `broadcast-{instanceId}` | 动态订阅，消费仿真实例控制消息。 | 动态订阅，消费回放实例控制消息。 |
| `situation-{instanceId}` | 动态订阅，消费态势并写入 TDengine。 | 只发布回放态势，不订阅消费。 |

同一 Topic 上通过协议 `messageType` 隔离记录和回放语义：

| 场景 | messageType | 默认 messageCode |
| --- | --- | --- |
| 记录侧全局生命周期 | `0` | `create=0`，`stop=1` |
| 回放侧全局生命周期 | `1` | `create=0`，`stop=1` |
| 记录侧实例控制 | `1100` | `start=1`，`pause=5`，`resume=6` |
| 回放侧实例控制 | `1200` | `start=1`，`pause=2`，`resume=3`，`rate=4`，`jump=5`，`metadata=9` |

## TDengine 数据

记录服务为每个仿真实例维护两类表：

```sql
CREATE STABLE IF NOT EXISTS situation_{instanceId}
(
  ts TIMESTAMP,
  simtime BIGINT,
  rawdata VARBINARY(8192)
)
TAGS (
  sender_id INT,
  msgtype INT,
  msgcode INT
);
```

态势子表命名：

```text
situation_{messageType}_{messageCode}_{senderId}_{instanceId}
```

控制时间点表：

```sql
CREATE TABLE IF NOT EXISTS time_control_{instanceId}
(
  ts TIMESTAMP,
  simtime BIGINT,
  rate DOUBLE,
  sender_id INT,
  msgtype INT,
  msgcode INT
);
```

`replay-server` 以 `time_control_{instanceId}` 计算回放起止时间，以 `situation_{instanceId}` 发现可回放子表，再按事件表和周期表语义组织回放发布。

## 配置文件

### logger-server

| 文件 | 说明 |
| --- | --- |
| `logger-server/src/main/resources/application.yml` | 应用名、默认 profile、日志级别、记录侧协议消息码、会话和写入参数。 |
| `logger-server/src/main/resources/application-dev.yml` | RocketMQ nameserver、TDengine JDBC、消费者组配置。 |

关键配置：

```yaml
logger-server:
  protocol:
    messages:
      global:
        message-type: 0
        create-message-code: 0
        stop-message-code: 1
      control:
        message-type: 1100
        start-message-code: 1
        pause-message-code: 5
        resume-message-code: 6
  write:
    retry-times: 3
    batch-size: 500
```

### replay-server

| 文件 | 说明 |
| --- | --- |
| `replay-server/src/main/resources/application.yml` | 应用名、默认 profile、回放侧协议消息码、事件表配置、查询、调度和发布参数。 |
| `replay-server/src/main/resources/application-dev.yml` | RocketMQ nameserver、TDengine JDBC、消费者组和生产者组配置。 |
| `replay-server/src/test/resources/application-test.yml` | 回放侧测试 profile 配置。 |
| `replay-server/src/test/resources/application-real.yml` | 回放侧真实环境测试补充配置。 |

关键配置：

```yaml
replay-server:
  protocol:
    messages:
      global:
        message-type: 1
        create-message-code: 0
        stop-message-code: 1
      control:
        message-type: 1200
        start-message-code: 1
        pause-message-code: 2
        resume-message-code: 3
        rate-message-code: 4
        jump-message-code: 5
        metadata-message-code: 9
  replay:
    event-messages:
      - message-type: 1001
        message-codes: [1, 2, 3]
      - message-type: 1002
        message-codes: [8]
    query:
      page-size: 1000
    scheduler:
      tick-millis: 50
    publish:
      batch-size: 500
      retry-times: 3
```

## 构建与测试

执行全部默认测试：

```powershell
mvn test
```

按模块执行测试：

```powershell
mvn -pl logger-server -am test
mvn -pl replay-server -am test
```

打包全部模块：

```powershell
mvn package
```

构建产物：

```text
logger-server/target/logger-server-0.2.0.jar
replay-server/target/replay-server-0.2.0.jar
```

真实环境测试默认不运行，需要 RocketMQ 与 TDengine 可用时显式开启：

```powershell
mvn -pl logger-server -am "-Dtest=RealEnvironmentFullFlowTest" "-Dlogger.real-env.test=true" -DfailIfNoTests=false test
mvn -pl replay-server -am "-Dtest=ReplayRealEnvironmentTest" "-Dreplay.real-env.test=true" -DfailIfNoTests=false test
```

## 运行

启动记录服务：

```powershell
java -jar logger-server/target/logger-server-0.2.0.jar
```

启动回放服务：

```powershell
java -jar replay-server/target/replay-server-0.2.0.jar
```

指定 profile 或外部配置时使用 Spring Boot 标准参数：

```powershell
java -jar logger-server/target/logger-server-0.2.0.jar --spring.profiles.active=dev
java -jar replay-server/target/replay-server-0.2.0.jar --spring.profiles.active=dev
```

## 架构文档

完整架构、消息流、状态迁移、TDengine 数据模型、回放调度和测试策略见 [ARCHITECTURE.md](ARCHITECTURE.md)。

## 发布信息

`v0.2` 是记录与回放双服务发布版本，包含 RocketMQ 接入、动态订阅、记录侧 TDengine 写入、控制时间点记录、回放侧 TDengine 查询、连续回放、倍速控制、时间跳转、批量发布控制、记录侧与回放侧控制配置命名统一、平台级 README/ARCHITECTURE 文档，以及默认测试闭环和显式真实环境测试入口。
