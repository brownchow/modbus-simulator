package com.sysbreak.simulator;

import com.alibaba.fastjson2.JSON;
import com.ghgande.j2mod.modbus.slave.ModbusSlave;
import com.ghgande.j2mod.modbus.slave.ModbusSlaveFactory;
import com.ghgande.j2mod.modbus.procimg.SimpleProcessImage;
import com.ghgande.j2mod.modbus.procimg.SimpleRegister;
import com.ghgande.j2mod.modbus.net.ModbusTCPListener;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Modbus电池模拟器应用
 * 模拟BMS电池数据并发布到MQTT代理
 * 使用j2mod进行Modbus通信
 *
 * @author sysbreak
 * @since 2026-01-16
 */
@Slf4j
public class ModbusSimulatorApplication {

    private static final String MQTT_BROKER = System.getenv().getOrDefault("MQTT_BROKER", "tcp://mqtt:1883");
    private static final String MQTT_TOPIC = System.getenv().getOrDefault("MQTT_TOPIC", "ems/bms/telemetry");
    private static final int PUBLISH_INTERVAL = Integer.parseInt(System.getenv().getOrDefault("PUBLISH_INTERVAL", "5000"));
    private static final String CLIENT_ID = "modbus-simulator-" + System.currentTimeMillis();
    private static final int MODBUS_PORT = Integer.parseInt(System.getenv().getOrDefault("MODBUS_PORT", "502"));

    private final Random random = new Random();
    private MqttClient mqttClient;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private ModbusSlave modbusSlave;
    private SimpleProcessImage processImage;

    // 电池模拟参数
    private double soc = 85.0; // 荷电状态 (%)
    private double voltage = 52.5; // 电池电压 (V)
    private double current = 15.0; // 电池电流 (A)
    private double temperature = 25.0; // 电池温度 (°C)

    public static void main(String[] args) {
        ModbusSimulatorApplication app = new ModbusSimulatorApplication();
        app.start();
    }

    public void start() {
        log.info("=== Modbus电池模拟器启动中 (j2mod) ===");
        log.info("MQTT Broker: {}", MQTT_BROKER);
        log.info("MQTT Topic: {}", MQTT_TOPIC);
        log.info("发布间隔: {} ms", PUBLISH_INTERVAL);
        log.info("Modbus端口: {}", MODBUS_PORT);

        // 初始化Modbus从站
        initModbusSlave();

        // 初始化MQTT连接
        connectMqtt();

        // 启动定期数据发布
        scheduler.scheduleAtFixedRate(this::publishBatteryData, 0, PUBLISH_INTERVAL, TimeUnit.MILLISECONDS);

        // 启动电池模拟
        scheduler.scheduleAtFixedRate(this::simulateBatteryParameters, 0, 5, TimeUnit.SECONDS);

        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

        log.info("模拟器启动成功");
    }

    private void initModbusSlave() {
        try {
            processImage = new SimpleProcessImage(1);

            for (int i = 0; i < 31; i++) {
                processImage.addRegister(new SimpleRegister(0));
            }

            modbusSlave = ModbusSlaveFactory.createTCPSlave(MODBUS_PORT, 1);
            modbusSlave.addProcessImage(1, processImage);
            modbusSlave.open();

            log.info("j2mod Modbus从站已启动，端口: {}", MODBUS_PORT);
        } catch (Exception e) {
            log.error("启动Modbus从站失败", e);
            throw new RuntimeException("Modbus从站初始化失败", e);
        }
    }

    private void connectMqtt() {
        try {
            mqttClient = new MqttClient(MQTT_BROKER, CLIENT_ID);
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            options.setKeepAliveInterval(30);
            options.setConnectionTimeout(10);

            mqttClient.connect(options);
            log.info("已连接到MQTT代理: {}", MQTT_BROKER);
        } catch (MqttException e) {
            log.error("连接MQTT代理失败", e);
            throw new RuntimeException("MQTT代理连接失败", e);
        }
    }

