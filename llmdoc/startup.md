# 启动指南

## 第一步

先读本文件，再按任务类型选择下面入口。不要直接从代码搜索开始，除非用户只问一个明确类或命令。

## 必读规则

- 读取 [must/repo-rules.md](must/repo-rules.md)。
- 读取 [must/current-contracts.md](must/current-contracts.md)。
- 若任务涉及实现或修改，先查看 `tasks/lessons.md` 和最新 `tasks/todo-*.md`。

## 常见任务路由

- 理解整体项目：读 [overview/project-overview.md](overview/project-overview.md) 和 [architecture/module-map.md](architecture/module-map.md)。
- 修改记录侧链路：读 [architecture/message-and-data-flow.md](architecture/message-and-data-flow.md) 中 `logger-server` 部分，再看 `SimulationLifecycleService`、`SimulationControlService`、`SituationRecordService`、`TdengineWriteService`。
- 修改回放侧链路：读 [architecture/message-and-data-flow.md](architecture/message-and-data-flow.md) 中 `replay-server` 部分，再看 `ReplayLifecycleService`、`ReplayControlController`、`ReplayControlService`、`ReplayScheduler`、`ReplayJumpService`。
- 修改 TDengine 字段或查询：读 [architecture/tdengine-and-replay.md](architecture/tdengine-and-replay.md) 和 [memory/decisions/0001-tdengine-restful-low-version.md](memory/decisions/0001-tdengine-restful-low-version.md)。
- 修改配置、消息码或真实环境连接：读 [reference/configuration.md](reference/configuration.md)，并以当前 `application*.yml` 为事实源。
- 运行验证：读 [guides/real-env-testing.md](guides/real-env-testing.md) 和 [reference/test-map.md](reference/test-map.md)。

## 当前高风险误区

- 不要把默认 `mvn test` 通过等同于真实 RocketMQ/TDengine 全链路通过。
- 不要使用旧的 `logger.real-env=true` 开关，当前记录侧真实环境开关是 `logger.real-env.test=true`。
- 不要把 `plan/005` 中早期回放 MQ 实例控制方案当作当前生产入口，当前实例级回放控制是 HTTP。
- 不要从旧 README 示例照抄回放事件表 `1002/8`，当前源码配置是 `2301/3`。
- 不要把 `rawdata` 改回 `VARBINARY(8192)`，当前低版本 RESTful 驱动验证后采用 `BINARY(8192)`。

