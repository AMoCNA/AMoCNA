#!/bin/sh
# SCIG Sensor Engine: generate SBOMs with Syft, scan CVE vulnerabilities with Grype and Trivy, and store results in Redis.

set -eu

REDIS_HOST="${REDIS_HOST:-redis.redis.svc.cluster.local}"
REDIS_PORT="${REDIS_PORT:-6379}"
SBOM_TTL_SECONDS="${SBOM_TTL_SECONDS:-172800}"
IMAGE_LIST_FILE="${IMAGE_LIST_FILE:-/config/images.txt}"
DISCOVER_CLUSTER_IMAGES="${DISCOVER_CLUSTER_IMAGES:-true}"
SCAN_NAMESPACES="${SCAN_NAMESPACES:-sock-shop,bookinfo,online-boutique}"

log() { echo "[scig] $*" >&2; }

require_tools() {
  for tool in syft redis-cli; do
    if ! command -v "$tool" >/dev/null 2>&1; then
      log "ERROR: missing required tool '$tool' in image"
      exit 1
    fi
  done

  if ! command -v grype >/dev/null 2>&1; then
    log "WARN: 'grype' not found in PATH"
  fi

  if [ "${DISCOVER_CLUSTER_IMAGES}" = "true" ] && ! command -v kubectl >/dev/null 2>&1; then
    log "ERROR: DISCOVER_CLUSTER_IMAGES=true requires kubectl in image"
    exit 1
  fi
}

normalize_image() {
  echo "$1" | sed 's/@sha256:.*//'
}

repo_of() {
  img="$(normalize_image "$1")"
  case "$img" in
    *:*) echo "${img%:*}" ;;
    *)   echo "${img}" ;;
  esac
}

tag_of() {
  img="$(normalize_image "$1")"
  case "$img" in
    *:*) echo "${img##*:}" ;;
    *)   echo "latest" ;;
  esac
}

redis_key_repo() {
  repo="$1"; tag="$2"
  echo "sbom:repo:${repo}:${tag}"
}

redis_key_cve() {
  repo="$1"; tag="$2"
  echo "sbom:cve:${repo}:${tag}"
}

redis_key_meta() {
  repo="$1"; tag="$2"
  echo "sbom:meta:${repo}:${tag}"
}

wait_for_redis() {
  log "Checking Redis connection at ${REDIS_HOST}:${REDIS_PORT}..."
  i=0
  while [ "$i" -lt 30 ]; do
    if redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" ping 2>/dev/null | grep -q PONG; then
      log "Redis is ready"
      return 0
    fi
    i=$((i + 1))
    sleep 2
  done
  log "ERROR: Redis not reachable"
  exit 1
}

collect_images() {
  TMP="$(mktemp)"

  if [ "${DISCOVER_CLUSTER_IMAGES}" = "true" ]; then
    OLD_IFS="$IFS"
    IFS=','
    for ns in ${SCAN_NAMESPACES}; do
      IFS="$OLD_IFS"
      ns="$(echo "$ns" | tr -d ' ')"
      [ -n "$ns" ] || continue
      log "Discovering images in namespace ${ns}"
      kubectl get pods -n "$ns" \
        -o jsonpath='{range .items[*]}{range .spec.containers[*]}{.image}{"\n"}{end}{range .spec.initContainers[*]}{.image}{"\n"}{end}{end}' \
        2>/dev/null >> "${TMP}" || true
    done
    IFS="$OLD_IFS"
  elif [ -f "${IMAGE_LIST_FILE}" ]; then
    grep -v '^[[:space:]]*$' "${IMAGE_LIST_FILE}" | grep -v '^[[:space:]]*#' >> "${TMP}" || true
  fi

  sort -u "${TMP}" | grep -v '^[[:space:]]*$' || true
  rm -f "${TMP}"
}

