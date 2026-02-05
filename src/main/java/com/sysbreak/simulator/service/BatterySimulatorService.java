package com.sysbreak.simulator.service;

import com.sysbreak.simulator.config.BatteryConfig;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Random;

/**
 * 电池模拟器服务
 * <p>
 * 负责模拟电池的各项参数（荷电状态、电压、电流、温度），
 * 根据预设的模型生成动态变化的电池数据。
 * </p>
 *
 * @author sysbreak
 * @since 2026-02-05
 */
@Slf4j
@Service
@Data
@RequiredArgsConstructor
public class BatterySimulatorService {

    private final BatteryConfig batteryConfig;

    private final Random random = new Random();

    /**
     * 电池当前荷电状态（%）
     */
    private double soc;

    /**
     * 电池当前电压（V）
     */
    private double voltage;

    /**
     * 电池当前电流（A）
     */
    private double current;

    /**
     * 电池当前温度（°C）
     */
    private double temperature;

    /**
     * 初始化电池模拟器
     * <p>
     * 从配置中读取初始参数值，设置电池的初始状态。
     * </p>
     */
    public void initialize() {
        this.soc = batteryConfig.getInitialSoc();
        this.voltage = batteryConfig.getInitialVoltage();
        this.current = batteryConfig.getInitialCurrent();
        this.temperature = batteryConfig.getInitialTemperature();
        log.info("电池模拟器已初始化 - SOC: {}, 电压: {}V, 电流: {}A, 温度: {}°C", soc, voltage, current, temperature);
    }

    /**
     * 模拟电池参数变化
     * <p>
     * 根据预设的模型模拟电池参数在充放电过程中的动态变化，
     * 包括三种模式：放电模式、充电模式和空闲模式。
     * </p>
     */
    public void simulate() {
        try {
            // 生成0.0-1.0之间的随机值，用于决定电池工作模式
            double randomValue = Math.random();

            if (randomValue < 0.4) {
                // === 放电模式（概率40%） ===
                // SOC以0.5%/次的速率下降，最低不低于10%
                soc = Math.max(10.0, soc - 0.5);
                // 电压随SOC变化，基础范围48-58V，叠加±1V波动
                voltage = 48.0 + (soc / 100.0) * 10.0 + (randomValue - 0.5) * 2.0;
                // 放电电流10-50A
                current = 10.0 + randomValue * 40.0;
            } else if (randomValue < 0.7) {
                // === 充电模式（概率30%） ===
                // SOC以0.3%/次的速率上升，最高不超过100%
                soc = Math.min(100.0, soc + 0.3);
                // 电压随SOC变化，基础范围48-58V，叠加±1V波动
                voltage = 48.0 + (soc / 100.0) * 10.0 + (randomValue - 0.5) * 2.0;
                // 充电电流5-25A
                current = 5.0 + randomValue * 20.0;
            } else {
                // === 空闲模式（概率30%） ===
                // SOC缓慢自然下降，0.1%/次
                soc = Math.max(10.0, soc - 0.1);
                // 电压稳定在48-58V之间，波动较小
                voltage = 48.0 + (soc / 100.0) * 10.0 + (randomValue - 0.5) * 1.0;
                // 待机电流0-5A
                current = randomValue * 5.0;
            }

            // 温度保持在20-35°C之间随机波动
            temperature = 20.0 + randomValue * 15.0;
        } catch (Exception e) {
            log.error("模拟电池参数时出错", e);
        }
    }
}
