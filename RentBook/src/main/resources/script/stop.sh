#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
. "${SCRIPT_DIR}/common.sh"

STOP_TIMEOUT="${STOP_TIMEOUT:-30}"
KILL_TIMEOUT="${KILL_TIMEOUT:-5}"

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
  if ! is_running_pid "${PID}"; then
    rm -f "${PID_FILE}"
    echo "${APP_NAME} stopped"
    exit 0
  fi
  i=$((i + 1))
  sleep 1
done

echo "Graceful stop timed out after ${STOP_TIMEOUT}s, killing pid=${PID}"
kill -9 "${PID}" >/dev/null 2>&1 || true

i=1
while [ "${i}" -le "${KILL_TIMEOUT}" ]; do
  if ! is_running_pid "${PID}"; then
    rm -f "${PID_FILE}"
    echo "${APP_NAME} stopped"
    exit 0
  fi
  i=$((i + 1))
  sleep 1
done

echo "Failed to stop ${APP_NAME}, pid=${PID} is still running"
exit 1
