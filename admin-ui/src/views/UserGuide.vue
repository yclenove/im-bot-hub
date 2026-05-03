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
        <span class="user-guide-badge" aria-hidden="true">IM</span>
        <div>
          <div class="user-guide-product">使用说明</div>
          <div class="user-guide-tagline">IM Bot Hub — 通用 IM 查询机器人配置中心</div>
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
            <strong>IM Bot Hub</strong> 是通用 IM 查询机器人配置中心，支持 <strong>Telegram / 飞书 / 钉钉 / 企业微信 / Slack / Discord</strong> 六大平台。
            通过<strong>机器人 → 渠道 → 数据源 → 查询定义</strong>的配置流程，即可在多个 IM 平台实现数据查询。
          </p>

          <section id="overview" class="user-guide-section">
            <h2>1. 概览</h2>
            <p>登录后首先进入<strong>概览</strong>页面，可查看系统整体状态：</p>
            <ul>
              <li><strong>机器人总数</strong>：已创建的机器人数量及启用数</li>
              <li><strong>渠道总数</strong>：所有平台渠道数量</li>
              <li><strong>查询定义</strong>：已配置的查询命令数量</li>
              <li><strong>今日命令</strong>：今日执行的命令总数及成功/失败数</li>
              <li><strong>7 天趋势</strong>：最近 7 天命令执行趋势（成功/失败柱状图）</li>
              <li><strong>渠道分布</strong>：各平台渠道数量一览</li>
              <li><strong>最近命令</strong>：最近 5 条命令执行记录</li>
            </ul>
          </section>

          <section id="bots" class="user-guide-section">
            <h2>2. 机器人</h2>
            <p>机器人是<strong>逻辑分组单元</strong>，不含任何平台专属配置。一个机器人可关联多个渠道。</p>
            <ul>
              <li><strong>新建机器人</strong>：只需填写名称和启用状态</li>
              <li><strong>详情</strong>：查看关联渠道、查询定义、最近命令日志</li>
              <li><strong>编辑机器人</strong>：修改名称、启用/禁用</li>
              <li><strong>删除机器人</strong>：会同时删除关联的渠道和查询定义</li>
              <li><strong>导出配置</strong>：导出所有机器人配置为 JSON 文件</li>
            </ul>
            <p class="user-guide-tip">💡 建议按业务场景创建机器人，例如"订单查询机器人"、"库存查询机器人"。</p>
          </section>

          <section id="channels" class="user-guide-section">
            <h2>3. 渠道管理</h2>
            <p>渠道是<strong>平台接入单元</strong>，每个渠道对应一个 IM 平台的接入配置。</p>

            <h3>3.1 支持平台</h3>
            <el-table :data="[
              { platform: 'Telegram', auth: 'Bot Token', webhook: '自动注册' },
              { platform: '飞书', auth: 'App ID + App Secret', webhook: '手动配置' },
              { platform: '钉钉', auth: 'AppSecret', webhook: '手动配置' },
              { platform: '企业微信', auth: 'CorpID + AgentId + Token + AES Key', webhook: '手动配置' },
              { platform: 'Slack', auth: 'Bot Token', webhook: '手动配置' },
              { platform: 'Discord', auth: 'Bot Token', webhook: '手动配置' }
            ]" style="width: 100%">
              <el-table-column prop="platform" label="平台" width="120" />
              <el-table-column prop="auth" label="认证方式" min-width="200" />
              <el-table-column prop="webhook" label="Webhook 配置" width="120" />
            </el-table>

            <h3>3.2 创建渠道</h3>
            <ol>
              <li>点击<strong>新建渠道</strong>按钮</li>
              <li>选择<strong>机器人</strong>（下拉框）</li>
              <li>选择<strong>平台</strong>（Telegram/飞书/钉钉/企业微信/Slack/Discord）</li>
              <li>填写平台专属凭证（如 Bot Token、App Secret 等）</li>
              <li>点击<strong>创建</strong></li>
            </ol>

            <h3>3.3 渠道操作</h3>
            <ul>
              <li><strong>详情</strong>：查看渠道信息、Webhook URL、凭证摘要</li>
              <li><strong>禁用/启用</strong>：临时禁用渠道而不删除</li>
              <li><strong>删除</strong>：删除渠道（需确认）</li>
            </ul>

            <h3>3.4 Webhook 配置</h3>
            <ul>
              <li><strong>Telegram</strong>：系统自动注册 Webhook，无需手动配置</li>
              <li><strong>其他平台</strong>：需将 Webhook URL 手动配置到对应平台后台</li>
            </ul>
            <p class="user-guide-tip">💡 Webhook URL 格式：<code>https://公网域名/api/webhook/{platform}/{channelId}</code></p>

            <h3>3.5 连通性测试</h3>
            <p>在渠道详情对话框中，可发送<strong>连通性测试</strong>消息验证渠道配置是否正确：</p>
            <ul>
              <li><strong>Telegram / Slack / Discord</strong>：可留空目标 ID 仅验证 Token，或填写目标发送测试消息</li>
              <li><strong>飞书</strong>：需填写 open_id 或 chat_id</li>
              <li><strong>钉钉 / 企业微信</strong>：仅验证凭证格式</li>
            </ul>
          </section>

          <section id="datasources" class="user-guide-section">
            <h2>4. 数据源</h2>
            <p>数据源定义查询的数据来源，支持<strong>数据库</strong>和<strong>API</strong>两种类型。</p>

            <h3>4.1 数据库数据源</h3>
            <ul>
              <li>建议使用<strong>只读</strong>账号</li>
              <li>支持简易表单或自定义 JDBC</li>
              <li>保存前可<strong>测试连接</strong></li>
            </ul>

            <h3>4.2 API 数据源</h3>
            <ul>
              <li>填写第三方 <strong>Base URL</strong></li>
              <li>配置鉴权（无 / Bearer / Basic / API Key 等）</li>
              <li>可选默认 Header、Query 参数</li>
              <li>保存前可<strong>测试连接</strong></li>
            </ul>
          </section>

          <section id="queries" class="user-guide-section">
            <h2>5. 查询定义</h2>
            <p>查询定义绑定到机器人，定义用户可执行的命令。支持<strong>导出/导入</strong>查询定义。</p>

            <h3>5.1 查询模式</h3>
            <ul>
              <li><strong>向导模式</strong>（仅数据库源）：选表、结果列、可选多列 OR 关键词、固定条件等</li>
              <li><strong>高级 SQL</strong>（仅数据库源）：手写 SELECT 模板，占位符 <code>#{参数名}</code></li>
              <li><strong>API 可视化</strong>（仅 API 源）：先选预制模板或自填路径，用「预览」拉真实 JSON，再点选/拖拽要返回的字段</li>
            </ul>

            <h3>5.2 命令菜单</h3>
            <p>保存/更新/删除已启用查询后，系统会自动同步命令菜单（Telegram 使用 <code>setMyCommands</code>）。</p>

            <h3>5.3 测试</h3>
            <p>列表行内或抽屉内可带参数试跑（不经 IM 平台）。</p>

            <h3>5.4 导出/导入</h3>
            <ul>
              <li><strong>导出</strong>：将所有查询定义导出为 JSON 文件</li>
              <li><strong>导入</strong>：从 JSON 文件导入查询定义（追加模式，不覆盖已有）</li>
            </ul>
          </section>

          <section id="allowlist" class="user-guide-section">
            <h2>6. 白名单</h2>
            <p>控制哪些用户可以使用查询命令，支持<strong>多平台</strong>和<strong>批量添加</strong>。</p>
            <ul>
              <li>若列表<strong>为空</strong>，所有用户可使用命令</li>
              <li>若<strong>有记录</strong>，仅列表中的用户 ID 可用</li>
              <li>支持<strong>单个添加</strong>或<strong>批量添加</strong>（逗号/换行分隔）</li>
              <li>可关联<strong>渠道</strong>，实现按平台控制白名单</li>
            </ul>
          </section>

          <section id="logs" class="user-guide-section">
            <h2>7. 命令日志</h2>
            <p>查看所有平台的命令执行记录，支持多维度筛选。</p>
            <ul>
              <li>支持按<strong>机器人</strong>、<strong>平台</strong>、<strong>时间</strong>、<strong>命令</strong>、<strong>结果类型</strong>筛选</li>
              <li>支持按<strong>用户 ID</strong>、<strong>会话 chat_id</strong> 筛选</li>
              <li>支持导出当前页 CSV</li>
              <li>记录命令执行结果，不含业务参数明文</li>
            </ul>
          </section>

          <section id="audit" class="user-guide-section">
            <h2>8. 审计日志</h2>
            <p>记录管理端的所有操作（创建、修改、删除等），用于安全审计。</p>
          </section>

          <section id="settings" class="user-guide-section">
            <h2>9. 系统设置</h2>
            <p>配置系统级参数：</p>
            <ul>
              <li><strong>公网基址</strong>：用于生成 Webhook URL（HTTPS）</li>
              <li><strong>默认查询超时</strong>：查询执行超时时间（毫秒）</li>
              <li><strong>默认最大行数</strong>：查询结果最大返回行数</li>
              <li><strong>加密状态</strong>：显示当前凭证加密配置状态（只读）</li>
            </ul>
          </section>

          <section id="tips" class="user-guide-section">
            <h2>10. 使用提示</h2>
            <ul>
              <li><strong>Webhook 地址</strong>必须为 HTTPS，本地开发可用 ngrok / cloudflared 暴露后端端口（默认 <code>18089</code>）</li>
              <li><strong>Telegram 群内</strong>有多台机器人时，建议使用 <code>/命令@你的机器人用户名</code></li>
              <li><strong>出站代理</strong>：若本机无法访问 IM 平台 API（如国内网络访问 Telegram），需配置代理</li>
              <li><strong>加密存储</strong>：配置 <code>app.encryption.secret-key-base64</code> 后，渠道凭证会加密存储</li>
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
  padding: 0 24px;
  height: 64px;
  background: var(--admin-header-bg, #141720);
  border-bottom: 1px solid var(--admin-border, #1e2230);
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
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  color: white;
  font-weight: 700;
  font-size: 14px;
  border-radius: 8px;
}

.user-guide-product {
  font-size: 18px;
  font-weight: 600;
}

.user-guide-tagline {
  font-size: 13px;
  color: #8b8fa3;
}

.user-guide-header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-guide-main {
  padding: 0;
}

.user-guide-inner {
  max-width: 900px;
  margin: 0 auto;
  padding: 32px 24px;
}

.user-guide-lead {
  font-size: 15px;
  line-height: 1.8;
  color: #c8ccd8;
  margin-bottom: 32px;
  padding: 16px 20px;
  background: var(--admin-card-bg, #141720);
  border-radius: 8px;
  border-left: 4px solid #3b82f6;
}

.user-guide-section {
  margin-bottom: 32px;
}

.user-guide-section h2 {
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--admin-border, #1e2230);
}

.user-guide-section h3 {
  font-size: 16px;
  font-weight: 600;
  margin-top: 20px;
  margin-bottom: 12px;
  color: #c8ccd8;
}

.user-guide-section p {
  font-size: 14px;
  line-height: 1.8;
  color: #a0a4b8;
  margin-bottom: 12px;
}

.user-guide-section ul,
.user-guide-section ol {
  font-size: 14px;
  line-height: 1.8;
  color: #a0a4b8;
  padding-left: 24px;
  margin-bottom: 12px;
}

.user-guide-section li {
  margin-bottom: 8px;
}

.user-guide-section code {
  background: var(--admin-code-bg, #1e2230);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
  color: #e6e8ee;
}

.user-guide-tip {
  background: var(--admin-tip-bg, #1a2332);
  padding: 12px 16px;
  border-radius: 6px;
  border-left: 4px solid #10b981;
  margin-top: 12px;
}
</style>
