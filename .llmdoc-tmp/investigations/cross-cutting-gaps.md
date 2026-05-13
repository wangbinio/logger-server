# 交叉检查与缺口

## 当前稳定事实

- 三模块边界清晰：`common` 共享契约，`logger-server` 负责记录，`replay-server` 负责回放。
- 记录与回放都已经适配低版本 TDengine RESTful 驱动。
- 记录侧和回放侧真实环境测试都已经有显式开关并在当前环境跑通过。
- 回放控制已改为 HTTP REST，`ReplayTopicSubscriptionManager` 属于保留兼容组件，不是当前生产控制入口。

## 需要后续注意的文档漂移

- `README.md` 回放事件表示例仍出现 `1002/8`，当前源码配置是 `2301/3`。
- `plan/005-回放系统设计/005-final.md` 仍保留早期 MQ 实例控制和回放全局消息类型建议，当前实现已经由 `plan/007-回放系统外部接口/007-final.md` 修订为 HTTP 控制。
- 旧记忆中曾出现 `logger.real-env=true`，当前测试类实际开关为 `logger.real-env.test=true`。

## 后续代理原则

- 若修改消息码、事件表、TDengine 字段或真实环境连接，先读源码配置和测试，再回写 README/ARCHITECTURE/llmdoc。
- 若修改回放控制链路，优先遵守 HTTP Controller -> CommandFactory -> ReplayControlService 的边界，不把状态机写进 Controller。
- 若修改 TDengine 读取，保留 `ReplayFrameRepository` 对低版本 RESTful 驱动的 `rawdata` 兼容读取。

