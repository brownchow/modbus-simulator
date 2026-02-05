package com.sysbreak.simulator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MQTT配置类
 * <p>
 * 用于配置MQTT客户端连接参数，包括MQTT代理地址、发布主题、客户端ID等。
 * </p>
 *
 * @author sysbreak
 * @since 2026-02-05
 */
@Data
@ConfigurationProperties(prefix = "mqtt")
public class MqttConfig {

    /**
     * MQTT代理服务器地址，格式为 {@code tcp://host:port}
     */
    private String broker = "tcp://mqtt:1883";

    /**
     * 发布电池遥测数据的主题
     */
    private String topic = "ems/bms/telemetry";

    /**
     * MQTT客户端标识符
     */
    private String clientId = "modbus-simulator";

    /**
     * MQTT消息服务质量等级（0: 最多一次, 1: 至少一次, 2: 恰好一次）
     */
    private int qos = 1;
}
