# 任务：设计一致性审阅

## 背景

当前项目声称已按 `plan/001-总体设计/final.md`、`plan/002-详细开发步骤/development-steps.md` 以及 Phase 00-05 全量开发完成。本任务需要从实现代码与测试出发，完整审阅当前交付物与总体设计的一致性，识别缺口、偏差与验证不足点。

## 执行项

- [x] 复核 `final.md`、`development-steps.md` 与各 phase 文档，提炼可核验的设计要求。
- [x] 审阅 `src/main` 中的配置、领域模型、RocketMQ、TDengine、服务编排与异常监控实现。
- [x] 审阅 `src/test` 中的单测与集成测试，核对是否覆盖设计要求与阶段声明。
- [x] 运行构建或测试命令，验证关键结论，避免仅凭静态阅读下结论。
- [x] 输出以问题为主的审阅结果，并在本文件补充 review 结论。

## 验收标准

- 能按设计维度逐项说明“已实现 / 不一致 / 证据不足”。
- 审阅结论包含明确代码证据与必要的验证结果。
- 若未发现阻塞性偏差，需要明确说明剩余风险与验证边界。

## Review

- 主链路整体与 `final.md` 大体一致：`broadcast-global` 固定监听、实例级动态订阅、`SimulationSession` 状态集中管理、虚拟时钟、TDengine 分实例超表、创建/启动/暂停/继续/停止/态势入库链路和基础异常指标均已落地。
- 已验证配置与依赖收敛基本符合设计：`pom.xml` 已收敛到 Spring JDBC、RocketMQ、TDengine Java Connector、Jackson、Lombok 与测试依赖；`application.yml` / `application-local.yml` 也已切到 `logger-server` 所需的最小配置域。
- 已使用 Java 8 执行测试验证：`JAVA_HOME=C:\Users\summer\.jdks\corretto-1.8.0_482`，命令 `mvn -q test`，本次审阅时测试通过。
- 发现的主要不一致点：
  1. `TdengineWriteService` 的实际主路径仍是标准 JDBC 单条写入，`SituationRecordService` 也只调用 `write(...)`，没有把设计中“高频写入优先走 WebSocket stmt 参数绑定”的方案真正接到主链路。
  2. `TopicSubscriptionManagerTest` 仍包含默认测试套件中的真实 RocketMQ 环境测试，这与 `final.md` 中“当前阶段暂不建议依赖真实 RocketMQ / TDengine 做端到端测试”的策略不一致，并引入环境耦合风险。
  3. `README.md` 仍声明仅完成 Phase 00、Phase 01-05 未开始，和当前代码与 `development-steps.md` 的完成状态不一致，容易误导后续维护者。
- 结论：当前实现可认为已覆盖 `final.md` 的大部分核心设计，但尚不能称为“与设计完全一致”；至少需要先处理上述三处偏差，才能把“全部按设计完成”的表述收紧为可信结论。
