package wang.dearend.bingwallpaper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.theme.TemplateNameResolver;
import run.halo.app.theme.router.ModelConst;

@Configuration(proxyBeanMethods = false)
public class BingWallpaperRouter {

    private final TemplateNameResolver templateNameResolver;
    private final WallpaperService service;
    private final PageRouteConfig pageRouteConfig;

    public BingWallpaperRouter(TemplateNameResolver templateNameResolver, WallpaperService service,
                               PageRouteConfig pageRouteConfig) {
        this.templateNameResolver = templateNameResolver;
        this.service = service;
        this.pageRouteConfig = pageRouteConfig;
    }

    @Bean
    RouterFunction<ServerResponse> bingWallpaperRouterFunction() {
        return RouterFunctions.route(this::matchesConfiguredPath, this::renderPage);
    }

    private boolean matchesConfiguredPath(ServerRequest request) {
        return pageRouteConfig.matches(request.path());
    }

    private Mono<ServerResponse> renderPage(ServerRequest request) {
        var setting = pageRouteConfig.current();
        String yearParam = request.queryParam("year").orElse("");
        int pageParam = request.queryParam("page")
            .map(value -> {
                try {
                    return Math.max(1, Integer.parseInt(value));
                } catch (NumberFormatException e) {
                    return 1;
                }
            })
            .orElse(1);
        int currentYear = LocalDate.now(BingFetcher.ZONE).getYear();
        var yearsMono = Flux.range(2000, currentYear - 2000 + 1)
            .map(String::valueOf)
            .concatMap(year -> service.countByYear(year)
                .map(count -> Map.entry(year, count)))
            .filter(entry -> entry.getValue() > 0)
            .collectList()
            .map(entries -> {
                var years = new ArrayList<Map<String, Object>>();
                for (int i = entries.size() - 1; i >= 0; i--) {
                    var entry = entries.get(i);
                    years.add(Map.of("year", entry.getKey(), "count", entry.getValue()));
                }
                return years;
            });
        var todayMono = service.get(WallpaperService.todayName())
            .map(w -> WallpaperService.toExportMap(w.getSpec()))
            .map(Optional::of)
            .defaultIfEmpty(Optional.empty());
        return Mono.zip(yearsMono, service.count(), todayMono)
            .flatMap(tuple -> {
                var years = tuple.getT1();
                String selected = years.stream()
                    .map(y -> (String) y.get("year"))
                    .filter(y -> y.equals(yearParam))
                    .findFirst()
                    .orElse(years.isEmpty()
                        ? String.valueOf(currentYear)
                        : (String) years.get(0).get("year"));
                int pageSize = setting.sizeOrDefault();
                return service.countByYear(selected)
                    .flatMap(yearTotal -> {
                        int totalPages = (int) Math.max(1,
                            Math.ceil(yearTotal / (double) pageSize));
                        int page = Math.min(pageParam, totalPages);
                        return service.page(selected, page, pageSize)
                            .flatMap(result -> {
                                var wallpapers = result.getItems().stream()
                                    .map(w -> WallpaperService.toExportMap(w.getSpec()))
                                    .toList();
                                var model = new HashMap<String, Object>();
                                model.put("wallpapers", wallpapers);
                                model.put("years", years);
                                model.put("year", selected);
                                model.put("total", tuple.getT2());
                                model.put("today", tuple.getT3().orElse(null));
                                model.put("routePath", setting.slug());
                                model.put("pageTitle", setting.titleOrDefault());
                                model.put("page", page);
                                model.put("totalPages", totalPages);
                                model.put("yearTotal", yearTotal);
                                model.put("pageSize", pageSize);
                                model.put(ModelConst.TEMPLATE_ID,
                                    "plugin:plugin-bing-wallpaper:bing-wallpaper");
                                return templateNameResolver
                                    .resolveTemplateNameOrDefault(request.exchange(),
                                        "bing-wallpaper")
                                    .flatMap(templateName ->
                                        ServerResponse.ok().render(templateName, model));
                            });
                    });
            });
    }
}