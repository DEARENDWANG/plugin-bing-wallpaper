package wang.dearend.bingwallpaper;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.ReactiveSettingFetcher;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
public class WallpaperEndpoint implements CustomEndpoint {

    private final WallpaperService service;
    private final BingFetcher fetcher;
    private final ReactiveSettingFetcher settingFetcher;
    private final ReactiveExtensionClient client;
    private final DailyFetchScheduler scheduler;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final long MAX_IMPORT_BYTES = 32L * 1024 * 1024;

    public WallpaperEndpoint(WallpaperService service, BingFetcher fetcher,
                             ReactiveSettingFetcher settingFetcher, ReactiveExtensionClient client,
                             DailyFetchScheduler scheduler) {
        this.service = service;
        this.fetcher = fetcher;
        this.settingFetcher = settingFetcher;
        this.client = client;
        this.scheduler = scheduler;
    }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return route()
            .GET("/wallpapers/stats", this::stats)
            .POST("/wallpapers/import", this::importYear)
            .POST("/wallpapers/import-data", this::importData)
            .GET("/wallpapers/export", this::export)
            .POST("/wallpapers/fetch-today", this::fetchToday)
            .DELETE("/wallpapers", this::purge)
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return new GroupVersion("console.api.bingwallpaper.dearend.wang", "v1alpha1");
    }

    private Mono<ServerResponse> stats(ServerRequest request) {
        int currentYear = LocalDate.now(BingFetcher.ZONE).getYear();
        String today = WallpaperService.todayName();
        var basicMono = settingFetcher.fetch(BasicSetting.GROUP, BasicSetting.class)
            .defaultIfEmpty(new BasicSetting(null, null));
        return basicMono.flatMap(basic ->
            Flux.range(2000, currentYear - 2000 + 1)
                .map(String::valueOf)
                .concatMap(year -> service.countByYear(year)
                    .map(count -> Map.entry(year, count)))
                .filter(entry -> entry.getValue() > 0)
                .collectList()
                .flatMap(entries -> Mono.zip(
                    service.count(),
                    service.page(null, 1, 1)
                        .map(result -> result.getItems().isEmpty()
                            ? "" : result.getItems().get(0).getSpec().getDate())
                        .defaultIfEmpty(""),
                    service.exists(today)
                ).map(tuple -> {
                    var years = new ArrayList<Map<String, Object>>();
                    for (int i = entries.size() - 1; i >= 0; i--) {
                        var entry = entries.get(i);
                        years.add(Map.of("year", entry.getKey(), "count", entry.getValue()));
                    }
                    String latestDate = tuple.getT2();
                    var result = new LinkedHashMap<String, Object>();
                    result.put("total", tuple.getT1());
                    result.put("years", years);
                    result.put("latestDate", latestDate.isEmpty() ? null : latestDate);
                    result.put("todayFetched", tuple.getT3());
                    result.put("startYear", basic.startYearOrDefault());
                    result.put("currentYear", currentYear);
                    result.put("dataSourceConfigured", basic.isDataSourceConfigured());
                    return result;
                }))
        ).flatMap(result -> ServerResponse.ok().contentType(APPLICATION_JSON).bodyValue(result));
    }

    private Mono<ServerResponse> importYear(ServerRequest request) {
        int year = request.queryParam("year")
            .map(value -> {
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "year 参数无效");
                }
            })
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少 year 参数"));
        int currentYear = LocalDate.now(BingFetcher.ZONE).getYear();
        if (year < 2000 || year > currentYear) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "year 超出范围 2000-" + currentYear);
        }
        return settingFetcher.fetch(BasicSetting.GROUP, BasicSetting.class)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "请先在插件设置中配置历史数据接口模板")))
            .flatMap(basic -> {
                if (!basic.isDataSourceConfigured()) {
                    return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "历史数据接口模板未配置或缺少 {year} 占位符"));
                }
                String url = basic.dataUrlTemplate().replace("{year}", String.valueOf(year));
                return BingFetcher.httpGet(url, "Mozilla/5.0 (compatible; BingWallpaperBot/1.0)")
                    .onErrorMap(e -> e instanceof IllegalArgumentException
                        ? new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage())
                        : new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "请求数据接口失败: " + e.getMessage()))
                    .flatMap(this::doImport)
                    .flatMap(counts -> {
                        counts.put("year", year);
                        return ServerResponse.ok().contentType(APPLICATION_JSON).bodyValue(counts);
                    });
            });
    }

    private Mono<ServerResponse> importData(ServerRequest request) {
        return request.bodyToFlux(DataBuffer.class)
            .reduce(new ByteArrayOutputStream(), (baos, buffer) -> {
                int size = buffer.readableByteCount();
                if (baos.size() + size > MAX_IMPORT_BYTES) {
                    DataBufferUtils.release(buffer);
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "文件过大，最大支持 32MB");
                }
                byte[] bytes = new byte[size];
                buffer.read(bytes);
                DataBufferUtils.release(buffer);
                baos.write(bytes, 0, size);
                return baos;
            })
            .flatMap(baos -> doImport(baos.toString(StandardCharsets.UTF_8)))
            .flatMap(counts -> ServerResponse.ok().contentType(APPLICATION_JSON).bodyValue(counts));
    }

    private Mono<Map<String, Object>> doImport(String body) {
        JsonNode array;
        try {
            array = objectMapper.readTree(body);
        } catch (Exception e) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "内容不是有效 JSON"));
        }
        if (!array.isArray()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "应为壁纸 JSON 数组"));
        }
        int currentYear = LocalDate.now(BingFetcher.ZONE).getYear();
        var imported = new AtomicInteger();
        var skipped = new AtomicInteger();
        var invalid = new AtomicInteger();
        return Flux.fromIterable(array)
            .concatMap(item -> {
                String date = item.path("date").asString("");
                if (!date.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    invalid.incrementAndGet();
                    return Mono.empty();
                }
                // 与按接口导入同范围，years/stats 才能按 2000..currentYear 索引统计
                int year = Integer.parseInt(date.substring(0, 4));
                if (year < 2000 || year > currentYear) {
                    invalid.incrementAndGet();
                    return Mono.empty();
                }
                var spec = new Wallpaper.Spec();
                spec.setDate(date);
                String title = item.path("title").asString("");
                spec.setTitle(title);
                String caption = item.path("caption").asString("");
                spec.setCaption(caption.isEmpty() ? title : caption);
                spec.setSubtitle(item.path("subtitle").asString(""));
                spec.setCopyright(item.path("copyright").asString(""));
                spec.setDescription(item.path("description").asString(""));
                spec.setBingUrl(item.path("bing_url").asString(""));
                spec.setUrl(item.path("url").asString(""));
                WallpaperFormats.normalize(spec);
                return service.saveIfAbsent(spec)
                    .doOnNext(created -> {
                        if (created) {
                            imported.incrementAndGet();
                        } else {
                            skipped.incrementAndGet();
                        }
                    });
            })
            .then(Mono.fromSupplier(() -> {
                var result = new LinkedHashMap<String, Object>();
                result.put("imported", imported.get());
                result.put("skipped", skipped.get());
                result.put("invalid", invalid.get());
                result.put("total", array.size());
                return result;
            }));
    }

    private Mono<ServerResponse> export(ServerRequest request) {
        String year = request.queryParam("year").orElse(null);
        Flux<Wallpaper> wallpapers;
        String filename;
        Sort sort = Sort.by(Sort.Order.asc("spec.date"));
        if (year != null && !year.isEmpty()) {
            if (!year.matches("\\d{4}")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "year 参数无效");
            }
            wallpapers = service.listByYear(year, sort);
            filename = "year_" + year + ".json";
        } else {
            wallpapers = service.listAll(sort);
            filename = "bing_wallpapers_all.json";
        }
        return wallpapers.map(w -> WallpaperService.toExportMap(w.getSpec()))
            .collectList()
            .flatMap(items -> {
                String json = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(items);
                return ServerResponse.ok()
                    .contentType(APPLICATION_JSON)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                    .bodyValue(json);
            });
    }

    private Mono<ServerResponse> purge(ServerRequest request) {
        String today = WallpaperService.todayName();
        scheduler.markPurgedToday(today);
        return service.listAll(null)
            .flatMap(client::delete, 8)
            .count()
            .flatMap(count -> markPurged(today).thenReturn(count))
            .flatMap(count -> ServerResponse.ok().contentType(APPLICATION_JSON)
                .bodyValue(Map.of("deleted", count, "purgedDate", today)));
    }

    private Mono<Void> markPurged(String today) {
        return client.fetch(ConfigMap.class, "plugin-bing-wallpaper-configmap")
            .flatMap(configMap -> {
                var data = configMap.getData();
                if (data == null) {
                    return Mono.empty();
                }
                ObjectNode fetch = readGroup(data, FetchSetting.GROUP);
                fetch.put("purgedDate", today);
                data.put(FetchSetting.GROUP, objectMapper.writeValueAsString(fetch));
                return client.update(configMap).then();
            })
            .then();
    }

    private ObjectNode readGroup(Map<String, String> data, String group) {
        String json = data.get(group);
        if (json == null || json.isEmpty()) {
            return objectMapper.createObjectNode();
        }
        return (ObjectNode) objectMapper.readTree(json);
    }

    private Mono<ServerResponse> fetchToday(ServerRequest request) {
        return fetcher.fetchToday()
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "必应官方接口未返回今日壁纸")))
            .flatMap(spec -> service.saveIfAbsent(spec)
                .flatMap(created -> {
                    var result = new LinkedHashMap<String, Object>();
                    result.put("created", created);
                    result.put("wallpaper", WallpaperService.toExportMap(spec));
                    return ServerResponse.ok().contentType(APPLICATION_JSON).bodyValue(result);
                }))
            .onErrorResume(e -> {
                if (e instanceof ResponseStatusException) {
                    return Mono.error(e);
                }
                return Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "抓取今日壁纸失败: " + e.getMessage()));
            });
    }
}