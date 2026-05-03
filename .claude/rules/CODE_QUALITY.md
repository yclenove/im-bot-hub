# 代码质量规则 — im-bot-hub

> 本文件是项目的代码质量基线。所有新代码必须在提交前符合这些规则，无需事后优化。

---

## 一、架构原则

### 1.1 单一职责
- **Controller**：只做参数校验、调用 Service、返回 DTO，禁止业务逻辑
- **Service**：业务逻辑封装，一个 Service 只管一个领域（如 QueryOrchestrationService 只管查询派发）
- **Entity**：纯数据载体，禁止业务方法
- **DTO**：纯传输对象，禁止业务逻辑

### 1.2 依赖方向
```
Controller → Service → Mapper/Repository
     ↓           ↓
    DTO      Entity
```
- Controller 不直接调用 Mapper
- Service 之间可以互相调用，但禁止循环依赖
- Entity 不引用 DTO，DTO 不引用 Entity

### 1.3 平台解耦
- 所有 IM 平台共用同一套 Service 层
- 平台差异通过 `OutboundMessenger` 接口 + 策略模式处理
- 新增平台只需实现接口，不修改核心逻辑

---

## 二、Java 后端规则

### 2.1 命名规范
| 类型 | 规则 | 示例 |
|------|------|------|
| 类名 | PascalCase | `QueryOrchestrationService` |
| 方法名 | camelCase | `dispatch()`, `buildParamMap()` |
| 常量 | UPPER_SNAKE_CASE | `TELEGRAM_REPLY_STYLES` |
| 变量 | camelCase | `channelId`, `queryDefinition` |
| 包名 | 全小写 | `com.sov.imhub.service` |
| 数据库表 | t_ 前缀 + snake_case | `t_query_definition` |
| 数据库字段 | snake_case | `channel_scope_json` |

### 2.2 注释规范
```java
/**
 * 类级别 Javadoc：说明职责、与其他类的关系。
 *
 * @see RelatedClass
 */
@Service
public class XxxService {

    /**
     * 公共方法必须有 Javadoc。
     *
     * @param channelId 渠道 ID
     * @param rawBody   原始请求体
     * @return 处理结果，null 表示无需响应
     */
    public Result handle(long channelId, String rawBody) {
        // 复杂逻辑必须有行内注释说明意图
        // 不要注释「做了什么」，要注释「为什么这样做」
    }
}
```

**必须注释的场景**：
- 所有 public 方法
- 所有 Controller 类（类级别 Javadoc）
- 所有 Service 类（类级别 Javadoc）
- 复杂算法、正则表达式、魔法数字
- 不明显的业务规则

**禁止注释的场景**：
- getter/setter
- 构造函数
- 简单的赋值语句

### 2.3 异常处理
```java
// ✅ 正确：抛出业务异常，由全局处理器统一处理
throw new NotFoundException("channel not found");
throw new IllegalArgumentException("凭证不完整");

// ❌ 错误：catch 后吞掉异常
try {
    doSomething();
} catch (Exception e) {
    // 什么都不做
}

// ❌ 错误：返回 null 表示失败（应抛异常）
public Entity findById(Long id) {
    Entity e = mapper.selectById(id);
    if (e == null) return null;  // 应该 throw NotFoundException
    return e;
}
```

### 2.4 日志规范
```java
// ✅ 正确：使用参数化日志，不拼接字符串
log.info("dispatch start botId={} command={} platform={}", botId, command, platform);

// ❌ 错误：字符串拼接
log.info("dispatch start botId=" + botId + " command=" + command);

// ❌ 错误：日志泄露敏感信息
log.info("token={}", token);  // 禁止！
log.info("password={}", password);  // 禁止！
```

**日志级别**：
- `ERROR`：系统错误，需要人工介入（如数据库连接失败）
- `WARN`：业务异常，可自动恢复（如用户输入错误、外部 API 超时）
- `INFO`：关键业务节点（如查询执行成功、菜单同步完成）
- `DEBUG`：调试信息，生产环境关闭（如请求参数、响应内容）

### 2.5 代码行数限制
| 文件类型 | 建议上限 | 硬性上限 |
|---------|---------|---------|
| Controller | 200 行 | 300 行 |
| Service | 300 行 | 500 行 |
| Entity | 100 行 | 150 行 |
| DTO | 80 行 | 120 行 |

超过上限必须拆分。

### 2.6 方法行数限制
- 单个方法不超过 **50 行**
- 超过 30 行考虑提取子方法
- 嵌套层级不超过 **3 层**

