#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 4 ]]; then
  echo "Usage: $0 <release_dir> <app_dir> <web_root_dir> <restart_cmd> [healthcheck_url] [jar_target_name]"
  exit 1
fi

RELEASE_DIR="$1"
APP_DIR="$2"
WEB_ROOT_DIR="$3"
RESTART_CMD="$4"
HEALTHCHECK_URL="${5:-}"
JAR_TARGET_NAME="${6:-telegram-query-bot.jar}"

UI_ARCHIVE="${RELEASE_DIR}/admin-ui-dist.tgz"
JAR_TARGET="${APP_DIR}/${JAR_TARGET_NAME}"
UI_TARGET_DIR="${WEB_ROOT_DIR}"
BACKUP_ROOT="${APP_DIR}/backup"
TIMESTAMP="$(date +%Y%m%d%H%M%S)"
JAR_BACKUP="${BACKUP_ROOT}/telegram-query-bot-${TIMESTAMP}.jar"
UI_BACKUP="${BACKUP_ROOT}/web-${TIMESTAMP}.tgz"
UI_PROTECTED_FILES=(".user.ini")

if [[ -f "${RELEASE_DIR}/telegram-query-bot.jar" ]]; then
  JAR_SOURCE="${RELEASE_DIR}/telegram-query-bot.jar"
elif [[ -f "${RELEASE_DIR}/backend/target/telegram-query-bot.jar" ]]; then
  JAR_SOURCE="${RELEASE_DIR}/backend/target/telegram-query-bot.jar"
else
  echo "Missing backend artifact: ${RELEASE_DIR}/telegram-query-bot.jar or ${RELEASE_DIR}/backend/target/telegram-query-bot.jar"
  exit 1
fi

if [[ ! -f "${UI_ARCHIVE}" ]]; then
  echo "Missing frontend artifact: ${UI_ARCHIVE}"
  exit 1
fi

mkdir -p "${APP_DIR}" "${UI_TARGET_DIR}" "${BACKUP_ROOT}"

if [[ -f "${JAR_TARGET}" ]]; then
  cp -f "${JAR_TARGET}" "${JAR_BACKUP}"
fi

if [[ -d "${UI_TARGET_DIR}" ]]; then
  tar -czf "${UI_BACKUP}" \
    $(for f in "${UI_PROTECTED_FILES[@]}"; do printf -- '--exclude=%q ' "./${f}"; done) \
    -C "${UI_TARGET_DIR}" .
fi

TMP_UI_DIR="${APP_DIR}/ui-tmp-${TIMESTAMP}"
mkdir -p "${TMP_UI_DIR}"
tar -xzf "${UI_ARCHIVE}" -C "${TMP_UI_DIR}"

# Replace frontend assets atomically where possible.
if [[ -d "${UI_TARGET_DIR}" ]]; then
  find "${UI_TARGET_DIR}" -mindepth 1 -maxdepth 1 \
    $(for f in "${UI_PROTECTED_FILES[@]}"; do printf '! -name %q ' "${f}"; done) \
    -exec rm -rf {} +
fi
cp -a "${TMP_UI_DIR}/." "${UI_TARGET_DIR}/"
rm -rf "${TMP_UI_DIR}"

cp -f "${JAR_SOURCE}" "${JAR_TARGET}"

restart_app() {
  echo "Running restart command..."
  local restart_script
  restart_script="$(mktemp)"
  cat > "${restart_script}" <<EOF
#!/usr/bin/env bash
set -euo pipefail
${RESTART_CMD}
EOF
  chmod +x "${restart_script}"
  bash "${restart_script}"
  rm -f "${restart_script}"
}

check_health() {
  if [[ -z "${HEALTHCHECK_URL}" ]]; then
    return 0
  fi

  echo "Checking health endpoint: ${HEALTHCHECK_URL}"
  for _ in {1..20}; do
    if curl -fsS "${HEALTHCHECK_URL}" >/dev/null; then
      return 0
    fi
    sleep 3
  done
  return 1
}

print_startup_hint() {
  local app_log
  app_log="${APP_DIR}/logs/app.out"
  if [[ -f "${app_log}" ]]; then
    echo "Recent app log (last 80 lines):"
    tail -n 80 "${app_log}" || true
  else
    echo "App log not found: ${app_log}"
  fi
}

rollback() {
  echo "Rolling back deployment..."
  if [[ -f "${JAR_BACKUP}" ]]; then
    cp -f "${JAR_BACKUP}" "${JAR_TARGET}"
  fi

  if [[ -f "${UI_BACKUP}" ]]; then
    find "${UI_TARGET_DIR}" -mindepth 1 -maxdepth 1 \
      $(for f in "${UI_PROTECTED_FILES[@]}"; do printf '! -name %q ' "${f}"; done) \
      -exec rm -rf {} +
    tar --skip-old-files -xzf "${UI_BACKUP}" -C "${UI_TARGET_DIR}"
  fi

  restart_app
}

restart_app
if check_health; then
  echo "Health check passed."
  echo "Deploy finished."
  exit 0
fi

echo "Health check failed after deploy."
print_startup_hint
rollback

if check_health; then
  echo "Rollback succeeded and service is healthy."
  exit 1
fi

echo "Rollback failed: service still unhealthy."
exit 1

