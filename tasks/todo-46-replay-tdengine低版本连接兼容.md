# replay-server TDengine 低版本连接兼容

## 背景

- logger-server 已按生产环境验证 `taos-jdbcdriver 3.2.4`、`jdbc:TAOS-RS` 和 `com.taosdata.jdbc.rs.RestfulDriver` 可用。
- 修改前 replay-server 仍保留 `jdbc:TAOS-WS` 与 `com.taosdata.jdbc.ws.WebSocketDriver` 配置。
- 需要同步 replay-server 的主配置、测试配置、默认属性、数据源配置校验和相关测试。

## 执行计划

- [x] 检查 replay-server 中 TDengine WS 配置和驱动残留。
- [x] 对齐 replay-server 的 `application-dev.yml`、`application-test.yml` 和默认驱动。
- [x] 给 `ReplayTdengineConfig` 增加 JDBC URL 前缀与 driver-class-name 匹配校验。
- [x] 更新 replay-server 配置测试，锁定 `TAOS-RS` / `RestfulDriver` 组合。
- [x] 收敛 Maven 依赖版本使用方式，避免 logger-server 与 replay-server 版本漂移。
- [x] 运行 replay-server 定向测试与模块回归。
- [x] 运行 replay-server 真实环境测试，确认低版本 TDengine 和 RocketMQ 全链路可用。

## 复盘记录

- 父工程 `taos-jdbcdriver.version` 已统一为 `3.2.4`，`logger-server` 与 `replay-server` 模块都引用该属性，避免模块版本漂移。
- `replay-server/src/main/resources/application-dev.yml` 已切换为 `jdbc:TAOS-RS://192.168.233.109:6041/db_satellite_367?...` 与 `com.taosdata.jdbc.rs.RestfulDriver`。
- `replay-server/src/test/resources/application-test.yml` 与 `ReplayServerProperties` 默认驱动已同步为 `TAOS-RS` / `RestfulDriver` 组合。
- `ReplayTdengineConfig` 已增加 JDBC URL 与驱动类匹配校验，明确拒绝当前低版本不支持的 `TAOS-WS`。
- 真实环境验证发现低版本 TDengine / RESTful 驱动组合下 `VARBINARY` 写入读回为空，已将当前态势表 `rawdata` 字段收敛为 `BINARY(8192)`。
- 已同步 README、ARCHITECTURE 和当前计划定稿文档中的 TDengine Connector 版本和连接方式说明。
- 定向验证通过：`mvn -pl replay-server -am "-Dtest=ReplayTdengineConfigTest,ReplayServerPropertiesTest" -DfailIfNoTests=false test`。
- replay-server 模块回归通过：`mvn -pl replay-server -am -DfailIfNoTests=false test`，122 个测试通过，1 个真实环境测试按开关跳过。
- replay-server 真实环境测试通过：`mvn -pl replay-server -am "-Dtest=ReplayRealEnvironmentTest" "-Dreplay.real-env.test=true" -DfailIfNoTests=false test`，1 个测试通过，0 失败、0 错误、0 跳过。
- 根工程回归通过：`mvn -DfailIfNoTests=false test`。
- 真实环境复核通过：再次运行 `mvn -pl replay-server -am "-Dtest=ReplayRealEnvironmentTest" "-Dreplay.real-env.test=true" -DfailIfNoTests=false test`，1 个测试通过，0 失败、0 错误、0 跳过。
