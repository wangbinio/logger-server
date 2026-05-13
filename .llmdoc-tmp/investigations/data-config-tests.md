# 数据、配置与测试调查

## 配置事实

- `logger-server/src/main/resources/application.yml`：记录侧消息码、写入参数和默认 profile。
- `logger-server/src/main/resources/application-dev.yml`：真实 RocketMQ 和 TDengine 连接。
- `replay-server/src/main/resources/application.yml`：回放侧消息码、事件表配置、查询、调度、发布参数。
- `replay-server/src/main/resources/application-dev.yml`：真实 RocketMQ 和 TDengine 连接。
- `replay-server/src/test/resources/application-test.yml`：测试 profile，默认关闭全局监听。
- `replay-server/src/test/resources/application-real.yml`：真实环境测试补充开启全局监听。

## 当前消息配置

- 记录侧全局消息：`messageType=0`，`create=0`，`stop=1`。
- 记录侧控制消息：`messageType=1100`，`start=1`，`pause=5`，`resume=6`。
- 回放侧全局消息：当前源码配置为 `messageType=0`，`create=2`，`stop=3`。
- 回放侧 HTTP 内部控制语义：`messageType=1200`，`start=1`，`pause=2`，`resume=3`，`rate=4`，`jump=5`，`metadata=9`。
- 回放事件表配置：当前源码配置为 `1001/[1,2,3]` 和 `2301/[3]`。

## TDengine 事实

- 当前使用 `taos-jdbcdriver 3.2.4`。
- 连接方式为 `jdbc:TAOS-RS://...:6041/logger?timezone=UTC-8&charset=utf-8&varcharAsString=true`。
- driver 为 `com.taosdata.jdbc.rs.RestfulDriver`。
- 当前低版本组合不使用 `jdbc:TAOS-WS`。
- 态势 `rawdata` 字段为 `BINARY(8192)`。
- 真实环境探测显示低版本 RESTful 组合下 `VARBINARY(8192)` 读写存在空值风险。

## 测试入口

- 默认全量回归：`mvn -DfailIfNoTests=false test`。
- 记录侧真实环境：`mvn -pl logger-server -am "-Dtest=RealEnvironmentFullFlowTest" "-Dlogger.real-env.test=true" -DfailIfNoTests=false test`。
- 回放侧真实环境：`mvn -pl replay-server -am "-Dtest=ReplayRealEnvironmentTest" "-Dreplay.real-env.test=true" -DfailIfNoTests=false test`。
- 真实环境测试默认跳过，不能把默认回归通过等同于 RocketMQ 和 TDengine 全链路通过。

