# 当前代码契约

## 模块契约

- `common` 只放跨服务稳定契约，不放记录或回放业务状态机。
- `logger-server` 是 RocketMQ 到 TDengine 的记录服务。
- `replay-server` 是 TDengine 到 RocketMQ 的回放服务。
- `logger-server` 和 `replay-server` 是两个独立 Spring Boot 应用，共享 `common`。

## 当前消息契约

- 记录侧全局生命周期：`messageType=0`，`create=0`，`stop=1`。
- 记录侧实例控制：`messageType=1100`，`start=1`，`pause=5`，`resume=6`。
- 回放侧全局生命周期当前源码配置：`messageType=0`，`create=2`，`stop=3`。
- 回放侧 HTTP 控制内部语义：`messageType=1200`，`start=1`，`pause=2`，`resume=3`，`rate=4`，`jump=5`，`metadata=9`。
- 回放事件表当前源码配置：`1001/[1,2,3]` 和 `2301/[3]`。

## 当前接口契约

- 回放创建和停止仍由 `broadcast-global` 驱动。
- 回放实例级控制由 HTTP REST 驱动，不通过 `broadcast-{instanceId}`。
- 回放服务只发布 `situation-{instanceId}`，不消费该 Topic。

## 当前 TDengine 契约

- JDBC 依赖版本为 `taos-jdbcdriver 3.2.4`。
- 连接方式为 `jdbc:TAOS-RS://...:6041/logger?...`。
- 驱动为 `com.taosdata.jdbc.rs.RestfulDriver`。
- 当前不使用 `jdbc:TAOS-WS`。
- 态势表 `rawdata` 字段为 `BINARY(8192)`。
- `ReplayFrameRepository` 必须保留对低版本 RESTful 驱动 `rawdata` 读取类型的兼容处理。

## 文档漂移提醒

旧设计文档是重要背景，但不能覆盖当前源码事实。尤其是 `plan/005` 的回放实例级 MQ 控制、回放全局消息类型建议，以及旧 README 的事件表示例，必须先和当前 YAML、生产类、测试类核对。

