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
