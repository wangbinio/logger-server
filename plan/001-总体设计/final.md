# logger-server 总体技术方案

## 1. 文档目标

本文档基于 `draft.md` 中的业务描述与当前代码骨架，给出 `logger-server` 的可实施技术方案，目标是指导后续按阶段编码落地，而不是停留在概念层。

本方案需要回答以下问题：

- 如何稳定地订阅 `broadcast-global`、`broadcast-{instanceId}`、`situation-{instanceId}` 三类主题。
- 如何维护支持暂停、继续、后续倍速扩展的仿真时间。
- 如何在 TDengine 中设计表结构，兼顾写入性能、按实例隔离和后续查询能力。
- 当前从旧项目复制而来的 `pom.xml`、`application.yml` 应保留什么、删除什么。
- 按现有 Spring Boot 骨架，后续代码应该如何拆分模块并分阶段实现。

## 2. 当前项目现状

### 2.1 已有代码

当前仓库只有以下基础内容：

- Spring Boot 启动类 `LoggerServerApplication`
- 协议对象 `ProtocolData`
- 协议工具 `ProtocolMessageUtil`
- 空的上下文加载测试

说明当前项目还处于脚手架阶段，尚未建立以下关键能力：

- RocketMQ 消费者生命周期管理
- 动态 topic 订阅与取消订阅
- 仿真实例运行状态管理
- TDengine 建表与写入能力
- 配置模型与监控日志

协议解析部分不再继续抽象讨论，后续实现直接复用现有 `ProtocolData` 和 `ProtocolMessageUtil` 的解析口径，不额外修正字段宽度、包头包尾或示例文字差异。

### 2.2 当前配置问题

`pom.xml` 与配置文件明显来自其他项目，存在以下不一致：

- `artifactId` 仍为 `platform-smcs`，与当前项目名称不符。
- `spring.application.name` 仍为 `CoPlatform`，与当前服务职责不符。
- 上下文路径 `/xtfz-logger`、安全配置、Redis、OAuth2、Knife4j、多语言、文件上传等均与当前记录服务无直接关系。
- 引入了 Web、WebSocket、Security、OAuth2、Redis、MapStruct、XStream、Jsoup 等大量冗余依赖。

结论已经明确：当前项目继续保留为 Spring Boot 单体后台服务，并立即执行一次依赖与配置收敛，其中 `pom.xml` 需要同步清理和修正，不再把它保留为后续建议项。

## 3. 需求与约束

### 3.1 功能需求

1. 服务启动后固定订阅 `broadcast-global`。
2. 收到任务创建消息后：
   - 解析 `instanceId`
   - 建立对应 TDengine 存储结构
   - 开始订阅 `broadcast-{instanceId}` 与 `situation-{instanceId}`
3. 收到启动仿真消息后：
   - 启动实例级仿真时钟
   - 开始真正写入态势消息
4. 收到暂停仿真消息后：
   - 暂停仿真时钟
   - 停止推进仿真时间
5. 收到继续仿真消息后：
   - 恢复仿真时钟
   - 继续写入态势消息
6. 收到任务停止消息后：
   - 停止实例所有记录工作
   - 取消实例级 topic 订阅
   - 释放运行时资源

### 3.2 非功能需求

- 多实例并发时互不影响。
- 任务创建、启动、暂停、继续、停止必须幂等。
- 消息消费异常不能导致整个应用退出。
- 后续支持仿真加速、减速时，不重写主流程。
- 数据写入路径尽量简单，避免引入不必要 ORM 复杂度。

## 4. 总体设计结论

### 4.1 架构风格

采用“单进程、固定全局监听、实例级会话上下文”的设计。

运行时由三层组成：

1. 全局入口层：固定监听 `broadcast-global`。
2. 实例会话层：每个 `instanceId` 维护独立 `SimulationSession`。
3. 基础设施层：负责 RocketMQ 动态订阅、TDengine 建表写入、协议解析、日志与监控。

### 4.2 核心设计决策

#### 决策一：使用实例会话对象管理运行时状态

每个仿真实例对应一个 `SimulationSession`，统一维护：

- `instanceId`
- 当前生命周期状态
- 仿真时钟对象
- 实例级 RocketMQ 订阅句柄
- 写库统计信息
- 创建时间、最后消息时间、最后异常信息

