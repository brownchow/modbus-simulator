package com.sysbreak.simulator;

import com.sysbreak.simulator.config.BatteryConfig;
import com.sysbreak.simulator.config.ModbusConfig;
import com.sysbreak.simulator.config.MqttConfig;
import com.sysbreak.simulator.service.BatterySimulatorService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 电池模拟器启动类
 *
 * @author sysbreak
 * @since 2026-02-05
 */
@Slf4j
@SpringBootApplication
@EnableConfigurationProperties({ModbusConfig.class, MqttConfig.class, BatteryConfig.class})
@RequiredArgsConstructor
public class ModbusSimulatorApplication {

    private final BatteryConfig batteryConfig;
    private final MqttConfig mqttConfig;
    private final ModbusConfig modbusConfig;
    private final BatterySimulatorService batterySimulatorService;

    /**
     * 启动入口
     *
     * @param args
     */
    public static void main(String[] args) {
        SpringApplication.run(ModbusSimulatorApplication.class, args);
    }

    @PostConstruct
    public void init() {
        log.info("=== Modbus电池模拟器启动中 ===");
        log.info("MQTT Broker: {}", mqttConfig.getBroker());
        log.info("MQTT Topic: {}", mqttConfig.getTopic());
        log.info("Modbus端口: {}", modbusConfig.getPort());
        log.info("发布间隔: {} ms", batteryConfig.getPublishInterval());
        log.info("模拟间隔: {} ms", batteryConfig.getSimulationInterval());
        batterySimulatorService.initialize();
        log.info("模拟器启动成功");
    }

}
