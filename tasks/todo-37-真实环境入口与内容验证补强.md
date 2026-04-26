# 真实环境入口与内容验证补强任务单

## 执行依据

- `plan/006-回放系统交付审阅/fix-steps.md`
- `plan/006-回放系统交付审阅/phases/phase-03.md`
- `tasks/todo-31-回放系统交付审阅.md`
- `plan/005-回放系统设计/005-final.md`

## 计划

- [x] 将 Phase 03 文档状态标记为进行中。
- [x] 将真实环境测试切换到 `real` profile 并验证配置生效。
- [x] 通过真实 `broadcast-global` create 创建回放会话。
- [x] 通过真实 `broadcast-{instanceId}` 控制入口执行跳转。
- [x] 通过真实 `broadcast-global` stop 停止并释放会话。
- [x] 为真实 TDengine 写入帧建立期望模型。
- [x] 校验真实 `situation-{instanceId}` 发布内容、协议字段、顺序和无重复。
- [x] 运行 Phase 03 定向测试和默认跳过验证。
- [x] 运行 `mvn -pl replay-server -am test` 和必要全量回归。
- [x] 回写 Phase 03 Review、修复索引状态和本任务单 Review。

## Review

- 代码改动：`ReplayRealEnvironmentTest` 不再绕过生产入口，改为真实 `broadcast-global` create/stop、真实 `broadcast-{instanceId}` jump、真实 `situation-{instanceId}` 消费断言；`application-real.yml` 去除非法 `spring.profiles.include` 并启用全局监听器。
- 期望模型：TDengine 写入 `1001/1`、`1002/8`、`1001/2` 事件帧，以及 `1003/3` 周期帧；测试逐条校验协议字段、原始 JSON、`simTime`、顺序和重复帧。
- 红灯记录：`mvn -pl replay-server -am "-Dtest=ReplayRealEnvironmentTest" "-Dreplay.real-env.test=true" -DfailIfNoTests=false test` 首次失败，原因是 `application-real.yml` 中 `spring.profiles.include` 不能出现在 profile-specific resource。
- 真实环境验证：修复后同一命令通过，`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`；日志确认全局监听容器订阅 `broadcast-global`，并完成 create、jump、stop。
- 默认跳过验证：`mvn -pl replay-server -am "-Dtest=ReplayRealEnvironmentTest" -DfailIfNoTests=false test` 通过，`Skipped: 1`。
- 回归验证：`mvn -pl replay-server -am test` 通过，`Tests run: 109, Failures: 0, Errors: 0, Skipped: 1`；`mvn test` 通过。
- 收尾检查：`git diff --check` 通过，仅输出 CRLF 换行提示。