这样可以避免把状态散落在多个 Service 中，后续支持倍速、重连、监控时也更易扩展。

#### 决策二：不依赖注解动态生成监听器

`@RocketMQMessageListener` 适合固定 topic，不适合大量实例动态增删。

因此建议：

- `broadcast-global` 使用固定监听器。
- 实例级 topic 使用程序化创建与销毁消费者容器。
- 由 `TopicSubscriptionManager` 统一管理 `instanceId -> consumer` 映射。

这样做的好处是：

- 可以按实例启动和停止订阅
- 易于做幂等校验和异常恢复
- 不需要在 Spring 容器中动态注册大量业务 Bean

#### 决策三：仿真时间采用虚拟时钟模型

仿真时间不能简单等于当前系统时间，否则暂停、继续、倍速会变得不可维护。

推荐使用以下字段描述时钟状态：

- `baseSimTime`：基准仿真时间
- `baseWallClockMillis`：最近一次状态切换对应的系统时间
- `speed`：时间倍率，默认 `1.0`
- `running`：当前是否推进

计算公式：

- 运行中：`currentSimTime = baseSimTime + (now - baseWallClockMillis) * speed`
- 暂停时：`currentSimTime = baseSimTime`

状态切换规则：

- 启动：`baseSimTime = System.currentTimeMillis()`，`baseWallClockMillis = now`，`running = true`
- 暂停：先结算一次当前仿真时间，再写回 `baseSimTime`，最后 `running = false`
- 继续：`baseWallClockMillis = now`，`running = true`
- 调整倍速：先结算当前仿真时间，再更新 `speed` 与 `baseWallClockMillis`

这个设计可以平滑支持后续“加速”和“减速”需求。

#### 决策四：TDengine 采用官方 Java Connector 的 WebSocket 路线

TDengine 操作不再使用 MyBatis Plus，直接采用官方 `taos-jdbcdriver`，通过 WebSocket 方式连接 `taosAdapter`。

选择该方案的原因是：

- 动态建表
- 动态子表名
- 大量时间序列写入
- 官方文档明确支持 Java 通过 `jdbc:TAOS-WS://` 建立 WebSocket 连接
- WebSocket 方式不依赖本地原生客户端库，更适合当前 Spring Boot 服务部署
- 官方参数绑定写入能力在 WebSocket 路线上可直接使用，且更适合批量写入

因此本项目确定采用以下实现策略：

- 使用官方 Java Connector 连接 TDengine，连接 URL 采用 `jdbc:TAOS-WS://{host}:6041/{db}`
- Spring 层使用 `spring-boot-starter-jdbc` 管理 `DataSource`
- 常规 DDL、查询和低频写入使用 `JdbcTemplate` / `PreparedStatement`
- 高频插入优先采用官方 `stmt` 参数绑定模式
- 当需要“自动建子表并写入”时，优先使用 WebSocket 扩展接口 `TSWSPreparedStatement`

这样既保留了 Spring 体系内的可维护性，也与 TDengine 官方推荐的连接和写入方式保持一致。

## 5. 实例生命周期设计

### 5.1 生命周期状态

建议定义如下状态枚举：

| 状态 | 含义 |
| ---- | ---- |
| `PREPARING` | 已收到创建消息，正在初始化资源 |
| `READY` | 实例资源就绪，但尚未开始记录 |
| `RUNNING` | 仿真进行中，允许写入态势消息 |
| `PAUSED` | 仿真暂停，消息可选择丢弃或仅计数不入库 |
| `STOPPED` | 实例已停止，不再接受写入 |
| `FAILED` | 初始化或运行发生不可恢复异常 |

### 5.2 状态迁移

- `broadcast-global` 创建消息：`PREPARING -> READY`
- `broadcast-{instanceId}` 启动消息：`READY/PAUSED -> RUNNING`
- `broadcast-{instanceId}` 暂停消息：`RUNNING -> PAUSED`
- `broadcast-{instanceId}` 继续消息：`PAUSED -> RUNNING`
- `broadcast-global` 停止消息：`READY/RUNNING/PAUSED/FAILED -> STOPPED`

### 5.3 幂等规则

- 同一个 `instanceId` 重复创建：若会话未停止，则直接忽略并记录 warn 日志。
- 重复启动：若已是 `RUNNING`，只刷新统计日志，不重复初始化时钟。
- 重复暂停：若已是 `PAUSED`，直接忽略。
- 重复继续：若已是 `RUNNING`，直接忽略。
- 重复停止：若已 `STOPPED` 或不存在，会话安全返回。

