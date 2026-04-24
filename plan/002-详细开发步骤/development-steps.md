# logger-server 开发步骤索引

## 1. 文档目标

本文档用于把 [总体技术方案](E:\project\5y\logger-server\plan\001-总体设计\final.md) 落成可执行开发步骤，按阶段组织实现顺序、依赖关系和当前状态，便于后续逐阶段推进与追踪。

## 2. 阶段总览

| 阶段 | 名称 | 目标摘要 | 依赖 | 当前状态 | 详细计划 |
| ---- | ---- | ---- | ---- | ---- | ---- |
| Phase 00 | 骨架清理与基础配置 | 完成配置收敛、目录骨架和公共常量，建立后续开发基线 | 无 | 已完成 | [phase-00.md](E:\project\5y\logger-server\plan\002-详细开发步骤\phases\phase-00.md) |
| Phase 01 | 核心领域模型 | 实现会话状态机、仿真时钟和会话管理器 | Phase 00 | 已完成 | [phase-01.md](E:\project\5y\logger-server\plan\002-详细开发步骤\phases\phase-01.md) |
| Phase 02 | TDengine 基础设施 | 建立 WebSocket 数据源、建表服务和写入服务 | Phase 00, Phase 01 | 已完成 | [phase-02.md](E:\project\5y\logger-server\plan\002-详细开发步骤\phases\phase-02.md) |
| Phase 03 | RocketMQ 动态订阅 | 实现全局监听、实例级订阅管理与消息处理器 | Phase 00, Phase 01 | 已完成 | [phase-03.md](E:\project\5y\logger-server\plan\002-详细开发步骤\phases\phase-03.md) |
| Phase 04 | 业务流程串联 | 打通创建、启动、暂停、继续、停止和态势入库主链路 | Phase 01, Phase 02, Phase 03 | 未开始 | [phase-04.md](E:\project\5y\logger-server\plan\002-详细开发步骤\phases\phase-04.md) |
| Phase 05 | 测试、日志与交付收尾 | 完成 TDD 回补、异常处理、日志指标和交付校验 | Phase 00, Phase 01, Phase 02, Phase 03, Phase 04 | 未开始 | [phase-05.md](E:\project\5y\logger-server\plan\002-详细开发步骤\phases\phase-05.md) |

## 3. 当前状态说明

- Phase 00 已完成：`pom.xml` 已切换到 `logger-server` + Spring JDBC + RocketMQ + TDengine Java Connector 的基础依赖，配置文件已完成收敛，基础常量类与配置类已建立。
- Phase 01 已完成：`SimulationClock`、`SimulationSessionState`、`SimulationSession`、`SimulationSessionManager` 已落地，且主类已通过 `javac` 静态编译校验。
- Phase 02 已完成：`SituationRecordCommand`、`TdengineConstants`、`TdengineSchemaService`、`TdengineWriteService` 已落地，并通过 Maven 测试验证。
- Phase 03 已完成：`RocketMqConsumerFactory`、`TopicSubscriptionManager`、`GlobalBroadcastListener`、`InstanceBroadcastMessageHandler`、`SituationMessageHandler` 已落地，订阅管理测试已补齐。
- Phase 04 的业务流程编排仍未开始编码。

## 4. 阶段依赖关系图

```text
Phase 00 骨架清理与基础配置
├─> Phase 01 核心领域模型
│   ├─> Phase 02 TDengine 基础设施
│   ├─> Phase 03 RocketMQ 动态订阅
│   └─> Phase 04 业务流程串联
│       ├─ 依赖 Phase 02
│       └─ 依赖 Phase 03
└─> Phase 05 测试、日志与交付收尾
    └─ 依赖 Phase 01 / 02 / 03 / 04 全部完成
```

## 5. 推荐执行顺序

1. 先完成 Phase 00，确保配置、目录结构和基础常量不再摇摆。
2. 再完成 Phase 01，把状态机和仿真时钟这两个最核心的领域模型做实。
3. 然后并行推进 Phase 02 与 Phase 03。
4. 在基础设施稳定后进入 Phase 04，串联完整消息处理链路。
5. 最后在 Phase 05 完成测试、日志、指标和交付收尾。

## 6. 阶段推进规则

- 每个阶段开始前，先对对应 `phase-xx.md` 中的待办条目做状态更新。
- 编码阶段遵循 TDD：先补失败测试，再写实现代码，再让测试通过。
- 如果实现中发现设计需调整，应先回写 [final.md](E:\project\5y\logger-server\plan\001-总体设计\final.md) 与本索引文档，再继续编码。

## 7. 当前无需澄清的问题

本次阶段规划所需输入已足够，当前没有阻塞性疑问。
