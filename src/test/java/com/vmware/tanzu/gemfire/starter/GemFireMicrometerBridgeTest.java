package com.vmware.tanzu.gemfire.starter;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.geode.StatisticDescriptor;
import org.apache.geode.Statistics;
import org.apache.geode.StatisticsType;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.distributed.internal.InternalDistributedSystem;
import org.apache.geode.internal.statistics.StatisticsManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GemFireMicrometerBridgeTest {

    private ClientCache clientCache;
    private SimpleMeterRegistry registry;
    private GemFireMicrometerBridge bridge;
    private InternalDistributedSystem distributedSystem;
    private StatisticsManager statisticsManager;

    @BeforeEach
    void setUp() {
        clientCache = mock(ClientCache.class);
        registry = new SimpleMeterRegistry();
        distributedSystem = mock(InternalDistributedSystem.class);
        statisticsManager = mock(StatisticsManager.class);
        bridge = new GemFireMicrometerBridge(clientCache, registry, new GemFireMetricBridgeProperties());
    }

    private void wireUpStatisticsManager() {
        when(clientCache.getDistributedSystem()).thenReturn(distributedSystem);
        when(distributedSystem.getStatisticsManager()).thenReturn(statisticsManager);
    }

    private void setExportConfig(Map<String, String> config) {
        GemFireMetricBridgeProperties props = new GemFireMetricBridgeProperties();
        props.setExport(config);
        bridge = new GemFireMicrometerBridge(clientCache, registry, props);
    }

    private Statistics mockStatistics(String typeName, String textId, long uniqueId, StatisticDescriptor... descriptors) {
        Statistics stats = mock(Statistics.class);
        StatisticsType type = mock(StatisticsType.class);
        lenient().when(type.getName()).thenReturn(typeName);
        lenient().when(type.getStatistics()).thenReturn(descriptors);
        lenient().when(stats.getType()).thenReturn(type);
        lenient().when(stats.getTextId()).thenReturn(textId);
        lenient().when(stats.getUniqueId()).thenReturn(uniqueId);
        return stats;
    }

    private StatisticDescriptor mockDescriptor(String name, boolean isCounter) {
        StatisticDescriptor d = mock(StatisticDescriptor.class);
        lenient().when(d.getName()).thenReturn(name);
        lenient().when(d.isCounter()).thenReturn(isCounter);
        return d;
    }

    @Test
    void rescanRegistersCounterAsFunction() throws Exception {
        wireUpStatisticsManager();
        StatisticDescriptor gets = mockDescriptor("gets", true);
        Statistics stats = mockStatistics("CachePerfStats", "cachePerfStats", 1, gets);
        when(stats.get(gets)).thenReturn(42L);
        when(statisticsManager.getStatsList()).thenReturn(List.of(stats));

        setExportConfig(Map.of("CachePerfStats", "cachePerfStats::gets"));
        bridge.rescan();

        FunctionCounter counter = registry.find("gemfire.cacheperfstats.gets").functionCounter();
        assertNotNull(counter, "Counter metric should be registered");
        assertEquals(42.0, counter.count());
    }

    @Test
    void rescanRegistersGauge() throws Exception {
        wireUpStatisticsManager();
        StatisticDescriptor connections = mockDescriptor("connections", false);
        Statistics stats = mockStatistics("PoolStats", "myPool", 2, connections);
        when(stats.get(connections)).thenReturn(5L);
        when(statisticsManager.getStatsList()).thenReturn(List.of(stats));

        setExportConfig(Map.of("PoolStats", "myPool::connections"));
        bridge.rescan();

        Gauge gauge = registry.find("gemfire.poolstats.connections").gauge();
        assertNotNull(gauge, "Gauge metric should be registered");
        assertEquals(5.0, gauge.value());
    }

    @Test
    void rescanAppliesTags() throws Exception {
        wireUpStatisticsManager();
        StatisticDescriptor puts = mockDescriptor("puts", true);
        Statistics stats = mockStatistics("CachePerfStats", "RegionStats-Orders", 3, puts);
        lenient().when(stats.get(puts)).thenReturn(100L);
        when(statisticsManager.getStatsList()).thenReturn(List.of(stats));

        setExportConfig(Map.of("CachePerfStats", "RegionStats-Orders::puts"));
        bridge.rescan();

        Meter meter = registry.find("gemfire.cacheperfstats.puts").tag("category", "CachePerfStats").tag("name", "RegionStats-Orders").meter();
        assertNotNull(meter, "Meter should have correct tags");
    }

    @Test
    void rescanSkipsWhenExportConfigIsEmpty() throws Exception {
        setExportConfig(Map.of());
        bridge.rescan();

        verifyNoInteractions(distributedSystem);
        assertTrue(registry.getMeters().isEmpty());
    }

    @Test
    void duplicateStatsAreNotReregistered() throws Exception {
        wireUpStatisticsManager();
        StatisticDescriptor gets = mockDescriptor("gets", true);
        Statistics stats = mockStatistics("CachePerfStats", "cachePerfStats", 1, gets);
        lenient().when(stats.get(gets)).thenReturn(10L);
        when(statisticsManager.getStatsList()).thenReturn(List.of(stats));

        setExportConfig(Map.of("CachePerfStats", "cachePerfStats::gets"));
        bridge.rescan();
        bridge.rescan();

        long count = registry.getMeters().stream().filter(m -> m.getId().getName().equals("gemfire.cacheperfstats.gets")).count();
        assertEquals(1, count, "Should only register the metric once");
    }

    @Test
    void typeNameRegexMatchingWorks() throws Exception {
        wireUpStatisticsManager();
        StatisticDescriptor gets = mockDescriptor("gets", true);
        Statistics cacheStats = mockStatistics("CachePerfStats", "cachePerfStats", 1, gets);
        Statistics poolStats = mockStatistics("PoolStats", "myPool", 2, gets);
        lenient().when(cacheStats.get(gets)).thenReturn(1L);
        when(statisticsManager.getStatsList()).thenReturn(List.of(cacheStats, poolStats));

        // Regex that matches only CachePerfStats
        setExportConfig(Map.of("Cache.*", ".*::gets"));
        bridge.rescan();

        assertNotNull(registry.find("gemfire.cacheperfstats.gets").meter());
        assertNull(registry.find("gemfire.poolstats.gets").meter(), "PoolStats should not match Cache.* regex");
    }

    @Test
    void instanceRegexFiltersCorrectly() throws Exception {
        wireUpStatisticsManager();
        StatisticDescriptor gets = mockDescriptor("gets", true);
        Statistics ordersStats = mockStatistics("CachePerfStats", "RegionStats-Orders", 1, gets);
        Statistics pricesStats = mockStatistics("CachePerfStats", "RegionStats-Prices", 2, gets);
        lenient().when(ordersStats.get(gets)).thenReturn(1L);
        when(statisticsManager.getStatsList()).thenReturn(List.of(ordersStats, pricesStats));

        // Only match instances with "Orders" in the textId
        setExportConfig(Map.of("CachePerfStats", ".*Orders::gets"));
        bridge.rescan();

        assertNotNull(registry.find("gemfire.cacheperfstats.gets").tag("name", "RegionStats-Orders").meter());
        assertNull(registry.find("gemfire.cacheperfstats.gets").tag("name", "RegionStats-Prices").meter(), "Prices instance should not match .*Orders regex");
    }

    @Test
    void pipeWorksAsRegexOrInStatsWithoutLeakingIntoInstanceFilter() throws Exception {
        wireUpStatisticsManager();
        StatisticDescriptor gets = mockDescriptor("gets", true);
        StatisticDescriptor puts = mockDescriptor("puts", true);
        Statistics ordersStats = mockStatistics("CachePerfStats", "RegionStats-Orders", 1, gets, puts);
        Statistics pricesStats = mockStatistics("CachePerfStats", "RegionStats-Prices", 2, gets, puts);
        lenient().when(ordersStats.get(gets)).thenReturn(1L);
        lenient().when(ordersStats.get(puts)).thenReturn(2L);
        when(statisticsManager.getStatsList()).thenReturn(List.of(ordersStats, pricesStats));

        // | is regex OR for stats, :: separates instance filter from stats filter
        setExportConfig(Map.of("CachePerfStats", ".*Orders::gets|puts"));
        bridge.rescan();

        // Orders instance: both gets and puts should be registered
        assertNotNull(registry.find("gemfire.cacheperfstats.gets").tag("name", "RegionStats-Orders").meter());
        assertNotNull(registry.find("gemfire.cacheperfstats.puts").tag("name", "RegionStats-Orders").meter());
        // Prices instance: nothing should leak through
        assertNull(registry.find("gemfire.cacheperfstats.gets").tag("name", "RegionStats-Prices").meter(),
                "Pipe should not cause instance filter to be bypassed");
        assertNull(registry.find("gemfire.cacheperfstats.puts").tag("name", "RegionStats-Prices").meter(),
                "Pipe should not cause instance filter to be bypassed");
    }

    @Test
    void multipleStatDescriptorsAreFilteredByCommaList() throws Exception {
        wireUpStatisticsManager();
        StatisticDescriptor gets = mockDescriptor("gets", true);
        StatisticDescriptor puts = mockDescriptor("puts", true);
        StatisticDescriptor misses = mockDescriptor("misses", true);
        Statistics stats = mockStatistics("CachePerfStats", "cachePerfStats", 1, gets, puts, misses);
        lenient().when(stats.get(gets)).thenReturn(1L);
        lenient().when(stats.get(puts)).thenReturn(2L);
        when(statisticsManager.getStatsList()).thenReturn(List.of(stats));

        setExportConfig(Map.of("CachePerfStats", "cachePerfStats::gets,puts"));
        bridge.rescan();

        assertNotNull(registry.find("gemfire.cacheperfstats.gets").meter());
        assertNotNull(registry.find("gemfire.cacheperfstats.puts").meter());
        assertNull(registry.find("gemfire.cacheperfstats.misses").meter(), "misses should not be exported since it's not in the filter");
    }

    @Test
    void nullTextIdDefaultsToDefault() throws Exception {
        wireUpStatisticsManager();
        StatisticDescriptor gets = mockDescriptor("gets", false);
        Statistics stats = mockStatistics("CachePerfStats", null, 1, gets);
        lenient().when(stats.get(gets)).thenReturn(7L);
        when(statisticsManager.getStatsList()).thenReturn(List.of(stats));

        // With no ::, the config is treated as stats regex with instance=.*
        setExportConfig(Map.of("CachePerfStats", "gets"));
        bridge.rescan();

        Meter meter = registry.find("gemfire.cacheperfstats.gets").tag("name", "default").meter();
        assertNotNull(meter, "Null textId should produce tag name=default");
    }

    @Test
    void sanitizeConvertsSpecialCharacters() throws Exception {
        wireUpStatisticsManager();
        StatisticDescriptor desc = mockDescriptor("get_Time", false);
        Statistics stats = mockStatistics("Cache-Perf:Stats", "test", 1, desc);
        lenient().when(stats.get(desc)).thenReturn(0L);
        when(statisticsManager.getStatsList()).thenReturn(List.of(stats));

        setExportConfig(Map.of("Cache-Perf:Stats", "test::get_Time"));
        bridge.rescan();

        // Underscores -> dots, colons -> dots, hyphens -> dots, all lowercase
        Meter meter = registry.find("gemfire.cache.perf.stats.get.time").meter();
        assertNotNull(meter, "Metric name should be sanitized");
    }

    @Test
    void configFilterWithoutDelimiterTreatsEntireValueAsStatsRegex() throws Exception {
        wireUpStatisticsManager();
        StatisticDescriptor gets = mockDescriptor("gets", true);
        Statistics stats = mockStatistics("CachePerfStats", "anyInstance", 1, gets);
        lenient().when(stats.get(gets)).thenReturn(1L);
        when(statisticsManager.getStatsList()).thenReturn(List.of(stats));

        // No :: -> instanceRegex defaults to ".*", statsRegex = "gets"
        setExportConfig(Map.of("CachePerfStats", "gets"));
        bridge.rescan();

        assertNotNull(registry.find("gemfire.cacheperfstats.gets").meter(), "Should match any instance when no pipe in config");
    }

    @Test
    void rescanHandlesExceptionGracefully() throws Exception {
        when(clientCache.getDistributedSystem()).thenThrow(new RuntimeException("connection lost"));

        setExportConfig(Map.of("CachePerfStats", ".*::gets"));

        assertDoesNotThrow(() -> bridge.rescan());
        assertTrue(registry.getMeters().isEmpty());
    }

    @Test
    void multipleExportConfigEntriesAreProcessed() throws Exception {
        wireUpStatisticsManager();
        StatisticDescriptor gets = mockDescriptor("gets", true);
        StatisticDescriptor connections = mockDescriptor("connections", false);
        Statistics cacheStats = mockStatistics("CachePerfStats", "cachePerfStats", 1, gets);
        Statistics poolStats = mockStatistics("PoolStats", "myPool", 2, connections);
        lenient().when(cacheStats.get(gets)).thenReturn(10L);
        lenient().when(poolStats.get(connections)).thenReturn(3L);
        when(statisticsManager.getStatsList()).thenReturn(List.of(cacheStats, poolStats));

        Map<String, String> config = new HashMap<>();
        config.put("CachePerfStats", ".*::gets");
        config.put("PoolStats", ".*::connections");
        setExportConfig(config);
        bridge.rescan();

        assertNotNull(registry.find("gemfire.cacheperfstats.gets").meter());
        assertNotNull(registry.find("gemfire.poolstats.connections").meter());
        assertEquals(2, registry.getMeters().size());
    }
}