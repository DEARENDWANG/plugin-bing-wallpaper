package wang.dearend.bingwallpaper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class WallpaperFormatsTest {

    @Test
    void derivesUrlbaseFromOfficialUhdUrl() {
        var spec = new Wallpaper.Spec();
        spec.setBingUrl("https://cn.bing.com/th?id=OHR.AbrahamCanada_ZH-CN0344365912_UHD.jpg");
        assertEquals("/th?id=OHR.AbrahamCanada_ZH-CN0344365912",
            WallpaperFormats.deriveUrlbase(spec));
    }

    @Test
    void derivesUrlbaseFromWwwHostAndSizedSuffix() {
        var spec = new Wallpaper.Spec();
        spec.setBingUrl("https://www.bing.com/th?id=OHR.Test_1920x1080.jpg");
        assertEquals("/th?id=OHR.Test", WallpaperFormats.deriveUrlbase(spec));
    }

    @Test
    void explicitUrlbaseWins() {
        var spec = new Wallpaper.Spec();
        spec.setUrlbase("/th?id=OHR.Explicit");
        spec.setBingUrl("https://cn.bing.com/th?id=OHR.Other_UHD.jpg");
        assertEquals("/th?id=OHR.Explicit", WallpaperFormats.deriveUrlbase(spec));
    }

    @Test
    void nonOfficialUrlYieldsNull() {
        var spec = new Wallpaper.Spec();
        spec.setBingUrl("https://example.com/img/cn/fhd/2026/11/13.jpg");
        assertNull(WallpaperFormats.deriveUrlbase(spec));
        assertNull(WallpaperFormats.deriveUrlbase(new Wallpaper.Spec()));
    }

    @Test
    void normalizeRewritesAllLinksToOfficial() {
        var spec = new Wallpaper.Spec();
        spec.setBingUrl("https://www.bing.com/th?id=OHR.Test_UHD.jpg");
        spec.setUrl("https://bing.npanuhin.me/CN/zh/2026-01-01.jpg");
        WallpaperFormats.normalize(spec);
        assertEquals("/th?id=OHR.Test", spec.getUrlbase());
        assertEquals("https://cn.bing.com/th?id=OHR.Test_UHD.jpg", spec.getBingUrl());
        assertEquals("https://cn.bing.com/th?id=OHR.Test_1920x1080.jpg", spec.getUrl());
    }

    @Test
    void normalizeKeepsValuesWhenNoOfficialSource() {
        var spec = new Wallpaper.Spec();
        spec.setBingUrl("");
        spec.setUrl("https://example.com/a.jpg");
        WallpaperFormats.normalize(spec);
        assertNull(spec.getUrlbase());
        assertEquals("https://example.com/a.jpg", spec.getUrl());
    }

    @Test
    void formatsCoverAllTenSizesInOrder() {
        var formats = WallpaperFormats.formats("/th?id=OHR.Test");
        assertEquals(List.of("UHD", "1920x1200", "1920x1080", "1366x768", "1280x720",
            "1024x768", "800x600", "768x1280", "480x800", "400x240"),
            List.copyOf(formats.keySet()));
        assertEquals("https://cn.bing.com/th?id=OHR.Test_UHD.jpg", formats.get("UHD"));
        assertTrue(WallpaperFormats.formats(null).isEmpty());
    }
}