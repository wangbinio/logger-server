# logger-server

`logger-server` 是一个基于 Spring Boot 的仿真消息记录服务。它从 RocketMQ 订阅仿真实例的控制消息和态势消息，维护实例生命周期与仿真时间，并将运行态势按实例写入 TDengine。

当前发布版本：`v0.1`

## 功能范围

- 固定监听全局广播主题 `broadcast-global`。
- 根据任务创建消息动态订阅 `broadcast-{instanceId}` 和 `situation-{instanceId}`。
- 支持实例创建、启动、暂停、继续和停止。
- 维护实例级仿真时钟，态势写入时记录当前仿真时间。
- 按实例创建 TDengine 超级表，并按 `messageType`、`messageCode`、`senderId` 写入子表。
- 支持协议消息类型和消息码通过 YAML 配置外置。
- 提供单元测试、主流程集成测试和可选真实环境完整测试。

## 技术栈

- Java 8
- Spring Boot 2.7.12
- RocketMQ Spring Boot Starter 2.2.3
- TDengine Java Connector 3.8.0
- Spring JDBC 与 HikariCP
- Lombok
- JUnit 5 与 Mockito

## 架构文档

完整架构、消息流、状态迁移、TDengine 数据模型和测试策略见 [ARCHITECTURE.md](ARCHITECTURE.md)。

## 目录结构

```text
src/main/java/com/szzh/loggerserver
├── config              # 配置绑定、RocketMQ 消费者工厂、TDengine 数据源
├── domain              # 仿真时钟、会话、状态机
├── model               # DTO 与写库命令
├── mq                  # RocketMQ 监听器、动态订阅、入口端口
├── service             # 生命周期、控制、态势记录、TDengine 编排
├── support             # 常量、异常、指标
└── util                # 协议解析、JSON 工具
```

## 配置文件

源码只维护两份 YAML：

- `src/main/resources/application.yml`：通用配置，包含默认 profile、日志级别、协议消息码、会话与写入参数。
- `src/main/resources/application-dev.yml`：开发环境配置，包含 RocketMQ nameserver、TDengine JDBC 和消费者组配置。

默认激活 `dev` profile：

```yaml
spring:
  profiles:
    active: dev
```

关键配置项：

```yaml
rocketmq:
  name-server: 192.168.233.109:9876

logger-server:
  tdengine:
    jdbc-url: jdbc:TAOS-WS://127.0.0.1:6041/logger?timezone=UTC-8&charset=utf-8&varcharAsString=true
    username: root
    password: taosdata
    driver-class-name: com.taosdata.jdbc.ws.WebSocketDriver
  rocketmq:
    global-consumer-group: logger-global-consumer
    instance-consumer-group-prefix: logger-instance
```

协议消息码配置位于 `logger-server.protocol.messages`：

```yaml
logger-server:
  protocol:
    messages:
      global:
        message-type: 0
        create-message-code: 0
        stop-message-code: 1
      instance:
        message-type: 1100
        start-message-code: 1
        pause-message-code: 5
        resume-message-code: 6
```

说明：现有测试代码中仍有历史 `application-local.yml` 读取路径。构建时会在 `process-test-resources` 阶段把 `application-dev.yml` 复制到测试输出目录并命名为 `application-local.yml`，源码中不再维护 `application-local.yml`。

## 构建与测试

执行全部测试：

```powershell
mvn -q test
```

打包：

```powershell
mvn -q package
```

构建产物：

```text
target/logger-server-0.1.0.jar
```

可选真实环境完整测试默认不运行。需要 RocketMQ 与 TDengine 可用时，可显式开启：

```powershell
mvn -q -Dlogger.real-env.test=true -Dtest=RealEnvironmentFullFlowTest test
```

## 运行

确认 RocketMQ 与 TDengine 配置后执行：

```powershell
java -jar target/logger-server-0.1.0.jar
```

使用其他 profile 或外部配置时，可通过 Spring Boot 标准参数覆盖：

```powershell
java -jar target/logger-server-0.1.0.jar --spring.profiles.active=dev
```

## 消息主题

| Topic | 说明 |
| --- | --- |
| `broadcast-global` | 全局任务创建与停止消息。 |
| `broadcast-{instanceId}` | 指定实例的启动、暂停、继续控制消息。 |
| `situation-{instanceId}` | 指定实例的态势数据消息。 |

## 发布信息

`v0.1` 是第一个可运行闭环版本，包含 RocketMQ 接入、动态订阅、会话管理、仿真时钟、TDengine 写入、配置外置与测试闭环。
