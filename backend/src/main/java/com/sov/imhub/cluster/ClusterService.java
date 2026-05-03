package com.sov.imhub.cluster;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 集群服务：支持高可用部署。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClusterService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${server.port}")
    private int serverPort;

    /**
     * 每 30 秒发送心跳。
     */
    @Scheduled(fixedRate = 30_000)
    public void sendHeartbeat() {
        String nodeId = applicationName + ":" + serverPort;

        jdbcTemplate.update(
                """
                INSERT INTO t_cluster_node (node_id, status, last_heartbeat, created_at)
                VALUES (:nodeId, 'ACTIVE', NOW(), NOW())
                ON DUPLICATE KEY UPDATE status = 'ACTIVE', last_heartbeat = NOW()
                """,
                Map.of("nodeId", nodeId));

        log.debug("heartbeat sent: {}", nodeId);
    }

    /**
     * 检测故障节点。
     */
    @Scheduled(fixedRate = 60_000)
    public void detectFailedNodes() {
        List<Map<String, Object>> failedNodes = jdbcTemplate.queryForList(
                """
                SELECT * FROM t_cluster_node
                WHERE last_heartbeat < DATE_SUB(NOW(), INTERVAL 2 MINUTE)
                AND status = 'ACTIVE'
                """,
                Map.of());

        for (Map<String, Object> node : failedNodes) {
            String nodeId = (String) node.get("node_id");
            log.warn("node failed: {}", nodeId);

            jdbcTemplate.update(
                    "UPDATE t_cluster_node SET status = 'FAILED' WHERE node_id = :nodeId",
                    Map.of("nodeId", nodeId));
        }
    }

    /**
     * 每小时清理超过 24 小时的心跳记录。
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void cleanupOldHeartbeats() {
        int deleted = jdbcTemplate.update(
                "DELETE FROM t_cluster_node WHERE last_heartbeat < DATE_SUB(NOW(), INTERVAL 24 HOUR)",
                Map.of());
        if (deleted > 0) {
            log.info("cleaned up {} old heartbeat records", deleted);
        }
    }

    /**
     * 获取集群状态。
     */
    public ClusterStatus getClusterStatus() {
        ClusterStatus status = new ClusterStatus();

        List<Map<String, Object>> nodes = jdbcTemplate.queryForList(
                "SELECT * FROM t_cluster_node ORDER BY last_heartbeat DESC", Map.of());

        status.setTotalNodes(nodes.size());
        status.setActiveNodes((int) nodes.stream()
                .filter(n -> "ACTIVE".equals(n.get("status")))
                .count());
        status.setFailedNodes((int) nodes.stream()
                .filter(n -> "FAILED".equals(n.get("status")))
                .count());
        status.setNodes(nodes);

        return status;
    }

    /**
     * 负载均衡：选择最优节点。
     */
    public String selectNode() {
        List<Map<String, Object>> activeNodes = jdbcTemplate.queryForList(
                """
                SELECT * FROM t_cluster_node
                WHERE status = 'ACTIVE'
                ORDER BY last_heartbeat DESC
                LIMIT 1
                """,
                Map.of());

        if (activeNodes.isEmpty()) {
            return applicationName + ":" + serverPort;
        }

        return (String) activeNodes.get(0).get("node_id");
    }

    /**
     * 集群状态。
     */
    @Data
    public static class ClusterStatus {
        private int totalNodes;
        private int activeNodes;
        private int failedNodes;
        private List<Map<String, Object>> nodes;
    }
}
