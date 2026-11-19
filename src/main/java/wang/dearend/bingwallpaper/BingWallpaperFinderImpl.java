package wang.dearend.bingwallpaper;

import java.util.LinkedHashMap;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.theme.finders.Finder;

@Finder("bingWallpaperFinder")
public class BingWallpaperFinderImpl implements BingWallpaperFinder {

    private final ReactiveExtensionClient client;
    private final WallpaperService service;
    private final PageRouteConfig pageRouteConfig;

    public BingWallpaperFinderImpl(ReactiveExtensionClient client, WallpaperService service,
                                   PageRouteConfig pageRouteConfig) {
        this.client = client;
        this.service = service;
        this.pageRouteConfig = pageRouteConfig;
    }

    @Override
    public String routePath() {
        return pageRouteConfig.current().slug();
    }

    @Override
    public int pageSize() {
        return pageRouteConfig.current().sizeOrDefault();
    }

    @Override
    public String pageTitle() {
        return pageRouteConfig.current().titleOrDefault();
    }

    @Override
    public Mono<WallpaperVo> today() {
        return get(WallpaperService.todayName());
    }

    @Override
    public Mono<WallpaperVo> get(String date) {
        return client.fetch(Wallpaper.class, date).map(w -> WallpaperVo.from(w.getSpec()));
    }

    @Override
    public Flux<WallpaperVo> listByYear(String year) {
        return service.listByYear(year, Sort.by(Sort.Order.desc("spec.date")))
            .map(w -> WallpaperVo.from(w.getSpec()));
    }

    @Override
    public Mono<ListResult<WallpaperVo>> listByYear(String year, int page, int size) {
        return service.page(year, page, size)
            .map(result -> new ListResult<>(
                result.getPage(), result.getSize(), result.getTotal(),
                result.getItems().stream().map(w -> WallpaperVo.from(w.getSpec())).toList()));
    }

    @Override
    public Mono<ListResult<WallpaperVo>> listByYear(String year, int page) {
        return listByYear(year, page, pageSize());
    }

    @Override
    public Flux<YearCount> years() {
        return service.listAll(Sort.by(Sort.Order.desc("spec.date")))
            .collectList()
            .flatMapMany(all -> {
                var counts = new LinkedHashMap<String, Long>();
                for (Wallpaper wallpaper : all) {
                    counts.merge(wallpaper.getSpec().getYear(), 1L, Long::sum);
                }
                return Flux.fromIterable(counts.entrySet())
                    .map(e -> new YearCount(e.getKey(), e.getValue()));
            });
    }
}