### 2.7 集合与流处理
```java
// ✅ 正确：使用 Stream API 处理集合
List<String> names = entities.stream()
        .filter(Entity::getEnabled)
        .map(Entity::getName)
        .collect(Collectors.toList());

// ❌ 错误：for 循环 + 临时变量
List<String> names = new ArrayList<>();
for (Entity e : entities) {
    if (e.getEnabled()) {
        names.add(e.getName());
    }
}
```

### 2.8 字符串处理
```java
// ✅ 正确：使用 StringUtils 或 Objects 工具类
if (!StringUtils.hasText(command)) { ... }
if (Objects.isNull(entity)) { ... }

// ❌ 错误：手动判断
if (command == null || command.isEmpty() || command.trim().isEmpty()) { ... }
```

### 2.9 时间处理
```java
// ✅ 正确：使用 Java 8+ 时间 API
LocalDateTime now = LocalDateTime.now();
Instant instant = Instant.now();

// ❌ 错误：使用 Date 类
Date now = new Date();
```

---

## 三、Vue/TypeScript 前端规则

### 3.1 组件规范
```vue
<script setup lang="ts">
// 1. 导入
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'

// 2. Props & Emits
const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{ 'update:visible': [val: boolean] }>()

// 3. 响应式状态
const loading = ref(false)
const data = ref<DataType | null>(null)

// 4. 计算属性
const filteredData = computed(() => data.value?.filter(...))

// 5. 方法
async function loadData() { ... }

// 6. 监听器
watch(() => props.visible, (val) => {
  if (val) loadData()
})
</script>

<template>
  <!-- 使用 Element Plus 组件 -->
</template>

<style scoped>
/* 组件样式 */
</style>
```

### 3.2 命名规范
| 类型 | 规则 | 示例 |
|------|------|------|
| 组件文件 | PascalCase.vue | `ChannelDetailDialog.vue` |
| 组件名 | PascalCase | `ChannelDetailDialog` |
| 变量/函数 | camelCase | `loadData`, `handleSubmit` |
| 常量 | UPPER_SNAKE_CASE | `API_BASE_URL` |
| 类型 | PascalCase | `BotChannelRow`, `QueryDefinition` |
| CSS 类 | kebab-case | `.channel-detail-dialog` |

### 3.3 组件拆分原则
- 单个 `.vue` 文件不超过 **300 行**
- 超过 300 行必须拆分子组件
- 超过 500 行的组件是上帝组件，必须重构

### 3.4 TypeScript 规范
```typescript
// ✅ 正确：定义接口类型
interface ChannelRow {
  id: number
  botId: number
  platform: string
  enabled: boolean
}

// ❌ 错误：使用 any
const data: any = await api.get(...)

// ✅ 正确：泛型约束
const { data } = await api.get<ChannelRow[]>('/admin/channels')

// ❌ 错误：类型断言滥用
const data = response.data as ChannelRow  // 应该用泛型
```

### 3.5 API 调用规范
```typescript
// ✅ 正确：统一使用 api client，带错误处理
async function loadData() {
  loading.value = true
  try {
    const { data } = await api.get<ResponseType>('/api/endpoint')
    result.value = data
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

// ❌ 错误：直接使用 fetch/axios
const response = await fetch('/api/endpoint')
```

### 3.6 Element Plus 使用规范
```vue
<!-- ✅ 正确：使用中文标签 -->
<el-button type="primary" :loading="loading" @click="handleSubmit">
  提交
</el-button>

<!-- ❌ 错误：英文标签 -->
<el-button type="primary" :loading="loading" @click="handleSubmit">
  Submit
</el-button>
```

### 3.7 状态管理
```typescript
// ✅ 正确：组件内状态用 ref/reactive
const visible = ref(false)
const form = reactive({ name: '', enabled: true })

// ❌ 错误：全局变量
window.globalData = { ... }
```

---

## 四、数据库规则

### 4.1 表结构规范
```sql
-- ✅ 正确：t_ 前缀，snake_case 命名
CREATE TABLE t_query_definition (
    id BIGINT NOT NULL AUTO_INCREMENT,
    bot_id BIGINT NOT NULL,
    command VARCHAR(64) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_bot_id (bot_id)
);
```

### 4.2 Flyway 规范
- 版本号递增：V1, V2, V3, ...
- 每次迁移一个文件，文件名格式：`V{N}__{description}.sql`
- 描述使用英文，单词间用下划线连接
- **Flyway 脚本不可逆**，写之前必须确认

```sql
-- ✅ 正确：V4__query_channel_scope.sql
ALTER TABLE t_query_definition
    ADD COLUMN channel_scope_json TEXT NULL COMMENT 'JSON array of channel IDs';
```

