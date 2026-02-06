#!/bin/bash

# Modbus Battery Simulator 停止脚本

# 查找并杀掉 java 进程
PIDS=$(pgrep -f "modbus-simulator.jar")

if [ -n "$PIDS" ]; then
    echo "正在停止 Modbus Simulator (PID: $PIDS)..."
    kill $PIDS 2>/dev/null
    echo "停止完成"
else
    echo "未找到运行中的进程"
fi
