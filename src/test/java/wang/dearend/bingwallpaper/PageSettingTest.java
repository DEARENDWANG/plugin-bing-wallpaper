package wang.dearend.bingwallpaper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PageSettingTest {

    @Test
    void slugFallsBackToDefault() {
        assertEquals("bing-wallpaper", new PageSetting(null, null, null).slug());
        assertEquals("bing-wallpaper", new PageSetting("   ", null, null).slug());
        assertEquals("bing-wallpaper", new PageSetting("///", null, null).slug());
    }

    @Test
    void slugTrimsSlashesAndKeepsMultiLevel() {
        assertEquals("bing-wallpaper", new PageSetting("/bing-wallpaper/", null, null).slug());
        assertEquals("wallpaper/bing", new PageSetting("wallpaper/bing", null, null).slug());
        assertEquals("/wallpaper/bing", new PageSetting("wallpaper/bing", null, null).path());
    }

    @Test
    void pageSizeClampsToBoundaries() {
        assertEquals(24, new PageSetting(null, null, null).sizeOrDefault());
        assertEquals(1, new PageSetting(null, 0, null).sizeOrDefault());
        assertEquals(100, new PageSetting(null, 500, null).sizeOrDefault());
        assertEquals(30, new PageSetting(null, 30, null).sizeOrDefault());
    }

    @Test
    void pageTitleFallsBackAndTrims() {
        assertEquals("Bing 每日一图", new PageSetting(null, null, null).titleOrDefault());
        assertEquals("Bing 每日一图", new PageSetting(null, null, "  ").titleOrDefault());
        assertEquals("每日壁纸", new PageSetting(null, null, " 每日壁纸 ").titleOrDefault());
    }
}