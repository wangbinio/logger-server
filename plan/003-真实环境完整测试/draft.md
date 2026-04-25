# 真实环境完整测试

现在我想要做一次完整的真实环境测试。在yml中已经配置了真实的rocketmq和tdengine环境，那么就可以写一个完整的测试用例

## 流程

1. 参考 `plan/001-总体设计/final.md` 中的 `7. 协议解析与消息处理流程` 说明，在loggerserver启动之后，测试程序根据流程往rocketmq中发送不同主题消息
2. broadcast-global 和 broadcast-{instanceId} 都是流程控制，覆盖每种情况就行
3. situation-{instanceId} 里面的消息才是真正记录的内容，按照下面的要求来批量构建situation-{instanceId}消息：
    - senderId ： 1 - 5
    - messageType ： 等于senderId
    - messageCode ： 1 - 5
    - rawData ： senderId、messageType、messageCode组成的json
4. 每个senderId都以1s的周期发送消息，循环发送messageCode 1 - 5的消息
5. 测试持续60s，60s之后通过broadcast-global控制停止
6. 停止之后，查询tdengine中的每个表的数据情况