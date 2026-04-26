# 记录仿真控制指令时间点

因为后续要设计回放系统，回放系统需要知道在仿真运行过程中，什么时候发送过控制命令来控制想定的开始、暂停、倍速（加减速），
所以在记录中要记录仿真控制指令时间点。

本系统暂时还未处理倍速的情况，但是 SimulationClock.java 已经预留了倍速调整接口，这里同样需要预留倍速调整接口。

## 添加记录表

```sql
# 时间调整记录表
# 其中rate为仿真的倍速，仿真开始为1，暂停为0，继续为1，倍速根据实际倍速调整确定。
CREATE TABLE time_control_{instanceId} (ts TIMESTAMP,  simtime BIGINT, rate INT)
```

## broadcast-instanceId 仿真控制指令消息

现在系统会对 broadcast-instanceId 中接收到仿真`启动/暂停/继续`消息进行处理。文件为： @src/main/java/com/szzh/loggerserver/service/SimulationControlService.java
只需要对应处理函数中插入一条数据到`time_control_{instanceId}`表中即可。

数据库的操作都继续封装到 @src/main/java/com/szzh/loggerserver/service/TdengineSchemaService.java 和 @src/main/java/com/szzh/loggerserver/service/TdengineWriteService.java 中
