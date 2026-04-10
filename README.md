# GemFire Micrometer Bridge Starter

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.0-brightgreen)](https://spring.io/projects/spring-boot)
[![GemFire](https://img.shields.io/badge/GemFire-10.x-blue)](https://tanzu.vmware.com/gemfire)

A high-performance, opt-in Spring Boot Starter that bridges **Apache Geode/VMware GemFire** internal statistics into the **Micrometer** ecosystem. Surface your client-side latency, pool usage, and cache performance metrics directly to **Prometheus** and **JMX** with zero boilerplate.

---

## 🚀 Features

* **Opt-in Activation**: Use `@EnableGemFireMicrometerBridge` to activate only where needed.
* **Dynamic Rescanning**: Automatically detects new Regions, Pools, or CQs created after application startup.
* **Regex Filtering**: Fine-grained control over which `StatisticsTypes`, `Instances`, and `Descriptors` are exported via standard properties.
* **Smart Mapping**: Automatically converts GemFire Counters to Micrometer `FunctionCounters` and Gauges to standard `Gauges`.
* **Multi-Backend Support**: Broadcasts metrics to Prometheus (pull-based) and JMX (push-based) simultaneously through the Micrometer Composite Registry.

---

## 📦 Installation

### 1. Build and Publish
In the root of the bridge library project, run:
```bash
./gradlew clean publishToMavenLocal
```

### 2. Add Dependency
Add the following to your GemFire Client application's build.gradle:

```
dependencies {
    implementation 'com.vmware.tanzu:gemfire-micrometer-starter:1.0.0'
    
    // Micrometer registries for your desired backends
    implementation 'io.micrometer:micrometer-registry-prometheus'
    implementation 'io.micrometer:micrometer-registry-jmx'
}
```

## 🛠 Usage
### 1. Enable the Bridge
Annotate your main Spring Boot Application class or a Configuration class to activate the bridge:

```
Java
@SpringBootApplication
@EnableGemFireMicrometerBridge // <--- Activates the bridge logic
public class GemFireClientApplication {
    public static void main(String[] args) {
        SpringApplication.run(GemFireClientApplication.class, args);
    }
}
```

### 2. Configure Metrics
Control exactly what gets exported via application.properties.
Format: 'TypeRegex' : 'InstanceRegex | Stat1,Stat2,Stat3'

## Properties

```
# Global Toggle & Rescan Interval
gemfire.metrics.bridge.enabled=true
gemfire.metrics.rescan-interval=30000

# Backend Toggles
management.jmx.metrics.export.enabled=true
management.prometheus.metrics.export.enabled=true

# Export Configuration (Map syntax for .properties)
gemfire.metrics.export={ \
  'CachePerfStats': 'RegionStats-MarketPrices|gets,puts,putTime,getTime', \
  'PoolStats': '.*|connections,activeConnects', \
  'ClientStats': '.*|sentBytes,receivedBytes' \
}
```

## 🔍 Observability
Prometheus
Metrics are available at the standard Actuator endpoint. Prometheus automatically treats . in the name as _ for compatibility.
```
GET /actuator/prometheus
```

Example Output:

```Plaintext
gemfire_cacheperfstats_gets_total{textId="RegionStats-MarketPrices",type="CachePerfStats"} 1250.0
gemfire_cacheperfstats_puttime_seconds_count{textId="RegionStats-MarketPrices",type="CachePerfStats"} 450.0
```

JMX (JConsole / VisualVM)
Metrics are pushed to the MBean server under the metrics domain. 

## 🛠 Troubleshooting
Empty Map: If your debug logs show exportConfig is {}, check the single quotes and backslashes in your .properties file.

Missing Stats: Ensure @EnableStatistics is active on your client cache and spring.data.gemfire.stats.enable-statistics=true is set.

Regex Debugging: Set logging.level.com.vmware.tanzu.gemfire.starter=DEBUG to see exactly which textId and typeName the bridge is finding during its rescan.

Developed with ☕ and 🤘 for the GemFire Community.
