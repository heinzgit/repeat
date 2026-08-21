#!/bin/bash
# 错题集管理系统 - macOS 启动脚本

set -e

cd /Users/heinz/claude_projects/repeat

PORT=8080
URL="http://localhost:${PORT}"

echo "================================"
echo "  错题集管理系统 启动脚本"
echo "================================"

# 检查端口是否已被占用
if lsof -Pi :${PORT} -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo "端口 ${PORT} 已被占用,先尝试停止已有进程..."
    PID=$(lsof -Pi :${PORT} -sTCP:LISTEN -t)
    kill ${PID}
    sleep 2
    if lsof -Pi :${PORT} -sTCP:LISTEN -t >/dev/null 2>&1; then
        echo "无法停止占用端口 ${PORT} 的进程 (PID: ${PID}),请手动处理后重试"
        exit 1
    fi
fi

# 用 Maven 启动 Spring Boot
echo "正在启动后端 (mvn spring-boot:run)..."
mvn spring-boot:run &

MVN_PID=$!
echo "Maven 进程 PID: ${MVN_PID}"

# 等待端口就绪
echo "等待服务在 ${URL} 上启动..."
for i in $(seq 1 60); do
    if curl -s -o /dev/null -w "%{http_code}" "${URL}" 2>/dev/null | grep -q "200\|404"; then
        echo "服务已就绪"
        break
    fi
    sleep 1
    if [ $i -eq 60 ]; then
        echo "等待超时,请检查后端日志"
        exit 1
    fi
done

# 打开默认浏览器
echo "打开浏览器: ${URL}"
open "${URL}"

echo "================================"
echo "  启动完成! 按 Ctrl+C 停止服务"
echo "================================"

# 前台等待,Ctrl+C 可停止整个服务
wait ${MVN_PID}