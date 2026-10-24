package io.github.tuck1297.kioskpos.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Background jobs: idempotency key cleanup. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
