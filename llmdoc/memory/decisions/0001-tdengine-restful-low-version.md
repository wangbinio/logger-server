# 决策 0001：低版本 TDengine RESTful 连接

## 背景

生产环境 TDengine 版本较低，当前兼容组合为：

```text
taos-jdbcdriver 3.2.4
jdbc:TAOS-RS://...:6041/logger
com.taosdata.jdbc.rs.RestfulDriver
```

此前的 WebSocket 方式不适用于当前生产环境。

## 决策

- `logger-server` 和 `replay-server` 统一使用 `TAOS-RS` 与 `RestfulDriver`。
- `TdengineConfig` 和 `ReplayTdengineConfig` 校验 URL 前缀与 driver-class-name 匹配。
- 当前明确拒绝 `TAOS-WS`。
- TDengine 写入使用标准 JDBC 和 batch，不依赖 WebSocket 专用 statement。
- 态势表 `rawdata` 字段使用 `BINARY(8192)`。

## 原因

真实环境验证发现低版本 RESTful 驱动组合下，`VARBINARY(8192)` 写入后读回存在空值风险；`BINARY(8192)` 可写可读，并已通过记录侧和回放侧真实环境测试。

## 后续约束

升级 TDengine 或 `taos-jdbcdriver` 时，不能只按类型语义判断字段选择，必须重新执行真实环境写入、读取和回放测试。