## 6. RocketMQ 订阅设计

### 6.1 主题约定

建议最终统一主题命名为：

- `broadcast-global`
- `broadcast-{instanceId}`
- `situation-{instanceId}`

### 6.2 消费者设计

#### 全局消费者

固定监听 `broadcast-global`，负责：

- 解析任务创建和任务停止
- 调用 `SimulationSessionManager`

#### 实例控制消费者

每个实例创建一个控制消费者，监听 `broadcast-{instanceId}`，负责：

- 解析启动、暂停、继续命令
- 驱动 `SimulationClock`
- 更新实例状态

#### 实例态势消费者

每个实例创建一个态势消费者，监听 `situation-{instanceId}`，负责：

- 解析协议消息
- 判断实例是否处于 `RUNNING`
- 生成写库记录
- 调用 `SituationWriter`

### 6.3 为什么建议“每个实例一组消费者”

优点：

- 实例边界清晰，便于停用与排障
- 订阅关系简单，不需要在单个 consumer 内做复杂 topic 路由
- 停止任务时，能直接释放该实例资源

代价：

- 实例数很多时，consumer 数量会上升

适用前提：

- 同时运行的仿真实例数量有限，通常为个位数或几十级

如果未来实例数量达到上百级，应升级为“共享消费者 + 内部路由”的模型。

## 7. 协议解析与消息处理流程

### 7.1 全局消息处理流程

1. 消费 `broadcast-global`
2. 调用 `ProtocolMessageUtil.parseData`
3. 校验 `messageType == 0`
4. 根据 `messageCode` 分支：
   - `0`：任务创建
   - `1`：任务停止
5. 创建消息时解析 `rawData` JSON，提取 `instanceId`
6. 调用 `SimulationSessionManager.createSession(instanceId)`

### 7.2 控制消息处理流程

1. 消费 `broadcast-{instanceId}`
2. 调用 `ProtocolMessageUtil.parseData`
3. 校验 `messageType == 1100`
4. 根据 `messageCode` 分支：
   - `1`：启动仿真
   - `5`：暂停仿真
   - `6`：继续仿真
5. 调用 `SimulationControlService`

### 7.3 态势消息处理流程

1. 消费 `situation-{instanceId}`
2. 调用 `ProtocolMessageUtil.parseData`
3. 获取 `senderId`、`messageType`、`messageCode`、`rawData`
4. 查询对应 `SimulationSession`
5. 仅当会话状态为 `RUNNING` 时继续处理
6. 调用虚拟时钟获取 `simtime`
7. 构造 TDengine 插入 SQL
8. 执行写入并更新统计

### 7.4 消息丢弃策略

以下情况建议丢弃并记录计数器，不抛出致命异常：

- 协议解析失败
- 找不到对应 `instanceId` 会话
- 会话不处于 `RUNNING`
- TDengine 短时不可用且未达到重试成功

原因是本服务本质为记录服务，不应反向阻断仿真主链路。

## 8. TDengine 表设计

### 8.1 方案选择

结合草案要求与当前业务场景，推荐采用“每个实例一个超级表，每类消息一个子表”的方案，兼容草案原始思路，同时增强查询能力。

### 8.2 推荐 DDL

实例创建时执行：

```sql
CREATE STABLE IF NOT EXISTS situation_${instanceId} (
    ts TIMESTAMP,
    simtime BIGINT,
    rawdata BINARY(8192)
) TAGS (
    sender_id INT,
    msgtype INT,
    msgcode INT
);
```

写入时按 `messageType + messageCode + senderId` 创建或复用子表：

```sql
INSERT INTO situation_${messageType}_${messageCode}_${senderId}_${instanceId}
USING situation_${instanceId}
TAGS (${senderId}, ${messageType}, ${messageCode})
VALUES (NOW, ${simtime}, ?);
```

### 8.3 相对草案的修正

草案中的超表 tags 为 `msgtype`、`msgcode`，但插入表名中又包含 `senderId`。建议将 `sender_id` 一并放入 tags，原因如下：

- 发送方通常是常见查询维度
- 仅依赖表名表达 `senderId`，后续统计不便
- tags 过滤比字符串解析表名更可靠

