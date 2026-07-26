#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
. "${SCRIPT_DIR}/common.sh"

LOG_DIR="${LOG_DIR:-${APP_HOME}/logs}"
APP_LOG="${RENTBOOK_LOG_FILE:-${APP_LOG:-${LOG_DIR}/rentbook.log}}"
LOGBACK_CONFIG="${RENTBOOK_LOGBACK_CONFIG:-${APP_HOME}/config/logback-spring.xml}"
SPRING_PROFILE="${SPRING_PROFILE:-prod}"
JAVA_BIN="${JAVA_BIN:-java}"
JAVA_OPTS="${JAVA_OPTS:-}"
APP_OPTS="${APP_OPTS:-}"
START_TIMEOUT="${START_TIMEOUT:-60}"

case "${APP_LOG}" in
  /*) ;;
  *) APP_LOG="${APP_HOME}/${APP_LOG}" ;;
esac

mkdir -p "${LOG_DIR}" "$(dirname -- "${APP_LOG}")"

if [ ! -f "${JAR_FILE}" ]; then
  echo "Jar not found: ${JAR_FILE}"
  exit 1
fi

if [ ! -f "${LOGBACK_CONFIG}" ]; then
  echo "Logback config not found: ${LOGBACK_CONFIG}"
  exit 1
fi

PID=$(find_pid)
if [ -n "${PID}" ]; then
  echo "${APP_NAME} is already running, pid=${PID}"
  echo "${PID}" > "${PID_FILE}"
  exit 0
fi

cd "${APP_HOME}"
touch "${APP_LOG}"
START_LOG_LINE=$(wc -l < "${APP_LOG}" | tr -d ' ')
export RENTBOOK_LOG_FILE="${APP_LOG}"

nohup ${JAVA_BIN} ${JAVA_OPTS} -jar "${JAR_FILE}" \
  --spring.config.additional-location="optional:file:${APP_HOME}/config/" \
  --logging.config="${LOGBACK_CONFIG}" \
  --spring.profiles.active="${SPRING_PROFILE}" \
  ${APP_OPTS} >> "${APP_LOG}" 2>&1 &

PID="$!"
echo "${PID}" > "${PID_FILE}"

i=1
while [ "${i}" -le "${START_TIMEOUT}" ]; do
  if ! is_running_pid "${PID}"; then
    rm -f "${PID_FILE}"
    echo "${APP_NAME} failed to start. Recent log:"
    tail -n 80 "${APP_LOG}" 2>/dev/null || true
    exit 1
  fi

  if tail -n "+$((START_LOG_LINE + 1))" "${APP_LOG}" 2>/dev/null \
      | grep -F "Started RentBookApplication" >/dev/null 2>&1; then
    echo "${APP_NAME} started, pid=${PID}, profile=${SPRING_PROFILE}"
    echo "App log: ${APP_LOG}"
    exit 0
  fi

  i=$((i + 1))
  sleep 1
done

echo "${APP_NAME} process is running, but startup was not confirmed within ${START_TIMEOUT}s."
echo "Check app log: ${APP_LOG}"
exit 1
