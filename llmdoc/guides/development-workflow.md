# 开发工作流

## 开始任务

1. 读取 `llmdoc/startup.md`。
2. 读取 `tasks/lessons.md`。
3. 查找与任务相关的 `plan/` 文档和最新 `tasks/todo-*.md`。
4. 新建下一个编号的任务单，例如 `tasks/todo-48-xxx.md`。
5. 写清执行计划和验证计划。

## 设计驱动任务

如果用户要求按某个 `final.md` 实现，默认该文档是用户确认过的边界。先补测试，再实现，再回写任务单和相关阶段文档。

## 修改代码时

- 保持 Java 8 兼容。
- 函数声明处添加中文注释。
- 复杂业务分支添加必要中文注释。
- 不随意改测试契约。
- 不做无关格式化或大规模重构。

## 修改配置时

- 先读 `reference/configuration.md`。
- 以当前 `application*.yml` 为事实源。
- 同步考虑 `LoggerServerProperties`、`ReplayServerProperties` 和对应配置测试。
- TDengine URL 和 driver 必须匹配。

## 结束任务

- 运行相关测试或说明为什么未运行。
- 运行 `git diff --check`。
- 回写任务单复盘。
- 如果发现用户纠正或可复用经验，更新 `tasks/lessons.md`。

