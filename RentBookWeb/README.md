# RentBookWeb

小胡收租簿前端项目，使用 Vite 构建，可和后端分开打包部署。

## 安装依赖

```powershell
pnpm install
```

## 本地启动

```powershell
pnpm dev
```

默认访问：

```text
http://localhost:5173
```

`pnpm dev` 默认使用本地配置：

```text
config/app-config.local.json
```

## 环境配置

前端构建前会把对应环境配置复制到：

```text
public/config/app-config.json
```

Vite 打包后该文件会进入：

```text
dist/config/app-config.json
```

当前提供三套环境：

```text
config/app-config.local.json
config/app-config.test.json
config/app-config.prod.json
```

配置内容示例：

```json
{
  "apiBaseUrl": "http://localhost:8080",
  "rentCollectAdvanceDays": 7
}
```

部署后也可以直接修改 `dist/config/app-config.json`，不用重新打包。

## 构建

默认生产构建：

```powershell
pnpm build
```

指定环境构建：

```powershell
pnpm build:local
pnpm build:test
pnpm build:prod
```

指定环境启动开发服务：

```powershell
pnpm dev:test
pnpm dev:prod
```

打包结果在：

```text
dist/
```
