# 桌面版（WebView）

以后端为主进程，启动后自动打开一个桌面窗口，用 WebView 加载前端界面，无需单独打开浏览器。

## 1. 打包前端

在项目根目录下执行：

```bash
cd vue_view
npm run build:desktop
```

会将 Vue 构建产物复制到 `java-work/src/main/resources/static/`。

## 2. 运行方式

直接运行主程序 **`PublishCenterApplication`** 即可：先起后端，再自动弹出 WebView 窗口。

- **IDE**：运行 `com.news.publish.PublishCenterApplication`。
- **Maven**：`cd java-work` 后执行 `mvn spring-boot:run`。
- **打包后**：`mvn package` 得到 JAR，执行 `java -jar target/xxx.jar`。

## 3. 登录状态保持

桌面版关闭后再次打开时，WebView 是全新进程，本地存储会清空。程序已做**会话恢复**：登录成功后会将会话保存到本机（用户目录下的 `.news-publisher/session.json`），下次启动时自动从该文件恢复，无需重新登录。
