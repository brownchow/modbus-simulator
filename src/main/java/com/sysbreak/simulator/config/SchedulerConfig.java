package com.sysbreak.simulator.config;

import com.sysbreak.simulator.service.BatterySimulatorService;
import com.sysbreak.simulator.service.MqttPublisherService;
import com.sysbreak.simulator.service.ModbusSlaveService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 调度器配置类
 * <p>
 * 负责配置和管理电池模拟器的定时任务调度器，
 * 包括电池数据发布任务和电池参数模拟任务的调度。
 * </p>
 *
 * @author sysbreak
 * @since 2026-02-05
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SchedulerConfig {

    private final BatteryConfig batteryConfig;

    private ScheduledExecutorService scheduler;

    @Bean
    public ApplicationRunner schedulerRunner(
            BatterySimulatorService batterySimulatorService,
            MqttPublisherService mqttPublisherService,
            ModbusSlaveService modbusSlaveService) {
        return args -> {
            scheduler = Executors.newScheduledThreadPool(2);
            log.info("启动定时任务调度器");
            log.info("发布间隔: {} ms", batteryConfig.getPublishInterval());
            log.info("模拟间隔: {} ms", batteryConfig.getSimulationInterval());

            scheduler.scheduleAtFixedRate(() -> {
                try {
                    mqttPublisherService.publish(
                            batterySimulatorService.getSoc(),
                            batterySimulatorService.getVoltage(),
                            batterySimulatorService.getCurrent(),
                            batterySimulatorService.getTemperature()
                    );
                } catch (Exception e) {
                    log.error("发布电池数据失败", e);
                }
            }, 0, batteryConfig.getPublishInterval(), TimeUnit.MILLISECONDS);

            scheduler.scheduleAtFixedRate(() -> {
                try {
                    batterySimulatorService.simulate();
                    modbusSlaveService.updateRegisters(
                            batterySimulatorService.getVoltage(),
                            batterySimulatorService.getTemperature(),
                            batterySimulatorService.getCurrent(),
                            batterySimulatorService.getSoc()
                    );
                } catch (Exception e) {
                    log.error("模拟电池参数失败", e);
                }
            }, 0, batteryConfig.getSimulationInterval(), TimeUnit.MILLISECONDS);

            log.info("定时任务调度器已启动");
        };
    }

    @PreDestroy
    public void shutdown() {
        if (scheduler != null) {
            log.info("正在关闭调度器...");
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("调度器已停止");
        }
    }
}
