<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api/client'

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  sql?: string
  confidence?: number
  result?: Record<string, unknown>[]
}

const props = defineProps<{ botId: number; datasourceId: number }>()

const messages = ref<ChatMessage[]>([])
const input = ref('')
const loading = ref(false)

async function sendMessage() {
  if (!input.value.trim() || loading.value) return

  const question = input.value.trim()
  input.value = ''

  // 添加用户消息
  messages.value.push({ role: 'user', content: question })

  // 滚动到底部
  scrollToBottom()

  loading.value = true
  try {
    // 调用 NL2SQL API
    const { data } = await api.post('/admin/ai/nl2sql', {
      question,
      datasourceId: props.datasourceId,
      botId: props.botId,
    })

    // 添加 AI 回复
    messages.value.push({
      role: 'assistant',
      content: `已生成 SQL 查询（置信度: ${data.confidence.toFixed(1)}%）`,
      sql: data.generatedSql,
      confidence: data.confidence,
    })

    // 自动执行查询
    try {
      const { data: result } = await api.post('/admin/ai/nl2sql/execute', {
        sql: data.generatedSql,
        datasourceId: props.datasourceId,
        params: [],
      })

      // 添加查询结果
      messages.value.push({
        role: 'assistant',
        content: `查询完成，返回 ${result.length} 条结果`,
        result: result,
      })
    } catch {
      messages.value.push({
        role: 'assistant',
        content: '查询执行失败，请检查 SQL 或参数',
      })
    }

    scrollToBottom()
  } catch (e: unknown) {
    const err = e as { response?: { data?: { message?: string } } }
    ElMessage.error(err?.response?.data?.message || 'NL2SQL 查询失败')
  } finally {
    loading.value = false
  }
}

function scrollToBottom() {
  setTimeout(() => {
    const container = document.querySelector('.chat-messages')
    if (container) {
      container.scrollTop = container.scrollHeight
    }
  }, 100)
}

function clearChat() {
  messages.value = []
}
</script>

<template>
  <div class="nl2sql-chat">
    <div class="chat-header">
      <h4>AI 自然语言查询</h4>
      <el-button size="small" @click="clearChat">清空对话</el-button>
    </div>

    <div class="chat-messages">
      <div v-if="messages.length === 0" class="chat-empty">
        <p>试试问：</p>
        <ul>
          <li>查询所有启用的机器人</li>
          <li>统计今天的命令数量</li>
          <li>查看最近的查询日志</li>
        </ul>
      </div>

      <div v-for="(msg, idx) in messages" :key="idx" :class="['chat-message', msg.role]">
        <div class="message-content">
          <div class="message-text">{{ msg.content }}</div>
          <div v-if="msg.sql" class="message-sql">
            <pre><code>{{ msg.sql }}</code></pre>
          </div>
          <div v-if="msg.result && msg.result.length > 0" class="message-result">
            <el-table :data="msg.result" size="small" max-height="300" border>
              <el-table-column
                v-for="(_, key) in msg.result[0]"
                :key="String(key)"
                :prop="String(key)"
                :label="String(key)"
                min-width="120"
              />
            </el-table>
          </div>
        </div>
      </div>

      <div v-if="loading" class="chat-message assistant">
        <div class="message-content">
          <div class="message-text">AI 正在思考...</div>
        </div>
      </div>
    </div>

    <div class="chat-input">
      <el-input
        v-model="input"
        placeholder="用自然语言描述你的查询需求..."
        :disabled="loading"
        @keyup.enter="sendMessage"
      >
        <template #append>
          <el-button :loading="loading" @click="sendMessage">发送</el-button>
        </template>
      </el-input>
    </div>
  </div>
</template>

<style scoped>
.nl2sql-chat {
  display: flex;
  flex-direction: column;
  height: 500px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  overflow: hidden;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: #f5f7fa;
  border-bottom: 1px solid #dcdfe6;
}

.chat-header h4 {
  margin: 0;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.chat-empty {
  text-align: center;
  color: #909399;
  padding: 40px 0;
}

.chat-empty ul {
  list-style: none;
  padding: 0;
}

.chat-empty li {
  cursor: pointer;
  padding: 8px;
  margin: 4px 0;
  background: #f5f7fa;
  border-radius: 4px;
}

.chat-empty li:hover {
  background: #ecf5ff;
}

.chat-message {
  margin-bottom: 16px;
}

.chat-message.user .message-content {
  background: #ecf5ff;
  margin-left: 40px;
}

.chat-message.assistant .message-content {
  background: #f5f7fa;
  margin-right: 40px;
}

.message-content {
  padding: 12px;
  border-radius: 8px;
}

.message-text {
  margin-bottom: 8px;
}

.message-sql {
  margin-top: 8px;
}

.message-sql pre {
  margin: 0;
  padding: 12px;
  background: #1e1e1e;
  color: #d4d4d4;
  border-radius: 4px;
  overflow-x: auto;
}

.message-sql code {
  font-family: 'Fira Code', monospace;
  font-size: 13px;
}

.message-result {
  margin-top: 12px;
}

.chat-input {
  padding: 12px;
  border-top: 1px solid #dcdfe6;
  background: #f5f7fa;
}
</style>
