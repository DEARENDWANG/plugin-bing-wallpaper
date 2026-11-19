package wang.dearend.bingwallpaper;

import java.util.Map;

public class WallpaperVo {

    private String date;
    private String year;
    private String title;
    private String caption;
    private String subtitle;
    private String copyright;
    private String description;
    private String bingUrl;
    private String url;
    private String urlbase;
    private Map<String, String> formats;

    public static WallpaperVo from(Wallpaper.Spec spec) {
        String urlbase = WallpaperFormats.deriveUrlbase(spec);
        var vo = new WallpaperVo();
        vo.date = spec.getDate();
        vo.year = spec.getYear();
        vo.title = spec.getTitle();
        vo.caption = spec.getCaption();
        vo.subtitle = spec.getSubtitle();
        vo.copyright = spec.getCopyright();
        vo.description = spec.getDescription();
        vo.bingUrl = urlbase != null
            ? WallpaperFormats.url(urlbase, "UHD") : spec.getBingUrl();
        vo.url = urlbase != null
            ? WallpaperFormats.url(urlbase, WallpaperFormats.DEFAULT_SIZE) : spec.getUrl();
        vo.urlbase = urlbase;
        vo.formats = WallpaperFormats.formats(urlbase);
        return vo;
    }

    public String getDate() {
        return date;
    }

    public String getYear() {
        return year;
    }

    public String getTitle() {
        return title;
    }

    public String getCaption() {
        return caption;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getCopyright() {
        return copyright;
    }

    public String getDescription() {
        return description;
    }

    public String getBingUrl() {
        return bingUrl;
    }

    public String getUrl() {
        return url;
    }

    public String getUrlbase() {
        return urlbase;
    }

    public Map<String, String> getFormats() {
        return formats;
    }
}