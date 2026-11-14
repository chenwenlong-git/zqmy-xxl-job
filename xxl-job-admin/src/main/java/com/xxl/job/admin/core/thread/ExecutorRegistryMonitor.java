package com.xxl.job.admin.core.thread;

import com.xxl.job.admin.dao.XxlJobRegistryDao;
import com.xxl.job.core.enums.RegistryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.Date;

/**
 * Alerts when a configured executor has no active registration.
 */
@Component
@ConditionalOnProperty(prefix = "xxl.job.executor.monitor", name = "enabled", havingValue = "true")
public class ExecutorRegistryMonitor {

    private static final Logger logger = LoggerFactory.getLogger(ExecutorRegistryMonitor.class);
    private static final String EXCEPTION_INFO = "xxl-job任务调度中心异常，执行器未能定时执行任务！";

    @Resource
    private XxlJobRegistryDao registryDao;
    @Value("${xxl.job.executor.monitor.enabled:}")
    private String enabled;
    @Value("${xxl.job.executor.monitor.targets:}")
    private String targets;
    @Value("${xxl.job.executor.monitor.alert-url:}")
    private String alertUrl;
    @Value("${xxl.job.executor.monitor.log-url:}")
    private String logUrl;
    private final RestTemplate restTemplate = new RestTemplateBuilder()
            .setConnectTimeout(Duration.ofSeconds(3))
            .setReadTimeout(Duration.ofSeconds(5))
            .build();

    @Scheduled(cron = "0 20 3,6,10,15,20,22 * * ?", zone = "Asia/Shanghai")
    public void check() {
        if (targets.trim().isEmpty()) {
            return;
        }
        Date now = new Date();
        for (String target : targets.split(",")) {
            String[] parts = target.trim().split(":", 2);
            String appName = parts[0].trim();
            String title = parts[1].trim();
            try {
                if (!registryDao.isRegistered(appName, RegistryConfig.DEAD_TIMEOUT, now)) {
                    MultiValueMap<String, String> form = new LinkedMultiValueMap<String, String>();
                    form.add("title", title);
                    form.add("logUrl", logUrl);
                    form.add("exceptionInfo", EXCEPTION_INFO);
                    restTemplate.postForEntity(alertUrl, form, String.class);
                    logger.warn("Executor {} has no active registration; alert request completed", appName);
                }
            } catch (Exception e) {
                logger.error("Executor registration check or alert failed for {}", appName, e);
            }
        }
    }
}
