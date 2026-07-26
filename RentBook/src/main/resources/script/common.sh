#!/bin/sh

APP_NAME="${APP_NAME:-RentBook}"
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
APP_HOME=$(CDPATH= cd -- "${SCRIPT_DIR}/.." && pwd)
JAR_FILE="${APP_HOME}/RentBook.jar"
PID_FILE="${PID_FILE:-${APP_HOME}/${APP_NAME}.pid}"

is_numeric_pid() {
  case "${1:-}" in
    ''|*[!0-9]*) return 1 ;;
    *) return 0 ;;
  esac
}

is_zombie_pid() {
  PID_TO_CHECK="${1:-}"
  if ! is_numeric_pid "${PID_TO_CHECK}"; then
    return 1
  fi

  PROCESS_STATE=$(ps -o stat= -p "${PID_TO_CHECK}" 2>/dev/null | awk 'NR == 1 { print $1 }')
  case "${PROCESS_STATE}" in
    Z*|z*) return 0 ;;
    *) return 1 ;;
  esac
}

is_running_pid() {
  PID_TO_CHECK="${1:-}"
  is_numeric_pid "${PID_TO_CHECK}" \
    && kill -0 "${PID_TO_CHECK}" >/dev/null 2>&1 \
    && ! is_zombie_pid "${PID_TO_CHECK}"
}

pid_matches_app() {
  PID_TO_CHECK="${1:-}"
  if ! is_running_pid "${PID_TO_CHECK}"; then
    return 1
  fi

  PROCESS_COMMAND=$(ps -o args= -p "${PID_TO_CHECK}" 2>/dev/null || true)
  printf '%s\n' "${PROCESS_COMMAND}" | grep -F "${JAR_FILE}" >/dev/null 2>&1
}

find_pid() {
  if [ -f "${PID_FILE}" ]; then
    SAVED_PID=$(cat "${PID_FILE}" 2>/dev/null || true)
    if pid_matches_app "${SAVED_PID}"; then
      echo "${SAVED_PID}"
      return 0
    fi
    rm -f "${PID_FILE}"
  fi

  if command -v pgrep >/dev/null 2>&1; then
    for CANDIDATE_PID in $(pgrep -f "${JAR_FILE}" 2>/dev/null || true); do
      if pid_matches_app "${CANDIDATE_PID}"; then
        echo "${CANDIDATE_PID}"
        return 0
      fi
    done
  fi
}
