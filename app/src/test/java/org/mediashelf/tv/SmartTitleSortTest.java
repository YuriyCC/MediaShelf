package org.mediashelf.tv;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public final class SmartTitleSortTest {
    @Test public void stripsMegaPackEvenWithoutSpace(){
        assertEquals("Alice",SmartTitleSort.withoutPack("Mega Pack - Alice"));
        assertEquals("Berta",SmartTitleSort.withoutPack("MegaPack: Berta"));
        assertEquals("Clara",SmartTitleSort.withoutPack("[MEGA-PACK] Clara"));
        assertEquals("Mega Rock",SmartTitleSort.withoutPack("Mega Rock"));
    }
    @Test public void smartSortUsesActualNameAfterPackagingPrefix(){
        List<String> titles=Arrays.asList("Mega Pack - Zoe",
            "MegaPack: Alice","MEGA PACK | Boris");
        assertEquals(Arrays.asList("MegaPack: Alice",
                "MEGA PACK | Boris","Mega Pack - Zoe"),
            SmartTitleSort.sorted(titles,x->x,3));
    }
    @Test public void detectsSharedMultiWordHeading(){
        List<String> titles=Arrays.asList("Studio Mega - Zoe",
            "Studio Mega - Alice","Studio Mega - Boris","Other Film");
        List<String> prefix=SmartTitleSort.sharedPrefix(titles);
        assertEquals(Arrays.asList("studio","mega"),prefix);
        assertEquals("Alice",SmartTitleSort.smartKey("Studio Mega - Alice",prefix));
    }
    @Test public void siteOrderAndOrdinarySortRemainAvailable(){
        List<String> titles=new ArrayList<>(Arrays.asList("Zulu","Alpha","Beta"));
        assertSame(titles,SmartTitleSort.sorted(titles,x->x,0));
        assertEquals(Arrays.asList("Alpha","Beta","Zulu"),
            SmartTitleSort.sorted(titles,x->x,1));
        assertEquals(Arrays.asList("Zulu","Beta","Alpha"),
            SmartTitleSort.sorted(titles,x->x,2));
    }
}