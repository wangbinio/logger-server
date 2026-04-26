# 回放系统交付审阅修复步骤索引

## 1. 文档目标

本文档基于 `006-回放系统交付审阅-draft.md`、`tasks/todo-31-回放系统交付审阅.md` 和 `plan/005-回放系统设计/005-final.md`，把回放系统交付审阅发现的问题拆解为可按阶段执行的修复计划。

修复目标是先消除会影响回放语义正确性的 P1 问题，再修复配置、历史数据兼容和终态清理偏差，最后补齐真实环境、日志、报告和批量发布验证，使 `replay-server` 的实现、测试和交付证据与总体设计重新一致。

## 2. 阶段总览

| 阶段 | 名称 | 目标摘要 | 覆盖发现 | 依赖 | 当前状态 | 详细计划 |
| ---- | ---- | ---- | ---- | ---- | ---- | ---- |
| Phase 00 | 回放水位与终态语义修复 | 修复初始水位、`COMPLETED` 可跳转、终态停止清理，保证状态机与设计一致 | Finding 1, Finding 5, Finding 6 | 无 | 已完成 | [phase-00.md](E:\project\5y\logger-server\plan\006-回放系统交付审阅\phases\phase-00.md) |
| Phase 01 | 调度互斥与跳转失败语义修复 | 让连续调度与跳转发布真正互斥，并确保跳转发布失败进入 `FAILED` 且不恢复调度 | Finding 2, Finding 3 | Phase 00 | 未开始 | [phase-01.md](E:\project\5y\logger-server\plan\006-回放系统交付审阅\phases\phase-01.md) |
| Phase 02 | 事件表配置与 TDengine 降级修复 | 补齐生产事件表配置，修复 `time_control` 缺表时的态势表降级 | Finding 4, Finding 7 | Phase 00 | 未开始 | [phase-02.md](E:\project\5y\logger-server\plan\006-回放系统交付审阅\phases\phase-02.md) |
| Phase 03 | 真实环境入口与内容验证补强 | 用真实 `broadcast-global` 入口创建/停止回放，并校验回放内容、顺序和协议字段 | Finding 8, Finding 9 | Phase 01, Phase 02 | 未开始 | [phase-03.md](E:\project\5y\logger-server\plan\006-回放系统交付审阅\phases\phase-03.md) |
| Phase 04 | 测试证据与日志回归补强 | 补 Spring 容器级 Mock 集成测试、结构化日志断言、干净测试报告策略 | 交付审阅验证缺口 | Phase 01, Phase 02 | 未开始 | [phase-04.md](E:\project\5y\logger-server\plan\006-回放系统交付审阅\phases\phase-04.md) |
| Phase 05 | 批量发布与交付收尾 | 让 `publish.batch-size` 生效或收敛配置语义，并完成全量回归、文档回写和验收记录 | Finding 10 | Phase 03, Phase 04 | 未开始 | [phase-05.md](E:\project\5y\logger-server\plan\006-回放系统交付审阅\phases\phase-05.md) |

## 3. 阶段依赖关系图

```text
Phase 00 回放水位与终态语义修复
└─> Phase 01 调度互斥与跳转失败语义修复
    ├─> Phase 03 真实环境入口与内容验证补强
    └─> Phase 04 测试证据与日志回归补强

Phase 00 回放水位与终态语义修复
└─> Phase 02 事件表配置与 TDengine 降级修复
    ├─> Phase 03 真实环境入口与内容验证补强
    └─> Phase 04 测试证据与日志回归补强

Phase 03 真实环境入口与内容验证补强
Phase 04 测试证据与日志回归补强
└─> Phase 05 批量发布与交付收尾
```

## 4. 推荐执行顺序

1. 先执行 Phase 00，修复回放状态机和水位边界；这些问题会影响后续所有调度、跳转和停止验证。
2. 再执行 Phase 01，集中修复同一实例发布串行化和跳转失败状态；该阶段完成后才能可信地扩展真实环境验证。
3. Phase 02 可以在 Phase 01 后并行或紧随执行，重点解决生产配置和历史数据兼容。
4. Phase 03 必须在 Phase 01 与 Phase 02 之后执行，避免真实环境测试继续验证错误语义。
5. Phase 04 与 Phase 03 可以部分并行，但日志和 Spring Mock 集成测试应覆盖 Phase 00 至 Phase 02 的修复后行为。
6. Phase 05 作为交付收尾，处理批量发布配置语义、全量回归、文档和任务记录。

## 5. 阶段推进规则

- 每个阶段开始前，先将对应 `phase-xx.md` 状态从“未开始”改为“进行中”。
- 每个阶段必须先补失败测试，再修改实现，最后运行该阶段定向测试。
- P1/P2 修复不允许只改文档或只放宽测试断言，必须让生产实现满足 `005-final.md` 的行为语义。
- 真实环境测试必须继续通过显式开关运行，不进入默认 CI；但开启后的报告、关键日志和验证结论必须写入任务单。
- 涉及函数声明新增或调整时，按项目规范补中文注释；复杂业务流程调用处保留 review 友好的中文注释。
- 每个阶段完成后，在对应阶段文档末尾补 Review，记录实际改动、测试命令、结果和遗留风险。

## 6. 预期最终验收

- `mvn -pl replay-server -am test` 在 Java 8 下通过。
- `mvn test` 在 Java 8 下通过。
- 开启 `-Dreplay.real-env.test=true` 后，真实 TDengine/RocketMQ 回放测试通过，并能证明全局入口、实例控制入口和态势发布内容正确。
- `ReplayRealEnvironmentTest` 或独立真实环境测试报告不再只证明跳过，而能保留明确的开启验证证据。
- `tasks/todo-31-回放系统交付审阅.md` 中 P1/P2/P3 发现均有对应修复或明确的设计裁定。
- `plan/005-回放系统设计/005-final.md`、`development-steps.md`、`plan/006-回放系统交付审阅/fix-steps.md` 和各 phase Review 与实际实现一致。

## 7. 当前无需澄清的问题

当前修复方向可以按上述阶段直接推进，暂无阻塞性疑问。
