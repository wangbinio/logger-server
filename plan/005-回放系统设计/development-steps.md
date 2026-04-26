# 回放系统开发步骤索引

## 1. 文档目标

本文档基于 [005-final.md](E:\project\5y\logger-server\plan\005-回放系统设计\005-final.md)，把回放系统方案拆解为可逐阶段执行的开发计划。

规划目标是先完成模块边界和公共能力抽取，再按 TDD 顺序落地 `replay-server` 的消息入口、TDengine 查询、回放领域模型、发布调度、控制跳转和联调验收。

## 2. 阶段总览

| 阶段 | 名称 | 目标摘要 | 依赖 | 当前状态 | 详细计划 |
| ---- | ---- | ---- | ---- | ---- | ---- |
| Phase 00 | 多模块拆分与 common 基线 | 将当前工程调整为 `common`、`logger-server`、`replay-server` 三模块结构，并保证记录系统测试不回退 | 无 | 已完成 | [phase-00.md](E:\project\5y\logger-server\plan\005-回放系统设计\phases\phase-00.md) |
| Phase 01 | replay-server 配置与消息入口 | 建立回放服务启动骨架、配置模型、全局监听器和实例级控制消息处理器 | Phase 00 | 未开始 | [phase-01.md](E:\project\5y\logger-server\plan\005-回放系统设计\phases\phase-01.md) |
| Phase 02 | TDengine 查询与表分类 | 实现控制时间点查询、态势子表元数据发现、事件表与周期表分类、帧数据分页查询 | Phase 00, Phase 01 | 未开始 | [phase-02.md](E:\project\5y\logger-server\plan\005-回放系统设计\phases\phase-02.md) |
| Phase 03 | 回放领域模型 | 实现 `ReplayClock`、`ReplaySession`、状态机和会话管理器 | Phase 00, Phase 01 | 未开始 | [phase-03.md](E:\project\5y\logger-server\plan\005-回放系统设计\phases\phase-03.md) |
| Phase 04 | 发布与连续调度 | 实现协议包重组、RocketMQ 发布、连续回放窗口查询和成功发布后推进水位 | Phase 02, Phase 03 | 未开始 | [phase-04.md](E:\project\5y\logger-server\plan\005-回放系统设计\phases\phase-04.md) |
| Phase 05 | 控制命令与时间跳转 | 实现开始、暂停、继续、倍速、向前跳转、向后跳转和周期快照发布 | Phase 03, Phase 04 | 未开始 | [phase-05.md](E:\project\5y\logger-server\plan\005-回放系统设计\phases\phase-05.md) |
| Phase 06 | 联调验收与交付收尾 | 完成集成测试、真实环境开关、日志指标、文档收敛和验收清单 | Phase 00, Phase 01, Phase 02, Phase 03, Phase 04, Phase 05 | 未开始 | [phase-06.md](E:\project\5y\logger-server\plan\005-回放系统设计\phases\phase-06.md) |

## 3. 阶段依赖关系图

```text
Phase 00 多模块拆分与 common 基线
└─> Phase 01 replay-server 配置与消息入口
    ├─> Phase 02 TDengine 查询与表分类
    │   └─> Phase 04 发布与连续调度
    └─> Phase 03 回放领域模型
        └─> Phase 04 发布与连续调度
            └─> Phase 05 控制命令与时间跳转
                └─> Phase 06 联调验收与交付收尾
```

## 4. 推荐执行顺序

1. 先执行 Phase 00，完成 Maven 多模块拆分，并证明 `logger-server` 现有测试仍通过。
2. 再执行 Phase 01，建立 `replay-server` 的配置、启动和消息入口，确保消息类型隔离正确。
3. Phase 02 与 Phase 03 可以在 Phase 01 后并行推进，但需要避免同时改同一配置类。
4. Phase 04 必须等 Phase 02 的查询接口和 Phase 03 的回放会话模型稳定后再做。
5. Phase 05 在发布调度稳定后实现，避免跳转逻辑和基础发布逻辑相互纠缠。
6. Phase 06 做全链路验证、真实环境开关和文档收敛，不能提前跳过。

## 5. 阶段推进规则

- 每个阶段开始前，先把对应 `phase-xx.md` 的状态从“未开始”改为“进行中”。
- 每个阶段必须先补测试，再写实现，最后运行与该阶段相关的测试。
- 阶段内发现方案变更时，先回写 [005-final.md](E:\project\5y\logger-server\plan\005-回放系统设计\005-final.md) 与本文档，再继续编码。
- 涉及函数声明时必须补充中文注释，复杂实现逻辑在函数内部补充必要中文注释。
- 每个阶段完成后，在对应阶段文档末尾补充 Review，记录实际改动、测试结果和遗留风险。

## 6. 当前状态说明

- Phase 00 已完成：工程已改造为 Maven 多模块结构，根工程为父工程，新增 `common`、`logger-server`、`replay-server` 三个子模块。
- `common` 已承载协议、JSON、Topic、通用异常和 TDengine 命名规则。
- `logger-server` 已迁入子模块并依赖 `common`，记录服务现有测试通过。
- `replay-server` 已建立空 Spring Boot 应用、基础配置和上下文加载测试。

## 7. 当前无需澄清的问题

本轮阶段拆解所需信息已经足够，当前没有阻塞性疑问。