### 8.4 为什么不建议“每条消息一个表”

不建议把粒度做得更细，否则会导致：

- 子表数量爆炸
- 元数据管理复杂
- 查询与运维成本升高

当前推荐粒度是“实例 + 消息类型 + 消息编号 + 发送方”。

### 8.5 可选的中长期优化方案

如果后续需要跨实例统一统计，可将存储模型改为全局超级表：

```sql
CREATE STABLE situation_log (
    ts TIMESTAMP,
    simtime BIGINT,
    rawdata BINARY(8192)
) TAGS (
    instance_id BINARY(64),
    sender_id INT,
    msgtype INT,
    msgcode INT
);
```

阶段一不建议直接采用该方案，原因是当前需求明显偏向按实例隔离、按实例清理，分实例超表更贴近业务心智模型。

## 9. 模块划分建议

建议按如下包结构组织：

```text
com.szzh.loggerserver
├─ config
│  ├─ LoggerProperties
│  ├─ RocketMqConsumerFactory
│  └─ TdengineConfig
├─ mq
│  ├─ GlobalBroadcastListener
│  ├─ InstanceBroadcastMessageHandler
│  ├─ SituationMessageHandler
│  └─ TopicSubscriptionManager
├─ domain
│  ├─ session
│  │  ├─ SimulationSession
│  │  ├─ SimulationSessionState
│  │  └─ SimulationSessionManager
│  └─ clock
│     └─ SimulationClock
├─ service
│  ├─ SimulationLifecycleService
│  ├─ SimulationControlService
│  ├─ SituationRecordService
│  ├─ TdengineSchemaService
│  └─ TdengineWriteService
├─ model
│  ├─ dto
│  │  ├─ TaskCreatePayload
│  │  └─ SituationRecordCommand
│  └─ vo
├─ util
│  ├─ ProtocolData
│  ├─ ProtocolMessageUtil
│  └─ JsonUtil
└─ support
   ├─ exception
   ├─ constant
   └─ metric
```

### 9.1 关键类职责

- `SimulationSessionManager`：维护实例会话 Map、提供幂等创建与销毁。
- `TopicSubscriptionManager`：负责实例级 consumer 创建、启动、停止、回收。
- `SimulationClock`：封装暂停、继续、倍速计算逻辑。
- `SimulationLifecycleService`：处理全局创建、停止。
- `SimulationControlService`：处理启动、暂停、继续。
- `SituationRecordService`：处理态势消息接入到写库之间的业务流程。
- `TdengineSchemaService`：负责建超表、检查表存在。
- `TdengineWriteService`：负责 insert 语句拼装与执行。

## 10. 配置与依赖收敛建议

### 10.1 建议保留的依赖

- `spring-boot-starter-jdbc`
- `spring-boot-starter-test`
- `rocketmq-spring-boot-starter`
- `taos-jdbcdriver`
- `lombok`
- `jackson-databind`

### 10.2 建议删除的依赖

- `spring-boot-starter-web`
- `spring-boot-starter-websocket`
- `spring-boot-starter-data-redis`
- `spring-boot-starter-security`
- `spring-boot-starter-oauth2-client`
- `spring-boot-starter-oauth2-resource-server`
- `mybatis-plus-boot-starter`
- `hutool-all`
- `commons-lang`
- `aspectjrt`
- `aspectjweaver`
- `mapstruct`
- `mapstruct-processor`
- `xstream`
- `javax.servlet-api`
- `jsoup`
- 额外显式声明的 `junit`

说明：

- 如果后续需要暴露健康检查 HTTP 接口，可以重新引入 `spring-boot-starter-web` 与 `spring-boot-starter-actuator`。
- 当前阶段不再引入 MyBatis Plus。

### 10.3 application.yml 建议收敛为

建议保留以下配置域：

```yaml
spring:
  application:
    name: logger-server

rocketmq:
  name-server: 127.0.0.1:9876
  consumer:
    global-group: logger-global-consumer
    instance-group-prefix: logger-instance

logger-server:
  tdengine:
    jdbc-url: jdbc:TAOS-WS://localhost:6041/logger?timezone=UTC-8&charset=utf-8&varcharAsString=true
    username: root
    password: taosdata
    driver-class-name: com.taosdata.jdbc.ws.WebSocketDriver
  protocol:
    max-payload-size: 102400
  session:
    cleanup-delay-seconds: 30
  write:
    retry-times: 3
```

