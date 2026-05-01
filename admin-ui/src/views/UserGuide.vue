<script setup lang="ts">
import { useRouter } from 'vue-router'
import { clearCredentials } from '../auth/session'

const router = useRouter()

function logout() {
  clearCredentials()
  void router.replace('/login')
}
</script>

<template>
  <el-container class="user-guide-shell">
    <el-header class="user-guide-header">
      <div class="user-guide-header-title">
        <span class="user-guide-badge" aria-hidden="true">TG</span>
        <div>
          <div class="user-guide-product">使用说明</div>
          <div class="user-guide-tagline">管理端操作与 Telegram 侧注意事项</div>
        </div>
      </div>
      <div class="user-guide-header-actions">
        <el-button text type="primary" @click="router.push('/')">返回工作台</el-button>
        <el-link href="/swagger-ui/index.html" target="_blank" type="primary">API 文档</el-link>
        <el-button plain @click="logout">退出</el-button>
      </div>
    </el-header>
    <el-main class="user-guide-main">
      <el-scrollbar max-height="calc(100vh - 64px)">
        <div class="user-guide-inner">
          <p class="user-guide-lead">
            本文说明如何在<strong>本管理端</strong>完成配置（含<strong>数据库 + API</strong>双数据源与<strong>SQL / 向导 / API</strong>三种查询方式）。服务器部署、Webhook 与命令菜单细节另见仓库
            <code>docs/</code> 下 <code>TELEGRAM-傻瓜配置.md</code>（全流程与 Webhook）、<code>DEPLOY-傻瓜部署.md</code> 等。
          </p>

          <section id="login" class="user-guide-section">
            <h2>1. 登录</h2>
            <p>
              使用在 <code>application.yml</code> / 生产配置中的 <code>app.security.admin</code> 账号密码（HTTP Basic）。登录状态保存在浏览器本地，退出后即需重新登录。
            </p>
          </section>

          <section id="ds" class="user-guide-section">
            <h2>2. 数据源</h2>
            <ul>
              <li>
                <strong>数据库</strong>：建议使用<strong>只读</strong>账号；支持简易表单或自定义 JDBC；保存前可<strong>测试连接</strong>。
              </li>
              <li>
                <strong>API</strong>：填写第三方 <strong>Base URL</strong>、鉴权（无 / Bearer / Basic / API Key 等）、可选默认 Header、Query
                与健康检查路径；保存前可<strong>测试连接</strong>。创建查询时仅能选「API 可视化」模式。
              </li>
            </ul>
          </section>

          <section id="bots" class="user-guide-section">
            <h2>3. 机器人</h2>
            <ul>
              <li>在 BotFather 创建机器人后，将 <strong>HTTP API Token</strong> 粘贴保存；列表中的 <strong>ID</strong> 即 Webhook 路径里的 <code>botId</code>。</li>
              <li><strong>Webhook 密钥</strong>：若你在 Telegram <code>setWebhook</code> 时设置了 <code>secret_token</code>，需在此填写一致值。</li>
              <li>
                <strong>接收范围</strong>：选「仅指定群」时，只有填写的 Telegram
                <code>chat_id</code> 群会执行查询，<strong>私聊会被静默忽略</strong>；每行一个群 ID（多为负数，如 <code>-100…</code>）。
              </li>
              <li><strong>注册 Webhook</strong>：填写公网 HTTPS 基址（如穿透域名），系统会向 Telegram 调用 <code>setWebhook</code>。</li>
              <li><strong>Webhook 状态</strong>：查看 Telegram 当前登记的 URL 与待处理更新数等。</li>
            </ul>
          </section>

          <section id="queries" class="user-guide-section">
            <h2>4. 查询定义</h2>
            <p>在<strong>查询定义</strong>标签选中机器人后新建/编辑；右侧抽屉内选择配置方式（随<strong>数据源类型</strong>自动约束）。</p>
            <ul>
              <li>
                <strong>向导（仅数据库源）</strong>：选表、结果列、可选多列 OR 关键词、固定条件等；保存后服务端生成 SQL 并<strong>覆盖同步</strong>该查询的字段映射。
              </li>
              <li>
                <strong>高级 SQL（仅数据库源）</strong>：手写 <code>SELECT …</code> 模板，占位符 <code>#{参数名}</code>；参数与
                <code>{"params":["参数名",…]}</code> 顺序一致，用户发 <code>/命令 值1 值2</code> 按空格传入。
              </li>
              <li>
                <strong>API 可视化（仅 API 源）</strong>：先选预制模板或自填路径；用「预览」拉真实 JSON，再<strong>点选 / 拖拽</strong>要返回的字段；样例参数框用于预览与生成
                <code>param_schema_json</code> 中的 <code>examples</code>（与 Telegram 菜单里的「示例」一致）。
              </li>
              <li>
                <strong>apiConfigJson.name</strong>（API 查询）：建议填写简短中文名；保存查询后会用于 Telegram <strong>命令菜单</strong>副标题，避免多条查询共用一个数据源名时菜单文案重复。
              </li>
              <li><strong>Telegram 展现</strong>：控制回复排版（列表、代码块、分块等）。</li>
              <li>
                <strong>测试</strong>：列表行内或抽屉内可带参数试跑（不经 Telegram）。内置 <code>/help</code>、<code>/start</code>（无同名已启用命令时）列出当前机器人命令与参数占位。
              </li>
              <li>
                <strong>命令菜单</strong>：保存/更新/删除已启用查询后，系统会调用 Telegram <code>setMyCommands</code>。用户在聊天里输入
                <code>/</code> 看到的描述含「查询 · 名称」与示例；<strong>点菜单只会插入 <code>/命令</code>，不会自动带参数</strong>，需在输入框<strong>同一条消息</strong>里补全参数（仅发命令无参时会收到「用法」提示而非错误样式）。
              </li>
            </ul>
          </section>

          <section id="fields" class="user-guide-section">
            <h2>5. 字段映射</h2>
            <p>
              配置展示列顺序、标签、脱敏与格式（含从列注释解析枚举）。未配置时按结果集列名原样输出。若使用向导保存，映射可能被向导覆盖。
            </p>
          </section>

          <section id="allow" class="user-guide-section">
            <h2>6. 白名单</h2>
            <p>
              若列表<strong>为空</strong>，所有 Telegram 用户可使用命令；若<strong>有记录</strong>，仅列表中的
              <strong>用户 ID</strong> 可用（与群 Restrict 无关，按 Telegram 用户维度）。
            </p>
          </section>

          <section id="logs" class="user-guide-section">
            <h2>7. 查询日志</h2>
            <p>
              <strong>查询日志</strong>标签可按机器人、时间、命令、是否成功、结果类型、用户 ID、会话等筛选 Telegram 侧调用记录（不含业务参数明文），支持导出当前页 CSV，便于排障与审计。
            </p>
          </section>

          <section id="tg-tips" class="user-guide-section">
            <h2>8. Telegram 使用提示</h2>
            <ul>
              <li>Webhook 地址必须为 <strong>HTTPS</strong>，形如：<code>https://公网域名/api/webhook/&lt;机器人ID&gt;</code>；本地开发常用 ngrok / cloudflared 暴露后端端口（默认 <code>18089</code>，见 <code>application.yml</code>）。</li>
              <li>群内有多台机器人时，建议使用 <strong><code>/命令@你的机器人用户名</code></strong>，避免更新被派发到别的 Bot。</li>
              <li>支持 <strong><code>@机器人 /命令 参数</code></strong> 的写法（句首 @ 再写斜杠命令）。</li>
              <li>
                若本机<strong>出站</strong>无法访问 <code>api.telegram.org</code>（如国内网络），需在服务器或 JVM 上配置访问 Telegram API 的<strong>代理</strong>，否则消息能进、回复发不出。
              </li>
            </ul>
          </section>
        </div>
      </el-scrollbar>
    </el-main>
  </el-container>
