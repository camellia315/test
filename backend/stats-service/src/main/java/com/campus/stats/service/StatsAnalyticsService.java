package com.campus.stats.service;

import com.campus.common.exception.BusinessException;
import com.campus.stats.model.StatsExportRow;
import com.campus.stats.model.StatsGranularity;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class StatsAnalyticsService {

    private static final DecimalFormat RATE_FORMAT = new DecimalFormat("0.00");

    private final JdbcTemplate jdbcTemplate;

    public StatsAnalyticsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> dashboard(StatsGranularity granularity, int size) {
        int normalizedSize = normalizeSize(size, granularity);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("overview", overview());
        response.put("trends", trends(granularity, normalizedSize));
        response.put("rankings", rankings());
        return response;
    }

    public Map<String, Object> overview() {
        Map<String, Object> response = new LinkedHashMap<>();

        String userTimeColumn = findFirstExistingColumn("user", "created_at", "create_time");
        long userTotal = countAll("`user`");
        long todayNewUsers = countToday("`user`", userTimeColumn);

        String lostFoundTimeColumn = findFirstExistingColumn("lost_found", "created_at", "create_time");
        long lostFoundTotal = countAll("lost_found");
        long lostFoundToday = countToday("lost_found", lostFoundTimeColumn);
        long recoveredTotal = countRecoveredLostFound();
        double recoveryRate = lostFoundTotal == 0 ? 0D : ((double) recoveredTotal * 100D / (double) lostFoundTotal);

        String activityTimeColumn = findFirstExistingColumn("activity", "create_time", "created_at");
        long activityTotal = countAll("activity");
        long activityToday = countToday("activity", activityTimeColumn);
        long activityApplyTotal = countActivityApplyTotal();
        long activityApplyToday = countToday("activity_apply",
                findFirstExistingColumn("activity_apply", "apply_time", "create_time", "created_at"));

        response.put("userTotal", userTotal);
        response.put("todayNewUsers", todayNewUsers);
        response.put("lostFoundTotal", lostFoundTotal);
        response.put("lostFoundToday", lostFoundToday);
        response.put("lostFoundRecovered", recoveredTotal);
        response.put("lostFoundRecoveryRate", roundTwo(recoveryRate));
        response.put("activityTotal", activityTotal);
        response.put("activityToday", activityToday);
        response.put("activityApplyTotal", activityApplyTotal);
        response.put("activityApplyToday", activityApplyToday);
        return response;
    }

    public Map<String, Object> trends(StatsGranularity granularity, int size) {
        int normalizedSize = normalizeSize(size, granularity);
        List<TrendBucket> buckets = buildBuckets(granularity, normalizedSize);

        String userTimeColumn = findFirstExistingColumn("user", "created_at", "create_time");
        String lostFoundTimeColumn = findFirstExistingColumn("lost_found", "created_at", "create_time");
        String applyTimeColumn = findFirstExistingColumn("activity_apply", "apply_time", "create_time", "created_at");

        Map<String, Long> userTrendMap = queryTrendMap("`user`", userTimeColumn, granularity, buckets);
        Map<String, Long> lostFoundTrendMap = queryTrendMap("lost_found", lostFoundTimeColumn, granularity, buckets);
        Map<String, Long> activityApplyTrendMap = queryTrendMap("activity_apply", applyTimeColumn, granularity, buckets);

        List<String> labels = new ArrayList<>(buckets.size());
        List<Long> userTrend = new ArrayList<>(buckets.size());
        List<Long> lostFoundTrend = new ArrayList<>(buckets.size());
        List<Long> activityApplyTrend = new ArrayList<>(buckets.size());

        for (TrendBucket bucket : buckets) {
            labels.add(bucket.label);
            userTrend.add(userTrendMap.getOrDefault(bucket.key, 0L));
            lostFoundTrend.add(lostFoundTrendMap.getOrDefault(bucket.key, 0L));
            activityApplyTrend.add(activityApplyTrendMap.getOrDefault(bucket.key, 0L));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("granularity", granularity.name());
        response.put("size", normalizedSize);
        response.put("labels", labels);
        response.put("userRegisterTrend", userTrend);
        response.put("lostFoundPublishTrend", lostFoundTrend);
        response.put("activityApplyTrend", activityApplyTrend);
        return response;
    }

    public Map<String, Object> rankings() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("helpfulUsers", buildHelpfulUsersRanking());
        response.put("hotActivities", buildHotActivityRanking());
        response.put("activeClubs", buildActiveClubRanking());
        return response;
    }

    public List<StatsExportRow> exportRows(StatsGranularity granularity, int size) {
        Map<String, Object> overview = overview();
        Map<String, Object> trends = trends(granularity, size);
        Map<String, Object> rankings = rankings();

        List<StatsExportRow> rows = new ArrayList<>();

        rows.add(new StatsExportRow("Overview", "Platform Users", "Total", String.valueOf(overview.get("userTotal"))));
        rows.add(new StatsExportRow("Overview", "Platform Users", "Today New", String.valueOf(overview.get("todayNewUsers"))));
        rows.add(new StatsExportRow("Overview", "Lost-Found", "Total Publish", String.valueOf(overview.get("lostFoundTotal"))));
        rows.add(new StatsExportRow("Overview", "Lost-Found", "Today Publish", String.valueOf(overview.get("lostFoundToday"))));
        rows.add(new StatsExportRow("Overview", "Lost-Found", "Recovered", String.valueOf(overview.get("lostFoundRecovered"))));
        rows.add(new StatsExportRow("Overview", "Lost-Found", "Recovery Rate (%)", String.valueOf(overview.get("lostFoundRecoveryRate"))));
        rows.add(new StatsExportRow("Overview", "Activity", "Total", String.valueOf(overview.get("activityTotal"))));
        rows.add(new StatsExportRow("Overview", "Activity", "Today Publish", String.valueOf(overview.get("activityToday"))));
        rows.add(new StatsExportRow("Overview", "Activity Apply", "Total", String.valueOf(overview.get("activityApplyTotal"))));
        rows.add(new StatsExportRow("Overview", "Activity Apply", "Today Apply", String.valueOf(overview.get("activityApplyToday"))));

        @SuppressWarnings("unchecked")
        List<String> labels = (List<String>) trends.get("labels");
        @SuppressWarnings("unchecked")
        List<Long> userTrend = (List<Long>) trends.get("userRegisterTrend");
        @SuppressWarnings("unchecked")
        List<Long> lostTrend = (List<Long>) trends.get("lostFoundPublishTrend");
        @SuppressWarnings("unchecked")
        List<Long> applyTrend = (List<Long>) trends.get("activityApplyTrend");
        for (int i = 0; i < labels.size(); i++) {
            rows.add(new StatsExportRow("Trend", "User Register", labels.get(i), String.valueOf(userTrend.get(i))));
            rows.add(new StatsExportRow("Trend", "Lost-Found Publish", labels.get(i), String.valueOf(lostTrend.get(i))));
            rows.add(new StatsExportRow("Trend", "Activity Apply", labels.get(i), String.valueOf(applyTrend.get(i))));
        }

        appendRankingRows(rows, "Ranking", "Helpful User TOP10", rankings.get("helpfulUsers"), "name", "total");
        appendRankingRows(rows, "Ranking", "Hot Activity TOP10", rankings.get("hotActivities"), "title", "total");
        appendRankingRows(rows, "Ranking", "Active Club TOP10", rankings.get("activeClubs"), "name", "activityTotal");

        return rows;
    }

    private void appendRankingRows(List<StatsExportRow> rows,
                                   String section,
                                   String metric,
                                   Object rankingObj,
                                   String nameKey,
                                   String valueKey) {
        if (!(rankingObj instanceof List<?> rankingList)) {
            return;
        }
        for (Object itemObj : rankingList) {
            if (!(itemObj instanceof Map<?, ?> map)) {
                continue;
            }
            Object rank = map.get("rank");
            Object name = map.get(nameKey);
            Object value = map.get(valueKey);
            rows.add(new StatsExportRow(section, metric, "#" + rank + " " + name, String.valueOf(value)));
        }
    }

    private List<Map<String, Object>> buildHelpfulUsersRanking() {
        if (!tableExists("lost_found") || !columnExists("lost_found", "user_id")) {
            return List.of();
        }
        String sql;
        if (tableExists("user") && columnExists("user", "id") && columnExists("user", "username")) {
            sql = "SELECT lf.user_id AS userId, " +
                    "COALESCE(u.username, CONCAT('User#', lf.user_id)) AS displayName, " +
                    "COUNT(*) AS total " +
                    "FROM lost_found lf " +
                    "LEFT JOIN `user` u ON u.id = lf.user_id " +
                    "GROUP BY lf.user_id, u.username " +
                    "ORDER BY total DESC, lf.user_id ASC " +
                    "LIMIT 10";
        } else {
            sql = "SELECT user_id AS userId, CONCAT('User#', user_id) AS displayName, COUNT(*) AS total " +
                    "FROM lost_found GROUP BY user_id ORDER BY total DESC, user_id ASC LIMIT 10";
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        return withRank(rows, row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", asLong(row.get("userId")));
            item.put("name", String.valueOf(row.get("displayName")));
            item.put("total", asLong(row.get("total")));
            return item;
        });
    }

    private List<Map<String, Object>> buildHotActivityRanking() {
        if (!tableExists("activity")) {
            return List.of();
        }
        String sql;
        if (tableExists("activity_apply") && columnExists("activity_apply", "activity_id")) {
            String countExpr = columnExists("activity_apply", "status")
                    ? "SUM(CASE WHEN ap.status <> 3 THEN 1 ELSE 0 END)"
                    : "COUNT(ap.id)";
            sql = "SELECT a.id AS activityId, COALESCE(a.title, CONCAT('Activity#', a.id)) AS title, " + countExpr + " AS total " +
                    "FROM activity a " +
                    "LEFT JOIN activity_apply ap ON ap.activity_id = a.id " +
                    "GROUP BY a.id, a.title " +
                    "ORDER BY total DESC, a.id ASC " +
                    "LIMIT 10";
        } else {
            sql = "SELECT id AS activityId, COALESCE(title, CONCAT('Activity#', id)) AS title, 0 AS total " +
                    "FROM activity ORDER BY id ASC LIMIT 10";
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        return withRank(rows, row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("activityId", asLong(row.get("activityId")));
            item.put("title", String.valueOf(row.get("title")));
            item.put("total", asLong(row.get("total")));
            return item;
        });
    }

    private List<Map<String, Object>> buildActiveClubRanking() {
        if (!tableExists("activity") || !columnExists("activity", "club_id")) {
            return List.of();
        }
        String participantExpr = columnExists("activity", "current_participants")
                ? "COALESCE(SUM(current_participants), 0)"
                : "0";
        String sql = "SELECT club_id AS clubId, COUNT(*) AS activityTotal, " + participantExpr + " AS participantTotal " +
                "FROM activity " +
                "WHERE club_id IS NOT NULL AND club_id <> 0 " +
                "GROUP BY club_id " +
                "ORDER BY activityTotal DESC, participantTotal DESC, club_id ASC " +
                "LIMIT 10";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        return withRank(rows, row -> {
            Map<String, Object> item = new LinkedHashMap<>();
            long clubId = asLong(row.get("clubId"));
            item.put("clubId", clubId);
            item.put("name", "Club#" + clubId);
            item.put("activityTotal", asLong(row.get("activityTotal")));
            item.put("participantTotal", asLong(row.get("participantTotal")));
            return item;
        });
    }

    private List<Map<String, Object>> withRank(List<Map<String, Object>> rows, RowMapper rowMapper) {
        List<Map<String, Object>> ranked = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> item = rowMapper.map(rows.get(i));
            item.put("rank", i + 1);
            ranked.add(item);
        }
        return ranked;
    }

    private Map<String, Long> queryTrendMap(String tableName,
                                            String timeColumn,
                                            StatsGranularity granularity,
                                            List<TrendBucket> buckets) {
        if (timeColumn == null || !tableExists(unquote(tableName))) {
            return Map.of();
        }
        String pattern = switch (granularity) {
            case DAY -> "%Y-%m-%d";
            case WEEK -> "%x-W%v";
            case MONTH -> "%Y-%m";
        };
        String sql = "SELECT DATE_FORMAT(" + timeColumn + ", '" + pattern + "') AS bucket, COUNT(*) AS total " +
                "FROM " + tableName + " " +
                "WHERE " + timeColumn + " >= ? " +
                "GROUP BY DATE_FORMAT(" + timeColumn + ", '" + pattern + "') " +
                "ORDER BY bucket";
        Timestamp start = Timestamp.valueOf(buckets.get(0).startTime);
        List<Map<String, Object>> rows = queryForList(sql, start);
        Map<String, Long> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            result.put(String.valueOf(row.get("bucket")), asLong(row.get("total")));
        }
        return result;
    }

    private List<TrendBucket> buildBuckets(StatsGranularity granularity, int size) {
        LocalDate now = LocalDate.now();
        List<TrendBucket> buckets = new ArrayList<>(size);
        switch (granularity) {
            case DAY -> {
                LocalDate start = now.minusDays(size - 1L);
                for (int i = 0; i < size; i++) {
                    LocalDate current = start.plusDays(i);
                    buckets.add(new TrendBucket(
                            current.toString(),
                            current.format(java.time.format.DateTimeFormatter.ofPattern("MM-dd")),
                            current.atStartOfDay()
                    ));
                }
            }
            case WEEK -> {
                LocalDate weekStart = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(size - 1L);
                for (int i = 0; i < size; i++) {
                    LocalDate current = weekStart.plusWeeks(i);
                    String key = formatIsoWeek(current);
                    buckets.add(new TrendBucket(key, key, current.atStartOfDay()));
                }
            }
            case MONTH -> {
                LocalDate monthStart = now.withDayOfMonth(1).minusMonths(size - 1L);
                for (int i = 0; i < size; i++) {
                    LocalDate current = monthStart.plusMonths(i);
                    String key = current.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
                    buckets.add(new TrendBucket(key, key, current.atStartOfDay()));
                }
            }
            default -> throw new BusinessException(400, "Unsupported granularity");
        }
        return buckets;
    }

    private long countRecoveredLostFound() {
        if (!tableExists("lost_found")) {
            return 0L;
        }
        if (columnExists("lost_found", "status")) {
            return queryForLong(
                    "SELECT COUNT(*) FROM lost_found WHERE UPPER(status) IN ('FOUND', 'RETURNED')"
            );
        }
        return 0L;
    }

    private long countActivityApplyTotal() {
        if (!tableExists("activity_apply")) {
            return 0L;
        }
        if (columnExists("activity_apply", "status")) {
            return queryForLong("SELECT COUNT(*) FROM activity_apply WHERE status <> 3");
        }
        return queryForLong("SELECT COUNT(*) FROM activity_apply");
    }

    private long countToday(String tableName, String timeColumn) {
        if (timeColumn == null || !tableExists(unquote(tableName))) {
            return 0L;
        }
        return queryForLong("SELECT COUNT(*) FROM " + tableName + " WHERE DATE(" + timeColumn + ") = CURDATE()");
    }

    private long countAll(String tableName) {
        if (!tableExists(unquote(tableName))) {
            return 0L;
        }
        return queryForLong("SELECT COUNT(*) FROM " + tableName);
    }

    private String findFirstExistingColumn(String tableName, String... candidates) {
        if (!tableExists(tableName)) {
            return null;
        }
        for (String candidate : candidates) {
            if (columnExists(tableName, candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class,
                tableName,
                columnName
        );
        return count != null && count > 0;
    }

    private long queryForLong(String sql, Object... args) {
        try {
            Long result = jdbcTemplate.queryForObject(sql, Long.class, args);
            return result == null ? 0L : result;
        } catch (DataAccessException ex) {
            return 0L;
        }
    }

    private List<Map<String, Object>> queryForList(String sql, Object... args) {
        try {
            return jdbcTemplate.queryForList(sql, args);
        } catch (DataAccessException ex) {
            return List.of();
        }
    }

    private long asLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private int normalizeSize(int size, StatsGranularity granularity) {
        int fallback = switch (granularity) {
            case DAY -> 14;
            case WEEK -> 12;
            case MONTH -> 12;
        };
        if (size <= 0) {
            return fallback;
        }
        if (size > 60) {
            return 60;
        }
        return size;
    }

    private double roundTwo(double value) {
        return Double.parseDouble(RATE_FORMAT.format(value));
    }

    private String formatIsoWeek(LocalDate date) {
        WeekFields wf = WeekFields.ISO;
        int year = date.get(wf.weekBasedYear());
        int week = date.get(wf.weekOfWeekBasedYear());
        return String.format(Locale.ROOT, "%04d-W%02d", year, week);
    }

    private String unquote(String tableName) {
        return tableName.replace("`", "");
    }

    private interface RowMapper {
        Map<String, Object> map(Map<String, Object> source);
    }

    private static class TrendBucket {
        private final String key;
        private final String label;
        private final LocalDateTime startTime;

        private TrendBucket(String key, String label, LocalDateTime startTime) {
            this.key = key;
            this.label = label;
            this.startTime = startTime;
        }
    }
}