### 4.3 查询规范
```java
// ✅ 正确：使用 MyBatis-Plus Lambda 查询
queryDefinitionMapper.selectList(
    new LambdaQueryWrapper<QueryDefinitionEntity>()
        .eq(QueryDefinitionEntity::getBotId, botId)
        .eq(QueryDefinitionEntity::getEnabled, true)
        .orderByDesc(QueryDefinitionEntity::getId));

// ❌ 错误：拼接 SQL
String sql = "SELECT * FROM t_query_definition WHERE bot_id = " + botId;
```

### 4.4 索引规范
- 外键字段必须建索引
- 高频查询字段建索引
- 避免在频繁更新的字段上建过多索引

---

## 五、安全规则

### 5.1 敏感信息
```java
// ❌ 禁止：日志泄露 token/密码
log.info("token={}", token);
log.info("password={}", password);

// ❌ 禁止：异常信息泄露敏感信息
catch (Exception e) {
    return "Error: " + e.getMessage();  // 可能包含密码
}

// ✅ 正确：脱敏处理
log.info("token={}***", token.substring(0, 6));
catch (Exception e) {
    log.warn("Operation failed: {}", e.getMessage());
    return "操作失败，请稍后重试";
}
```

### 5.2 SQL 注入
```java
// ✅ 正确：参数化查询
queryDefinitionMapper.selectList(
    new LambdaQueryWrapper<QueryDefinitionEntity>()
        .eq(QueryDefinitionEntity::getCommand, command));

// ❌ 禁止：字符串拼接
String sql = "SELECT * FROM t_query_definition WHERE command = '" + command + "'";
```

### 5.3 XSS 防护
```typescript
// ✅ 正确：使用框架的自动转义
<template>
  <span>{{ userContent }}</span>
</template>

// ❌ 禁止：直接渲染 HTML
<div v-html="userContent"></div>
```

### 5.4 凭证存储
- 所有凭证（Bot Token、App Secret）必须加密存储
- 使用 `ChannelCredentialsCrypto` 加解密
- 配置文件中的密钥使用环境变量或加密配置

---

## 六、性能规则

### 6.1 数据库查询
```java
// ✅ 正确：避免 N+1 查询
List<Bot> bots = botMapper.selectList(new LambdaQueryWrapper<>());
List<Long> botIds = bots.stream().map(Bot::getId).toList();
Map<Long, List<BotChannelEntity>> channelsByBotId = botChannelMapper.selectList(
    new LambdaQueryWrapper<BotChannelEntity>()
        .in(BotChannelEntity::getBotId, botIds))
    .stream()
    .collect(Collectors.groupingBy(BotChannelEntity::getBotId));

// ❌ 错误：循环中查询
for (Bot bot : bots) {
    List<BotChannelEntity> channels = botChannelMapper.selectList(
        new LambdaQueryWrapper<BotChannelEntity>()
            .eq(BotChannelEntity::getBotId, bot.getId()));
}
```

### 6.2 缓存使用
```java
// ✅ 正确：使用 Caffeine 缓存
private final Cache<String, TokenEntry> tokenCache =
    Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(30))
        .maximumSize(500)
        .build();

// ❌ 错误：无缓存重复请求
public String getToken() {
    return api.getToken();  // 每次都请求
}
```

### 6.3 前端性能
```typescript
// ✅ 正确：使用 computed 缓存
const filteredList = computed(() => 
    list.value.filter(item => item.enabled)
)

// ❌ 错误：在模板中直接过滤
<template>
  <div v-for="item in list.filter(i => i.enabled)">
```

### 6.4 异步处理
```java
// ✅ 正确：Webhook 立即返回，异步处理
@Async
public void handleMessageAsync(JsonNode root, BotChannelEntity ch) {
    // 处理消息
}

// ❌ 错误：同步处理导致超时
public ResponseEntity<?> handle(String body) {
    processMessage(body);  // 可能耗时 3 秒
    return ResponseEntity.ok().build();
}
```

---

## 七、测试规则

### 7.1 测试覆盖
- 新增 Service 方法必须有单元测试
- 新增 Controller 端点必须有集成测试
- Bug 修复必须先写失败测试，再修复

### 7.2 测试命名
```java
@Test
void shouldReturnEmptyListWhenNoQueries() {
    // 测试空场景
}

@Test
void shouldThrowExceptionWhenChannelNotFound() {
    // 测试异常场景
}

@Test
void shouldFilterByEnabledStatus() {
    // 测试过滤逻辑
}
```

### 7.3 测试结构
```java
@Test
void shouldCreateQueryDefinition() {
    // Given - 准备测试数据
    QueryDefinitionEntity entity = new QueryDefinitionEntity();
    entity.setCommand("test");
    
    // When - 执行被测试方法
    QueryDefinitionResponse result = controller.create(botId, request);
    
    // Then - 验证结果
    assertThat(result.getCommand()).isEqualTo("test");
}
```

