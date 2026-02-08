package com.sysbreak.simulator.service;

import com.alibaba.fastjson2.JSON;
import com.sysbreak.simulator.config.MqttConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MQTT发布服务
 * <p>
 * 负责连接MQTT代理服务器并将电池遥测数据发布到指定主题。
 * </p>
 *
 * @author sysbreak
 * @since 2026-02-05
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MqttPublisherService {

    private final MqttConfig mqttConfig;

    private MqttClient mqttClient;

    /**
     * 连接MQTT代理服务器
     * <p>
     * 根据配置创建MQTT客户端并连接到代理服务器。
     * </p>
     *
     * @throws RuntimeException 如果连接MQTT代理失败
     */
    @PostConstruct
    public void connect() {
        try {
            String clientId = mqttConfig.getClientId() + "-" + UUID.randomUUID();
            mqttClient = new MqttClient(mqttConfig.getBroker(), clientId);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            options.setKeepAliveInterval(30);
            options.setConnectionTimeout(10);

            mqttClient.connect(options);
            log.info("已连接到MQTT代理: {}", mqttConfig.getBroker());
        } catch (MqttException e) {
            log.error("连接MQTT代理失败", e);
            throw new RuntimeException("MQTT代理连接失败", e);
        }
    }

    /**
     * 发布电池遥测数据
     * <p>
     * 将电池参数封装为遥测数据JSON并发布到MQTT主题。
     * </p>
     *
     * @param soc        电池荷电状态（%）
     * @param voltage    电池电压（V）
     * @param current    电池电流（A）
     * @param temperature 电池温度（°C）
     */
    public void publish(double soc, double voltage, double current, double temperature) {
        try {
            Map<String, Object> telemetry = createTelemetryData(soc, voltage, current, temperature);
            String jsonPayload = JSON.toJSONString(telemetry);

            MqttMessage message = new MqttMessage(jsonPayload.getBytes());
            message.setQos(mqttConfig.getQos());
            message.setRetained(false);

            mqttClient.publish(mqttConfig.getTopic(), message);
            log.info("发布电池数据 - SOC: {}%, 电压: {}V, 电流: {}A, 温度: {}°C",
                    String.format("%.2f", soc),
                    String.format("%.2f", voltage),
                    String.format("%.2f", current),
                    String.format("%.2f", temperature));
        } catch (Exception e) {
            log.error("发布电池数据失败", e);
        }
    }

    /**
     * 创建电池遥测数据
     * <p>
     * 将电池参数封装为包含设备ID、时间戳、状态等信息的Map。
     * </p>
     *
     * @param soc        电池荷电状态（%）
     * @param voltage    电池电压（V）
     * @param current    电池电流（A）
     * @param temperature 电池温度（°C）
     * @return 遥测数据Map
     */
    private Map<String, Object> createTelemetryData(double soc, double voltage, double current, double temperature) {
        Map<String, Object> data = new HashMap<>();
        data.put("deviceId", mqttConfig.getDeviceId());
        data.put("timestamp", System.currentTimeMillis());
        data.put("soc", Math.round(soc * 100.0) / 100.0);
        data.put("voltage", Math.round(voltage * 100.0) / 100.0);
        data.put("current", Math.round(current * 100.0) / 100.0);
        data.put("temperature", Math.round(temperature * 100.0) / 100.0);
        data.put("power", Math.round(voltage * current * 100.0) / 100.0);

        double[] cellVoltages = new double[16];
        for (int i = 0; i < 16; i++) {
            cellVoltages[i] = Math.round((voltage / 16.0 + (Math.random() - 0.5) * 0.2) * 1000.0) / 1000.0;
        }
        data.put("cellVoltages", cellVoltages);
        data.put("status", soc > 20 ? "NORMAL" : "LOW_SOC");
        data.put("chargingStatus", current > 0 ? "DISCHARGING" : "CHARGING");

        return data;
    }

    /**
     * 断开MQTT连接
     * <p>
     * 断开与MQTT代理的连接并释放客户端资源。
     * </p>
     */
    @PreDestroy
    public void disconnect() {
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect();
                mqttClient.close();
                log.info("已断开MQTT连接");
            } catch (MqttException e) {
                log.error("断开MQTT连接时出错", e);
            }
        }
    }
}
