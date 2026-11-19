package wang.dearend.bingwallpaper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;
import run.halo.app.plugin.PluginConfigUpdatedEvent;
import run.halo.app.plugin.ReactiveSettingFetcher;

@Component
public class PageRouteConfig {

    private static final Logger log = LoggerFactory.getLogger(PageRouteConfig.class);

    private final ReactiveSettingFetcher settingFetcher;
    private volatile PageSetting current = new PageSetting(null, null, null);

    public PageRouteConfig(ReactiveSettingFetcher settingFetcher) {
        this.settingFetcher = settingFetcher;
        reload();
    }

    public PageSetting current() {
        return current;
    }

    public boolean matches(String requestPath) {
        String path = current.path();
        return requestPath.equals(path) || requestPath.equals(path + "/");
    }

    @EventListener
    public void onConfigUpdated(PluginConfigUpdatedEvent event) {
        if (event.getNewSettingValues().containsKey(PageSetting.GROUP)) {
            reload();
        }
    }

    private void reload() {
        settingFetcher.fetch(PageSetting.GROUP, PageSetting.class)
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(
                setting -> current = setting,
                e -> log.warn("加载前台页面配置失败，使用默认值: {}", e.getMessage()));
    }
}