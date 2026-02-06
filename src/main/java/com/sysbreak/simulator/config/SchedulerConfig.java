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
     * 此方法使用 Spring Boot 的 @Bean 注解返回一个 ApplicationRunner 实例。
     * ApplicationRunner 是 Spring Boot 提供的接口，其 run() 方法会在
     * 应用上下文完全启动后、ApplicationRunner.run() 执行前被自动调用。
     * </p>
     * <p>
     * 实现了两种定时任务：
     * <ol>
     *   <li>MQTT 发布任务：按照 publish-interval 配置的间隔，将电池数据发布到 MQTT broker</li>
     *   <li>电池模拟任务：按照 simulation-interval 配置的间隔，更新电池模拟数据并同步到 Modbus 寄存器</li>
     * </ol>
     * </p>
     * <p>
     * 使用 Lambda 表达式简化了 ApplicationRunner 接口的实现，
     * 等价于创建一个匿名内部类实现 ApplicationRunner 接口。
     * </p>
     *
     * @param batterySimulatorService 电池模拟服务，用于获取电池状态数据
     * @param mqttPublisherService    MQTT发布服务，用于发布电池遥测数据
     * @param modbusSlaveService      Modbus从站服务，用于更新寄存器数据
     * @return ApplicationRunner 实例，Spring Boot 会在启动完成后自动执行其 run() 方法
     */
    @Bean
    public ApplicationRunner schedulerRunner(
            BatterySimulatorService batterySimulatorService,
            MqttPublisherService mqttPublisherService,
            ModbusSlaveService modbusSlaveService) {
        /**
         * 返回 ApplicationRunner 接口的实现
         * Lambda 语法 (args) -> { ... } 是对 () -> void run(ApplicationArguments args) 方法的实现
         * Spring Boot 会自动调用这个方法
         */
        return args -> {
            /**
             * 创建自定义线程工厂
             * 作用：控制调度池中线程的创建行为
             * - 设置线程名称为 "电池模拟器-调度线程池-N"，便于问题排查和日志追踪
             * - 设置为守护线程 (setDaemon(true))，当主线程结束时自动回收
             */
            ThreadFactory threadFactory = new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "电池模拟器-调度线程池-" + threadNumber.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                }
            };

            /**
             * 创建定时任务调度器
             * - 核心线程数：2（一个用于 MQTT 发布，一个用于电池模拟）
             * - 线程工厂：使用自定义的 threadFactory
             * - 拒绝策略：默认使用 AbortPolicy（抛出 RejectedExecutionException）
             */
            scheduler = Executors.newScheduledThreadPool(2, threadFactory);
            log.info("启动定时任务调度器");
            log.info("发布间隔: {} ms", batteryConfig.getPublishInterval());
            log.info("模拟间隔: {} ms", batteryConfig.getSimulationInterval());

            /**
             * 任务1：MQTT 数据发布
             * - 使用 scheduleAtFixedRate 实现固定速率调度
             * - 无论任务执行时间长短，都会按照配置的间隔时间重复执行
             * - initialDelay=0 表示启动后立即执行第一次
             */
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

            /**
             * 任务2：电池参数模拟与寄存器更新
             * - 按照 simulation-interval 更新电池状态（电压、电流、温度、SOC）
             * - 将模拟后的数据同步到 Modbus 从站寄存器，供外部设备读取
             * - 使用固定速率调度，保证模拟的实时性
             */
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
     * 使用 @PreDestroy 注解标记此方法，在 Spring 容器关闭时自动调用。
     * 实现优雅停机，确保定时任务能够完成正在执行的任务后再停止。
     * </p>
     * <p>
     * 关闭流程：
     * <ol>
     *   <li>调用 shutdown()：停止接受新任务，等待已提交的任务完成</li>
     *   <li>等待最多 5 秒：如果超时未完成，调用 shutdownNow() 强制中断</li>
     *   <li>恢复中断状态：如果当前线程被中断，记录状态并保持中断标记</li>
     * </ol>
     * </p>
     * <p>
     * 注意：shutdownNow() 只是尝试中断正在执行的线程，
     * 无法保证一定能停止所有任务，这是 JVM 的限制。
     * </p>
     */
    @PreDestroy
    public void shutdown() {
        if (scheduler != null) {
            log.info("正在关闭调度器...");
            /**
             * 第一步：停止接受新任务，已提交的任务继续执行
             */
            scheduler.shutdown();
            try {
                /**
                 * 第二步：等待已提交的任务完成，最多等待 5 秒
                 * awaitTermination 会阻塞当前线程
                 */
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    /**
                     * 第三步：如果超时，强制关闭所有正在执行的任务
                     * shutdownNow() 会向所有线程发送中断信号
                     */
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                /**
                 * 如果在等待过程中被中断，再次尝试强制关闭
                 * 并恢复中断状态（让上层调用者知道当前线程被中断了）
                 */
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("调度器已停止");
        }
    }
}
