package com.sov.imhub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Cors cors = new Cors();

    private Security security = new Security();
    private Telegram telegram = new Telegram();
    private RateLimit rateLimit = new RateLimit();
    private Encryption encryption = new Encryption();

    @Data
    public static class Security {
        private Admin admin = new Admin();

        @Data
        public static class Admin {
            private String username;
            private String password;
        }
    }

    @Data
    public static class Telegram {
        private String apiBase = "https://api.telegram.org";
        /**
         * 公网 HTTPS 基址（无末尾 /），供管理端一键 setWebhook；也可在请求体里临时覆盖。
         * 示例：https://xxxx.trycloudflare.com
         */
        private String publicBaseUrl = "";
        /**
         * 访问 Telegram Bot API（sendMessage、setWebhook 等）时的出站 HTTP 代理。
         * 国内直连 api.telegram.org 常超时；可填本机 v2rayN 的 HTTP 入站（一般为 SOCKS+1，如 10809）。
         * host 留空或 port 为 0 表示不使用代理。
         */
        private OutboundProxy outboundProxy = new OutboundProxy();

        @Data
        public static class OutboundProxy {
            private String host = "";
            private int port = 0;
        }
    }

    @Data
    public static class RateLimit {
        private int capacity = 30;
        private int refillPerMinute = 30;
    }

    @Data
    public static class Encryption {
        /** Base64 AES-256 key; empty = store passwords as plain (dev only). */
        private String secretKeyBase64 = "";
    }

    @Data
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>(List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "http://localhost:5174",
                "http://127.0.0.1:5174"));
    }
}
