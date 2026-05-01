package com.sov.telegram.bot.service.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sov.telegram.bot.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramApiClient {

    private static final Pattern WEBHOOK_SECRET_ALLOWED = Pattern.compile("^[A-Za-z0-9_-]{1,256}$");

    private final AppProperties appProperties;
    private final RestClient telegramRestClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void sendMessage(String botToken, long chatId, String text) {
        if (text == null) {
            text = "";
        }
        String base = appProperties.getTelegram().getApiBase();
        String url = base + "/bot" + botToken + "/sendMessage";
        boolean useHtml = true;
        if (text.length() > 3900) {
            // Avoid cutting HTML tags in half (Telegram would reject parse_mode=HTML).
            text = toPlainText(text);
            useHtml = false;
        }
        if (text.length() > 3900) {
            text = text.substring(0, 3900) + "\n...(truncated)";
        }
        try {
            ObjectNode body = objectMapper.createObjectNode()
                    .put("chat_id", chatId)
                    .put("text", text);
            if (useHtml) {
                body.put("parse_mode", "HTML");
            }
            telegramRestClient
                    .post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body.toString())
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("sendMessage failed for chatId={}: {}", chatId, e.getMessage());
        }
    }

    public void sendError(String botToken, long chatId, String userMessage) {
        sendMessage(botToken, chatId, "<b>错误</b>\n" + escapeHtml(userMessage));
    }

    public void sendRateLimitedMessage(String botToken, long chatId) {
        sendError(botToken, chatId, "请求过于频繁，请稍后再试。");
    }

    public void sendNotAllowedMessage(String botToken, long chatId) {
        sendError(botToken, chatId, "你没有权限使用此机器人。");
    }

    public void sendUnknownCommand(String botToken, long chatId, String command) {
        sendMessage(
                botToken,
                chatId,
                "<b>提示</b>\n未知命令：<code>/" + escapeHtml(command) + "</code>");
    }

    public void sendMissingParamMessage(String botToken, long chatId, String paramName) {
        sendError(botToken, chatId, "缺少参数：" + escapeHtml(paramName));
    }

    public void sendQueryFailedMessage(String botToken, long chatId) {
        sendError(botToken, chatId, "查询执行失败，请稍后再试或联系管理员。");
    }

    /** 调用 Telegram setWebhook；返回完整 JSON（含 ok、description）。 */
    public JsonNode setWebhook(String botToken, String webhookUrl, String secretToken) {
        String apiBase = appProperties.getTelegram().getApiBase();
        String url = apiBase + "/bot" + botToken + "/setWebhook";
        ObjectNode body = objectMapper.createObjectNode().put("url", webhookUrl);
        log.info(
                "telegram.setWebhook request apiBase={} webhookUrl={} botTokenMasked={}",
                apiBase,
                webhookUrl,
                maskToken(botToken));
        if (secretToken != null && !secretToken.isBlank()) {
            String s = secretToken.trim();
            log.info(
                    "telegram.setWebhook secret received rawLen={} trimmedLen={} tokenPreview={}",
                    secretToken.length(),
                    s.length(),
                    maskSecret(s));
            validateWebhookSecretToken(s);
            body.put("secret_token", s);
        } else {
            log.info("telegram.setWebhook secret not provided");
        }
        try {
            String raw =
                    telegramRestClient
                            .post()
                            .uri(url)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body.toString())
                            .retrieve()
                            .body(String.class);
            log.info("telegram.setWebhook response raw={}", truncateForLog(raw));
            return objectMapper.readTree(raw);
        } catch (HttpClientErrorException e) {
            String bodyText = e.getResponseBodyAsString();
            log.warn(
                    "telegram.setWebhook http error status={} body={} webhookUrl={} botTokenMasked={}",
                    e.getStatusCode().value(),
                    truncateForLog(bodyText),
                    webhookUrl,
                    maskToken(botToken));
            if (bodyText != null && bodyText.contains("secret token contains unallowed characters")) {
                throw new IllegalArgumentException("Webhook 密钥仅允许字母、数字、下划线(_)和短横线(-)，且长度 1-256");
            }
            String msg = (bodyText == null || bodyText.isBlank()) ? e.getStatusText() : bodyText;
            throw new IllegalArgumentException("Telegram setWebhook 失败（HTTP " + e.getStatusCode().value() + "）：" + msg);
        } catch (ResourceAccessException e) {
            throw new IllegalArgumentException("连接 Telegram 失败：请检查本机网络、代理配置或 api-base 可达性");
        } catch (Exception e) {
            log.warn("setWebhook parse failed: {}", e.getMessage());
            throw new IllegalArgumentException("Telegram 返回非 JSON，请检查网络与 Bot Token");
        }
    }

    public JsonNode getWebhookInfo(String botToken) {
        String apiBase = appProperties.getTelegram().getApiBase();
        String url = apiBase + "/bot" + botToken + "/getWebhookInfo";
        try {
            String raw = telegramRestClient.get().uri(url).retrieve().body(String.class);
            return objectMapper.readTree(raw);
        } catch (HttpClientErrorException.NotFound e) {
            // Telegram returns 404 for invalid bot token or wrong API base path.
            throw new IllegalArgumentException("Telegram 返回 404：请检查 Bot Token 是否正确，或 app.telegram.api-base 配置是否有效");
        } catch (HttpClientErrorException e) {
            String body = e.getResponseBodyAsString();
            String msg = (body == null || body.isBlank()) ? e.getStatusText() : body;
            throw new IllegalArgumentException("Telegram 接口调用失败（HTTP " + e.getStatusCode().value() + "）：" + msg);
        } catch (ResourceAccessException e) {
            throw new IllegalArgumentException("连接 Telegram 失败：请检查本机网络、代理配置或 api-base 可达性");
        } catch (Exception e) {
            log.warn("getWebhookInfo parse failed: {}", e.getMessage());
            throw new IllegalArgumentException("Telegram 返回非 JSON，请检查网络与 Bot Token");
        }
    }

    /** Telegram 菜单命令项。 */
    public record CommandSpec(String command, String description) {}

    /**
     * 同步机器人命令菜单（setMyCommands）。
     *
     * <p>除默认范围外，再对 {@code all_group_chats} 写一份相同列表，避免部分客户端在<strong>群 / 超级群</strong>里输入 {@code /}
     * 时不展示命令（私聊与默认 scope 仍由第一次调用覆盖）。
     */
    public JsonNode setMyCommands(String botToken, List<CommandSpec> commands) {
        ArrayNode commandArray = buildBotCommandArray(commands);
        JsonNode first = postSetMyCommands(botToken, commandArray, null);
        if (!first.path("ok").asBoolean(false)) {
            return first;
        }
        try {
            ObjectNode groupScope = objectMapper.createObjectNode().put("type", "all_group_chats");
            JsonNode second = postSetMyCommands(botToken, commandArray, groupScope);
            if (!second.path("ok").asBoolean(false)) {
                log.warn(
                        "setMyCommands(scope=all_group_chats) not ok: {}",
                        second.path("description").asText(second.toString()));
            }
        } catch (Exception e) {
            log.warn("setMyCommands(scope=all_group_chats) failed: {}", e.getMessage());
        }
        return first;
    }

    private ArrayNode buildBotCommandArray(List<CommandSpec> commands) {
        ArrayNode commandArray = objectMapper.createArrayNode();
        if (commands == null) {
            return commandArray;
        }
        for (CommandSpec commandSpec : commands) {
            if (commandSpec == null || commandSpec.command() == null) {
                continue;
            }
            String normalized = commandSpec.command().trim().toLowerCase();
            if (normalized.isBlank()) {
                continue;
            }
            String desc = commandSpec.description() == null ? "" : commandSpec.description().trim();
            if (desc.isBlank()) {
                desc = "执行 /" + normalized + " 查询";
            }
            if (desc.length() > 255) {
                desc = desc.substring(0, 255);
            }
            commandArray.add(objectMapper.createObjectNode()
                    .put("command", normalized)
                    .put("description", desc));
        }
        return commandArray;
    }

    private JsonNode postSetMyCommands(String botToken, ArrayNode commandArray, ObjectNode scope) {
        String apiBase = appProperties.getTelegram().getApiBase();
        String url = apiBase + "/bot" + botToken + "/setMyCommands";
        ObjectNode body = objectMapper.createObjectNode().set("commands", commandArray);
        if (scope != null) {
            body.set("scope", scope);
        }
        String raw =
                telegramRestClient
                        .post()
                        .uri(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body.toString())
                        .retrieve()
                        .body(String.class);
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            log.warn("setMyCommands parse failed: {}", e.getMessage());
            throw new IllegalArgumentException("Telegram 返回非 JSON，请检查网络与 Bot Token");
        }
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String toPlainText(String htmlText) {
        if (htmlText == null || htmlText.isBlank()) {
            return "";
        }
        String text = htmlText
                .replace("<br>", "\n")
                .replace("<br/>", "\n")
                .replace("<br />", "\n")
                .replace("</p>", "\n");
        text = text.replaceAll("<[^>]+>", "");
        return text
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }

    private static void validateWebhookSecretToken(String s) {
        if (s.length() > 256) {
            log.warn("telegram webhook secret invalid: length={} > 256", s.length());
            throw new IllegalArgumentException("Webhook 密钥长度不能超过 256（Telegram 限制）");
        }
        if (!WEBHOOK_SECRET_ALLOWED.matcher(s).matches()) {
            log.warn(
                    "telegram webhook secret invalid chars: preview={} diagnostics={}",
                    maskSecret(s),
                    describeSecretChars(s));
            throw new IllegalArgumentException("Webhook 密钥仅允许字母、数字、下划线(_)和短横线(-)，且长度 1-256");
        }
    }

    private static String maskToken(String token) {
        if (token == null || token.isBlank()) {
            return "<empty>";
        }
        String t = token.trim();
        if (t.length() <= 8) {
            return "****";
        }
        return t.substring(0, 4) + "..." + t.substring(t.length() - 4);
    }

    private static String maskSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            return "<empty>";
        }
        if (secret.length() <= 6) {
            return "***";
        }
        return secret.substring(0, 2) + "***" + secret.substring(secret.length() - 2);
    }

    private static String truncateForLog(String text) {
        if (text == null) {
            return "<null>";
        }
        String t = text.replace("\n", "\\n").replace("\r", "\\r");
        return t.length() > 300 ? t.substring(0, 300) + "...(truncated)" : t;
    }

    /**
     * Useful for diagnosing copied hidden chars (e.g. full-width dash, spaces, newline).
     */
    private static String describeSecretChars(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            boolean ok = (ch >= 'A' && ch <= 'Z')
                    || (ch >= 'a' && ch <= 'z')
                    || (ch >= '0' && ch <= '9')
                    || ch == '_'
                    || ch == '-';
            if (!ok) {
                if (sb.length() > 0) {
                    sb.append("; ");
                }
                sb.append("idx=").append(i)
                        .append(",char='").append(ch).append('\'')
                        .append(",code=U+")
                        .append(String.format(Locale.ROOT, "%04X", (int) ch));
            }
        }
        return sb.length() == 0 ? "all-chars-allowed" : sb.toString();
    }
}
