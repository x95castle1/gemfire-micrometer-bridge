package com.vmware.tanzu.gemfire.starter;

import org.springframework.context.annotation.Import;
import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(GemFireMicrometerAutoConfiguration.class)
public @interface EnableGemFireMicrometerBridge {
}
