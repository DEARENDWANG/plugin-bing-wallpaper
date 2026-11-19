package wang.dearend.bingwallpaper;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListResult;

public interface BingWallpaperFinder {

    Mono<WallpaperVo> today();

    Mono<WallpaperVo> get(String date);

    Flux<WallpaperVo> listByYear(String year);

    Mono<ListResult<WallpaperVo>> listByYear(String year, int page, int size);

    Mono<ListResult<WallpaperVo>> listByYear(String year, int page);

    Flux<YearCount> years();

    String routePath();

    int pageSize();

    String pageTitle();

    class YearCount {
        private final String year;
        private final long count;

        public YearCount(String year, long count) {
            this.year = year;
            this.count = count;
        }

        public String getYear() {
            return year;
        }

        public long getCount() {
            return count;
        }
    }
}