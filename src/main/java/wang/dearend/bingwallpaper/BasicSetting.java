package wang.dearend.bingwallpaper;

public record BasicSetting(String dataUrlTemplate, Integer startYear) {

    public static final String GROUP = "basic";

    public boolean isDataSourceConfigured() {
        return dataUrlTemplate != null && dataUrlTemplate.contains("{year}");
    }

    public int startYearOrDefault() {
        return startYear == null ? 2019 : startYear;
    }
}