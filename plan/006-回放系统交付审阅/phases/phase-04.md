# Phase 04 测试证据与日志回归补强

## 1. 阶段目标

补齐交付审阅中暴露的验证证据不足：

- Mock 全链路测试升级为 Spring 容器级集成测试，证明 Bean 装配和真实服务编排。
- 结构化日志字段增加自动化断言。
- 清理或解释 surefire dump，保证交付报告干净可信。

## 2. 实现思路

保留快速对象级测试，但新增 Spring Boot 级 Mock 集成测试。外部 TDengine 和 RocketMQ 可以用 `@MockBean` 或测试专用 Bean 替代，核心 Spring 配置、监听器、服务、调度器和消息常量应由容器真实装配。

日志测试使用 Spring Boot 的 `OutputCaptureExtension` 或 Logback `ListAppender`，对生命周期、控制、调度失败、发布失败路径逐字段断言，避免日志字段漂移。

测试报告策略应在阶段任务单中明确：修复前的历史 dump 不作为验收证据；修复后从干净目录重新执行默认测试和必要真实环境测试。

## 3. 需要新增或修改的文件

| 文件 | 说明 |
| ---- | ---- |
| `replay-server/src/test/java/com/szzh/replayserver/integration/ReplaySpringFlowIntegrationTest.java` | 新增 Spring 容器级 Mock 全链路测试。 |
| `replay-server/src/test/java/com/szzh/replayserver/service/*LogTest.java` 或现有测试 | 补结构化日志字段断言。 |
| `replay-server/src/test/resources/application-test.yml` | 提供 Spring Mock 集成测试所需配置。 |
| `replay-server/pom.xml` 或根 `pom.xml` | 如需要，调整测试插件配置以便报告稳定生成。 |
| `tasks/todo-xx-*.md` | 记录干净测试报告和 dump 处置结论。 |

## 4. 待办条目

| 编号 | 待办 | 简要说明 | 前置依赖 |
| ---- | ---- | ---- | ---- |
| 04-01 | 新增 Spring Mock 集成测试骨架 | 使用 `@SpringBootTest` 加载 `replay-server` 上下文，替代外部适配器。 | Phase 01, Phase 02 |
| 04-02 | 覆盖全链路消息流 | 通过 listener 或控制端口输入 create/start/pause/resume/rate/jump/stop，断言 sender 输出和状态变化。 | 04-01 |
| 04-03 | 新增生命周期日志断言 | 校验 create、stop、create failed 日志包含核心结构化字段。 | 04-02 |
| 04-04 | 新增控制日志断言 | 校验 start、pause、resume、rate、jump 日志字段完整。 | 04-03 |
| 04-05 | 新增失败路径日志断言 | 校验调度查询失败、发布重试失败、协议解析失败日志字段完整。 | 04-04 |
| 04-06 | 清理并重跑测试报告 | 删除旧报告或使用 Maven clean 后重新生成，确认没有新的异常 dump。 | 04-05 |
| 04-07 | 记录报告证据 | 在任务单 Review 中记录 clean test 命令、结果和 dump 处置结论。 | 04-06 |

## 5. 验证要求

- Spring Mock 集成测试不能手动 new 全部服务替代容器装配。
- 结构化日志测试必须覆盖成功路径和失败路径。
- 默认测试报告中不应混入未解释的历史 fork dump。
- 如仍存在 dump，必须定位来源并记录是否影响验收。

## 6. 当前无需澄清的问题

本阶段可以按测试补强方向直接执行，暂无阻塞性疑问。
