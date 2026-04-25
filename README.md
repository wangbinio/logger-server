# logger-server

`logger-server` 是一个基于 Spring Boot 的数据记录服务，用于订阅 RocketMQ 中的仿真消息，并将态势数据写入 TDengine。

当前代码基线已完成 [总体设计](E:\project\5y\logger-server\plan\001-总体设计\final.md)、[详细开发步骤](E:\project\5y\logger-server\plan\002-详细开发步骤\development-steps.md) 以及 Phase 00-05 中定义的主体实现，覆盖会话管理、仿真时钟、RocketMQ 动态订阅、TDengine 建表写入、主链路编排、异常处理、日志指标与测试闭环。

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
- 已完成 `Phase 01`：核心领域模型
- 已完成 `Phase 02`：TDengine 基础设施
- 已完成 `Phase 03`：RocketMQ 动态订阅
- 已完成 `Phase 04`：业务流程串联
- 已完成 `Phase 05`：测试、日志与交付收尾

## 当前状态

- 已具备 `broadcast-global` 固定监听、`broadcast-{instanceId}` / `situation-{instanceId}` 实例级动态订阅能力
- 已实现 `SimulationSession` + `SimulationClock` 的实例状态与仿真时间管理
- 已实现按实例创建 TDengine 超表、按消息维度写入子表
- 已完成创建、启动、暂停、继续、停止、态势入库的主链路服务编排
- 已补齐协议解析、领域服务、订阅管理与主流程集成测试
- 当前设计一致性审阅结果见 [todo-12-设计一致性审阅.md](E:\project\5y\logger-server\tasks\todo-12-设计一致性审阅.md)

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
- 分阶段计划：
  - [phase-00.md](E:\project\5y\logger-server\plan\002-详细开发步骤\phases\phase-00.md)
  - [phase-01.md](E:\project\5y\logger-server\plan\002-详细开发步骤\phases\phase-01.md)
  - [phase-02.md](E:\project\5y\logger-server\plan\002-详细开发步骤\phases\phase-02.md)
  - [phase-03.md](E:\project\5y\logger-server\plan\002-详细开发步骤\phases\phase-03.md)
  - [phase-04.md](E:\project\5y\logger-server\plan\002-详细开发步骤\phases\phase-04.md)
  - [phase-05.md](E:\project\5y\logger-server\plan\002-详细开发步骤\phases\phase-05.md)

## 开发说明

- 协议解析直接复用现有 `ProtocolData` 和 `ProtocolMessageUtil`
- TDengine 采用官方 Java Connector 的 WebSocket 路线
- 编码阶段遵循 TDD：先写失败测试，再补实现
- 当前默认以 Java 8 作为目标运行环境
