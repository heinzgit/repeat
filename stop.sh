#!/bin/bash
# 错题集管理系统 - macOS 停止脚本

PORT=8080

PID=$(lsof -Pi :${PORT} -sTCP:LISTEN -t 2>/dev/null)
if [ -z "${PID}" ]; then
    echo "端口 ${PORT} 上没有运行中的服务"
    exit 0
fi

echo "停止占用端口 ${PORT} 的进程 (PID: ${PID})..."
kill ${PID}
sleep 2

if lsof -Pi :${PORT} -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo "进程未退出,强制结束"
    kill -9 ${PID}
fi
echo "已停止"