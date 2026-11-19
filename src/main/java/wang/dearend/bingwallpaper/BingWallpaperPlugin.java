package wang.dearend.bingwallpaper;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;
import run.halo.app.extension.Scheme;
import run.halo.app.extension.SchemeManager;
import run.halo.app.extension.index.IndexSpecs;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

@Component
@EnableScheduling
public class BingWallpaperPlugin extends BasePlugin {

    private final SchemeManager schemeManager;

    public BingWallpaperPlugin(PluginContext pluginContext, SchemeManager schemeManager) {
        super(pluginContext);
        this.schemeManager = schemeManager;
    }

    @Override
    public void start() {
        schemeManager.register(Wallpaper.class, indexSpecs -> {
            indexSpecs.add(IndexSpecs.<Wallpaper, String>single("spec.date", String.class)
                .indexFunc(w -> w.getSpec().getDate()));
            indexSpecs.add(IndexSpecs.<Wallpaper, String>single("spec.year", String.class)
                .indexFunc(w -> w.getSpec().getYear()));
        });
    }

    @Override
    public void stop() {
        schemeManager.unregister(Scheme.buildFromType(Wallpaper.class));
    }
}