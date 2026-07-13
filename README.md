# RentBook

RentBook 是给租房中介使用的前后端分离工作台。

## 项目结构

```text
RentBook/
  RentBook/        后端 Spring Boot + MyBatis + PostgreSQL
  RentBookWeb/     前端 Vite 工作台，可单独打包部署
```

## 后端

本地启动：

```powershell
cd C:\Users\liu\Documents\Projects\RentBook\RentBook
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

后端配置外置在：

```text
RentBook/config/
```

包括：

```text
application-local.yml
application-test.yml
application-prod.yml
```

后端打包：

```powershell
cd C:\Users\liu\Documents\Projects\RentBook\RentBook
.\gradlew.bat clean assemble
```

如果需要同时运行测试，可执行 `.\gradlew.bat clean build`。测试会连接配置中的 PostgreSQL，数据库不可达时测试会失败，但不影响 `assemble` 生成发布包。

打包后会生成外置依赖和外置配置的发布目录：

```text
RentBook/build/distribution/RentBook/
  RentBook.jar
  bin/
  lib/
  config/
```

同时会生成部署压缩包：

```text
RentBook/build/distribution/RentBook-deploy.zip
```

其中：

- `RentBook.jar`：只包含业务代码和必要资源，例如 Flyway 迁移 SQL。
- `bin/`：Linux 启动、停止、重启、状态脚本。
- `lib/`：所有运行依赖 jar，外置。
- `config/`：`application.yml`、`application-local.yml`、`application-test.yml`、`application-prod.yml`，外置。

部署时复制整个 `build/distribution/RentBook/` 目录即可。Linux 环境进入该目录后运行：

```bash
chmod +x bin/*.sh
./bin/start.sh
./bin/status.sh
./bin/stop.sh
./bin/restart.sh
```

如需修改端口、数据库、日志、跨域等配置，直接改 `config/` 目录下的配置文件，不需要重新打包。

脚本可用环境变量覆盖运行参数：

```bash
SPRING_PROFILE=prod JAVA_OPTS="-Xms256m -Xmx512m" ./bin/start.sh
```

## 前端

本地启动：

```powershell
cd C:\Users\liu\Documents\Projects\RentBook\RentBookWeb
pnpm install
pnpm dev
```

默认访问：

```text
http://localhost:5173
```

前端后端地址不在页面上展示和修改，统一使用外置配置：

```text
RentBookWeb/public/config/app-config.json
```

打包后对应文件在：

```text
RentBookWeb/dist/config/app-config.json
```

部署时修改 `apiBaseUrl` 即可，不需要重新打包：

```json
{
  "apiBaseUrl": "http://localhost:8080"
}
```

前端打包：

```powershell
pnpm build
```

打包结果：

```text
RentBookWeb/dist/
```

## 数据库

数据库表结构由 Flyway 自动迁移，迁移文件在：

```text
RentBook/src/main/resources/db/migration/
```

当前已包含房源、房间、租客、合同、收租流水、提醒事项等表，并已添加表和字段注释。

## 中间件

当前版本暂不需要 Redis、MQ。现阶段 PostgreSQL 足够承载核心业务数据和一致性要求。