### 10.4 必须清理的错误项

- 修改 `artifactId` 为 `logger-server`
- 修改 `spring.application.name` 为 `logger-server`
- 删除 `context-path: /xtfz-logger`
- 删除安全、Redis、Knife4j、多语言、文件上传等无关配置

## 11. 异常处理与可观测性

### 11.1 异常分层

- 协议异常：记录 warn，丢弃该消息
- 业务状态异常：记录 info 或 warn，不中断消费
- TDengine 写入异常：记录 error，按次数重试，超过阈值后进入失败计数
- 订阅初始化异常：实例状态置为 `FAILED`

### 11.2 日志建议

至少输出以下结构化字段：

- `instanceId`
- `topic`
- `messageType`
- `messageCode`
- `senderId`
- `sessionState`
- `simtime`
- `costMs`
- `result`

### 11.3 指标建议

建议后续接入 Micrometer，输出：

- 每实例接收消息数
- 每实例成功写入数
- 协议解析失败数
- TDengine 写入失败数
- 当前活跃实例数

## 12. 测试策略

按照项目规范，正式编码阶段应采用 TDD。

### 12.1 优先编写的单元测试

- `ProtocolMessageUtil` 对合法和非法协议包的解析测试
- `SimulationClock` 的启动、暂停、继续、倍速计算测试
- `SimulationSessionManager` 的幂等创建、重复停止测试
- `SituationRecordService` 对不同状态下消息写入/丢弃分支测试

### 12.2 集成测试建议

- 模拟 `broadcast-global` 创建消息，验证实例会话建立
- 模拟启动、暂停、继续，验证 `simtime` 计算正确
- Mock TDengine 写入接口，验证插入 SQL 与参数构造

### 12.3 暂不建议做的测试

- 直接依赖真实 RocketMQ 与真实 TDengine 的端到端测试

原因是当前项目尚在设计和骨架阶段，优先保证核心状态机与时钟逻辑可测。

## 13. 实施步骤建议

### 第一阶段：清理骨架

1. 精简 `pom.xml`
2. 重写 `application.yml`
3. 引入配置类与基础常量

### 第二阶段：实现核心领域模型

1. 实现 `SimulationSession`
2. 实现 `SimulationClock`
3. 实现 `SimulationSessionManager`

### 第三阶段：实现 MQ 与写库基础设施

1. 实现固定全局监听器
2. 实现实例级动态订阅管理
3. 实现 TDengine 建表与写入服务

### 第四阶段：联调业务流程

1. 串联创建、启动、暂停、继续、停止
2. 验证多实例并发场景
3. 补充日志与指标

## 14. 对 draft 中问题的直接回答

### 14.1 `pom.xml` 和 `application.yml` 是否需要清理

需要，而且已经明确为当前阶段必须执行项。当前冗余依赖和配置过多，会让后续实现误入无关框架能力，增加调试复杂度。

### 14.2 数据记录流程是否有问题

流程主体正确，但需要补足三个关键点：

- 实例状态机
- 动态订阅管理
- 可暂停可恢复的仿真时钟

如果不补这三项，流程在并发实例和异常场景下会失控。

### 14.3 数据库表设计是否有问题

原始思路基本可行，但 tags 缺少 `senderId`，且需要明确子表粒度。推荐采用“每实例一个超表，每类消息一个子表”的增强版方案。

在连接与写入实现上，已经确定不使用 MyBatis Plus，而是采用 TDengine 官方 Java Connector 的 WebSocket 路线，并优先使用官方参数绑定写入能力。

### 14.4 如何支持后续加速和减速

通过虚拟时钟模型实现，不要把仿真时间直接绑定到系统时间。只要时钟模型设计正确，倍速仅是修改 `speed` 参数，不影响主流程。

## 15. 最终建议

建议将本项目定位为“无界面、无外部 HTTP 依赖、专注消息接入与时序写库的后台记录服务”。

第一版实现应坚持两个原则：

- 主链路尽量简单：协议解析 -> 状态判断 -> 计算仿真时间 -> 写 TDengine
- 运行时状态集中管理：所有实例相关状态统一收敛到 `SimulationSession`

这样既能满足当前需求，也为后续支持倍速控制、统计分析和故障恢复留出扩展空间。
