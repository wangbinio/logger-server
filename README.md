# logger-server

`logger-server` 是一个基于 Spring Boot 的数据记录服务，用于订阅 RocketMQ 中的仿真消息，并将态势数据写入 TDengine。

当前项目仍处于开发阶段，已完成基础骨架清理与配置收敛，后续将按阶段逐步实现会话管理、仿真时钟、TDengine 写入和 RocketMQ 动态订阅能力。

## 项目目标

- 启动后固定订阅 `broadcast-global`
- 根据任务创建消息动态订阅 `broadcast-{instanceId}` 和 `situation-{instanceId}`
- 维护支持启动、暂停、继续、后续倍速扩展的仿真时间
- 将态势消息按实例写入 TDengine

## 技术栈

- Java 8
- Spring Boot 2.7
- RocketMQ
- TDengine Java Connector
- Spring JDBC
- Lombok

## 当前进度

- 已完成 `Phase 00`：骨架清理与基础配置
- 未开始 `Phase 01`：核心领域模型
- 未开始 `Phase 02`：TDengine 基础设施
- 未开始 `Phase 03`：RocketMQ 动态订阅
- 未开始 `Phase 04`：业务流程串联
- 未开始 `Phase 05`：测试、日志与交付收尾

## 目录说明

- `src/main/java/com/szzh/loggerserver`：主代码目录
- `src/main/resources`：应用配置
- `src/test/java/com/szzh/loggerserver`：测试代码
- `plan/001-总体设计`：总体设计文档
- `plan/002-详细开发步骤`：阶段开发计划文档
- `tasks`：任务执行记录

## 配置说明

当前默认使用本地配置文件：

- [application.yml](E:\project\5y\logger-server\src\main\resources\application.yml)
- [application-local.yml](E:\project\5y\logger-server\src\main\resources\application-local.yml)

其中已收敛为当前阶段最小配置，重点包括：

- `rocketmq.name-server`
- `logger-server.tdengine.*`
- `logger-server.rocketmq.*`
- `logger-server.protocol.*`
- `logger-server.session.*`
- `logger-server.write.*`

## 主要文档

- 总体设计：[final.md](E:\project\5y\logger-server\plan\001-总体设计\final.md)
- 开发步骤索引：[development-steps.md](E:\project\5y\logger-server\plan\002-详细开发步骤\development-steps.md)
- 当前阶段计划：[phase-00.md](E:\project\5y\logger-server\plan\002-详细开发步骤\phases\phase-00.md)

## 开发说明

- 协议解析直接复用现有 `ProtocolData` 和 `ProtocolMessageUtil`
- TDengine 采用官方 Java Connector 的 WebSocket 路线
- 编码阶段遵循 TDD：先写失败测试，再补实现

## 后续建议

建议下一步从 [phase-01.md](E:\project\5y\logger-server\plan\002-详细开发步骤\phases\phase-01.md) 开始，实现：

- `SimulationClock`
- `SimulationSession`
- `SimulationSessionManager`
