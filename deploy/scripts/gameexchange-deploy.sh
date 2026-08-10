#!/usr/bin/env bash

set -Eeuo pipefail
umask 077

readonly PROJECT_DIR="${GAMEEXCHANGE_PROJECT_DIR:-/opt/gameexchange}"
readonly ENV_FILE="${GAMEEXCHANGE_ENV_FILE:-${PROJECT_DIR}/prod.env}"
readonly COMPOSE_FILE="${GAMEEXCHANGE_COMPOSE_FILE:-${PROJECT_DIR}/docker-compose.prod.yaml}"
readonly BACKUP_ROOT="${GAMEEXCHANGE_BACKUP_ROOT:-/var/backups/gameexchange/deployments}"
readonly ALLOWED_REPOSITORY="${GAMEEXCHANGE_ALLOWED_REPOSITORY:-crpi-npa4w6l8amsghlzs.cn-hangzhou.personal.cr.aliyuncs.com/smzhiman/gameexchange}"
readonly HEALTH_TIMEOUT_SECONDS="${GAMEEXCHANGE_HEALTH_TIMEOUT_SECONDS:-180}"
readonly STATIC_SMOKE_URL="${GAMEEXCHANGE_STATIC_SMOKE_URL:-http://127.0.0.1:8080/vue/assets/js/app-config.js}"
readonly BUSINESS_SMOKE_URL="${GAMEEXCHANGE_BUSINESS_SMOKE_URL:-http://127.0.0.1:8080/stats}"

target_image=""
dry_run=false
deployment_id=""
snapshot_dir=""
log_file=""
temporary_env_file=""
temporary_docker_config=""
rollback_required=false
current_image=""
current_image_id=""
mysql_before=""
healthy_app_id=""

usage() {
    cat <<'EOF'
Usage:
  sudo gameexchange-deploy.sh --image REGISTRY/REPOSITORY@sha256:DIGEST [--dry-run]

Options:
  --image     完整且不可变的 ACR Digest 引用。
  --dry-run   只执行配置、运行基线和参数检查，不拉取或重建容器。
  --help      显示帮助。
EOF
}

log() {
    printf '%s [%s] %s\n' "$(date --iso-8601=seconds)" "$1" "$2"
}

fail() {
    log "ERROR" "$1" >&2
    return 1
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || fail "缺少必需命令: $1"
}

compose() {
    docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

read_app_image() {
    local -a matches=()
    mapfile -t matches < <(sed -n 's/^APP_IMAGE=//p' "$ENV_FILE")
    [[ "${#matches[@]}" -eq 1 ]] || fail "prod.env 必须且只能包含一个 APP_IMAGE"
    [[ -n "${matches[0]}" ]] || fail "APP_IMAGE 不能为空"
    printf '%s' "${matches[0]}"
}

validate_image_reference() {
    local image="$1"
    local digest=""

    case "$image" in
        "${ALLOWED_REPOSITORY}"@sha256:*)
            digest="${image#${ALLOWED_REPOSITORY}@}"
            ;;
        *)
            fail "镜像必须来自批准仓库并使用 @sha256: 引用"
            return 1
            ;;
    esac

    [[ "$digest" =~ ^sha256:[0-9a-f]{64}$ ]] || fail "镜像 Digest 格式无效"
}

container_fingerprint() {
    local container_id="$1"
    docker inspect "$container_id" \
        --format '{{.Id}}|{{.Image}}|{{.State.Status}}|{{.State.Health.Status}}|{{.State.StartedAt}}|{{.RestartCount}}'
}

service_container_id() {
    local service="$1"
    local container_id=""
    container_id="$(compose ps -q --all "$service")"
    [[ -n "$container_id" ]] || fail "未找到运行中的 Compose 服务: $service"
    printf '%s' "$container_id"
}

wait_for_app_healthy() {
    local deadline=$((SECONDS + HEALTH_TIMEOUT_SECONDS))
    local container_id=""
    local state=""
    local health=""

    healthy_app_id=""
    while ((SECONDS < deadline)); do
        container_id="$(compose ps -q --all app 2>/dev/null || true)"
        if [[ -n "$container_id" ]]; then
            state="$(docker inspect "$container_id" --format '{{.State.Status}}' 2>/dev/null || true)"
            health="$(docker inspect "$container_id" --format '{{.State.Health.Status}}' 2>/dev/null || true)"

            if [[ "$state" == "running" && "$health" == "healthy" ]]; then
                healthy_app_id="$container_id"
                return 0
            fi

            if [[ "$state" == "exited" || "$state" == "dead" ]]; then
                docker logs --tail 80 "$container_id" >&2 || true
                fail "App 在健康检查完成前退出，state=$state"
                return 1
            fi
        fi
        sleep 2
    done

    if [[ -n "$container_id" ]]; then
        docker logs --tail 80 "$container_id" >&2 || true
    fi
    fail "App 未在 ${HEALTH_TIMEOUT_SECONDS} 秒内达到 healthy"
}

