package com.sysbreak.simulator.config;

import com.sysbreak.simulator.service.BatterySimulatorService;
import com.sysbreak.simulator.service.ModbusSlaveService;
import com.sysbreak.simulator.service.MqttPublisherService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 电池模拟器调度器配置类
 * <p>
 * 负责配置和管理电池模拟器的定时任务调度器，
 * 包括电池数据发布任务和电池参数模拟任务的调度。
 * 使用自定义线程工厂创建线程池，线程名称以"电池模拟器-调度线程池-"为前缀。
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

    /**
     * 电池模拟器调度器启动器
     * <p>
     * 创建并配置定时任务调度器，负责启动电池数据发布任务和电池参数模拟任务。
     * 调度器使用自定义线程工厂创建线程，线程名称以"battery-scheduler-"为前缀。
     * </p>
     *
     * @param batterySimulatorService 电池模拟服务，用于获取电池状态数据
     * @param mqttPublisherService    MQTT发布服务，用于发布电池遥测数据
     * @param modbusSlaveService      Modbus从站服务，用于更新寄存器数据
     * @return ApplicationRunner实例
     */
    @Bean
    public ApplicationRunner schedulerRunner(
            BatterySimulatorService batterySimulatorService,
            MqttPublisherService mqttPublisherService,
            ModbusSlaveService modbusSlaveService) {
        return args -> {
            ThreadFactory threadFactory = new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "电池模拟器-调度线程池-" + threadNumber.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                }
            };

            scheduler = Executors.newScheduledThreadPool(2, threadFactory);
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

    /**
     * 关闭调度器
     * <p>
     * 在应用关闭时优雅地停止调度器。
     * 首先尝试正常关闭，等待最多5秒完成所有待处理任务；
     * 如果超时则强制关闭所有正在执行的任务。
     * </p>
     */
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
