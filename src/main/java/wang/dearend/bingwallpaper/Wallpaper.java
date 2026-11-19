package wang.dearend.bingwallpaper;

import io.swagger.v3.oas.annotations.media.Schema;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;

@GVK(group = "bingwallpaper.dearend.wang",
    version = "v1alpha1",
    kind = "Wallpaper",
    plural = "wallpapers",
    singular = "wallpaper")
public class Wallpaper extends AbstractExtension {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Spec spec;

    public Spec getSpec() {
        return spec;
    }

    public void setSpec(Spec spec) {
        this.spec = spec;
    }

    @Schema(name = "WallpaperSpec")
    public static class Spec {

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
            pattern = "\\d{4}-\\d{2}-\\d{2}")
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

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
            this.year = (date != null && date.length() >= 4) ? date.substring(0, 4) : null;
        }

        public String getYear() {
            return year;
        }

        public void setYear(String year) {
            this.year = year;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getCaption() {
            return caption;
        }

        public void setCaption(String caption) {
            this.caption = caption;
        }

        public String getSubtitle() {
            return subtitle;
        }

        public void setSubtitle(String subtitle) {
            this.subtitle = subtitle;
        }

        public String getCopyright() {
            return copyright;
        }

        public void setCopyright(String copyright) {
            this.copyright = copyright;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getBingUrl() {
            return bingUrl;
        }

        public void setBingUrl(String bingUrl) {
            this.bingUrl = bingUrl;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUrlbase() {
            return urlbase;
        }

        public void setUrlbase(String urlbase) {
            this.urlbase = urlbase;
        }
    }
}