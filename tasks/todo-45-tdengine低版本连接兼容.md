# TDengine 低版本连接兼容排障

## 背景

- 生产环境 TDengine 版本较低，不能继续使用 `jdbc:TAOS-WS` WebSocket 连接方式。
- 当前生产环境使用 `taos-jdbcdriver 3.2.4`、`jdbc:TAOS-RS` 和 `com.taosdata.jdbc.rs.RestfulDriver`。
- 本地曾短暂改成 `jdbc:TAOS` 和 `com.taosdata.jdbc.TSDBDriver`，该组合与 `6041` RESTful 端口不匹配。
- 需要确认 `TdengineConfig` 是否需要同步调整，并收敛为可验证的最小修复。

## 执行计划

- [x] 对比当前 TDengine JDBC URL、driver-class-name、驱动版本与代码实现的匹配关系。
- [x] 补充驱动和 URL 前缀匹配校验测试，避免 `TAOS` / `TAOS-RS` / `TAOS-WS` 配置混用。
- [x] 修复记录侧非 WS 驱动兼容问题，移除运行时对 WebSocket 专用 statement 的依赖。
- [x] 运行 TDengine 配置与写入服务定向测试。
- [x] 视外部环境可用性运行 `RealEnvironmentFullFlowTest`，记录真实环境验证结果。

## 复盘记录

- `logger-server/pom.xml` 已对齐生产依赖 `taos-jdbcdriver 3.2.4`。
- `application-dev.yml` 已对齐低版本兼容连接方式：`jdbc:TAOS-RS://...` 与 `com.taosdata.jdbc.rs.RestfulDriver`。
- `TdengineConfig` 增加 JDBC URL 前缀与驱动类匹配校验，避免 `jdbc:TAOS://...:6041` 配 `TSDBDriver` 这类错误组合延迟到运行期才暴露。
- `TdengineWriteService.writeBatchByStmt(...)` 改为标准 JDBC batch，不再依赖 `TSWSPreparedStatement`。
- 定向验证通过：`mvn -pl logger-server -am "-Dtest=ApplicationProfileConfigurationTest,TdengineConfigTest,TdengineWriteServiceTest" -DfailIfNoTests=false test`，13 个测试通过。
- 默认回归通过：`mvn -pl logger-server -am -DfailIfNoTests=false test`，64 个测试通过，2 个真实环境相关测试按开关跳过。
- 用户已确认 logger-server 在生产同款 TDengine 低版本连接配置下通过真实环境测试。
