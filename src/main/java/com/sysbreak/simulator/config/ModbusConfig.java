package com.sysbreak.simulator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Modbus配置类
 * <p>
 * 用于配置Modbus从站参数，包括监听端口和从站ID。
 * </p>
 *
 * @author sysbreak
 * @since 2026-02-05
 */
@Data
@ConfigurationProperties(prefix = "modbus")
public class ModbusConfig {

    /**
     * Modbus TCP服务监听端口，默认为1502
     */
    private int port = 1502;

    /**
     * Modbus从站ID
     */
    private int slaveId = 1;
}
