package wang.dearend.bingwallpaper;

public record PageSetting(String routePath, Integer pageSize, String pageTitle) {

    public static final String GROUP = "page";

    public String slug() {
        String path = routePath == null ? "" : routePath.trim().replaceAll("^/+|/+$", "");
        return path.isEmpty() ? "bing-wallpaper" : path;
    }

    public String path() {
        return "/" + slug();
    }

    public int sizeOrDefault() {
        if (pageSize == null) {
            return 24;
        }
        return Math.max(1, Math.min(100, pageSize));
    }

    public String titleOrDefault() {
        String title = pageTitle == null ? "" : pageTitle.trim();
        return title.isEmpty() ? "Bing 每日一图" : title;
    }
}