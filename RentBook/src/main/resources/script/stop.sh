#!/bin/sh
set -eu

APP_NAME="${APP_NAME:-RentBook}"
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
APP_HOME=$(CDPATH= cd -- "${SCRIPT_DIR}/.." && pwd)
JAR_FILE="${APP_HOME}/RentBook.jar"
PID_FILE="${PID_FILE:-${APP_HOME}/${APP_NAME}.pid}"
STOP_TIMEOUT="${STOP_TIMEOUT:-30}"

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

PID=$(find_pid)
if [ -z "${PID}" ]; then
  echo "${APP_NAME} is not running"
  rm -f "${PID_FILE}"
  exit 0
fi

echo "Stopping ${APP_NAME}, pid=${PID}"
kill "${PID}" >/dev/null 2>&1 || true

i=1
while [ "${i}" -le "${STOP_TIMEOUT}" ]; do
  if ! kill -0 "${PID}" >/dev/null 2>&1; then
    rm -f "${PID_FILE}"
    echo "${APP_NAME} stopped"
    exit 0
  fi
  i=$((i + 1))
  sleep 1
done

echo "Graceful stop timed out after ${STOP_TIMEOUT}s, killing pid=${PID}"
kill -9 "${PID}" >/dev/null 2>&1 || true
rm -f "${PID_FILE}"
echo "${APP_NAME} stopped"
