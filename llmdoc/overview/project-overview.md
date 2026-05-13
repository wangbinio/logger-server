# 项目总览

## 定位

`logger-platform` 是面向仿真实例的记录与回放平台。记录服务把仿真控制和态势消息写入 TDengine，回放服务基于已记录数据重新发布态势并支持 HTTP 控制。

## 技术栈

- Java 8
- Spring Boot 2.7.12
- RocketMQ Spring Boot Starter 2.2.3
- TDengine Java Connector 3.2.4
- Spring JDBC 与 HikariCP
- Lombok
- JUnit 5 与 Mockito

## Maven 模块

- `common`：公共协议、JSON、Topic、TDengine 命名和异常。
- `logger-server`：记录服务，消费 RocketMQ 并写入 TDengine。
- `replay-server`：回放服务，读取 TDengine，通过 HTTP 控制并向 RocketMQ 发布态势。

## 生产服务

生产部署两个服务：

```text
logger-server
replay-server
```

`common` 只作为共享 jar，不单独部署。

## 当前能力

- 固定全局 Topic `broadcast-global`。
- 记录侧动态订阅 `broadcast-{instanceId}` 和 `situation-{instanceId}`。
- 记录侧维护 `SimulationSession` 和 `SimulationClock`。
- 记录侧写入态势超级表和控制时间点表。
- 回放侧读取 TDengine 表和控制时间点。
- 回放侧通过 HTTP 启动、暂停、继续、倍速、跳转和查询元信息。
- 回放侧连续窗口调度和跳转补偿发布。
- 真实环境测试覆盖当前 RocketMQ 和 TDengine 配置。

