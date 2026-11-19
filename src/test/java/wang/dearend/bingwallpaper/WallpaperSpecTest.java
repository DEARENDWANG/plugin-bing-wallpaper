package wang.dearend.bingwallpaper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class WallpaperSpecTest {

    @Test
    void setDateDerivesYear() {
        var spec = new Wallpaper.Spec();
        spec.setDate("2026-11-13");
        assertEquals("2026", spec.getYear());
    }

    @Test
    void invalidDateLeavesYearNull() {
        var spec = new Wallpaper.Spec();
        spec.setDate("202");
        assertNull(spec.getYear());
        spec.setDate(null);
        assertNull(spec.getYear());
    }
}