# 横向扩展与 Redis（Horizontal scaling / Redis）

## 中文

多实例部署时，每台 JVM 内的 **Bucket4j 限流**（[`WebhookRateLimiter`](../backend/src/main/java/com/sov/telegram/bot/service/telegram/WebhookRateLimiter.java)）互不共享，**全局限流会被稀释**。若需在多副本间共享令牌桶，可采用：

- 在 API 网关（Nginx、Kong、Envoy）做按 IP/chat 的限流；或
- 将限流状态外置到 **Redis**，使用 Bucket4j 的 Redis/JCache 扩展或自研滑动窗口。

Webhook 若已配置 **secret token**，可在各实例前由网关统一校验，或在应用内校验（当前为应用内实现）。

## English

With **multiple app instances**, in-memory **Bucket4j** buckets are not shared; **global rate limits are weakened**. Use an **edge/gateway limiter** or move state to **Redis** (Bucket4j Redis / JCache backend) for shared limits.
