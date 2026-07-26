#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
. "${SCRIPT_DIR}/common.sh"

PID=$(find_pid)
if [ -n "${PID}" ]; then
  echo "${APP_NAME} is running, pid=${PID}"
  exit 0
fi

rm -f "${PID_FILE}"
echo "${APP_NAME} is not running"
exit 3
