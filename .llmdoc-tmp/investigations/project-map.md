# 项目结构调查

## 结论

`logger-platform` 是 Java 8 / Spring Boot 2.7.12 的 Maven 多模块工程，父工程版本为 `0.2.0`，包含 `common`、`logger-server`、`replay-server` 三个模块。

## 模块边界

- `common`：共享协议解析、JSON、Topic 命名、TDengine 命名、通用异常，不包含 Spring Boot 启动类。
- `logger-server`：记录服务，从 RocketMQ 消费仿真生命周期、控制、态势消息，维护记录会话并写入 TDengine。
- `replay-server`：回放服务，从 RocketMQ 接收回放任务创建和停止，从 TDengine 查询历史记录，通过 HTTP 控制回放并向 RocketMQ 发布态势。

## 关键根文件

- `pom.xml`：父工程，统一 `java.version=1.8`、`rocketmq-spring.version=2.2.3`、`taos-jdbcdriver.version=3.2.4`。
- `README.md`：用户可读能力说明和命令入口。
- `ARCHITECTURE.md`：平台级架构、链路、数据模型、测试策略。
- `plan/`：历史设计、阶段计划和审阅修复计划。
- `tasks/`：本地任务单、交付记录和经验教训。

## 事实优先级

当前代码和 YAML 是最高优先级事实来源。`README.md` 和 `plan/005-回放系统设计/005-final.md` 中仍存在历史示例，例如回放事件表配置写成 `1002/8`，而当前 `replay-server/src/main/resources/application.yml` 实际使用 `2301/3`。后续代理应先核对源码配置，再引用文档示例。

