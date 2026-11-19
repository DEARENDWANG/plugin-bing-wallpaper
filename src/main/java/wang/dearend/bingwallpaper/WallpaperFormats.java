package wang.dearend.bingwallpaper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WallpaperFormats {

    public static final String HOST = "https://cn.bing.com";

    public static final String DEFAULT_SIZE = "1920x1080";

    public static final List<String> SIZES = List.of(
        "UHD", "1920x1200", "1920x1080", "1366x768", "1280x720",
        "1024x768", "800x600", "768x1280", "480x800", "400x240");

    private WallpaperFormats() {
    }

    public static String deriveUrlbase(Wallpaper.Spec spec) {
        if (spec.getUrlbase() != null && !spec.getUrlbase().isEmpty()) {
            return spec.getUrlbase();
        }
        String bingUrl = spec.getBingUrl();
        if (bingUrl == null) {
            return null;
        }
        String path = bingUrl.replaceFirst("^https?://(cn|www)\\.bing\\.com", "");
        if (!path.startsWith("/th?id=")) {
            return null;
        }
        return path.replaceFirst("_(UHD|\\d+x\\d+)\\.jpg$", "");
    }

    public static String url(String urlbase, String size) {
        return HOST + urlbase + "_" + size + ".jpg";
    }

    public static Map<String, String> formats(String urlbase) {
        var map = new LinkedHashMap<String, String>();
        if (urlbase == null) {
            return map;
        }
        for (String size : SIZES) {
            map.put(size, url(urlbase, size));
        }
        return map;
    }

    public static void normalize(Wallpaper.Spec spec) {
        String urlbase = deriveUrlbase(spec);
        if (urlbase == null) {
            return;
        }
        spec.setUrlbase(urlbase);
        spec.setBingUrl(url(urlbase, "UHD"));
        spec.setUrl(url(urlbase, DEFAULT_SIZE));
    }
}