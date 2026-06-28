#!/usr/bin/env bash
set -euo pipefail

require_all=false

usage() {
  cat <<'USAGE'
Usage: validate-image-tags.sh [--require-all] [IMAGE_TAGS]

Validates whitespace-separated service=tag entries. Tags must be explicit;
"latest" and tags ending in ":latest" are rejected.
USAGE
}

while (($# > 0)); do
  case "$1" in
    --require-all)
      require_all=true
      shift
      ;;
    -h | --help)
      usage
      exit 0
      ;;
    *)
      if [[ -n "${raw_tags:-}" ]]; then
        echo "unexpected extra argument: $1" >&2
        usage >&2
        exit 2
      fi
      raw_tags="$1"
      shift
      ;;
  esac
done

raw_tags="${raw_tags:-${IMAGE_TAGS:-}}"
raw_tags="${raw_tags#"${raw_tags%%[![:space:]]*}"}"
raw_tags="${raw_tags%"${raw_tags##*[![:space:]]}"}"

allowed_services=(
  auth-api
  auth-ui
  home-portal
  knowledge-api
  agents-api
  agents-ui
  agent-runtime
)

contains_service() {
  local wanted="$1"
  local service
  for service in "${allowed_services[@]}"; do
    [[ "$service" == "$wanted" ]] && return 0
  done
  return 1
}

if [[ -z "$raw_tags" ]]; then
  echo "IMAGE_TAGS is required" >&2
  exit 1
fi

declare -A seen=()

read -r -a entries <<<"$raw_tags"
for entry in "${entries[@]}"; do
  if [[ ! "$entry" =~ ^([a-z0-9][a-z0-9-]*)=(.+)$ ]]; then
    echo "invalid IMAGE_TAGS entry: $entry" >&2
    exit 1
  fi

  service="${BASH_REMATCH[1]}"
  tag="${BASH_REMATCH[2]}"

  if ! contains_service "$service"; then
    echo "unsupported IMAGE_TAGS service: $service" >&2
    exit 1
  fi

  if [[ -n "${seen[$service]:-}" ]]; then
    echo "duplicate IMAGE_TAGS service: $service" >&2
    exit 1
  fi

  if [[ -z "$tag" || "$tag" == "latest" || "$tag" == *":latest" ]]; then
    echo "IMAGE_TAGS entry for $service must use an explicit non-latest tag" >&2
    exit 1
  fi

  seen["$service"]="$tag"
done

if [[ "$require_all" == "true" ]]; then
  for service in "${allowed_services[@]}"; do
    if [[ -z "${seen[$service]:-}" ]]; then
      echo "missing required IMAGE_TAGS service: $service" >&2
      exit 1
    fi
  done
fi

echo "validated ${#seen[@]} IMAGE_TAGS entries"
