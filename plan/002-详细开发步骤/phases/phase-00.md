# Phase 00：骨架清理与基础配置

## 实现思路

先把项目从“旧项目复制残留状态”清理为可持续开发的最小骨架。该阶段不追求业务功能，而是统一依赖、配置、目录和公共常量，避免后续各阶段在错误配置上反复返工。

## 需要新增的文件

- `src/main/java/com/szzh/loggerserver/config/LoggerServerProperties.java`
- `src/main/java/com/szzh/loggerserver/config/TdengineConfig.java`
- `src/main/java/com/szzh/loggerserver/support/constant/TopicConstants.java`
- `src/main/java/com/szzh/loggerserver/support/constant/MessageConstants.java`
- `src/main/java/com/szzh/loggerserver/util/JsonUtil.java`

## 待办步骤

- [x] 清理 `application.yml`
  说明：移除安全、Redis、Knife4j、旧应用名、旧上下文路径等无关配置，仅保留 logger-server 运行所需项。
- [x] 清理 `application-local.yml`
  说明：切换到 TDengine WebSocket 数据源配置，统一 RocketMQ consumer 组与本地连接信息。
- [x] 新增 `LoggerServerProperties`
  说明：集中承载 `logger-server.*` 配置，避免后续散落读取。
- [x] 新增 `TdengineConfig`
  说明：配置 `DataSource`、`JdbcTemplate` 或相关 TDengine 访问 Bean。
- [x] 新增主题与消息常量类
  说明：统一管理 `broadcast-global`、实例 topic 前缀、消息编号和类型编号，减少硬编码。
- [x] 新增 JSON 工具类
  说明：封装 `instanceId` 等 JSON 解析入口，为全局消息处理阶段做准备。
- [x] 校验目录骨架
  说明：按总体设计预创建 `config`、`domain`、`mq`、`service`、`support`、`model` 目录，降低后续大范围移动文件的成本。

## 预期完成标志

- 配置文件已与总体设计一致。
- 基础 Bean 与常量准备完成。
- 后续阶段可以直接在稳定骨架上编码。

## 疑问或待澄清

当前无阻塞性疑问。
