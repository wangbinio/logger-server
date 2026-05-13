# 真实环境测试指南

## 前提

当前 `application-dev.yml` 已配置可连接的真实 RocketMQ 和 TDengine。真实环境测试默认跳过，必须显式打开系统属性。

## logger-server

命令：

```powershell
mvn -pl logger-server -am "-Dtest=RealEnvironmentFullFlowTest" "-Dlogger.real-env.test=true" -DfailIfNoTests=false test
```

验证内容：

- 真实 RocketMQ 全局消息、实例控制消息和态势消息。
- TDengine 建库、建表、写入。
- 创建、启动、暂停、继续、停止完整流程。
- 态势表数量和记录数校验。

注意：正确开关是 `logger.real-env.test=true`。

## replay-server

命令：

```powershell
mvn -pl replay-server -am "-Dtest=ReplayRealEnvironmentTest" "-Dreplay.real-env.test=true" -DfailIfNoTests=false test
```

验证内容：

- 真实 RocketMQ 创建和停止回放任务。
- 真实 TDengine 写入测试数据和回放读取。
- HTTP jump 控制。
- 态势发布和水位推进。

## 表述要求

- 默认回归通过只能说明普通测试通过。
- 真实环境测试通过才能说明当前 RocketMQ 和 TDengine 全链路通过。
- 如果真实环境测试失败，先区分网络连通、broker 路由、Spring 自动监听、TDengine 连接、数据内容校验，不要只给重启建议。

