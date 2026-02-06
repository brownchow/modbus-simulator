#!/bin/bash

# Modbus Battery Simulator 启动脚本

set -e

APP_HOME="$(cd "$(dirname "$0")" && pwd)"
JAR_FILE="${APP_HOME}/../modbus-simulator.jar"
CONFIG_DIR="${APP_HOME}/../config"
LOG_DIR="${APP_HOME}/../logs"
LIB_DIR="${APP_HOME}/../lib"

CLASSPATH="${JAR_FILE}"
for jar in "${LIB_DIR}"/*.jar; do
    CLASSPATH="${CLASSPATH}:${jar}"
done

echo "检查环境..."
if ! command -v java &> /dev/null; then
    echo "错误: 未找到 Java，请安装 JDK 21 或更高版本"
    exit 1
fi

if [ ! -f "${JAR_FILE}" ]; then
    echo "错误: 未找到 JAR 文件: ${JAR_FILE}"
    exit 1
fi

mkdir -p "${LOG_DIR}"

JAVA_OPTS="-Xms256m -Xmx512m -Xmn256m -server"
CONFIG_OPTS="${CONFIG_DIR}/application.yaml"
LOG_OPTS="${CONFIG_DIR}/logback-spring.xml"

if [ ! -f "${CONFIG_OPTS}" ]; then
    echo "错误: 未找到配置文件: ${CONFIG_OPTS}"
    exit 1
fi

echo "启动 Modbus Battery Simulator..."
nohup java ${JAVA_OPTS} \
    -Dlog.path="${LOG_DIR}" \
    -Dspring.config.location="${CONFIG_OPTS}" \
    -classpath "${CLASSPATH}" \
    com.sysbreak.simulator.ModbusSimulatorApplication \
    --logging.config="${LOG_OPTS}" \
    > /dev/null 2>&1 &

PID=$!
echo "启动完成, PID: ${PID}"

sleep 2
if ps -p ${PID} > /dev/null 2>&1; then
    echo "服务正在运行"
else
    echo "错误: 服务启动失败，请检查 logs 目录下的日志文件"
    exit 1
fi
