# Modbus Battery Simulator

电池模拟器，通过 Modbus TCP 暴露电池状态寄存器，并通过 MQTT 发布遥测数据。

## 功能特性

- Modbus TCP 服务器，用于电池状态模拟
- MQTT 客户端，用于发布电池遥测数据
- 可配置的模拟参数（SOC、电压、电流、温度）
- Maven Assembly 打包，便于部署

## 环境要求

- JDK 21 或更高版本
- Maven 3.6+
- MQTT broker（默认：`tcp://127.0.0.1:1883`）

## 编译打包

```bash
mvn clean package -DskipTests
```

打包输出目录：`target/modbus-simulator-1.0.0-bin/`

## 目录结构

```
modbus-simulator-1.0.0-bin/
├── bin/
│   ├── start.sh    # 启动应用
│   └── stop.sh     # 停止应用
├── config/
│   ├── application.yaml  # 应用配置
│   └── logback-spring.xml # 日志配置
├── lib/            # 依赖 JAR 包
├── logs/           # 应用日志（首次启动时创建）
└── modbus-simulator.jar  # 主应用 JAR
```

## 配置说明

编辑 `config/application.yaml` 自定义设置：

```yaml
modbus:
  port: 1502       # Modbus TCP 端口
  slave-id: 1     # Modbus 从站 ID

mqtt:
  broker: "tcp://127.0.0.1:1883"  # MQTT broker 地址
  topic: "ems/bms/telemetry"       # MQTT 主题
  client-id: "modbus-simulator"    # MQTT 客户端 ID
  qos: 1                            # 服务质量等级

battery:
  publish-interval: 5000    # MQTT 发布间隔（毫秒）
  simulation-interval: 5000 # 模拟更新间隔（毫秒）
  initial-soc: 85.0         # 初始荷电状态（%）
  initial-voltage: 52.5     # 初始电压（V）
  initial-current: 15.0     # 初始电流（A）
  initial-temperature: 25.0 # 初始温度（℃）
```

## 启动运行

```bash
cd modbus-simulator-1.0.0-bin
./bin/start.sh
```

应用启动后会自动创建 `logs/` 目录。

## 停止应用

```bash
./bin/stop.sh
```

## Modbus 寄存器

| 地址    | 寄存器 | 类型   | 说明           |
|---------|--------|--------|----------------|
| 40001   | 0      | INT16  | SOC (%)       |
| 40002   | 1      | INT16  | 电压 (0.1V)   |
| 40003   | 2      | INT16  | 电流 (0.1A)   |
| 40004   | 3      | INT16  | 温度 (0.1℃)   |
| 40005   | 4      | INT16  | 状态标志      |

## MQTT 数据格式

发布到配置主题的 JSON 数据示例：

```json
{
  "soc": 85.0,
  "voltage": 52.5,
  "current": 15.0,
  "temperature": 25.0,
  "timestamp": 1700000000000
}
```

## License

MIT