run_smoke_test() {
    local stats_json=""

    curl --fail --silent --show-error --max-time 5 "$STATIC_SMOKE_URL" >/dev/null
    stats_json="$(curl --fail --silent --show-error --max-time 10 "$BUSINESS_SMOKE_URL")"
    jq -e '.code == 200' <<<"$stats_json" >/dev/null \
        || fail "业务 Smoke Test 未返回 code=200"
}

replace_app_image() {
    local image="$1"
    local env_dir=""

    env_dir="$(dirname "$ENV_FILE")"
    temporary_env_file="$(mktemp "${env_dir}/.prod.env.deploy.XXXXXX")"

    awk -v image="$image" '
        BEGIN { replaced = 0 }
        /^APP_IMAGE=/ {
            print "APP_IMAGE=" image
            replaced++
            next
        }
        { print }
        END { if (replaced != 1) exit 42 }
    ' "$ENV_FILE" >"$temporary_env_file" \
        || fail "无法安全替换 APP_IMAGE"

    chown --reference="$ENV_FILE" "$temporary_env_file"
    chmod --reference="$ENV_FILE" "$temporary_env_file"
    mv -f -- "$temporary_env_file" "$ENV_FILE"
    temporary_env_file=""
}

restore_environment_file() {
    local env_dir=""

    env_dir="$(dirname "$ENV_FILE")"
    temporary_env_file="$(mktemp "${env_dir}/.prod.env.rollback.XXXXXX")"
    cp -- "$snapshot_dir/prod.env" "$temporary_env_file"
    chown --reference="$ENV_FILE" "$temporary_env_file"
    chmod --reference="$ENV_FILE" "$temporary_env_file"
    mv -f -- "$temporary_env_file" "$ENV_FILE"
    temporary_env_file=""
}

verify_mysql_unchanged() {
    local mysql_after=""
    mysql_after="$(container_fingerprint "$(service_container_id mysql)")"
    [[ "$mysql_after" == "$mysql_before" ]] \
        || fail "MySQL 指纹发生变化，必须立即人工检查"
}

perform_rollback() {
    local rollback_image_id=""
    local running_image_id=""

    log "WARN" "开始恢复原 APP_IMAGE"
    restore_environment_file || return 1
    compose config --quiet || return 1

    rollback_image_id="$(docker image inspect "$current_image" --format '{{.Id}}')" || return 1
    compose up -d --no-deps --pull never app || return 1
    wait_for_app_healthy || return 1
    run_smoke_test || return 1
    verify_mysql_unchanged || return 1

    running_image_id="$(docker inspect "$healthy_app_id" --format '{{.Image}}')" || return 1
    [[ "$running_image_id" == "$rollback_image_id" ]] || return 1

    log "PASS" "回滚完成，App 已恢复到原镜像"
}

cleanup() {
    if [[ -n "$temporary_env_file" && -e "$temporary_env_file" ]]; then
        rm -f -- "$temporary_env_file"
    fi

    if [[ -n "$temporary_docker_config" ]]; then
        case "$temporary_docker_config" in
            /run/gameexchange-docker-config.*)
                docker --config "$temporary_docker_config" logout \
                    "${ALLOWED_REPOSITORY%%/*}" >/dev/null 2>&1 || true
                rm -rf -- "$temporary_docker_config"
                ;;
            *)
                log "ERROR" "拒绝清理非预期 Docker 配置目录: $temporary_docker_config" >&2
                ;;
        esac
    fi
}

handle_error() {
    local exit_code="$1"
    local line_number="$2"

    trap - ERR
    set +e
    log "ERROR" "部署在第 ${line_number} 行失败，exit=${exit_code}"

    if [[ "$rollback_required" == true ]]; then
        if ! perform_rollback; then
            log "CRITICAL" "自动回滚失败；保留快照并停止后续操作"
            exit_code=90
        fi
    fi

    exit "$exit_code"
}

handle_signal() {
    local signal_name="$1"
    local exit_code="$2"

    trap - ERR HUP INT TERM
    set +e
    log "ERROR" "收到 ${signal_name}，停止部署"

    if [[ "$rollback_required" == true ]]; then
        if ! perform_rollback; then
            log "CRITICAL" "中断后的自动回滚失败；保留快照并停止后续操作"
            exit_code=90
        fi
    fi

    exit "$exit_code"
}

parse_arguments() {
    while (($# > 0)); do
        case "$1" in
            --image)
                [[ $# -ge 2 ]] || fail "--image 缺少参数"
                target_image="$2"
                shift 2
                ;;
            --dry-run)
                dry_run=true
                shift
                ;;
            --help|-h)
                usage
                exit 0
                ;;
            *)
                fail "未知参数: $1"
                ;;
        esac
    done

    [[ -n "$target_image" ]] || fail "必须提供 --image"
}

