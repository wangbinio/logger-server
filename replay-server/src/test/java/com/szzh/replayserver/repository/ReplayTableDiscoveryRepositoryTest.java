package com.szzh.replayserver.repository;

import com.szzh.replayserver.model.query.ReplayTableDescriptor;
import com.szzh.replayserver.model.query.ReplayTableType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 回放态势子表发现 Repository 测试。
 */
class ReplayTableDiscoveryRepositoryTest {

    /**
     * 验证表发现按 stable_name 查询 TDengine tag 元数据并按 table_name 聚合。
     */
    @Test
    void shouldDiscoverTablesFromInsTags() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        ReplayTableDiscoveryRepository repository = new ReplayTableDiscoveryRepository(jdbcTemplate);
        Mockito.when(jdbcTemplate.queryForList(Mockito.anyString(), Mockito.eq("situation_instance_001")))
                .thenReturn(Arrays.asList(
                        row("situation_1001_2_7_instance_001", "sender_id", 7),
                        row("situation_1001_2_7_instance_001", "msgtype", 1001),
                        row("situation_1001_2_7_instance_001", "msgcode", 2),
                        row("situation_1002_8_9_instance_001", "sender_id", "9"),
                        row("situation_1002_8_9_instance_001", "msgtype", "1002"),
                        row("situation_1002_8_9_instance_001", "msgcode", "8")));

        List<ReplayTableDescriptor> descriptors = repository.discoverTables("instance-001");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(jdbcTemplate).queryForList(sqlCaptor.capture(), Mockito.eq("situation_instance_001"));
        Assertions.assertTrue(sqlCaptor.getValue().contains("information_schema.ins_tags"));
        Assertions.assertEquals(2, descriptors.size());
        Assertions.assertEquals("situation_1001_2_7_instance_001", descriptors.get(0).getTableName());
        Assertions.assertEquals(7, descriptors.get(0).getSenderId());
        Assertions.assertEquals(1001, descriptors.get(0).getMessageType());
        Assertions.assertEquals(2, descriptors.get(0).getMessageCode());
        Assertions.assertEquals(ReplayTableType.PERIODIC, descriptors.get(0).getTableType());
        Assertions.assertEquals(9, descriptors.get(1).getSenderId());
    }

    /**
     * 验证缺失必需 tag 时抛出明确异常。
     */
    @Test
    void shouldRejectTableWithMissingTags() {
        JdbcTemplate jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        ReplayTableDiscoveryRepository repository = new ReplayTableDiscoveryRepository(jdbcTemplate);
        Mockito.when(jdbcTemplate.queryForList(Mockito.anyString(), Mockito.eq("situation_instance_001")))
                .thenReturn(Arrays.asList(
                        row("situation_1001_2_7_instance_001", "sender_id", 7),
                        row("situation_1001_2_7_instance_001", "msgtype", 1001)));

        Assertions.assertThrows(IllegalStateException.class, () -> repository.discoverTables("instance-001"));
    }

    /**
     * 创建元数据行。
     *
     * @param tableName 表名。
     * @param tagName tag 名。
     * @param tagValue tag 值。
     * @return 元数据行。
     */
    private Map<String, Object> row(String tableName, String tagName, Object tagValue) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("table_name", tableName);
        row.put("tag_name", tagName);
        row.put("tag_value", tagValue);
        return row;
    }
}
