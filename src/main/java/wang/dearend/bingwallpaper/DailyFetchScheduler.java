package wang.dearend.bingwallpaper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.ReactiveSettingFetcher;

@Component
public class DailyFetchScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyFetchScheduler.class);

    private static final Duration RETRY_INTERVAL = Duration.ofMinutes(10);

    private final ReactiveSettingFetcher settingFetcher;
    private final BingFetcher fetcher;
    private final WallpaperService service;
    private final AtomicReference<Instant> lastAttempt = new AtomicReference<>(Instant.EPOCH);
    private volatile String purgedDateMemory;

    public DailyFetchScheduler(ReactiveSettingFetcher settingFetcher, BingFetcher fetcher,
                               WallpaperService service) {
        this.settingFetcher = settingFetcher;
        this.fetcher = fetcher;
        this.service = service;
    }

    public void markPurgedToday(String date) {
        this.purgedDateMemory = date;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 20_000)
    public Mono<Void> tick() {
        return settingFetcher.fetch(FetchSetting.GROUP, FetchSetting.class)
            .flatMap(setting -> {
                if (!setting.isEnabled()) {
                    return Mono.empty();
                }
                LocalTime target;
                try {
                    target = LocalTime.parse(setting.fetchTime());
                } catch (Exception e) {
                    log.warn("每日抓取时间配置无效: {}，应为 HH:mm", setting.fetchTime());
                    return Mono.empty();
                }
                if (LocalTime.now(BingFetcher.ZONE).isBefore(target)) {
                    return Mono.empty();
                }
                String today = WallpaperService.todayName();
                if (today.equals(purgedDateMemory) || today.equals(setting.purgedDate())) {
                    return Mono.empty();
                }
                return service.exists(today).flatMap(exists -> {
                    if (exists) {
                        return Mono.empty();
                    }
                    if (Instant.now().isBefore(lastAttempt.get().plus(RETRY_INTERVAL))) {
                        return Mono.empty();
                    }
                    lastAttempt.set(Instant.now());
                    return fetcher.fetchToday()
                        .flatMap(service::saveIfAbsent)
                        .doOnNext(created -> {
                            if (created) {
                                log.info("已抓取今日 Bing 壁纸: {}", today);
                            }
                        })
                        .doOnError(e -> log.warn("抓取今日 Bing 壁纸失败，{} 分钟后重试: {}",
                            RETRY_INTERVAL.toMinutes(), e.getMessage()))
                        .onErrorResume(e -> Mono.empty())
                        .then();
                });
            })
            .then();
    }
}