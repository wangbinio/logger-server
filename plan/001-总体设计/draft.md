
# 基于rocketmq的数据记录服务

本服务通过订阅rocketmq中不同的topic，接收并解析其数据，将需要的数据记录到TDEngine数据库中。

## Topic主题消息说明

### broadcast-global 仿真任务管理消息

最高层消息，管理仿真任务的创建和停止，服务启动就需要开始订阅`broadcast-global`主题。通过调用已有的`ProtocolMessageUtil::parseData`解析出消息结构`ProtocolData`获取类型编号(messageType)，消息编号(messageCode)。

```java
@Data
public class ProtocolData {
	private int senderId;
	private int messageType;
	private int messageCode;
	private byte[] rawData;
}
```

其中类型编号固定为0。
消息编号含义如下：

|  消息编号   | 消息含义  |
|  ----  | ----  |
| 0  | 任务创建 |
| 1  | 任务停止 |

当收到仿真任务管理消息解析出`消息编号`为0时，开始记录准备工作：

  1. 解析`ProtocolData::rawData`。`ProtocolData::rawData`是json数据，ProtocolData::rawData["instanceId"] 是全局唯一仿真实例id，命名为instanceId，后续很多地方都会用到这个instanceId作为唯一标识。
  2. 在TDEngine数据库中建立超级表（注意表名是"situation_" + instanceId，后续这种带instanceId的默认都是如此，不再特别说明）。`CREATE STABLE situation_{instanceId} (ts TIMESTAMP, simtime BIGINT, rawdata BINARY(8192)) TAGS (msgtype INT, msgcode INT);`
  3. 在rocketmq中订阅2个新的Topic: `broadcast-instanceId`和`situation-instanceId`

当收到仿真任务管理消息解析出`消息编号`为1时，停止所有记录工作，停止订阅`situation-instanceId`。


### broadcast-instanceId 仿真控制指令消息

控制仿真的开始、暂停。同样通过调用`ProtocolMessageUtil::parseData`解析出消息结构`ProtocolData`获取类型编号(messageType)，消息编号(messageCode)。
其中类型编号固定为1100。
消息编号含义如下：

|  消息编号   | 消息含义  |
|  ----  | ----  |
| 1  | 启动仿真 |
| 5  | 暂停仿真 |
| 6  | 继续仿真 |

当收到仿真任务管理消息解析出`消息编号`为1时，记录开始：

  1. 服务自己维护一个`仿真时间`，记录开始时，仿真时间 = 系统时间
  2. 解析`situation-instanceId`主题中的消息，存入TDEngine。

当收到仿真任务管理消息解析出`消息编号`为5时，`仿真时间`也需要暂停。

当收到仿真任务管理消息解析出`消息编号`为6时，`仿真时间`也需要继续。


### situation-instanceId 仿真态势数据消息

真正需要记录的仿真态势数据。同样通过调用`ProtocolMessageUtil::parseData`解析出消息结构`ProtocolData`。
然后将数据插入数据库：
`insert into situation_{instanceId} (tbname, ts, simtime, rawdata, msgtype, msgcode) values("{messageType}_{messageCode}_{instanceId}_{senderId}", NOW, {仿真时间}, {rawData}, {messageType}, {messageCode});`


## 项目核心技术栈
- spring boot
- RocketMQ
- TDengine
- Mybatis Plus


## 一些想法和问题

- 这个项目的pom.xml和application.yml是从以前的项目复制过来的，存在很多冗余信息，是否需要清理？
- 数据记录服务的流程是否有问题？
- 数据库表设计是否有问题？有没有更好的数据库表设计方式？
- 后续还需要支持仿真的 加速和减速，仿真时间的维护要支持扩展。


## To codex

根据这份文档和现有代码框架，生成详细的技术方案，输出到final.md




