# AIRecharge

基于 Orion Key 二次开发的数字商品商城，前后端在同一仓库管理。

## 项目结构

- `apps/web`：Next.js 16、React 19、TypeScript 商城与管理后台。
- `apps/api`：Java 22、Spring Boot 3.4、JPA 后端。
- `docs`：实施计划与验证说明。

## 当前功能

- 商城：真实商品、规格、搜索筛选、购物车、游客与会员订单、卡密交付。
- 响应式布局，访客可选择语言和亮色/暗色/跟随系统。
- 经营工作台：今天/昨天/近7天/近30天/上月/自定义付款日期，渠道筛选、上一周期比较；点击趋势查看当天订单，明细支持搜索、状态筛选、分页、勾选合计及CSV导出。
- 对账导出：整个筛选范围最多10000笔，勾选导出最多100笔；含渠道支付单号、上海时区付款时间及CNY金额。渠道到账与手续费目前明确为尚未核对。
- 石墨灰后台暗色布局，保留亮色、语言选择、真实低库存提醒和订单详情。
- 站点外观：后台统一配色、电脑和手机横幅、横幅开关、标题、说明和跳转链接。
- 支付：保留易支付接入；真实商户参数需要单独配置。原生微信/支付宝仍未实现。
- 已移除 USDT 和积分业务入口；保留数据库历史字段，不做破坏性数据清理。

## 本地开发

准备 Java 22、Maven 3.9+、Node.js 22、pnpm 10.29.3、PostgreSQL。后端配置见 `apps/api/src/main/resources/application.yml` 和 `.env.example`。通过环境变量设置数据库、JWT 和邮件配置，不要提交真实凭据。

```powershell
# 后端：在当前终端设置 DB_URL、DB_USERNAME、DB_PASSWORD、JWT_SECRET 等变量
# 本地不发邮件时使用 MAIL_ENABLED=false；密码使用 PASSWORD_PLAIN=false
mvn -f apps/api/pom.xml spring-boot:run

# 前端：另一个终端，项目根目录
pnpm install --frozen-lockfile
pnpm dev:web
```

前端默认代理到 `http://localhost:8083`；可通过 `BACKEND_URL` 指定后端地址。数据库表由现有 JPA 配置创建；初始化数据请检查 `data.sql`，按自己的管理员账号与密码模式调整后执行。

本机已有 `start-local.cmd` / `stop-local.cmd` 辅助脚本，依赖本机 `.local` 内的运行环境。`.local`、数据库、上传文件、登录信息和构建产物不进入 Git，新克隆环境使用上面的标准开发方式。

## 验证

```powershell
mvn -B -ntp -f apps/api/pom.xml -Dspring.mail.host=127.0.0.1 -Dspring.mail.username=ci@example.invalid -Dmail.enabled=false package
pnpm build:web
pnpm --filter @orion-key/web exec tsc --noEmit
```

`verify.yml` 在 push main / Pull Request 时执行前后端验证，不部署网站。原有镜像工作流仅在版本标签或手动触发时运行；服务器发布需要另行配置并明确触发。

## Git 管理

业务代码、测试、字体许可和站点静态资源在本仓库提交。真实账号、密钥、数据库和运行数据保存在部署环境，不随代码上传。

建议后续需求从 `feature/功能名` 分支开发，经测试后合并到 `main`。提交信息使用 `feat(web):...`、`fix(api):...` 等格式。

## 开源来源

基于 [RivenLau/orion-key](https://github.com/RivenLau/orion-key)，基线提交 `217ed2e`。保留原项目 MIT 许可证，见 [LICENSE](LICENSE)。Inter 字体使用 SIL Open Font License，见 `apps/web/public/fonts/Inter-LICENSE.txt`。上游演示截图与赞助宣传素材不纳入二开仓库版本管理。
