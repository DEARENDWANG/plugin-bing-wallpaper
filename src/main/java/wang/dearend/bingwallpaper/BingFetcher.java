package wang.dearend.bingwallpaper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class BingFetcher {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final String BING_API =
        "https://cn.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&pid=opal&mbl=1&desc=1&mkt=zh-cn";

    private static final String MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 16; PJZ110 Build/BP2A.250605.015; ) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Version/4.0 Chrome/138.0.7204.179 Mobile Safari/537.36 "
            + "BingSapphire/32.6.2110003561";

    private static final Pattern COPYRIGHT_PATTERN = Pattern.compile("^(.*?)\\s*\\(([^)]+)\\)$");

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public static Mono<String> httpGet(String url, String userAgent) {
        return Mono.fromCallable(() -> {
            String current = url;
            for (int hop = 0; hop < 5; hop++) {
                OutboundGuard.validate(current);
                var request = HttpRequest.newBuilder(URI.create(current))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", userAgent)
                    .header("Accept", "application/json")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .GET()
                    .build();
                var response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                int code = response.statusCode();
                if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                    String location = response.headers().firstValue("Location")
                        .orElseThrow(() -> new IOException("重定向缺少 Location"));
                    current = URI.create(current).resolve(location).toString();
                    continue;
                }
                if (code != 200) {
                    throw new IOException("HTTP " + code + " for " + current);
                }
                return response.body();
            }
            throw new IOException("重定向次数过多");
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Wallpaper.Spec> fetchToday() {
        return fetchFromBing();
    }

    private Mono<Wallpaper.Spec> fetchFromBing() {
        return httpGet(BING_API, MOBILE_UA).mapNotNull(body -> {
            JsonNode image = objectMapper.readTree(body).path("images").path(0);
            if (image.isMissingNode()) {
                return null;
            }
            String urlbase = image.path("urlbase").asString("");
            String enddate = image.path("enddate").asString("");
            if (urlbase.isEmpty() || enddate.length() != 8) {
                return null;
            }
            String year = enddate.substring(0, 4);
            String month = enddate.substring(4, 6);
            String day = enddate.substring(6, 8);

            var spec = new Wallpaper.Spec();
            spec.setDate(year + "-" + month + "-" + day);
            String title = image.path("title").asString("");
            spec.setTitle(title);
            spec.setCaption(title);

            String copyrightRaw = image.path("copyright").asString("");
            Matcher matcher = COPYRIGHT_PATTERN.matcher(copyrightRaw);
            if (matcher.matches()) {
                spec.setSubtitle(matcher.group(1).trim());
                String copyrightPart = matcher.group(2).trim();
                spec.setCopyright(copyrightPart.startsWith("©") ? copyrightPart : "© " + copyrightPart);
            } else {
                spec.setSubtitle(copyrightRaw);
                spec.setCopyright("");
            }

            spec.setDescription(joinNonEmpty(
                image.path("desc").asString(""),
                image.path("desc2").asString(""),
                image.path("desc3").asString("")));
            spec.setBingUrl(WallpaperFormats.url(urlbase, "UHD"));
            spec.setUrl(WallpaperFormats.url(urlbase, WallpaperFormats.DEFAULT_SIZE));
            spec.setUrlbase(urlbase);
            return spec;
        });
    }

    private static String joinNonEmpty(String... parts) {
        var sb = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append("\n\n");
                }
                sb.append(part);
            }
        }
        return sb.toString();
    }
}