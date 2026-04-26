# todo-32-log 优化

## 背景

logger-server 模块中部分结构化日志直接嵌入业务流程，模板和参数列表过长，影响代码阅读和 review。参考 replay-server 中 `ReplayControlService` 的日志封装方式，将 logger-server 主模块中的日志表达收敛为语义化私有方法，保持原有字段和处理语义不变。

## 计划

- [x] 读取 `tasks/lessons.md`、`ReplayControlService` 和 logger-server 当前日志调用点，确认优化边界。
- [x] 优化 `logger-server/src/main/java/com/szzh/loggerserver` 下 MQ 处理器的日志调用，保留原结构化字段。
- [x] 优化 service 层控制、生命周期、记录写入等日志调用，保留原结构化字段。
- [x] 检查函数声明中文注释和复杂逻辑注释，避免新增不可读辅助方法。
- [x] 运行测试验证改动未影响行为。
- [x] 回写本任务 review 结果。

## 验收标准

- logger-server 主模块业务流程中的长日志调用被语义化封装，核心流程阅读时不再被多行日志模板打断。
- 日志字段名称、日志级别和异常附带方式保持兼容，不改变线上排查语义。
- 自动化测试通过，或明确记录已知外部依赖失败边界。

## Review

- 已参考 `ReplayControlService` 的方式，将业务流程中的长日志模板收敛为私有日志方法调用。
- MQ 处理器保留 `protocol_parse_failed`、`port_missing`、`ignored_unknown_message`、`unexpected_exception`、`business_exception` 的原日志级别和字段。
- service 层保留控制命令、生命周期、态势记录、TDengine 重试写入的原结构化字段和异常附带方式。
- 已执行 `mvn -pl logger-server -am test`，结果为 BUILD SUCCESS；logger-server 测试 59 个，通过 58 个，真实环境测试按开关跳过 1 个。
