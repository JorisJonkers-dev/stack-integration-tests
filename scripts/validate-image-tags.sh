#!/usr/bin/env bash
set -euo pipefail

require_all=false

usage() {
  cat <<'USAGE'
Usage: validate-image-tags.sh [--require-all] [IMAGE_TAGS]

Validates whitespace-separated service=tag entries or exact image refs emitted by
"deploy-config-schema lock images --format image-tags". Tags must be explicit;
"latest" and refs ending in ":latest" are rejected. Unsupported raw image refs
are ignored because deployment.lock.yml also contains third-party images that
this test suite does not exercise.
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

service_from_image_ref() {
  local ref="$1"
  local without_digest="${ref%%@*}"
  local image_path="${without_digest##*/}"
  local image_name="${image_path%%:*}"

  case "$image_name" in
    knowledge)
      echo "knowledge-api"
      ;;
    *)
      echo "$image_name"
      ;;
  esac
}

has_explicit_image_version() {
  local ref="$1"
  local without_digest="${ref%%@*}"
  local image_path="${without_digest##*/}"

  [[ "$ref" == *@sha256:* || "$image_path" == *:* ]]
}

is_latest_ref() {
  local ref="$1"
  local without_digest="${ref%%@*}"
  local image_path="${without_digest##*/}"
  local image_tag="${image_path#*:}"

  [[ "$ref" == "latest" || "$image_path" == "latest" || ( "$image_path" == *:* && "$image_tag" == "latest" ) ]]
}

if [[ -z "$raw_tags" ]]; then
  echo "IMAGE_TAGS is required" >&2
  exit 1
fi

declare -A seen=()

read -r -a entries <<<"$(printf '%s\n' "$raw_tags" | tr '\n' ' ')"
for entry in "${entries[@]}"; do
  if [[ "$entry" =~ ^([a-z0-9][a-z0-9-]*)=(.+)$ ]]; then
    service="${BASH_REMATCH[1]}"
    tag="${BASH_REMATCH[2]}"

    if ! contains_service "$service"; then
      echo "unsupported IMAGE_TAGS service: $service" >&2
      exit 1
    fi
  else
    tag="$entry"
    service="$(service_from_image_ref "$tag")"

    if ! has_explicit_image_version "$tag"; then
      echo "IMAGE_TAGS entry must use an explicit image tag or digest: $tag" >&2
      exit 1
    fi

    if is_latest_ref "$tag"; then
      echo "IMAGE_TAGS entry must not use latest: $tag" >&2
      exit 1
    fi

    if ! contains_service "$service"; then
      continue
    fi
  fi

  if [[ -n "${seen[$service]:-}" ]]; then
    echo "duplicate IMAGE_TAGS service: $service" >&2
    exit 1
  fi

  if [[ -z "$tag" ]] || is_latest_ref "$tag"; then
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
