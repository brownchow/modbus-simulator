package com.sysbreak.simulator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 电池模拟器配置类
 * <p>
 * 用于配置电池模拟器的各项参数，包括数据发布间隔、模拟间隔以及电池初始状态等。
 * </p>
 *
 * @author sysbreak
 * @since 2026-02-05
 */
@Data
@ConfigurationProperties(prefix = "battery")
public class BatteryConfig {

    /**
     * 电池数据发布到MQTT的时间间隔（毫秒）
     */
    private long publishInterval = 5000;

    /**
     * 电池参数模拟更新的时间间隔（毫秒）
     */
    private long simulationInterval = 5000;

    /**
     * 电池初始荷电状态（%）
     */
    private double initialSoc = 85.0;

    /**
     * 电池初始电压（V）
     */
    private double initialVoltage = 52.5;

    /**
     * 电池初始电流（A）
     */
    private double initialCurrent = 15.0;

    /**
     * 电池初始温度（°C）
     */
    private double initialTemperature = 25.0;
}
