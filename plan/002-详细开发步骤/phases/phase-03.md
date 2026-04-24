# Phase 03：RocketMQ 动态订阅

## 实现思路

把 MQ 能力分成固定监听和动态订阅两类：固定监听器只处理 `broadcast-global`，实例级 topic 的订阅和销毁由统一管理器负责。这样能把订阅生命周期和业务处理解耦，避免后续并发实例场景失控。

## 需要新增的文件

- `src/main/java/com/szzh/loggerserver/mq/GlobalBroadcastListener.java`
- `src/main/java/com/szzh/loggerserver/mq/TopicSubscriptionManager.java`
- `src/main/java/com/szzh/loggerserver/mq/InstanceBroadcastMessageHandler.java`
- `src/main/java/com/szzh/loggerserver/mq/SituationMessageHandler.java`
- `src/main/java/com/szzh/loggerserver/config/RocketMqConsumerFactory.java`
- `src/test/java/com/szzh/loggerserver/mq/TopicSubscriptionManagerTest.java`

## 待办步骤

- [x] 实现 `RocketMqConsumerFactory`
  说明：统一创建实例级控制消费者和态势消费者，封装公共 consumer 参数。
- [x] 实现 `TopicSubscriptionManager`
  说明：维护 `instanceId -> consumer handles` 映射，负责注册、启动、停止和回收。
- [x] 实现 `GlobalBroadcastListener`
  说明：固定监听 `broadcast-global`，解析创建/停止消息并转交生命周期服务。
- [x] 实现 `InstanceBroadcastMessageHandler`
  说明：处理实例级启动、暂停、继续控制消息。
- [x] 实现 `SituationMessageHandler`
  说明：处理态势消息解析、状态检查和入库委派。
- [x] 编写订阅管理测试
  说明：覆盖重复订阅、重复取消、异常回收和会话不存在场景。

## 预期完成标志

- MQ 订阅生命周期可单独管理。
- 消息处理器与 consumer 创建逻辑已经解耦。

## 疑问或待澄清

当前阶段代码已完成。真实 RocketMQ 探测表明 `rocketmq.name-server=192.168.233.109:9876` 可连通，但 namesrv 返回的 broker 地址为 `127.0.0.1:10911`，因此当前机器无法完成真正的动态订阅端到端消费；相关集成测试已保留，并在该环境条件下自动跳过，待 MQ 对外注册地址修正后即可恢复真实验证。
