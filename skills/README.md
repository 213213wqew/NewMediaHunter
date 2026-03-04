# Skills 目录规范

## 职责分离

| 职责       | 位置与说明 |
|------------|------------|
| **登录**   | 仅负责：打开登录页、等待用户完成、取回 Cookie。每个平台在 `platforms/{平台}/login.py` 中实现。 |
| **存放 Token** | 仅负责：按「平台 + 账号」读写 Token/Cookie 文件。统一在 `core/token_store.py`，路径为 `tokens/{platform}/{account_id}.json`。 |
| **注入网页**   | 仅负责：把已有 Cookie/Token 注入到浏览器上下文。统一在 `core/inject.py`。 |

## 目录结构

```
skills/
├── README.md                 # 本说明
├── agent_master.py           # 统一入口：解析参数，按 platform 分发到 platforms/{platform}
├── core/                     # 与平台无关的公共能力
│   ├── __init__.py
│   ├── token_store.py        # Token 存取：tokens/{platform}/{account_id}.json
│   ├── inject.py             # 向 Playwright context 注入 Cookie
│   └── browser.py             # 创建持久化浏览器上下文（按 session_dir）
├── platforms/                # 按平台分目录，每平台可多账号（靠 account_id 区分）
│   ├── __init__.py
│   ├── baijiahao/
│   │   ├── __init__.py       # 暴露 execute()，内部调 login / publish
│   │   ├── config.py         # 登录/发布 URL、超时等配置
│   │   ├── login.py          # 仅登录流程，返回 Cookie
│   │   └── publish.py        # 仅发布流程（先注入再填表发文）
│   └── toutiao/
│       ├── __init__.py
│       ├── config.py
│       ├── login.py
│       └── publish.py
├── sessions/                 # 浏览器 Profile，按「平台 + 账号」隔离，便于多账号切换
│   ├── baijiahao/
│   │   ├── {account_id_1}/   # 账号 1 的登录态
│   │   └── {account_id_2}/
│   └── toutiao/
│       ├── {account_id_1}/
│       └── {account_id_2}/
├── tokens/                   # 本地 Token 文件（可选，与 DB 的 cookieData 可同步）
│   ├── baijiahao/
│   │   ├── {account_id_1}.json
│   │   └── {account_id_2}.json
│   └── toutiao/
│       └── ...
└── shared/                   # 其他公共依赖（如 ai_bridge）
```

## 多账号与切换注入

- **账号标识**：`account_id` 由调用方传入（如 DB 主键、或临时 `bind_xxx`），同一平台下不同账号用不同 `account_id`。
- **Session 目录**：`sessions/{platform}/{account_id}`，每个账号独立浏览器 Profile，互不串号。
- **Token 文件**：`tokens/{platform}/{account_id}.json`，发布时可按「当前选中的 platform + account_id」读取并注入，实现**按账号切换注入**。
- 发布流程：调用方指定 `platform` + `account_id`（及可选 `cookieJson`），Master 解析后使用对应 `session_dir` 和 Token，再调 `platforms/{platform}/publish.py` 注入并执行发布。

## 调用约定

- **agent_master.py** 从 stdin 读 JSON：`platform`、`account_id`（或 `accountId`）、`command`（如 `BIND_LOGIN`、`PUBLISH`）。
- `session_dir = skills_root/sessions/{platform}/{account_id}`，便于多账号隔离与切换注入。
- Token 读写只通过 `core.token_store`，注入只通过 `core.inject`；各平台模块不直接写文件或操作 Cookie 字典。
- 优先使用 **platforms/{platform}**（登录/Token/注入已拆分）；若无则回退到 **sub_skills/{platform}.py**（旧版单文件）。

## 可选忽略

- `skills/sessions/`、`skills/tokens/` 建议加入 `.gitignore`，避免提交本地登录态与 Token。
