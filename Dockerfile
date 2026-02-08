# 多阶段构建的 Modbus 模拟器镜像
# 阶段1：构建阶段，启用 BuildKit 缓存加速依赖下载
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# 配置 Maven 阿里云镜像（国内加速）
COPY maven-settings.xml /root/.m2/settings.xml

# 复制项目源码并构建，依赖使用本地缓存
COPY pom.xml .
COPY src ./src

RUN --mount=type=cache,target=/root/.m2/.repository \
    mvn clean package -DskipTests -B

# 阶段2：运行时阶段，使用轻量级 JRE 镜像
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Install bash for start script
RUN apk add --no-cache bash

# 创建非 root 用户，提升安全性
RUN addgroup -S simulator && adduser -S simulator -G simulator

# 从构建阶段复制打包好的目录结构
COPY --from=builder /app/target/modbus-simulator-1.0.0-bin/. .

# 修改文件所有者
RUN chown -R simulator:simulator /app && \
    mkdir -p /var/log/modbus-simulator && \
    chown -R simulator:simulator /var/log/modbus-simulator

# 切换到非 root 用户运行
USER simulator:simulator

# 健康检查：每 30 秒检查一次，超时 10 秒，失败重试 3 次
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD pgrep -f java || exit 1

# 使用启动脚本
ENTRYPOINT ["/app/bin/start.sh"]