### 7.4 验证命令
```bash
# 后端改动后必须运行
cd backend && ./mvnw.cmd test

# 前端改动后必须运行
cd admin-ui && npm run build
```

---

## 八、Git 提交规范

### 8.1 提交信息格式
```
<type>(<scope>): <description>

<body>

<footer>
```

### 8.2 类型
| 类型 | 说明 |
|------|------|
| feat | 新功能 |
| fix | Bug 修复 |
| refactor | 重构（不改变功能） |
| style | 代码格式（不影响逻辑） |
| docs | 文档更新 |
| test | 测试相关 |
| chore | 构建/工具相关 |

### 8.3 示例
```
feat(query): 添加查询定义渠道作用域支持

- 新增 channel_scope_json 字段
- 派发时检查渠道匹配
- 前端表单支持多选渠道

Closes #123
```

### 8.4 禁止提交的内容
- `.env` 文件
- `application-local.yml` 中的密码
- IDE 配置文件（.idea, .vscode）
- 编译产物（target/, dist/）
- 日志文件（*.log）

---

## 九、API 设计规范

### 9.1 RESTful 规范
```
GET    /api/admin/bots                    # 列表
GET    /api/admin/bots/{id}               # 详情
POST   /api/admin/bots                    # 创建
PUT    /api/admin/bots/{id}               # 更新
DELETE /api/admin/bots/{id}               # 删除
PUT    /api/admin/bots/{id}/toggle        # 特殊操作
```

### 9.2 响应格式
```java
// ✅ 正确：返回 DTO
public BotResponse getOne(@PathVariable Long id) {
    return service.findById(id);
}

// ❌ 错误：返回 Entity
public BotEntity getOne(@PathVariable Long id) {
    return botMapper.selectById(id);
}
```

### 9.3 错误响应
```java
// ✅ 正确：抛出异常，由全局处理器统一处理
throw new NotFoundException("channel not found");
throw new IllegalArgumentException("凭证不完整");

// ❌ 错误：返回自定义错误格式
return Map.of("error", "not found", "code", 404);
```

---

## 十、代码审查检查清单

每次提交前，检查以下项目：

### 后端
- [ ] Controller 是否只做参数校验和调用 Service？
- [ ] Service 方法是否有 Javadoc？
- [ ] 是否有 N+1 查询问题？
- [ ] 日志是否泄露敏感信息？
- [ ] 异常处理是否正确（不吞异常）？
- [ ] 是否有未使用的 import？
- [ ] 方法行数是否超过 50 行？

### 前端
- [ ] 组件是否超过 300 行？
- [ ] 是否使用 any 类型？
- [ ] API 调用是否有 loading 和 error 处理？
- [ ] 是否有未使用的变量？
- [ ] 模板中是否有 v-html？
- [ ] 中文界面是否使用中文标签？

### 数据库
- [ ] Flyway 脚本是否可逆？
- [ ] 是否有索引建议？
- [ ] 字段是否有 COMMENT？

### 安全
- [ ] 敏感信息是否加密存储？
- [ ] 是否有 SQL 注入风险？
- [ ] 是否有 XSS 风险？

---

## 十一、重构触发条件

当代码出现以下情况时，必须重构：

1. **文件超过行数限制**（见 2.5 节）
2. **方法超过 50 行**
3. **嵌套超过 3 层**
4. **重复代码出现 3 次以上**
5. **类的职责超过 3 个**
6. **方法参数超过 4 个**

---

## 十二、工具配置

### 12.1 IDE 配置
```xml
<!-- .editorconfig -->
root = true

[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
trim_trailing_whitespace = true

[*.java]
indent_style = space
indent_size = 4

[*.{vue,ts,js}]
indent_style = space
indent_size = 2

[*.{sql,xml}]
indent_style = space
indent_size = 2
```

### 12.2 ESLint 配置
```json
{
  "rules": {
    "no-unused-vars": "warn",
    "no-console": "warn",
    "prefer-const": "error"
  }
}
```

### 12.3 Checkstyle 配置
- 最大行长度：120 字符
- 缩进：4 空格
- 大括号：K&R 风格

---

## 附录：快速检查命令

```bash
# 后端编译检查
cd backend && ./mvnw.cmd compile

# 后端测试
cd backend && ./mvnw.cmd test

# 前端编译检查
cd admin-ui && npm run build

# 前端类型检查
cd admin-ui && npm run type-check

# 代码格式化
cd admin-ui && npm run lint
```

---

*本文件最后更新：2026-05-03*
*维护者：im-bot-hub 开发团队*
