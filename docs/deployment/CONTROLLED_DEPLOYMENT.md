# GameExchange 受控部署流程

## 1. 适用范围

本流程用于单 ECS、Docker Compose 环境中的 App-only 部署。操作者必须先批准完整的 ACR `repository@sha256:digest`，再通过 SSH 手动触发脚本。脚本不构建镜像、不修改 MySQL、不保存 ACR 密码，也不替代发布审批。

当前实现不是 GitHub Actions 远程 CD。这样可以避免在尚未建立云端非交互身份边界时，把 ECS SSH 私钥或 ACR 固定密码保存为仓库 Secret。

## 2. 控制边界

- 只接受 `crpi-npa4w6l8amsghlzs.cn-hangzhou.personal.cr.aliyuncs.com/smzhiman/gameexchange@sha256:<64 位十六进制>`。
- 部署前验证 App/MySQL 均为 `running/healthy`，并确认当前 App 与 `prod.env` 一致。
- 部署前保存 `prod.env`、Compose 和 SHA-256 到 `/var/backups/gameexchange/deployments/<UTC 时间>/`。
- 只执行 App 更新，禁止重建 MySQL。
- 目标镜像必须为 `linux/amd64`，运行用户必须为 `gameexchange:gameexchange`。
- App 必须通过容器健康检查、静态资源探测和 `/stats` 业务码检查。
- 部署失败、SSH 中断或收到终止信号时恢复原 `APP_IMAGE` 并重新执行健康检查；自动回滚失败时退出码为 `90`，必须人工处理。
- 单实例 App 更新会造成短暂中断并使现有 Session 失效。

## 3. 安装脚本

从本地仓库上传脚本，公网地址只在终端中使用，不写入仓库：

```powershell
scp .\deploy\scripts\gameexchange-deploy.sh smzhiman@<ECS_PUBLIC_IP>:/tmp/
```

在 ECS 上安装：

```bash
sudo install -d -o root -g root -m 0750 /opt/gameexchange/bin
sudo install -o root -g root -m 0750 \
  /tmp/gameexchange-deploy.sh \
  /opt/gameexchange/bin/gameexchange-deploy.sh
rm -f /tmp/gameexchange-deploy.sh
```

安装后先执行帮助和语法检查：

```bash
bash -n /opt/gameexchange/bin/gameexchange-deploy.sh
sudo /opt/gameexchange/bin/gameexchange-deploy.sh --help
```

## 4. 无变更预检

设置已批准的完整镜像引用：

```bash
TARGET_IMAGE='REGISTRY/REPOSITORY@sha256:APPROVED_DIGEST'
```

执行 Dry Run：

```bash
sudo /opt/gameexchange/bin/gameexchange-deploy.sh \
  --image "$TARGET_IMAGE" \
  --dry-run
```

预期结果为 `Dry run 完成`。该模式不会登录 ACR、拉取镜像、创建快照、修改 `prod.env` 或重建容器。

## 5. 正式部署

确认 Dry Run 通过并接受短暂中断、Session 失效后，执行：

```bash
sudo /opt/gameexchange/bin/gameexchange-deploy.sh \
  --image "$TARGET_IMAGE"
```

脚本会提示输入批准的 ACR 登录名和固定密码。密码不会出现在命令、环境文件或 Shell History 中；登录状态只保存在 `/run/gameexchange-docker-config.*` 临时目录，并在成功、失败或自动回滚后清理。

成功条件：

- 输出 `受控 App 部署完成`。
- 新 App 为 `running/healthy`，Image ID 与目标镜像一致。
- 静态资源和 `/stats` 业务探测通过。
- MySQL 指纹与部署前完全一致。
- 证据目录包含 `prod.env`、Compose、`SHA256SUMS.txt`、`deploy.log` 和 `result.txt`，权限为 root-only。

## 6. 故障处理

脚本在更新 `APP_IMAGE` 后发生错误时会自动回滚。看到 `回滚完成` 表示原 App 已恢复；看到 `自动回滚失败` 时不得继续部署或修改 MySQL，应保留证据目录并检查：

```bash
sudo docker compose \
  --env-file /opt/gameexchange/prod.env \
  -f /opt/gameexchange/docker-compose.prod.yaml \
  ps
```

人工恢复必须使用证据目录中的原 `prod.env`，重新渲染 Compose 后只更新 App。禁止执行 `docker compose down`、`down -v`、删除 MySQL 数据目录或重建数据库容器。

## 7. 验证边界

仓库侧验证只能证明脚本语法、参数控制和静态边界正确。ECS Dry Run 和同 Digest 幂等部署只能证明脚本能在目标主机上完成预检、临时 ACR 登录、镜像拉取、快照、健康检查和证据归档；它不能证明未来新版本替换一定成功，也不能把未实际触发的失败回滚记录为 `PASSED`。

只有在 ECS 完成一次真实新 Digest App-only 部署，以及一次受控失败回滚验证后，才能声明受控部署流程完整通过。

## 8. P3.4-B ECS 验证记录

验证时间：`2026-08-10`。本次使用当前已经运行的 RC2 Digest 执行 Dry Run 和同 Digest 幂等部署验证，未替换到新版本。

| 检查项 | 结果 | 状态 |
| --- | --- | --- |
| 脚本安装 | `/opt/gameexchange/bin/gameexchange-deploy.sh` 为 `root:root 0750`，SHA-256 为 `e2e16822be533f22aee2fb201469a3c97729897a0f9f5c0023b38e32778b586f` | `PASSED` |
| Dry Run | App/MySQL 为 `running/healthy`，当前镜像与 `prod.env` 一致；未拉取镜像、修改配置或重建容器 | `PASSED` |
| 幂等部署 | ACR 登录成功，目标 Digest 已是最新；脚本完成快照、健康检查和 Smoke Test | `PASSED` |
| 证据目录 | `/var/backups/gameexchange/deployments/20260810T100345Z` 包含 `deploy.log`、`prod.env`、`SHA256SUMS.txt`、`docker-compose.prod.yaml` 和 `result.txt`，权限均为 `root:root 0600` | `PASSED` |
| 快照校验 | `sha256sum -c SHA256SUMS.txt` 对 `prod.env` 和 `docker-compose.prod.yaml` 均返回 `OK` | `PASSED` |
| App 影响 | App 容器 ID、Image ID、Started At 和 `RestartCount=0` 前后一致 | `UNCHANGED` |
| MySQL 影响 | MySQL 容器 ID、Image ID、Started At 和 `RestartCount=0` 前后一致 | `UNCHANGED` |
| 临时凭据 | `/run/gameexchange-docker-config.*` 无残留 | `PASSED` |
| 真实新版本替换 | 本次未提供新的目标 Digest | `READY` |
| 失败回滚 | 脚本已实现普通失败、SSH 中断和终止信号下的回滚逻辑；本次未故意触发失败 | `READY` |

`result.txt` 记录的目标镜像为 `crpi-npa4w6l8amsghlzs.cn-hangzhou.personal.cr.aliyuncs.com/smzhiman/gameexchange@sha256:b920c62c349ef0b89591932f7bac0b030b7b134394363b321842ef55b62e6ac4`，运行中 App Image ID 为 `sha256:b920c62c349ef0b89591932f7bac0b030b7b134394363b321842ef55b62e6ac4`。
