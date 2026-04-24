# 任务：Lombok 收敛

## 背景

根据用户纠正，项目中的 Java 类不应手写大量 getter / setter，应优先使用 Lombok 类注解或字段注解简化样板代码。

## 执行项

- [x] 扫描当前主代码中的手写 getter / setter。
- [x] 将适合的纯访问器替换为 Lombok 生成。
- [x] 使用 Java 8 重新执行 Maven 测试。
- [x] 回填 review。

## 验收标准

- 像 `SimulationSession` 这类仅提供字段访问的类不再手写大量 getter / setter。
- Maven 测试在 Java 8 下通过。

## Review

- 已将 `SimulationSession` 中适合的纯访问器改为 Lombok 生成，保留计数类语义方法 `receivedMessageCount()`、`writtenRecordCount()`、`droppedMessageCount()`。
- 已将 `SimulationClock` 中 `speed`、`running`、`initialized` 的访问器改为 Lombok 生成。
- 已修复 `SimulationSessionManagerTest` 中 `Optional.isEmpty()` 的 Java 11+ 用法，改为兼容 Java 8 的 `!isPresent()`。
- 已在 Java 8 环境下执行 Maven 测试并通过。
  - `JAVA_HOME=C:\Users\summer\.jdks\corretto-1.8.0_482`
  - 命令：`mvn -q test`
