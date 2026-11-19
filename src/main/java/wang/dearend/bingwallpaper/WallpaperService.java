package wang.dearend.bingwallpaper;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.Queries;

@Service
public class WallpaperService {

    private final ReactiveExtensionClient client;

    public WallpaperService(ReactiveExtensionClient client) {
        this.client = client;
    }

    public static String todayName() {
        return LocalDate.now(BingFetcher.ZONE).toString();
    }

    public Mono<Boolean> saveIfAbsent(Wallpaper.Spec spec) {
        String name = spec.getDate();
        return client.fetch(Wallpaper.class, name)
            .map(existing -> false)
            .switchIfEmpty(Mono.defer(() -> {
                var wallpaper = new Wallpaper();
                var metadata = new Metadata();
                metadata.setName(name);
                wallpaper.setMetadata(metadata);
                wallpaper.setSpec(spec);
                return client.create(wallpaper).thenReturn(true);
            }));
    }

    public Mono<Boolean> exists(String name) {
        return client.fetch(Wallpaper.class, name).hasElement();
    }

    public Mono<Wallpaper> get(String name) {
        return client.fetch(Wallpaper.class, name);
    }

    public Flux<Wallpaper> listAll(Sort sort) {
        return client.listAll(Wallpaper.class, null, sort);
    }

    public Flux<Wallpaper> listByYear(String year, Sort sort) {
        var options = ListOptions.builder()
            .fieldQuery(Queries.equal("spec.year", year))
            .build();
        return client.listAll(Wallpaper.class, options, sort);
    }

    public Mono<ListResult<Wallpaper>> page(String year, int page, int size) {
        var options = year == null ? null : ListOptions.builder()
            .fieldQuery(Queries.equal("spec.year", year))
            .build();
        return client.listBy(Wallpaper.class, options,
            PageRequestImpl.of(page, size, Sort.by(Sort.Order.desc("spec.date"))));
    }

    public Mono<Long> count() {
        return client.countBy(Wallpaper.class, null);
    }

    public Mono<Long> countByYear(String year) {
        return client.countBy(Wallpaper.class, ListOptions.builder()
            .fieldQuery(Queries.equal("spec.year", year))
            .build());
    }

    public static Map<String, Object> toExportMap(Wallpaper.Spec spec) {
        String urlbase = WallpaperFormats.deriveUrlbase(spec);
        var map = new LinkedHashMap<String, Object>();
        map.put("title", nullToEmpty(spec.getTitle()));
        map.put("caption", nullToEmpty(spec.getCaption()));
        map.put("subtitle", nullToEmpty(spec.getSubtitle()));
        map.put("copyright", nullToEmpty(spec.getCopyright()));
        map.put("description", nullToEmpty(spec.getDescription()));
        map.put("date", nullToEmpty(spec.getDate()));
        map.put("bing_url", urlbase != null
            ? WallpaperFormats.url(urlbase, "UHD") : nullToEmpty(spec.getBingUrl()));
        map.put("url", urlbase != null
            ? WallpaperFormats.url(urlbase, WallpaperFormats.DEFAULT_SIZE) : nullToEmpty(spec.getUrl()));
        map.put("urlbase", nullToEmpty(urlbase));
        map.put("formats", WallpaperFormats.formats(urlbase));
        return map;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}