# 回放系统设计

记录系统已经全部实现。总体设计文档为 @plan/001-总体设计/final.md，系统架构文档为 @ARCHITECTURE.md。
现在需要根据记录系统的实现，设计回放系统。

## 整体架构

回放系统的整体架构与记录系统非常类似。也是通过订阅`broadcast-global`与`broadcast-{instanceId}`2个主题完成整个流程。
记录系统订阅`situation-instanceId`主题，拿到消息记录到tdengine，回放系统从tdengine中读取数据，发送到`situation-instanceId`主题中，但是回放系统多很多细节。

## Topic: broadcast-global  回放任务管理消息

最高层消息，管理回放任务的创建和停止，服务启动就需要开始订阅`broadcast-global`主题。通过调用已有的`ProtocolMessageUtil::parseData`解析出消息结构`ProtocolData`获取类型编号(messageType)，消息编号(messageCode)。

```java
@Data
public class ProtocolData {
	private int senderId;
	private int messageType;
	private int messageCode;
	private byte[] rawData;
}
```

其中类型编号固定为1。
消息编号含义如下：

|  消息编号   | 消息含义  |
|  ----  | ----  |
| 0  | 回放任务创建 |
| 1  | 回放任务停止 |

当解析出`消息编号`为0时，开始回放准备工作：

  1. 解析`ProtocolData::rawData`。`ProtocolData::rawData`是json数据，ProtocolData::rawData["instanceId"] 是全局唯一回放实例id，命名为instanceId，后续很多地方都会用到这个instanceId作为唯一标识。
  2. 在rocketmq中订阅1个新的Topic: `broadcast-instanceId`
  3. 根据instanceId确定记录时候所有的数据表。记录只用了2句sql，但是这两句sql会产生大量的记录数据表。
  4. 在记录的时候，所有的数据都是统一处理，一句sql就存入了对应的数据库表。`situation_${messageType}_${messageCode}_${senderId}_${instanceId}`。
  5. 但是在回放的时候要根据表名将表分为2类：周期类表和事件类表。2种类型的表在回放阶段要做不同的处理。
  6. 通过表名中的`${messageType}_${messageCode}`关键部分区分表的类型，在yml中配置`messageType`和`messageCode`来匹配`事件类表`，messageType有多个，每个messageType下又可以有多个messageCode。只要表名中的`${messageType}_${messageCode}`能在yml配置中匹配到，那么它就是`事件类表`，否则是`周期类表`。
  7. 通过`time_control_{instanceId}`表获取并记下仿真时间调整信息、仿真开始时间、结束时间，计算持续时间。参考 `plan/004-记录仿真控制指令时间点/final.md`
  8. 发送一包消息到`broadcast-instanceId`中, messageType为1200，messageCode为9，rawData为`仿真开始时间`、`仿真结束时间`，`仿真持续时间`组成的json。

当解析出`消息编号`为1时，停止所有回放工作，停止订阅`broadcast-instanceId`。


## Topic: broadcast-instanceId 回放控制指令消息

控制回放的开始、暂停、倍速。同样通过调用`ProtocolMessageUtil::parseData`解析出消息结构`ProtocolData`。
其中类型编号固定为1200。
消息编号含义如下：

|  消息编号   | 消息含义  |
|  ----  | ----  |
| 1  | 启动回放 |
| 2  | 暂停回放 |
| 3  | 继续回放 |
| 4  | 倍速回放 |
| 5  | 时间跳转 |

当解析出`消息编号`为1时，回放开始（速度x1）：

  1. 服务自己维护一个`仿真时间`，回放开始时，仿真时间 = 前面通过`time_control_{instanceId}`获取到的仿真开始时间。
  2. 将所有`事件类表`和`周期类表`中`simtime` < `仿真时间` 的数据发送到 `situation-instanceId` 主题中。
  3. `仿真时间`正常推进。
  
当解析出`消息编号`为2时，暂停回放（速度x0），`仿真时间`也需要暂停。

当解析出`消息编号`为3时，继续回放（速度x1），同`回放开始`。

当解析出`消息编号`为4时，倍速回放（速度xN）：

  1. 解析`ProtocolData::rawData`。`ProtocolData::rawData`是json数据，ProtocolData::rawData["rate"]是目标倍速N。
  2. `仿真时间`也要倍速推进。

当解析出`消息编号`为5时，时间跳转：

  1. 解析`ProtocolData::rawData`。`ProtocolData::rawData`是json数据，ProtocolData::rawData["time"]是目标时间点(native long表示)，暂定为`jumpTime`。
  2. 将`jumpTime`限制到[`仿真开始时间`, `仿真结束时间`]范围内。
  3. 如果`jumpTime` < `仿真时间`，也就是说往回跳：
   
     1. 将所有`事件类表`中 `simtime` < `jumpTime` 的数据发送到`situation-instanceId`中。
     2. 将所有`周期类表`中 `simtime` < `jumpTime` 的最后一帧数据发送到`situation-instanceId`中。
     3. `仿真时间`同步到`jumpTime`。
   
  4. 如果`jumpTime` > `仿真时间`，也就是说往后跳：
   
     1. 将所有`事件类表`中 `simtime` 处于 [`仿真时间`, `jumpTime`]范围内的数据发送到`situation-instanceId`中。
     2. 将所有`周期类表`中 `simtime` < `jumpTime` 的最后一帧数据发送到`situation-instanceId`中。
     3. `仿真时间`同步到`jumpTime`。

  5. 时间跳转的要求就是事件数据不能丢，从开始到`jumpTime`的数据全要。但是周期数据只要每个表`jumpTime`前的最后一帧数据。
   

## 一些想法和问题

- 回放系统要不要和记录系统一个项目，是不是开一个新的项目更好？虽然它们有很多可以复用的内容。
- 回放系统的架构是不是可以尽量和记录系统保持一致，方便用户理解和维护2套系统。
- 回放的流程这么设计有没有什么问题？还可以做哪些改进？
- 记录系统是接收到主题消息之后，直接写数据库，正常来说不会存在效率问题，所以也没有引入redis做缓存，后续如果有效率问题也可以通过batch write解决。
- 但是回放系统有倍速和时间跳转要求，特别是时间跳转，需要读取大量`事件类表`，我不确定要不要引入redis来做缓存，怎么做。


## To codex

根据这份文档和现有代码框架，先和我讨论上面的这些问题以及你发现的一些问题，然后再生成详细的技术方案，输出到final.md