scan_and_store() {
  image_ref="$1"
  repo="$(repo_of "$image_ref")"
  tag="$(tag_of "$image_ref")"
  key="$(redis_key_repo "$repo" "$tag")"
  cve_key="$(redis_key_cve "$repo" "$tag")"
  meta_key="$(redis_key_meta "$repo" "$tag")"
  out_sbom="$(mktemp)"
  out_cve="$(mktemp)"

  log "Scanning SBOM for ${image_ref}..."
  syft_ms=0
  grype_ms=0
  trivy_ms=0
  t0="$(date +%s)"
  if ! syft scan "${image_ref}" -o "syft-json=${out_sbom}" --quiet; then
    log "WARN: syft failed for ${image_ref}, skipping"
    rm -f "${out_sbom}" "${out_cve}"
    return 0
  fi
  syft_ms=$(( ($(date +%s) - t0) * 1000 ))

  pkg_count="$(grep -o '"id":' "${out_sbom}" | wc -l | tr -d ' ')"
  scanned_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

  # Scan vulnerabilities via Grype if available
  vuln_count=0
  critical_count=0
  high_count=0
  medium_count=0
  low_count=0

  if command -v grype >/dev/null 2>&1; then
    log "Scanning CVE vulnerabilities for ${image_ref} with Grype..."
    t0="$(date +%s)"
    if grype "sbom:${out_sbom}" -o json --quiet > "${out_cve}" 2>/dev/null; then
      if command -v jq >/dev/null 2>&1; then
        vuln_count="$(jq '.matches | length' "${out_cve}" 2>/dev/null || echo 0)"
        critical_count="$(jq '[.matches[] | select(.vulnerability.severity=="Critical")] | length' "${out_cve}" 2>/dev/null || echo 0)"
        high_count="$(jq '[.matches[] | select(.vulnerability.severity=="High")] | length' "${out_cve}" 2>/dev/null || echo 0)"
        medium_count="$(jq '[.matches[] | select(.vulnerability.severity=="Medium")] | length' "${out_cve}" 2>/dev/null || echo 0)"
        low_count="$(jq '[.matches[] | select(.vulnerability.severity=="Low")] | length' "${out_cve}" 2>/dev/null || echo 0)"
      fi
    else
      echo '{"matches":[]}' > "${out_cve}"
    fi
    grype_ms=$(( ($(date +%s) - t0) * 1000 ))
  else
    echo '{"matches":[]}' > "${out_cve}"
  fi

  # Scan vulnerabilities via Trivy if available
  trivy_vuln_count=0
  trivy_critical_count=0
  trivy_high_count=0
  trivy_medium_count=0
  out_trivy="$(mktemp)"

  if command -v trivy >/dev/null 2>&1; then
    log "Scanning CVE vulnerabilities for ${image_ref} with Trivy..."
    t0="$(date +%s)"
    if trivy image --format json --scanners vuln --skip-db-update "${image_ref}" --quiet > "${out_trivy}" 2>/dev/null; then
      if command -v jq >/dev/null 2>&1; then
        trivy_vuln_count="$(jq '[.Results[]?.Vulnerabilities // [] | length] | add // 0' "${out_trivy}" 2>/dev/null || echo 0)"
        trivy_critical_count="$(jq '[.Results[]?.Vulnerabilities // [] | map(select(.Severity=="CRITICAL")) | length] | add // 0' "${out_trivy}" 2>/dev/null || echo 0)"
        trivy_high_count="$(jq '[.Results[]?.Vulnerabilities // [] | map(select(.Severity=="HIGH")) | length] | add // 0' "${out_trivy}" 2>/dev/null || echo 0)"
        trivy_medium_count="$(jq '[.Results[]?.Vulnerabilities // [] | map(select(.Severity=="MEDIUM")) | length] | add // 0' "${out_trivy}" 2>/dev/null || echo 0)"
      fi
    else
      echo '{"Results":[]}' > "${out_trivy}"
    fi
    trivy_ms=$(( ($(date +%s) - t0) * 1000 ))
  else
    echo '{"Results":[]}' > "${out_trivy}"
  fi

  # Store SBOM in Redis
  redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" -x SET "${key}" < "${out_sbom}" >/dev/null
  redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" EXPIRE "${key}" "${SBOM_TTL_SECONDS}" >/dev/null

  # Store Grype CVE results in Redis
  redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" -x SET "${cve_key}" < "${out_cve}" >/dev/null
  redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" EXPIRE "${cve_key}" "${SBOM_TTL_SECONDS}" >/dev/null

  # Store Trivy CVE results in Redis
  trivy_key="sbom:trivy:${repo}:${tag}"
  redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" -x SET "${trivy_key}" < "${out_trivy}" >/dev/null
  redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" EXPIRE "${trivy_key}" "${SBOM_TTL_SECONDS}" >/dev/null

  # Store Metadata in Redis
  meta="{\"imageRef\":\"${image_ref}\",\"repository\":\"${repo}\",\"tag\":\"${tag}\",\"scannedAt\":\"${scanned_at}\",\"packageCount\":${pkg_count},\"vulnerabilityCount\":${vuln_count},\"criticalCount\":${critical_count},\"highCount\":${high_count},\"mediumCount\":${medium_count},\"lowCount\":${low_count},\"syftDurationMs\":${syft_ms},\"grypeDurationMs\":${grype_ms},\"trivyDurationMs\":${trivy_ms},\"trivyVulnerabilityCount\":${trivy_vuln_count},\"trivyCriticalCount\":${trivy_critical_count},\"trivyHighCount\":${trivy_high_count},\"trivyMediumCount\":${trivy_medium_count},\"ttlSeconds\":${SBOM_TTL_SECONDS}}"
  printf '%s' "${meta}" | redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" -x SET "${meta_key}" >/dev/null
  redis-cli -h "${REDIS_HOST}" -p "${REDIS_PORT}" EXPIRE "${meta_key}" "${SBOM_TTL_SECONDS}" >/dev/null

  log "Stored ${key} (pkgs=${pkg_count}, Grype=${vuln_count}, Trivy=${trivy_vuln_count}, syft=${syft_ms}ms grype=${grype_ms}ms)"

  # Annotate matching deployments in Kubernetes cluster
  if command -v kubectl >/dev/null 2>&1; then
    status="CLEAN"
    if [ "${critical_count}" -gt 0 ] || [ "${high_count}" -gt 0 ]; then
      status="VULNERABLE"
    fi
    OLD_IFS="$IFS"
    IFS=','
    for ns in ${SCAN_NAMESPACES}; do
      IFS="$OLD_IFS"
      ns="$(echo "$ns" | tr -d ' ')"
      [ -n "$ns" ] || continue
      base_repo="$(basename "${repo}")"
      deps="$(kubectl get deployments -n "$ns" -o jsonpath="{range .items[*]}{.metadata.name}{' '}{.spec.template.spec.containers[*].image}{'\n'}{end}" 2>/dev/null | grep "${base_repo}" | awk '{print $1}' || true)"
      for dep in $deps; do
        [ -n "$dep" ] || continue
        kubectl annotate deployment "$dep" -n "$ns" \
          "scig.amocna.io/managed-by=SCIG-Engine" \
          "scig.amocna.io/last-scanned-at=${scanned_at}" \
          "scig.amocna.io/vulnerability-status=${status}" \
          "scig.amocna.io/vulnerabilities-total=${vuln_count}" \
          "scig.amocna.io/critical-count=${critical_count}" \
          "scig.amocna.io/high-count=${high_count}" \
          --overwrite >/dev/null 2>&1 || true
      done
    done
    IFS="$OLD_IFS"
  fi

  rm -f "${out_sbom}" "${out_cve}" "${out_trivy}"
}

