package com.sov.telegram.bot.service.jdbc;

import com.sov.telegram.bot.domain.DatasourceEntity;
import com.sov.telegram.bot.domain.DatasourceType;
import com.sov.telegram.bot.mapper.DatasourceMapper;
import com.sov.telegram.bot.service.crypto.EncryptionService;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessDataSourceRegistry {

    private final DatasourceMapper datasourceMapper;
    private final EncryptionService encryptionService;

    private final ConcurrentHashMap<Long, HikariDataSource> pools = new ConcurrentHashMap<>();

    /** 与 {@link #pools} 同步失效：reload 时须清空，避免指向已关闭的池。 */
    private final ConcurrentHashMap<Long, NamedParameterJdbcTemplate> namedTemplates = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        reloadAll();
    }

    public synchronized void reloadAll() {
        namedTemplates.clear();
        pools.values().forEach(HikariDataSource::close);
        pools.clear();
        List<DatasourceEntity> list = datasourceMapper.selectList(new LambdaQueryWrapper<DatasourceEntity>());
        for (DatasourceEntity e : list) {
            if (DatasourceType.fromString(e.getSourceType()) != DatasourceType.DATABASE) {
                continue;
            }
            pools.put(e.getId(), createPool(e));
        }
        log.info("Reloaded {} business datasource pool(s)", pools.size());
    }

    public synchronized void reloadOne(Long datasourceId) {
        namedTemplates.remove(datasourceId);
        HikariDataSource old = pools.remove(datasourceId);
        if (old != null) {
            old.close();
        }
        DatasourceEntity e = datasourceMapper.selectById(datasourceId);
        if (e != null && DatasourceType.fromString(e.getSourceType()) == DatasourceType.DATABASE) {
            pools.put(datasourceId, createPool(e));
        }
    }

    public NamedParameterJdbcTemplate namedJdbc(Long datasourceId) {
        return namedTemplates.computeIfAbsent(
                datasourceId,
                id -> {
                    HikariDataSource ds = pools.get(id);
                    if (ds == null) {
                        throw new IllegalStateException("Datasource not loaded: " + id + ". Call reload.");
                    }
                    return new NamedParameterJdbcTemplate(ds);
                });
    }

    public DataSource dataSource(Long datasourceId) {
        HikariDataSource ds = pools.get(datasourceId);
        if (ds == null) {
            throw new IllegalStateException("Datasource not loaded: " + datasourceId);
        }
        return ds;
    }

    private HikariDataSource createPool(DatasourceEntity e) {
        String password = encryptionService.decrypt(e.getPasswordCipher());
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(e.getJdbcUrl());
        ds.setUsername(e.getUsername());
        ds.setPassword(password);
        ds.setMaximumPoolSize(e.getPoolMax() != null ? e.getPoolMax() : 5);
        ds.setPoolName("tgq-" + e.getId());
        ds.setAutoCommit(true);
        ds.setReadOnly(true);
        return ds;
    }
}
