# 平台文档收口任务单

## 执行依据

- `README.md`
- `ARCHITECTURE.md`
- 根 `pom.xml`
- `logger-server/src/main/resources/application.yml`
- `replay-server/src/main/resources/application.yml`
- `tasks/lessons.md`

## 计划

- [x] 核对当前 Maven 多模块、配置文件和真实环境测试入口。
- [x] 将 README 更新为 `logger-platform` 项目级说明。
- [x] 将 ARCHITECTURE 更新为记录与回放平台架构说明。
- [x] 验证 Markdown 文件结构和关键命令。
- [x] 记录本次文档收口结果。

## Review

- `README.md` 已从单服务说明升级为 `logger-platform` 项目级说明，覆盖 `common`、`logger-server`、`replay-server` 三个模块。
- `README.md` 已补齐双服务当前能力、Topic 职责、消息类型隔离、TDengine 表模型、配置文件、构建测试和运行命令。
- `ARCHITECTURE.md` 已重写为平台级架构说明，按总体架构、协议隔离、公共模块、记录链路、回放链路、TDengine 数据、配置、异常指标、并发一致性和测试策略组织。
- 文档中的真实环境开关已核对为 `logger.real-env.test=true` 和 `replay.real-env.test=true`。
- 文档中的服务产物路径已随 `v0.2` 发布核对为 `logger-server/target/logger-server-0.2.0.jar` 和 `replay-server/target/replay-server-0.2.0.jar`。
- `mvn test` 通过：根反应堆 `common`、`logger-server`、`replay-server` 均为 `BUILD SUCCESS`；`Tests run: 114, Failures: 0, Errors: 0, Skipped: 1`，跳过项为默认关闭的真实环境测试。
- `mvn -DskipTests package` 通过：已生成 `common/target/common-0.2.0.jar`、`logger-server/target/logger-server-0.2.0.jar`、`replay-server/target/replay-server-0.2.0.jar`。
- 检查 `common/target/surefire-reports`、`logger-server/target/surefire-reports`、`replay-server/target/surefire-reports`，未发现 `.dump` 或 `.dumpstream` 文件。
