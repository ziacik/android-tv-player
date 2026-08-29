#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: scripts/ace-hls-from-log.sh [--serial ADB_SERIAL] [--timeout SECONDS] CONTENT_ID [CONTENT_ID ...]

Asks the running local Ace engine for each content ID, captures only the
resulting AceEngine log lines, and prints the upstream HLS manifest URL.
The Ace engine must already be running on the target device.
EOF
}

adb_serial=""
request_timeout=10
content_ids=()

while (($#)); do
  case "$1" in
    --serial)
      adb_serial="${2:?--serial needs a value}"
      shift 2
      ;;
    --timeout)
      request_timeout="${2:?--timeout needs a value}"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      content_ids+=("${1#acestream://}")
      shift
      ;;
  esac
done

if ((${#content_ids[@]} == 0)); then
  usage >&2
  exit 2
fi

adb=(adb)
if [[ -n "$adb_serial" ]]; then
  adb+=(-s "$adb_serial")
fi

capture_pid=""
forward_port=""
cleanup() {
  [[ -n "$capture_pid" ]] && kill "$capture_pid" 2>/dev/null || true
  [[ -n "$forward_port" ]] && "${adb[@]}" forward --remove "tcp:$forward_port" >/dev/null 2>&1 || true
}
trap cleanup EXIT

"${adb[@]}" get-state >/dev/null
forward_port="$("${adb[@]}" forward tcp:0 tcp:6878)"

for content_id in "${content_ids[@]}"; do
  log_file="$(mktemp)"
  "${adb[@]}" logcat -T 1 -v raw AceEngine:I '*:S' >"$log_file" &
  capture_pid=$!
  sleep 0.2

  curl --silent --show-error --max-time "$request_timeout" \
    "http://127.0.0.1:$forward_port/ace/manifest.m3u8?content_id=$content_id" \
    -o /dev/null || true
  sleep 1
  kill "$capture_pid" 2>/dev/null || true
  wait "$capture_pid" 2>/dev/null || true
  capture_pid=""

  hls_url="$(awk -v content_id="$content_id" '
    index($0, "process_bt_request: urlpath=/manifest.m3u8?content_id=" content_id) {
      split($0, fields, "|")
      request_thread = fields[2]
    }
    request_thread != "" && index($0, "|" request_thread "|") && match($0, /manifest_url=[^ ]+/) {
      value = substr($0, RSTART + length("manifest_url="), RLENGTH - length("manifest_url="))
      print value
      exit
    }
  ' "$log_file")"
  rm -f "$log_file"

  if [[ -n "$hls_url" ]]; then
    printf '%s\t%s\n' "$content_id" "$hls_url"
  else
    printf '%s\t(no HLS manifest URL in Ace log)\n' "$content_id"
  fi
done
