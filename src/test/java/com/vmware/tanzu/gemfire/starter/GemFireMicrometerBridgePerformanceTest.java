package com.vmware.tanzu.gemfire.starter;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.geode.StatisticDescriptor;
import org.apache.geode.Statistics;
import org.apache.geode.StatisticsType;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.distributed.internal.InternalDistributedSystem;
import org.apache.geode.internal.statistics.StatisticsManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("performance")
class GemFireMicrometerBridgePerformanceTest {

    private static final int NUM_STAT_TYPES = 10;
    private static final int NUM_DESCRIPTORS_PER_TYPE = 10;
    private static final int NUM_INSTANCES_PER_TYPE = 5;
    private static final int RESCAN_ITERATIONS = 500;

    @Test
    void rescanPerformanceUnderLoad() {
        ClientCache clientCache = mock(ClientCache.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        InternalDistributedSystem distributedSystem = mock(InternalDistributedSystem.class);
        StatisticsManager statisticsManager = mock(StatisticsManager.class);

        when(clientCache.getDistributedSystem()).thenReturn(distributedSystem);
        when(distributedSystem.getStatisticsManager()).thenReturn(statisticsManager);

        // 10 types x 5 instances = 50 Statistics objects, each with 10 descriptors
        List<Statistics> allStats = new ArrayList<>();
        for (int t = 0; t < NUM_STAT_TYPES; t++) {
            String typeName = "StatType" + t;
            StatisticDescriptor[] descriptors = new StatisticDescriptor[NUM_DESCRIPTORS_PER_TYPE];
            for (int d = 0; d < NUM_DESCRIPTORS_PER_TYPE; d++) {
                descriptors[d] = mock(StatisticDescriptor.class);
                lenient().when(descriptors[d].getName()).thenReturn("stat" + d);
                lenient().when(descriptors[d].isCounter()).thenReturn(d % 2 == 0);
                lenient().when(descriptors[d].getDescription()).thenReturn("desc" + d);
            }

            StatisticsType type = mock(StatisticsType.class);
            lenient().when(type.getName()).thenReturn(typeName);
            lenient().when(type.getStatistics()).thenReturn(descriptors);

            for (int i = 0; i < NUM_INSTANCES_PER_TYPE; i++) {
                Statistics stats = mock(Statistics.class);
                lenient().when(stats.getType()).thenReturn(type);
                lenient().when(stats.getTextId()).thenReturn("instance-" + i);
                lenient().when(stats.getUniqueId()).thenReturn((long) (t * NUM_INSTANCES_PER_TYPE + i));
                for (StatisticDescriptor desc : descriptors) {
                    lenient().when(stats.get(desc)).thenReturn(42L);
                }
                allStats.add(stats);
            }
        }

        when(statisticsManager.getStatsList()).thenReturn(allStats);

        // 5 filter entries with regex patterns
        Map<String, String> exportConfig = new HashMap<>();
        for (int i = 0; i < 5; i++) {
            exportConfig.put("StatType" + (i * 2) + ".*", "instance-[0-2]::stat[02468]");
        }

        GemFireMetricBridgeProperties props = new GemFireMetricBridgeProperties();
        props.setExport(exportConfig);
        GemFireMicrometerBridge bridge = new GemFireMicrometerBridge(clientCache, registry, props);

        // Warm up
        for (int i = 0; i < 50; i++) {
            bridge.rescan();
        }

        // Timed run
        long start = System.nanoTime();
        for (int i = 0; i < RESCAN_ITERATIONS; i++) {
            bridge.rescan();
        }
        long elapsed = System.nanoTime() - start;

        double totalMs = elapsed / 1_000_000.0;
        double perRescanMs = totalMs / RESCAN_ITERATIONS;

        System.out.printf("%n=== Rescan Performance ===%n");
        System.out.printf("Stats: %d types x %d instances x %d descriptors = %d objects%n",
                NUM_STAT_TYPES, NUM_INSTANCES_PER_TYPE, NUM_DESCRIPTORS_PER_TYPE, allStats.size());
        System.out.printf("Export filters: %d entries%n", exportConfig.size());
        System.out.printf("Iterations: %d%n", RESCAN_ITERATIONS);
        System.out.printf("Total: %.1f ms%n", totalMs);
        System.out.printf("Per rescan: %.3f ms%n", perRescanMs);
        System.out.printf("============================%n");
    }
}