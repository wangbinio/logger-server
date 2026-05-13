# llmdoc 索引

## 启动入口

- [startup.md](startup.md)：每次进入本仓库任务时优先阅读。
- [must/repo-rules.md](must/repo-rules.md)：仓库协作、语言、任务单和验证规则。
- [must/current-contracts.md](must/current-contracts.md)：当前代码契约和不能误用的历史文档差异。

## 总览

- [overview/project-overview.md](overview/project-overview.md)：项目定位、模块职责和关键能力。

## 架构

- [architecture/module-map.md](architecture/module-map.md)：三模块包结构和职责边界。
- [architecture/message-and-data-flow.md](architecture/message-and-data-flow.md)：RocketMQ、HTTP、会话和回放链路。
- [architecture/tdengine-and-replay.md](architecture/tdengine-and-replay.md)：TDengine 数据模型、低版本兼容和回放查询语义。

## 指南

- [guides/development-workflow.md](guides/development-workflow.md)：本仓库开发、任务单和验证流程。
- [guides/real-env-testing.md](guides/real-env-testing.md)：真实 RocketMQ/TDengine 测试入口和注意事项。

## 参考

- [reference/configuration.md](reference/configuration.md)：当前 YAML 配置、消息码和连接参数。
- [reference/test-map.md](reference/test-map.md)：测试分层和常用 Maven 命令。

## 记忆区

- [memory/decisions/0001-tdengine-restful-low-version.md](memory/decisions/0001-tdengine-restful-low-version.md)：低版本 TDengine RESTful 连接决策。
- [memory/reflections/2026-05-13-init.md](memory/reflections/2026-05-13-init.md)：本次初始化反思。
- [memory/doc-gaps.md](memory/doc-gaps.md)：当前发现的文档漂移和后续补齐点。

## 临时调查

`.llmdoc-tmp/investigations/` 是本次初始化的临时调查材料，不作为稳定文档入口。需要事实溯源时可阅读，但后续索引不应把它当作长期知识库。