    private void publishBatteryData() {
        try {
            // 更新电池参数到Modbus寄存器
            updateModbusRegisters();

            // 创建遥测数据
            Map<String, Object> telemetry = createTelemetryData();

            // 转换为JSON
            String jsonPayload = JSON.toJSONString(telemetry);

            // 发布到MQTT
            MqttMessage message = new MqttMessage(jsonPayload.getBytes());
            message.setQos(1);
            message.setRetained(false);

            mqttClient.publish(MQTT_TOPIC, message);
            log.info("发布电池数据: SOC={}%, 电压={}V, 电流={}A, 温度={}°C",
                    String.format("%.2f", soc),
                    String.format("%.2f", voltage),
                    String.format("%.2f", current),
                    String.format("%.2f", temperature));

        } catch (Exception e) {
            log.error("发布电池数据失败", e);
        }
    }

    private void updateModbusRegisters() {
        try {
            int voltageValue = (int) (voltage * 100);
            for (int i = 0; i < 10; i++) {
                processImage.getRegister(i).setValue(voltageValue);
            }

            int temperatureValue = (int) (temperature * 10);
            for (int i = 0; i < 10; i++) {
                processImage.getRegister(10 + i).setValue(temperatureValue);
            }

            int currentValue = (int) (current * 1000);
            for (int i = 0; i < 10; i++) {
                processImage.getRegister(20 + i).setValue(currentValue);
            }

            int socValue = (int) (soc * 100);
            processImage.getRegister(30).setValue(socValue);

            log.debug("已通过j2mod更新Modbus寄存器中的电池数据");

        } catch (Exception e) {
            log.error("更新Modbus寄存器失败", e);
        }
    }

    private void simulateBatteryParameters() {
        try {
            // 模拟电池参数变化
            double random = Math.random();
            
            if (random < 0.4) {
                // 放电模式
                soc = Math.max(10.0, soc - 0.5);
                voltage = 48.0 + (soc / 100.0) * 10.0 + (random - 0.5) * 2.0;
                current = 10.0 + random * 40.0;
            } else if (random < 0.7) {
                // 充电模式
                soc = Math.min(100.0, soc + 0.3);
                voltage = 48.0 + (soc / 100.0) * 10.0 + (random - 0.5) * 2.0;
                current = 5.0 + random * 20.0;
            } else {
                // 空闲模式
                soc = Math.max(10.0, soc - 0.1);
                voltage = 48.0 + (soc / 100.0) * 10.0 + (random - 0.5) * 1.0;
                current = random * 5.0;
            }
            
            temperature = 20.0 + random * 15.0;

        } catch (Exception e) {
            log.error("模拟电池参数时出错", e);
        }
    }

    private Map<String, Object> createTelemetryData() {
        Map<String, Object> data = new HashMap<>();

        data.put("deviceId", "BMS-001");
        data.put("timestamp", System.currentTimeMillis());
        data.put("soc", Math.round(soc * 100.0) / 100.0);
        data.put("voltage", Math.round(voltage * 100.0) / 100.0);
        data.put("current", Math.round(current * 100.0) / 100.0);
        data.put("temperature", Math.round(temperature * 100.0) / 100.0);
        data.put("power", Math.round(voltage * current * 100.0) / 100.0);

        // 电池单体电压 (模拟16个电池单元)
        double[] cellVoltages = new double[16];
        for (int i = 0; i < 16; i++) {
            cellVoltages[i] = Math.round((voltage / 16.0 + (random.nextDouble() - 0.5) * 0.2) * 1000.0) / 1000.0;
        }
        data.put("cellVoltages", cellVoltages);

        // 电池状态
        data.put("status", soc > 20 ? "NORMAL" : "LOW_SOC");
        data.put("chargingStatus", current > 0 ? "DISCHARGING" : "CHARGING");

        return data;
    }

    private void shutdown() {
        log.info("正在关闭模拟器...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                mqttClient.close();
            }
            if (modbusSlave != null) {
                try {
                    modbusSlave.close();
                } catch (Exception e) {
                    // Ignore close errors
                }
            }
        } catch (Exception e) {
            log.error("关闭时出错", e);
        }
        log.info("模拟器已停止");
    }
}