preflight() {
    local app_id=""
    local app_before=""
    local mysql_id=""

    [[ "$EUID" -eq 0 ]] || fail "必须通过 sudo 以 root 身份运行"
    [[ "$HEALTH_TIMEOUT_SECONDS" =~ ^[1-9][0-9]*$ ]] || fail "健康检查超时必须为正整数"

    for command_name in awk chown chmod cp curl date dirname docker flock install jq mktemp mv rm sed sha256sum sleep stat tee; do
        require_command "$command_name"
    done

    [[ -f "$ENV_FILE" ]] || fail "环境文件不存在: $ENV_FILE"
    [[ -f "$COMPOSE_FILE" ]] || fail "Compose 文件不存在: $COMPOSE_FILE"

    validate_image_reference "$target_image"
    current_image="$(read_app_image)"
    validate_image_reference "$current_image"
    compose config --quiet

    app_id="$(service_container_id app)"
    mysql_id="$(service_container_id mysql)"
    app_before="$(container_fingerprint "$app_id")"
    mysql_before="$(container_fingerprint "$mysql_id")"
    current_image_id="$(docker image inspect "$current_image" --format '{{.Id}}')"

    [[ "$app_before" == *"|running|healthy|"* ]] || fail "当前 App 不是 running/healthy"
    [[ "$mysql_before" == *"|running|healthy|"* ]] || fail "当前 MySQL 不是 running/healthy"
    [[ "$(docker inspect "$app_id" --format '{{.Image}}')" == "$current_image_id" ]] \
        || fail "当前 App Image ID 与 prod.env 不一致"

    log "PASS" "预检完成：App/MySQL 健康，当前镜像与配置一致"
    log "INFO" "CurrentImage=$current_image"
    log "INFO" "TargetImage=$target_image"
}

prepare_snapshot_and_log() {
    deployment_id="$(date -u +%Y%m%dT%H%M%SZ)"
    snapshot_dir="${BACKUP_ROOT}/${deployment_id}"

    install -d -o root -g root -m 0700 "$BACKUP_ROOT"
    install -d -o root -g root -m 0700 "$snapshot_dir"
    install -o root -g root -m 0600 "$ENV_FILE" "$snapshot_dir/prod.env"
    install -o root -g root -m 0600 "$COMPOSE_FILE" "$snapshot_dir/docker-compose.prod.yaml"

    sha256sum "$snapshot_dir/prod.env" "$snapshot_dir/docker-compose.prod.yaml" \
        >"$snapshot_dir/SHA256SUMS.txt"
    chmod 0600 "$snapshot_dir/SHA256SUMS.txt"

    log_file="$snapshot_dir/deploy.log"
    install -o root -g root -m 0600 /dev/null "$log_file"
    exec > >(tee -a "$log_file") 2>&1
    log "INFO" "DeploymentId=$deployment_id"
    log "INFO" "SnapshotDir=$snapshot_dir"
}

deploy() {
    local target_image_id=""
    local target_platform=""
    local target_user=""
    local running_image_id=""

    temporary_docker_config="$(mktemp -d /run/gameexchange-docker-config.XXXXXX)"
    chmod 0700 "$temporary_docker_config"
    export DOCKER_CONFIG="$temporary_docker_config"

    log "INFO" "请使用批准的 ACR 访问凭据完成交互式登录"
    docker login "${ALLOWED_REPOSITORY%%/*}"
    docker pull "$target_image"
    target_image_id="$(docker image inspect "$target_image" --format '{{.Id}}')"
    target_platform="$(docker image inspect "$target_image" --format '{{.Os}}/{{.Architecture}}')"
    target_user="$(docker image inspect "$target_image" --format '{{.Config.User}}')"

    [[ "$target_platform" == "linux/amd64" ]] || fail "目标镜像平台不是 linux/amd64"
    [[ "$target_user" == "gameexchange:gameexchange" ]] || fail "目标镜像运行用户不符合非 root 基线"

    replace_app_image "$target_image"
    rollback_required=true
    compose config --quiet
    compose up -d --no-deps --pull never app
    wait_for_app_healthy
    run_smoke_test
    verify_mysql_unchanged

    running_image_id="$(docker inspect "$healthy_app_id" --format '{{.Image}}')"
    [[ "$running_image_id" == "$target_image_id" ]] \
        || fail "运行中的 App Image ID 与目标镜像不一致"

    rollback_required=false
    {
        printf 'DeploymentId=%s\n' "$deployment_id"
        printf 'PreviousImage=%s\n' "$current_image"
        printf 'TargetImage=%s\n' "$target_image"
        printf 'TargetImageId=%s\n' "$target_image_id"
        printf 'AppContainerId=%s\n' "$healthy_app_id"
        printf 'MySQLFingerprint=%s\n' "$mysql_before"
        printf 'Result=PASSED\n'
    } >"$snapshot_dir/result.txt"
    chmod 0600 "$snapshot_dir/result.txt"

    log "PASS" "受控 App 部署完成；MySQL 未重建"
}

main() {
    parse_arguments "$@"

    exec 9>/run/lock/gameexchange-deploy.lock
    flock -n 9 || fail "已有 GameExchange 部署任务正在运行"

    preflight
    if [[ "$dry_run" == true ]]; then
        log "PASS" "Dry run 完成；未拉取镜像、修改配置或重建容器"
        return 0
    fi

    prepare_snapshot_and_log
    deploy
}

trap cleanup EXIT
trap 'handle_error $? $LINENO' ERR
trap 'handle_signal HUP 129' HUP
trap 'handle_signal INT 130' INT
trap 'handle_signal TERM 143' TERM

main "$@"
