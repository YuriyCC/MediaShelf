package org.mediashelf.tv;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class BannerSettingsTest {
    @Test public void defaultIsAllNewReleases() {
        assertEquals(Arrays.asList(0),BannerSettings.parse(null));
        assertEquals(Arrays.asList(0),BannerSettings.parse("0"));
    }
    @Test public void multipleCategoriesWithoutAllAreRetained(){
        assertEquals(Arrays.asList(6090,10001,6070),
            BannerSettings.parse("6070,10001,6090"));
    }
    @Test public void invalidCodesAreIgnored(){
        assertEquals(Arrays.asList(0),BannerSettings.parse("x,999,-4"));
    }
    @Test public void selectingAllExcludesOtherCategories(){
        assertEquals(Arrays.asList(0),BannerSettings.parse("0,6090,6070"));
    }
    @Test public void canSerializeMultiselect(){
        boolean[] checked=new boolean[BannerSettings.CODES.length];
        checked[1]=true;checked[3]=true;
        assertEquals("6090,10002",BannerSettings.serialize(checked));
    }
    @Test public void blankSelectionDefaultsToAll(){
        assertEquals("0",BannerSettings.serialize(new boolean[BannerSettings.CODES.length]));
    }
    @Test public void normalizesKeyword(){
        assertEquals("Gangbang",BannerSettings.normalizeQuery("  Gangbang \n "));
        assertEquals(90,BannerSettings.normalizeQuery("x".repeat(140)).length());
    }
    @Test public void summaryIncludesSearchTheme(){
        assertTrue(BannerSettings.summary("0","Gangbang").contains("Gangbang"));
    }
}
