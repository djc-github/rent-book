#!/bin/sh
set -eu

APP_NAME="${APP_NAME:-RentBook}"
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
APP_HOME=$(CDPATH= cd -- "${SCRIPT_DIR}/.." && pwd)
JAR_FILE="${APP_HOME}/RentBook.jar"
PID_FILE="${PID_FILE:-${APP_HOME}/${APP_NAME}.pid}"
LOG_DIR="${LOG_DIR:-${APP_HOME}/logs}"
APP_LOG="${APP_LOG:-${LOG_DIR}/rentbook.log}"
STDOUT_LOG="${STDOUT_LOG:-/dev/null}"
LOGBACK_CONFIG="${RENTBOOK_LOGBACK_CONFIG:-${APP_HOME}/config/logback-spring.xml}"
SPRING_PROFILE="${SPRING_PROFILE:-prod}"
JAVA_BIN="${JAVA_BIN:-java}"
JAVA_OPTS="${JAVA_OPTS:-}"
APP_OPTS="${APP_OPTS:-}"

find_pid() {
  if [ -f "${PID_FILE}" ]; then
    PID=$(cat "${PID_FILE}" 2>/dev/null || true)
    if [ -n "${PID}" ] && kill -0 "${PID}" >/dev/null 2>&1; then
      echo "${PID}"
      return 0
    fi
    rm -f "${PID_FILE}"
  fi
  if command -v pgrep >/dev/null 2>&1; then
    pgrep -f "java .*${JAR_FILE}" 2>/dev/null | head -n 1 || true
  fi
}

mkdir -p "${LOG_DIR}"

if [ ! -f "${JAR_FILE}" ]; then
  echo "Jar not found: ${JAR_FILE}"
  exit 1
fi

PID=$(find_pid)
if [ -n "${PID}" ]; then
  echo "${APP_NAME} is already running, pid=${PID}"
  echo "${PID}" > "${PID_FILE}"
  exit 0
fi

cd "${APP_HOME}"
nohup ${JAVA_BIN} ${JAVA_OPTS} -jar "${JAR_FILE}" \
  --spring.config.additional-location="optional:file:${APP_HOME}/config/" \
  --logging.config="${LOGBACK_CONFIG}" \
  --spring.profiles.active="${SPRING_PROFILE}" \
  ${APP_OPTS} >> "${STDOUT_LOG}" 2>&1 &

PID="$!"
echo "${PID}" > "${PID_FILE}"
echo "${APP_NAME} started, pid=${PID}, profile=${SPRING_PROFILE}"
echo "App log: ${APP_LOG}"
if [ "${STDOUT_LOG}" != "/dev/null" ]; then
  echo "Stdout log: ${STDOUT_LOG}"
fi
