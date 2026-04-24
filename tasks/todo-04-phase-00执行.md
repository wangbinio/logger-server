# 任务：执行 Phase 00

## 背景

根据 `plan/002-详细开发步骤/development-steps.md` 与 `plan/002-详细开发步骤/phases/phase-00.md`，执行骨架清理与基础配置阶段，完成配置收敛、公共常量、基础配置 Bean 和目录骨架初始化。

## 执行项

- [x] 读取阶段计划、当前配置和现有代码结构，确认需要落地的骨架内容。
- [x] 清理 `application.yml` 与 `application-local.yml`。
- [x] 新增 `LoggerServerProperties` 与 `TdengineConfig`。
- [x] 新增 `TopicConstants`、`MessageConstants`、`JsonUtil`。
- [x] 预创建后续阶段所需包目录并完成静态校验。
- [x] 回写 Phase 00 状态与本任务 review。

## 验收标准

- 配置文件仅保留 logger-server 当前阶段所需配置。
- 基础配置类、常量类、JSON 工具类已创建。
- 目录骨架已创建，可直接进入 Phase 01。

## Review

- 已清理 `application.yml` 与 `application-local.yml`，移除了旧项目遗留的安全、Redis、Knife4j、上下文路径等配置，并切换到 TDengine WebSocket + RocketMQ 的最小运行配置。
- 已新增 `LoggerServerProperties`、`TdengineConfig`、`TopicConstants`、`MessageConstants`、`JsonUtil`，为后续阶段提供统一配置入口和公共基础能力。
- 已预创建 `config`、`domain`、`mq`、`service`、`model`、`support` 等目录骨架，并补充 `JsonUtilTest`、`TopicConstantsTest` 两个基础测试文件。
- 已把 `development-steps.md` 中的 Phase 00 状态更新为“已完成”。
- 当前环境缺少 `mvn` 或 `mvnw`，未执行 Maven 构建或测试，只完成了配置清理、文件存在性和关键项移除的静态校验。
