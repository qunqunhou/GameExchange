#!/usr/bin/env bash

set -Eeuo pipefail
umask 077

readonly CONFIG_SOURCE="${GAMEEXCHANGE_HTTP_NGINX_CONFIG:-/tmp/gameexchange-http.conf}"
readonly TARGET_CONFIG="${GAMEEXCHANGE_HTTP_NGINX_TARGET:-/etc/nginx/conf.d/gameexchange-http.conf}"
readonly BACKUP_ROOT="${GAMEEXCHANGE_HTTP_NGINX_BACKUP_ROOT:-/var/backups/gameexchange/nginx}"
readonly APP_PRECHECK_URL="${GAMEEXCHANGE_HTTP_APP_PRECHECK_URL:-http://127.0.0.1:8080/vue/login.html}"
readonly STATIC_SMOKE_URL="${GAMEEXCHANGE_HTTP_STATIC_SMOKE_URL:-http://127.0.0.1/vue/login.html}"
readonly METRICS_SMOKE_URL="${GAMEEXCHANGE_HTTP_METRICS_SMOKE_URL:-http://127.0.0.1/metrics}"

log() {
    printf '%s [%s] %s\n' "$(date --iso-8601=seconds)" "$1" "$2"
}

fail() {
    log "ERROR" "$1" >&2
    return 1
}

require_root() {
    [[ "$(id -u)" -eq 0 ]] || fail "必须使用 root 权限执行，例如 sudo $0"
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || fail "缺少必需命令: $1"
}

validate_source_config() {
    [[ -f "$CONFIG_SOURCE" ]] || fail "未找到 Nginx 配置: $CONFIG_SOURCE"
    grep -q 'listen 80 default_server' "$CONFIG_SOURCE" || fail "HTTP 配置缺少 listen 80 default_server"
    grep -q 'proxy_pass http://gameexchange_http_app' "$CONFIG_SOURCE" || fail "HTTP 配置缺少 App 反向代理"
    ! grep -Eq 'listen[[:space:]]+443|ssl_certificate|Strict-Transport-Security' "$CONFIG_SOURCE" || fail "HTTP 演示配置不得包含 TLS/HSTS 指令"
}

validate_runtime_boundary() {
    local listeners
    listeners="$(ss -lnt)"

    grep -q '127\.0\.0\.1:8080' <<<"$listeners" || fail "App 必须只通过宿主机回环地址提供 8080"
    ! grep -Eq '(^|[[:space:]])(0\.0\.0\.0|\[::\]):(443|8080|3306|3000|9090)' <<<"$listeners" || fail "禁止公网监听 443/8080/3306/3000/9090"
}

preflight_app() {
    curl --fail --show-error --silent --max-time 5 "$APP_PRECHECK_URL" >/dev/null ||
        fail "启用 Nginx 前 App 回环静态资源不可访问: $APP_PRECHECK_URL"
}

install_config() {
    local deployment_id snapshot_dir

    deployment_id="$(date -u +%Y%m%dT%H%M%SZ)"
    snapshot_dir="$BACKUP_ROOT/p3.5-http-$deployment_id"

    install -d -o root -g root -m 0700 "$snapshot_dir"
    cp -a /etc/nginx "$snapshot_dir/nginx"

    rm -f /etc/nginx/sites-enabled/default
    install -o root -g root -m 0644 "$CONFIG_SOURCE" "$TARGET_CONFIG"

    nginx -t

    if systemctl is-active --quiet nginx; then
        systemctl reload nginx
    else
        systemctl enable --now nginx
    fi
    systemctl enable nginx >/dev/null

    printf 'Snapshot=%s\n' "$snapshot_dir"
    printf 'TargetConfig=%s\n' "$TARGET_CONFIG"
}

verify_http_entrypoint() {
    local metrics_code
    local listeners

    curl --fail --show-error --silent --max-time 5 "$STATIC_SMOKE_URL" >/dev/null
    metrics_code="$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' --max-time 5 "$METRICS_SMOKE_URL")"
    [[ "$metrics_code" == "404" ]] || fail "/metrics 预期返回 404，实际为 $metrics_code"
    listeners="$(ss -lnt)"
    grep -Eq '(^|[[:space:]])(0\.0\.0\.0|\[::\]):80' <<<"$listeners" || fail "Nginx 未监听公网 HTTP 80"
    ! grep -Eq '(^|[[:space:]])(0\.0\.0\.0|\[::\]):(443|8080|3306|3000|9090)' <<<"$listeners" || fail "禁止公网监听 443/8080/3306/3000/9090"
}

main() {
    require_root
    require_command curl
    require_command grep
    require_command install
    require_command nginx
    require_command ss
    require_command systemctl

    log "INFO" "Mode=PUBLIC_HTTP_PORTFOLIO_DEMO"
    log "INFO" "该模式仅用于作品集 HTTP 演示，不代表安全或生产就绪"

    validate_source_config
    validate_runtime_boundary
    preflight_app
    install_config
    verify_http_entrypoint

    log "PASS" "P3.5 HTTP 本机入口验证完成；下一步仅在阿里云安全组开放 TCP 80"
}

main "$@"
