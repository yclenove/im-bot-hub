package com.sov.imhub.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.net.Proxy;

@Slf4j
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        // Default builder for generic outbound APIs: no proxy by default.
        return RestClient.builder();
    }

    /** 单例出站客户端，供 {@link com.sov.imhub.service.telegram.TelegramApiClient} 复用。 */
    @Bean
    public RestClient telegramRestClient(AppProperties appProperties) {
        RestClient.Builder builder = RestClient.builder();
        AppProperties.Telegram.OutboundProxy p = appProperties.getTelegram().getOutboundProxy();
        if (p != null && StringUtils.hasText(p.getHost()) && p.getPort() > 0) {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(p.getHost().trim(), p.getPort())));
            factory.setConnectTimeout(15_000);
            factory.setReadTimeout(60_000);
            builder.requestFactory(factory);
            log.info("Telegram RestClient 使用 HTTP 代理: {}:{}", p.getHost().trim(), p.getPort());
        }
        return builder.build();
    }

    /** Required for {@code selectPage} on audit logs and other paged queries. */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
