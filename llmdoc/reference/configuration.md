# 配置参考

## 父工程版本

| 配置 | 当前值 |
| --- | --- |
| `java.version` | `1.8` |
| `rocketmq-spring.version` | `2.2.3` |
| `taos-jdbcdriver.version` | `3.2.4` |
| `jackson.version` | `2.13.5` |

## logger-server

主配置：

| 配置 | 当前值 |
| --- | --- |
| `spring.application.name` | `logger-server` |
| `spring.profiles.active` | `dev` |
| `logger-server.protocol.messages.global.message-type` | `0` |
| `logger-server.protocol.messages.global.create-message-code` | `0` |
| `logger-server.protocol.messages.global.stop-message-code` | `1` |
| `logger-server.protocol.messages.control.message-type` | `1100` |
| `logger-server.protocol.messages.control.start-message-code` | `1` |
| `logger-server.protocol.messages.control.pause-message-code` | `5` |
| `logger-server.protocol.messages.control.resume-message-code` | `6` |
| `logger-server.write.retry-times` | `3` |
| `logger-server.write.batch-size` | `500` |

dev 配置：

| 配置 | 当前值 |
| --- | --- |
| `rocketmq.name-server` | `192.168.233.109:9876` |
| `logger-server.tdengine.jdbc-url` | `jdbc:TAOS-RS://192.168.233.109:6041/logger?timezone=UTC-8&charset=utf-8&varcharAsString=true` |
| `logger-server.tdengine.driver-class-name` | `com.taosdata.jdbc.rs.RestfulDriver` |
| `logger-server.rocketmq.global-consumer-group` | `logger-global-consumer` |
| `logger-server.rocketmq.instance-consumer-group-prefix` | `logger-instance` |

## replay-server

主配置：

| 配置 | 当前值 |
| --- | --- |
| `spring.application.name` | `replay-server` |
| `spring.profiles.active` | `dev` |
| `replay-server.protocol.messages.global.message-type` | `0` |
| `replay-server.protocol.messages.global.create-message-code` | `2` |
| `replay-server.protocol.messages.global.stop-message-code` | `3` |
| `replay-server.protocol.messages.control.message-type` | `1200` |
| `replay-server.protocol.messages.control.start-message-code` | `1` |
| `replay-server.protocol.messages.control.pause-message-code` | `2` |
| `replay-server.protocol.messages.control.resume-message-code` | `3` |
| `replay-server.protocol.messages.control.rate-message-code` | `4` |
| `replay-server.protocol.messages.control.jump-message-code` | `5` |
| `replay-server.replay.event-messages` | `1001/[1,2,3]`，`2301/[3]` |
| `replay-server.replay.query.page-size` | `1000` |
| `replay-server.replay.scheduler.tick-millis` | `50` |
| `replay-server.replay.publish.batch-size` | `500` |
| `replay-server.replay.publish.retry-times` | `3` |

dev 配置：

| 配置 | 当前值 |
| --- | --- |
| `rocketmq.name-server` | `192.168.233.109:9876` |
| `rocketmq.producer.group` | `replay-producer` |
| `replay-server.tdengine.jdbc-url` | `jdbc:TAOS-RS://192.168.233.109:6041/logger?timezone=UTC-8&charset=utf-8&varcharAsString=true` |
| `replay-server.tdengine.driver-class-name` | `com.taosdata.jdbc.rs.RestfulDriver` |
| `replay-server.rocketmq.global-consumer-group` | `replay-global-consumer` |
| `replay-server.rocketmq.producer-group` | `replay-producer` |
| `replay-server.rocketmq.enable-global-listener` | `true` |

## 配置漂移提示

如果本文件与源码 YAML 不一致，以源码 YAML 为准，并立即更新本文件和相关 llmdoc 索引。

