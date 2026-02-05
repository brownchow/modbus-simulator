package com.sysbreak.simulator.service;

import com.ghgande.j2mod.modbus.procimg.SimpleProcessImage;
import com.ghgande.j2mod.modbus.procimg.SimpleRegister;
import com.ghgande.j2mod.modbus.slave.ModbusSlave;
import com.ghgande.j2mod.modbus.slave.ModbusSlaveFactory;
import com.sysbreak.simulator.config.ModbusConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Modbus从站服务
 * <p>
 * 负责创建和管理Modbus TCP从站，维护电池数据的寄存器映像，
 * 供外部Modbus主站读取电池模拟数据。
 * </p>
 * <p>
 * 寄存器映射：
 * <ul>
 *   <li>0-9: 电压值（放大100倍存储）</li>
 *   <li>10-19: 温度值（放大10倍存储）</li>
 *   <li>20-29: 电流值（放大1000倍存储）</li>
 *   <li>30: 荷电状态（放大100倍存储）</li>
 * </ul>
 * </p>
 *
 * @author sysbreak
 * @since 2026-02-05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModbusSlaveService {

    private final ModbusConfig modbusConfig;

    private ModbusSlave modbusSlave;
    private SimpleProcessImage processImage;

    /**
     * 初始化Modbus从站
     * <p>
     * 创建Modbus TCP从站，初始化寄存器映像，并启动监听。
     * </p>
     *
     * @throws RuntimeException 如果Modbus从站初始化失败
     */
    @PostConstruct
    public void init() {
        try {
            processImage = new SimpleProcessImage(modbusConfig.getSlaveId());

            for (int i = 0; i < 31; i++) {
                processImage.addRegister(new SimpleRegister(0));
            }

            modbusSlave = ModbusSlaveFactory.createTCPSlave(modbusConfig.getPort(), modbusConfig.getSlaveId());
            modbusSlave.addProcessImage(modbusConfig.getSlaveId(), processImage);
            modbusSlave.open();

            log.info("Modbus从站已启动，端口: {}, 从站ID: {}", modbusConfig.getPort(), modbusConfig.getSlaveId());
        } catch (Exception e) {
            log.error("启动Modbus从站失败", e);
            throw new RuntimeException("Modbus从站初始化失败", e);
        }
    }

    /**
     * 更新Modbus寄存器中的电池数据
     * <p>
     * 将电池参数写入对应的寄存器位置。
     * </p>
     *
     * @param voltage    电池电压（V）
     * @param temperature 电池温度（°C）
     * @param current    电池电流（A）
     * @param soc        电池荷电状态（%）
     */
    public void updateRegisters(double voltage, double temperature, double current, double soc) {
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

            log.debug("已更新Modbus寄存器");
        } catch (Exception e) {
            log.error("更新Modbus寄存器失败", e);
        }
    }

    /**
     * 关闭Modbus从站
     * <p>
     * 释放Modbus从站资源，关闭网络连接。
     * </p>
     */
    @PreDestroy
    public void shutdown() {
        if (modbusSlave != null) {
            try {
                modbusSlave.close();
                log.info("Modbus从站已关闭");
            } catch (Exception e) {
                log.error("关闭Modbus从站时出错", e);
            }
        }
    }
}
