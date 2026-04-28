# 回放系统外部接口

在回放系统的详细设计文档 `../005-回放系统设计/005-final.md` 中，关于回放控制是这么设计的：

```
### 4.5 实例级回放控制消息

实例级控制消息来自 `broadcast-{instanceId}`。

| 字段 | 值 | 说明 |
| --- | --- | --- |
| `messageType` | `1200` | 回放控制消息类型。 |
| `messageCode` | `1` | 启动回放。 |
| `messageCode` | `2` | 暂停回放。 |
| `messageCode` | `3` | 继续回放。 |
| `messageCode` | `4` | 倍速回放。 |
| `messageCode` | `5` | 时间跳转。 |
| `messageCode` | `9` | 回放元信息通知，由回放系统发布，回放控制处理器忽略。 |
```

但是根据用户需求，现在回放系统不能订阅 `broadcast-{instanceId}` 主题来控制，而是实现5个外部接口给前端进行控制。


## 需要修改的内容

1. 代码中只取消订阅 `broadcast-{instanceId}`，其他原有的逻辑不动，尽量少改代码。
2. 收到 `broadcast-global` 中 `messageType=1,messageCode=0` 的创建消息后，取消向 `broadcast-{instanceId}` 发布 `messageType=1200,messageCode=9` 的回放元信息通知。
3. 添加5个接口给前端调用：
   1. 启动回放。
   2. 暂停回放。
   3. 继续回放。
   4. 倍速回放。前端需要传入倍速（double值）。
   5. 时间跳转。前端需要传入时间点（long值）。


## To Codex

根据这份文档和现有代码框架，看看是否还有什么不明确的问题，先和我讨论，然后生成详细的技术方案，输出到007-final.md