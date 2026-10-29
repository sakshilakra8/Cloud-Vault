package com.shardvault.api.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/** Append-only audit writer and owner/admin scoped query service. */
@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    public AuditService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void record(UUID userId, String action, String resourceType, String resourceId,
                       String result, String ip, String userAgent) {
        jdbc.update("insert into audit_logs(id,user_id,action,metadata,ip_address,user_agent,resource_type,resource_id,result,created_at) values(?,?,?,'{}',?,?,?,?,?,CURRENT_TIMESTAMP)",
                UUID.randomUUID(), userId, action, truncate(ip, 64), truncate(userAgent, 512),
                truncate(resourceType, 24), truncate(resourceId, 64), truncate(result, 16));
    }

    public Page query(UUID owner, boolean admin, Instant from, Instant to, String action, int page, int size) {
        int boundedSize = Math.max(1, Math.min(200, size));
        int boundedPage = Math.max(0, page);
        StringBuilder where = new StringBuilder(" where 1=1");
        List<Object> args = new ArrayList<>();
        if (!admin) { where.append(" and user_id = ?"); args.add(owner); }
        if (from != null) { where.append(" and created_at >= ?"); args.add(from); }
        if (to != null) { where.append(" and created_at < ?"); args.add(to); }
        if (action != null && !action.isBlank()) { where.append(" and action = ?"); args.add(action); }
        long total = jdbc.queryForObject("select count(*) from audit_logs" + where, Long.class, args.toArray());
        List<Object> dataArgs = new ArrayList<>(args);
        dataArgs.add(boundedSize); dataArgs.add((long) boundedPage * boundedSize);
        List<Map<String, Object>> rows = jdbc.queryForList("select id,user_id,action,ip_address,user_agent,resource_type,resource_id,result,created_at from audit_logs" + where + " order by created_at desc limit ? offset ?", dataArgs.toArray());
        return new Page(rows, total, boundedPage, boundedSize);
    }

    public String csv(UUID owner, boolean admin, Instant from, Instant to, String action, int page, int size) {
        Page pageResult = query(owner, admin, from, to, action, page, size);
        StringBuilder csv = new StringBuilder("id,user_id,action,ip_address,user_agent,resource_type,resource_id,result,created_at\r\n");
        for (Map<String, Object> row : pageResult.items()) {
            csv.append(csv(row.get("id"))).append(',').append(csv(row.get("user_id"))).append(',')
                    .append(csv(row.get("action"))).append(',').append(csv(row.get("ip_address"))).append(',')
                    .append(csv(row.get("user_agent"))).append(',').append(csv(row.get("resource_type"))).append(',')
                    .append(csv(row.get("resource_id"))).append(',').append(csv(row.get("result"))).append(',')
                    .append(csv(row.get("created_at"))).append("\r\n");
        }
        return csv.toString();
    }

    private static String csv(Object value) {
        String raw = value == null ? "" : value.toString();
        return "\"" + raw.replace("\"", "\"\"") + "\"";
    }
    private static String truncate(String value, int max) { return value == null ? null : value.substring(0, Math.min(max, value.length())); }
    public record Page(List<Map<String, Object>> items, long total, int page, int size) {}
}