main() {
  require_tools
  wait_for_redis

  # Initial annotation of all managed deployments in target namespaces
  if command -v kubectl >/dev/null 2>&1; then
    scanned_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    OLD_IFS="$IFS"
    IFS=','
    for ns in ${SCAN_NAMESPACES}; do
      IFS="$OLD_IFS"
      ns="$(echo "$ns" | tr -d ' ')"
      [ -n "$ns" ] || continue
      deps="$(kubectl get deployments -n "$ns" -o jsonpath='{.items[*].metadata.name}' 2>/dev/null || true)"
      for dep in $deps; do
        [ -n "$dep" ] || continue
        kubectl annotate deployment "$dep" -n "$ns" \
          "scig.amocna.io/managed-by=SCIG-Engine" \
          "scig.amocna.io/last-scanned-at=${scanned_at}" \
          "scig.amocna.io/vulnerability-status=PENDING" \
          --overwrite >/dev/null 2>&1 || true
      done
    done
    IFS="$OLD_IFS"
  fi

  IMG_FILE="$(mktemp)"
  collect_images > "${IMG_FILE}"
  if [ ! -s "${IMG_FILE}" ]; then
    log "ERROR: no images to scan"
    rm -f "${IMG_FILE}"
    exit 1
  fi

  log "Images to scan:"
  cat "${IMG_FILE}" >&2

  while IFS= read -r img; do
    [ -n "${img}" ] || continue
    scan_and_store "${img}"
  done < "${IMG_FILE}"
  rm -f "${IMG_FILE}"

  log "Done. SCIG Redis keys updated."
}

main "$@"
