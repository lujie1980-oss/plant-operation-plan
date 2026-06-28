# Docker 打包与发布

## 前置

- 安装 [Docker Desktop](https://www.docker.com/products/docker-desktop/)（Windows）或 Docker Engine（Linux）
- 本机构建镜像时建议可用内存 ≥ 8GB（Maven + Node + Timefold 编译较耗资源）

## 一键构建并运行（推荐）

在项目根目录：

```powershell
cd d:\AILab\PlantOperationPlan\plant-operation-plan
docker compose up -d --build
```

- 界面：http://localhost:8080/#/
- API / Swagger：http://localhost:8080/q/swagger-ui
- 健康检查：http://localhost:8080/q/health/ready

数据持久化在 Docker 卷 `plantops-data`（H2 文件 `/app/data/plantops.*`）。

查看日志：

```powershell
docker compose logs -f plantops
```

停止：

```powershell
docker compose down
```

仅停止、保留数据卷：

```powershell
docker compose down
# 连数据一起删（慎用）
# docker compose down -v
```

## 仅构建镜像

```powershell
docker build -t plant-operation-plan:1.0.0 .
```

运行容器：

```powershell
docker run -d --name plantops `
  -p 8080:8080 `
  -v plantops-data:/app/data `
  -e QUARKUS_PROFILE=docker `
  -e PLANTOPS_SAMPLE_DATA_ENABLED=false `
  plant-operation-plan:1.0.0
```

首次想加载演示数据：

```powershell
docker run -d --name plantops `
  -p 8080:8080 `
  -v plantops-data:/app/data `
  -e QUARKUS_PROFILE=docker `
  -e PLANTOPS_SAMPLE_DATA_ENABLED=true `
  plant-operation-plan:1.0.0
```

## 发布到镜像仓库

### 阿里云 ACR（推荐，第二步）

**控制台准备（只需做一次）**

1. 登录 [容器镜像服务 ACR](https://cr.console.aliyun.com/)
2. 创建 **个人版实例**（选地域，如华东1 杭州 → 地址 `registry.cn-hangzhou.aliyuncs.com`）
3. **命名空间** → 创建（如 `plantops`）
4. **镜像仓库** → 创建仓库 `plant-operation-plan`
5. **访问凭证** → 设置 **固定密码**（不是阿里云登录密码）

**本机推送**

```powershell
cd d:\AILab\PlantOperationPlan\plant-operation-plan

# 1. 确保本地已有镜像（第一步）
powershell -ExecutionPolicy Bypass -File tools\docker-build-local.ps1

# 2. 配置 ACR（复制示例并填写）
copy tools\acr.env.example tools\acr.local.env
notepad tools\acr.local.env

# 3. 登录并推送
powershell -ExecutionPolicy Bypass -File tools\docker-push-acr.ps1
```

推送成功后镜像地址形如：

`registry.cn-hangzhou.aliyuncs.com/plantops/plant-operation-plan:1.0.0`

### 阿里云 ECS 运行（第三步）

**ECS 准备（只需做一次）**

1. 创建 ECS（建议 4 核 8G+，与 ACR 同地域如华东1）
2. 安全组 **入方向** 放行 **8080**（及 SSH 22）
3. 绑定公网 IP，记下 IP 与 SSH 密钥（`.pem`）

**本机一键部署（SSH 到 ECS 拉镜像并运行）**

```powershell
cd d:\AILab\PlantOperationPlan\plant-operation-plan

# 1. 配置 ECS（复制示例并填写公网 IP、SSH 用户、密钥路径）
copy tools\ecs.env.example tools\ecs.local.env
notepad tools\ecs.local.env

# 2. 确保 acr.local.env 已填写（第二步）
# 3. 部署
powershell -ExecutionPolicy Bypass -File tools\docker-deploy-ecs.ps1
```

脚本会在 ECS 上：安装 Docker（若无）→ 登录 ACR → `docker pull` → `docker run`（数据目录 `/data/plantops`）。

访问：`http://ECS公网IP:8080/#/`  
健康检查：`http://ECS公网IP:8080/q/health/ready`

**或在 ECS 终端手动执行**

```bash
docker login crpi-hjfrj3sdmymxy3ub.cn-hangzhou.personal.cr.aliyuncs.com
docker pull crpi-hjfrj3sdmymxy3ub.cn-hangzhou.personal.cr.aliyuncs.com/plantops/plant-operation-plan:1.0.0
docker run -d --name plantops --restart unless-stopped \
  -p 8080:8080 -v /data/plantops:/app/data \
  -e QUARKUS_PROFILE=docker -e PLANTOPS_SAMPLE_DATA_ENABLED=false \
  crpi-hjfrj3sdmymxy3ub.cn-hangzhou.personal.cr.aliyuncs.com/plantops/plant-operation-plan:1.0.0
```

### 其它仓库（Harbor / Docker Hub）

以 Harbor / 阿里云 ACR / Docker Hub 为例：

```powershell
# 打标签（替换为你的仓库地址）
docker tag plant-operation-plan:1.0.0 registry.example.com/aps/plant-operation-plan:1.0.0

# 登录并推送
docker login registry.example.com
docker push registry.example.com/aps/plant-operation-plan:1.0.0
```

在服务器上拉取并运行：

```bash
docker pull registry.example.com/aps/plant-operation-plan:1.0.0
docker run -d --name plantops \
  -p 8080:8080 \
  -v /data/plantops:/app/data \
  -e QUARKUS_PROFILE=docker \
  registry.example.com/aps/plant-operation-plan:1.0.0
```

## 配置说明

| 项 | Docker 默认值 |
|----|----------------|
| Profile | `docker`（见 `application-docker.properties`） |
| 数据库 | H2 文件库 `/app/data/plantops` |
| 演示数据 | `plantops.sample-data.enabled=false` |
| 监听地址 | `0.0.0.0:8080` |

环境变量（Quarkus 会自动映射）：

| 环境变量 | 作用 |
|----------|------|
| `QUARKUS_PROFILE` | 使用 `docker` / `prod` 等 profile |
| `PLANTOPS_SAMPLE_DATA_ENABLED` | `true` / `false` |
| `JAVA_OPTS` | JVM 参数，如 `-Xmx4g` |

生产若改用 PostgreSQL，需在 `pom.xml` 增加 `quarkus-jdbc-postgresql`，并新增 `application-prod.properties`，启动时 `-e QUARKUS_PROFILE=prod`。

## 本地先打包再构建镜像（可选）

若不想在 Docker 里跑 Maven，可先本地打包再使用精简 Dockerfile：

```powershell
.\mvnw.cmd package -DskipTests
docker build -f Dockerfile.prebuilt -t plant-operation-plan:1.0.0 .
```

（需自行维护 `Dockerfile.prebuilt`，仅 `COPY target/quarkus-app`。）

## 故障排查

| 现象 | 处理 |
|------|------|
| 构建超时 / OOM | 增大 Docker Desktop 内存；或本地 `mvnw package` 后用 prebuilt 镜像 |
| 启动后 502 / 不健康 | 等待 1–2 分钟（Flyway + 首次启动）；`docker compose logs` |
| 刷新页面 404 | 必须用 `/#/` Hash 路由，访问根路径 http://host:8080/#/ |
| 数据丢失 | 确认挂载了卷 `-v plantops-data:/app/data` |
