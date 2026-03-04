# 新闻发布中心 · Multi-Platform Hub

多平台内容创作与分发系统，支持今日头条（含西瓜视频）、百度百家号等平台的账号绑定、图文/视频一键发布与数据统计。

---

## 功能概览

| 模块 | 说明 |
|------|------|
| **控制大盘** | 发布统计、任务概览 |
| **内容创作** | 富文本编辑、多平台分发设置、一键同步发布 |
| **视频创作** | 选择视频文件，按渠道勾选账号，一键发布到头条/百家号（含封面、标题等） |
| **热点资讯** | 热点抓取与筛选 |
| **账号管理** | 平台账号绑定（扫码登录）、多账号、本地文件存储；昨日/总粉丝·阅读·收益展示与后台更新 |
| **分发渠道** | 按平台展示已绑定账号，勾选要发布的账号；仅向勾选账号发布 |
| **我的发文** | 发布任务列表与状态 |
| **AI 设置** | 大模型接口配置、标题/摘要/标签等辅助 |
| **合规中心** | 敏感词管理 |

---

## 技术栈

- **后端**：Java 17、Spring Boot 3.2、MySQL（可选，账号/文章/任务可仅用本地文件）
- **前端**：Vue 3、TypeScript、Vite、Element Plus、Pinia
- **自动化**：Python 3、Playwright；按平台技能（头条/百家号）执行登录、图文发布、视频发布、数据拉取

---

## 项目结构

```
├── java-work/                 # 后端（Spring Boot）
│   ├── src/main/java/         # 控制器、服务、适配器、自动化调用
│   └── src/main/resources/    # 配置；static/ 为前端构建产物（可忽略提交）
├── vue_view/                  # 前端（Vue 3 + Vite）
│   ├── src/views/             # 页面：创作、视频、账号、渠道、任务等
│   └── scripts/               # 构建后复制到 java-work 的 static
├── skills/                    # Python 自动化技能
│   ├── agent_master.py        # 统一入口，按 platform 分发
│   ├── core/                  # Token 存储、Cookie 注入、浏览器上下文
│   └── platforms/             # 各平台实现
│       ├── toutiao/           # 头条：登录、图文发布、视频发布、数据拉取
│       └── baijiahao/         # 百家号：登录、图文发布、数据拉取
└── README.md
```

---

## 快速开始

### 环境要求

- JDK 17+
- Node.js 18+、npm
- Python 3.8+（用于 skills）
- MySQL 8+（若使用数据库；仅平台/用户等表时可保留）

### 1. 后端

```bash
cd java-work
# 配置 application.yml 中的数据库等（可选）
mvn spring-boot:run
```

默认端口：**8080**。

### 2. 前端开发

```bash
cd vue_view
npm install
npm run dev
```

访问 http://localhost:3000，开发时通过 Vite 代理请求后端 `/api`。

### 3. 打包为单体（前端嵌入后端）

```bash
cd vue_view
npm run build:desktop   # 构建并复制到 java-work/src/main/resources/static
cd ../java-work
mvn package
java -jar target/publish-center-*.jar
```

访问 http://localhost:8080 即为嵌入的前端页面。

### 4. Python 技能（自动化）

- 安装依赖：`pip install playwright`，并执行 `playwright install chromium`
- 技能由 Java 通过 `PythonSkillRunner` 调用 `skills/agent_master.py`，传入 `platform`、`command`、`account_id` 等 JSON

---

## 配置说明

- **账号与数据**：账号、文章、发布任务、账号统计等可仅存于本地文件（`~/.news-publisher/`），无需数据库。
- **技能路径**：可在 `application.yml` 或环境变量中配置 `app.skills-path`，指向本仓库下的 `skills` 目录。

---

## 开源与免责

本项目仅供学习与内部使用。使用各平台自动化能力时，请遵守平台服务条款与 robots 规范；账号与内容安全由使用者自行负责。

---

## License

MIT