</template>

<style scoped>
.user-guide-shell {
  min-height: 100svh;
  flex-direction: column;
  background: var(--admin-bg, #0b0d11);
  color: #e6e8ee;
}

.user-guide-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  height: 56px;
  border-bottom: 1px solid var(--admin-header-border, rgba(255, 255, 255, 0.06));
  background: var(--admin-surface, #12151c);
}

.user-guide-header-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-guide-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  font-weight: 700;
  font-size: 14px;
  background: var(--admin-accent-soft, rgba(124, 58, 237, 0.2));
  color: #c4b5fd;
}

.user-guide-product {
  font-size: 16px;
  font-weight: 600;
  line-height: 1.3;
}

.user-guide-tagline {
  font-size: 12px;
  color: var(--admin-muted, #8b92a5);
  margin-top: 2px;
}

.user-guide-header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-guide-main {
  padding: 0;
  overflow: hidden;
}

.user-guide-inner {
  max-width: 880px;
  margin: 0 auto;
  padding: 24px 20px 48px;
}

.user-guide-lead {
  color: #a8b0c0;
  font-size: 14px;
  line-height: 1.6;
  margin: 0 0 24px;
}

.user-guide-section {
  margin-bottom: 28px;
}

.user-guide-section h2 {
  font-size: 17px;
  font-weight: 600;
  margin: 0 0 12px;
  color: #e8eaf0;
  border-left: 3px solid #7c3aed;
  padding-left: 10px;
}

.user-guide-section ul {
  margin: 0;
  padding-left: 1.25rem;
  color: #c4c9d4;
  line-height: 1.75;
  font-size: 14px;
}

.user-guide-section p {
  margin: 0;
  color: #c4c9d4;
  line-height: 1.75;
  font-size: 14px;
}

.user-guide-section :deep(code) {
  font-family: ui-monospace, monospace;
  font-size: 0.92em;
  padding: 1px 6px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.06);
  color: #e2e8f0;
}

.user-guide-section li {
  margin-bottom: 8px;
}
</style>
