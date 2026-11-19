package wang.dearend.bingwallpaper;

public record FetchSetting(Boolean enabled, String fetchTime, String purgedDate) {

    public static final String GROUP = "fetch";

    public boolean isEnabled() {
        return enabled == null || enabled;
    }
}