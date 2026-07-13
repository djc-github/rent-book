#!/bin/sh
set -eu

APP_NAME="${APP_NAME:-RentBook}"
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
APP_HOME=$(CDPATH= cd -- "${SCRIPT_DIR}/.." && pwd)
JAR_FILE="${APP_HOME}/RentBook.jar"
PID_FILE="${PID_FILE:-${APP_HOME}/${APP_NAME}.pid}"

find_pid() {
  if [ -f "${PID_FILE}" ]; then
    PID=$(cat "${PID_FILE}" 2>/dev/null || true)
    if [ -n "${PID}" ] && kill -0 "${PID}" >/dev/null 2>&1; then
      echo "${PID}"
      return 0
    fi
  fi
  if command -v pgrep >/dev/null 2>&1; then
    pgrep -f "java .*${JAR_FILE}" 2>/dev/null | head -n 1 || true
  fi
}

PID=$(find_pid)
if [ -n "${PID}" ]; then
  echo "${APP_NAME} is running, pid=${PID}"
  exit 0
fi

rm -f "${PID_FILE}"
echo "${APP_NAME} is not running"
exit 3
