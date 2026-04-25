# 任务：消息常量与环境配置外置化

## 背景

当前 `MessageConstants.java` 内直接硬编码了全局广播与实例控制消息的 `messageType`、`messageCode`，这会让协议变更必须改代码并重新发布。同时 `application.yml` 中混放了通用配置与开发环境专属的 `tdengine`、`rocketmq` 配置，导致基础配置职责不清，不利于后续区分不同环境。

## 计划

- [x] 梳理 `MessageConstants`、属性绑定类和现有 YAML 结构，确认消息常量外置化后的最小改动方案。
- [x] 先补充失败测试，覆盖消息配置绑定、监听器消息判定以及环境配置拆分后的加载约束。
- [x] 实现 `messageType/messageCode` 的 YAML 外置化，调整相关监听器与测试使用方式。
- [x] 新增 `application-dev.yml`，将 `application.yml` 中的 `tdengine`、`rocketmq` 配置迁移过去，并保留基础通用配置。
- [x] 运行相关测试并记录验证结果。

## Review

- 已将协议消息配置收敛到 `logger-server.protocol.messages`：
  - `LoggerServerProperties.Protocol` 新增 `messages.global` 与 `messages.instance` 两级结构，承载全局生命周期与实例控制消息的 `messageType/messageCode`。
  - `MessageConstants` 不再暴露硬编码静态常量，改为基于 `LoggerServerProperties` 构建的配置快照，并向监听器提供读取与判定方法。
- 已将消息分发主路径切到配置值：
  - `GlobalBroadcastListener` 与 `InstanceBroadcastMessageHandler` 改为依赖注入 `MessageConstants`，不再直接依赖静态硬编码。
  - 因 Java 8 `switch case` 不能使用运行期配置值，消息码分发改为显式 `if/else`，行为保持不变但允许动态配置。
- 已完成配置文件拆分：
  - `application.yml` 仅保留通用配置，并新增默认协议消息配置。
  - 新建 `application-dev.yml`，承载原先位于 `application.yml` 中的 `logger-server.tdengine` 与 `logger-server.rocketmq` 配置。
- 已补充并通过的测试：
  - `ApplicationProfileConfigurationTest`：校验基础配置不再包含 `tdengine/rocketmq`，且开发配置文件包含迁移后的条目。
  - `MessageConstantsTest`：校验全局/实例消息配置能够从属性对象读取。
  - `GlobalBroadcastListenerTest`、`InstanceBroadcastMessageHandlerTest`：校验监听器和处理器真正使用配置化的消息类型与消息码。
  - 相关集成测试与订阅测试已同步改造，并通过全量 `mvn -q test` 回归。
- `llmdoc-update` 未执行：当前仓库不存在 `llmdoc/` 目录，缺少该工作流所需的文档基线。
