#!/usr/bin/env bash
set -euo pipefail

# 按你服务器当前 Java 项目参数启动。
# 建议把真实脚本放到服务器固定路径，例如：
# /www/wwwroot/telegram-query-bot/scripts/restart-java.sh

APP_DIR="/www/wwwroot/telegram-query-bot"
LOG_DIR="${APP_DIR}/logs"
JAVA_BIN="/www/server/java/jdk-17.0.8/bin/java"
JAR_FILE="${APP_DIR}/telegram-query-bot.jar"
APP_PORT="18089"

mkdir -p "${LOG_DIR}"

# Prefer killing process by listening port to avoid terminating SSH action session.
if command -v fuser >/dev/null 2>&1; then
  fuser -k "${APP_PORT}/tcp" || true
else
  OLD_PID="$(pgrep -f "^${JAVA_BIN} .* -jar ${JAR_FILE}($| )" || true)"
  if [[ -n "${OLD_PID}" ]]; then
    kill "${OLD_PID}" || true
  fi
fi

nohup "${JAVA_BIN}" -Xms512m -Xmx1536m -XX:+UseG1GC \
  -jar "${JAR_FILE}" \
  --spring.profiles.active=prod \
  --spring.config.additional-location=file:/www/wwwroot/telegram-query-bot/config/ \
  > "${LOG_DIR}/app.out" 2>&1 &
