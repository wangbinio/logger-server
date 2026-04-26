package com.szzh.replayserver.repository;

import com.szzh.common.tdengine.TdengineNaming;
import com.szzh.replayserver.model.query.ReplayTableDescriptor;
import com.szzh.replayserver.model.query.ReplayTableType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 回放态势子表发现 Repository。
 */
@Repository
public class ReplayTableDiscoveryRepository {

    private static final String DISCOVER_TAGS_SQL =
            "SELECT table_name, tag_name, tag_type, tag_value "
                    + "FROM information_schema.ins_tags WHERE stable_name = ?";

    private final JdbcTemplate jdbcTemplate;

    /**
     * 创建回放态势子表发现 Repository。
     *
     * @param jdbcTemplate JDBC 模板。
     */
    public ReplayTableDiscoveryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate 不能为空");
    }

    /**
     * 发现指定实例的所有态势子表。
     *
     * @param instanceId 实例 ID。
     * @return 子表描述列表。
     */
    public List<ReplayTableDescriptor> discoverTables(String instanceId) {
        String stableName = TdengineNaming.buildStableName(instanceId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(DISCOVER_TAGS_SQL, stableName);
        Map<String, TableTags> tableTagsMap = new LinkedHashMap<String, TableTags>();

        for (Map<String, Object> row : rows) {
            String tableName = readRequiredText(row, "table_name");
            String tagName = readRequiredText(row, "tag_name").toLowerCase(Locale.ENGLISH);
            Object tagValue = row.get("tag_value");
            TableTags tableTags = tableTagsMap.computeIfAbsent(tableName, TableTags::new);
            tableTags.put(tagName, tagValue);
        }

        List<ReplayTableDescriptor> descriptors = new ArrayList<ReplayTableDescriptor>();
        for (TableTags tableTags : tableTagsMap.values()) {
            descriptors.add(tableTags.toDescriptor());
        }
        return descriptors;
    }

    /**
     * 从行数据中读取必填文本字段。
     *
     * @param row 行数据。
     * @param columnName 列名。
     * @return 文本字段。
     */
    private String readRequiredText(Map<String, Object> row, String columnName) {
        Object value = row.get(columnName);
        if (value == null || value.toString().trim().isEmpty()) {
            throw new IllegalStateException("TDengine tag 元数据缺少字段: " + columnName);
        }
        return value.toString().trim();
    }

    /**
     * 单张子表的 tag 聚合结果。
     */
    private static final class TableTags {

        private final String tableName;

        private Object senderId;

        private Object messageType;

        private Object messageCode;

        /**
         * 创建 tag 聚合结果。
         *
         * @param tableName 子表名。
         */
        private TableTags(String tableName) {
            this.tableName = tableName;
        }

        /**
         * 写入单个 tag 值。
         *
         * @param tagName tag 名。
         * @param tagValue tag 值。
         */
        private void put(String tagName, Object tagValue) {
            if ("sender_id".equals(tagName)) {
                senderId = tagValue;
            } else if ("msgtype".equals(tagName)) {
                messageType = tagValue;
            } else if ("msgcode".equals(tagName)) {
                messageCode = tagValue;
            }
        }

        /**
         * 转换为子表描述。
         *
         * @return 子表描述。
         */
        private ReplayTableDescriptor toDescriptor() {
            return new ReplayTableDescriptor(
                    tableName,
                    toInt(senderId, "sender_id"),
                    toInt(messageType, "msgtype"),
                    toInt(messageCode, "msgcode"),
                    ReplayTableType.PERIODIC);
        }

        /**
         * 转换 tag 值为整型。
         *
         * @param value tag 值。
         * @param tagName tag 名。
         * @return 整型 tag 值。
         */
        private int toInt(Object value, String tagName) {
            if (value == null) {
                throw new IllegalStateException("子表缺少必需 tag: " + tableName + "." + tagName);
            }
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return Integer.parseInt(value.toString().trim());
        }
    }
}
