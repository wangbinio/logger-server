# llmdoc 初始化

## 背景

- 当前仓库尚未存在 `llmdoc/` 目录，需要按 `$llmdoc:llmdoc-init` 建立面向后续 Codex 代理的稳定文档入口。
- 仓库已经是 `common`、`logger-server`、`replay-server` 三模块 Maven 工程，现有 `README.md`、`ARCHITECTURE.md`、`plan/` 和 `tasks/` 中有大量稳定事实。
- 本次任务只沉淀文档和调查材料，不改业务代码。

## 执行计划

- [x] 建立 `llmdoc/` 与 `.llmdoc-tmp/` 骨架。
- [x] 调查项目入口、模块职责、配置、消息协议、TDengine 数据模型、测试与真实环境验证路径。
- [x] 生成 `.llmdoc-tmp/investigations/` 调查草稿。
- [x] 生成 `llmdoc/index.md`、`llmdoc/startup.md`、MUST 文档、overview、architecture、guides、reference 文档。
- [x] 检查索引路由、文档互链和关键事实一致性。

## 复盘记录

- 已创建 `llmdoc/index.md` 和 `llmdoc/startup.md`，作为后续代理进入仓库的稳定入口。
- 已创建 MUST 文档：`llmdoc/must/repo-rules.md`、`llmdoc/must/current-contracts.md`，固化语言、任务单、验证、消息码、TDengine 低版本兼容等关键约束。
- 已创建 overview、architecture、guides、reference 文档，覆盖项目结构、消息流、TDengine 数据模型、真实环境测试和配置参考。
- 已创建 `.llmdoc-tmp/investigations/` 调查草稿，按项目结构、记录链路、回放链路、数据配置测试、交叉缺口分片沉淀。
- 已记录文档漂移：旧 README 和 `plan/005` 中仍有历史回放事件表和 MQ 控制说明，当前以源码 YAML 和 `plan/007` 后续修订为准。
- 文档链接检查通过：`llmdoc markdown links OK`。
- `git diff --check` 通过。
- 默认回归通过：`mvn -DfailIfNoTests=false test`，123 个测试通过，1 个真实环境测试按开关跳过。
