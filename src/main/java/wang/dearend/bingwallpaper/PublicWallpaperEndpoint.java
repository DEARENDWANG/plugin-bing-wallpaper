package wang.dearend.bingwallpaper;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

@Component
public class PublicWallpaperEndpoint implements CustomEndpoint {

    private final WallpaperService service;

    public PublicWallpaperEndpoint(WallpaperService service) {
        this.service = service;
    }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return route()
            .GET("/wallpapers/today", this::today)
            .GET("/wallpapers/years", this::years)
            .GET("/wallpapers", this::list)
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return new GroupVersion("api.bingwallpaper.dearend.wang", "v1alpha1");
    }

    private Mono<ServerResponse> today(ServerRequest request) {
        return service.get(WallpaperService.todayName())
            .flatMap(w -> ServerResponse.ok().contentType(APPLICATION_JSON)
                .bodyValue(WallpaperVo.from(w.getSpec())))
            .switchIfEmpty(ServerResponse.notFound().build());
    }

    private Mono<ServerResponse> years(ServerRequest request) {
        return service.listAll(Sort.by(Sort.Order.desc("spec.date")))
            .collectList()
            .flatMap(all -> {
                var counts = new LinkedHashMap<String, Integer>();
                for (Wallpaper wallpaper : all) {
                    counts.merge(wallpaper.getSpec().getYear(), 1, Integer::sum);
                }
                var result = new ArrayList<Map<String, Object>>();
                counts.forEach((year, count) -> result.add(Map.of("year", year, "count", count)));
                return ServerResponse.ok().contentType(APPLICATION_JSON).bodyValue(result);
            });
    }

    private Mono<ServerResponse> list(ServerRequest request) {
        String year = request.queryParam("year").orElse(null);
        if (year != null && !year.matches("\\d{4}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "year 参数无效");
        }
        int page = intParam(request, "page", 1);
        int size = Math.min(100, intParam(request, "size", 20));
        if (page < 1 || size < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page/size 参数无效");
        }
        return service.page(year, page, size)
            .flatMap(result -> {
                var body = new LinkedHashMap<String, Object>();
                body.put("page", result.getPage());
                body.put("size", result.getSize());
                body.put("total", result.getTotal());
                body.put("items", result.getItems().stream()
                    .map(w -> WallpaperVo.from(w.getSpec())).toList());
                return ServerResponse.ok().contentType(APPLICATION_JSON).bodyValue(body);
            });
    }

    private static int intParam(ServerRequest request, String name, int defaultValue) {
        return request.queryParam(name)
            .map(value -> {
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, name + " 参数无效");
                }
            })
            .orElse(defaultValue);
    }
}