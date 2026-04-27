# 任务：v0.2 发布提交

## 执行依据

- `README.md`
- `ARCHITECTURE.md`
- 根 `pom.xml`
- `common/pom.xml`
- `logger-server/pom.xml`
- `replay-server/pom.xml`
- `tasks/lessons.md`

## 计划

- [x] 将 Maven 发布版本从 `0.1.0` 提升到 `0.2.0`。
- [x] 将 README 中的当前发布版本、构建产物和运行命令同步到 `v0.2`。
- [x] 将 ARCHITECTURE 中的当前范围标记为 `v0.2` 发布范围。
- [x] 执行默认测试和发布打包验证。
- [x] 检查发布 diff 并提交。
- [x] 创建本地 `v0.2` 标签。

## Review

- Maven 版本已统一提升为 `0.2.0`：根 `pom.xml`、`common/pom.xml`、`logger-server/pom.xml`、`replay-server/pom.xml` 已同步。
- `README.md` 已更新当前发布版本为 `v0.2`，并将构建产物与运行命令更新为 `logger-server-0.2.0.jar`、`replay-server-0.2.0.jar`。
- `ARCHITECTURE.md` 已补充当前发布版本，并将当前范围章节标记为 `v0.2 当前范围`，明确记录侧与回放侧实例控制配置均使用 `protocol.messages.control` 节点。
- `tasks/todo-40-平台文档收口.md` 已同步本次 `v0.2` 产物路径和打包结果，避免发布提交内残留上一轮 `0.1.0` 产物记录。
- `mvn test` 通过：根反应堆 `common`、`logger-server`、`replay-server` 均为 `BUILD SUCCESS`；`Tests run: 114, Failures: 0, Errors: 0, Skipped: 1`，跳过项为默认关闭的真实环境测试。
- `mvn -DskipTests package` 通过：已生成 `common/target/common-0.2.0.jar`、`logger-server/target/logger-server-0.2.0.jar`、`replay-server/target/replay-server-0.2.0.jar`。
- surefire dump 检查：仅发现 `logger-server/target/surefire-reports/2026-04-26T21-51-08_779-jvmRun1.dump`，长度为 `0`，不包含失败堆栈或错误信息，不影响本次发布验证结论。
