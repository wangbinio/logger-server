# Lessons

- 当用户对设计文档中的开放决策给出明确裁定时，需要同步收敛文档和实际工程配置，不能只保留为“建议”状态。
- 当用户纠正本地工具可用性时，应立即重新验证，不要延续上一轮“环境不可用”的结论；若用户提供了工具绝对路径，应按该路径继续执行。
- 当项目目标版本是 Java 8 时，兼容性验证必须使用 Java 8 运行 Maven 或编译器，不能仅用系统默认的更高版本 JDK 代替验证。
- 当类中的 getter / setter 只是简单字段访问且项目已使用 Lombok 时，优先使用 Lombok 注解生成，不手写样板访问器。
- 当用户反馈“手工验证通过”时，必须在当前代码与当前环境下重新执行对应自动化用例，不能直接沿用历史 surefire 报告，也不能把手工结论等同于自动化路径已打通。
- 设计回放系统时，必须明确回放服务只发布不消费 `situation-{instanceId}`；停止回放应取消 `broadcast-{instanceId}` 订阅，不能沿用记录系统的态势订阅模型。
- 由于现在代码中的日志服务代码很多，严重影响了代码的阅读，所以需要在所有业务流程函数调用的地方添加注释，而且log和业务代码要间隔一行，方便review代码。
- log行数缩减成2行，这样
  ```java
    log.info("result=pause_success instanceId={} topic=- messageType={} messageCode={} senderId={} simtime={} sessionState={}",
                instanceId, protocolData.getMessageType(), protocolData.getMessageCode(), protocolData.getSenderId(), session.getSimulationClock().currentSimTimeMillis(), session.getState());
  ```
