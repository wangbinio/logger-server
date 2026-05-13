# 测试地图

## 默认回归

```powershell
mvn -DfailIfNoTests=false test
```

## 模块回归

```powershell
mvn -pl common test
mvn -pl logger-server -am -DfailIfNoTests=false test
mvn -pl replay-server -am -DfailIfNoTests=false test
```

## logger-server 重点测试

- `SimulationFlowIntegrationTest`：记录侧内存集成流程。
- `RealEnvironmentFullFlowTest`：真实环境完整流程，默认跳过。
- `TopicSubscriptionManagerTest`：动态订阅和真实 RocketMQ 探测相关。
- `SimulationLifecycleServiceTest`：创建、停止和建表链路。
- `SimulationControlServiceTest`：start、pause、resume 和控制时间点。
- `SituationRecordServiceTest`：态势写入状态判断。
- `TdengineConfigTest`：TDengine URL 与 driver 校验。
- `TdengineWriteServiceTest`：标准 JDBC 写入和 batch。

## replay-server 重点测试

- `ReplayFlowIntegrationTest`：回放服务内存集成流程。
- `ReplaySpringFlowIntegrationTest`：Spring + MockMvc 回放控制集成。
- `ReplayRealEnvironmentTest`：真实环境回放链路，默认跳过。
- `ReplayControlControllerTest`：HTTP 接口响应和状态码。
- `ReplayControlServiceTest`：回放状态机控制。
- `ReplaySchedulerTest`：连续窗口调度和水位推进。
- `ReplayJumpServiceTest`：跳转补偿语义。
- `ReplayFrameRepositoryTest`：TDengine 帧查询和 rawdata 兼容读取。
- `ReplayTableDiscoveryRepositoryTest`：子表 tag 元数据发现。
- `ReplayTimeControlRepositoryTest`：时间范围解析和降级。

## 真实环境

```powershell
mvn -pl logger-server -am "-Dtest=RealEnvironmentFullFlowTest" "-Dlogger.real-env.test=true" -DfailIfNoTests=false test
mvn -pl replay-server -am "-Dtest=ReplayRealEnvironmentTest" "-Dreplay.real-env.test=true" -DfailIfNoTests=false test
```

真实环境测试依赖当前可用的 RocketMQ 和 TDengine，失败时不要直接归因代码，需要先看连接、路由和外部服务状态。

