package org.mediashelf.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public class FavoritesStoreTest {
    @Test public void sameReleaseHasSameKeyDespiteCaseAndSpaces(){
        assertEquals(FavoritesStore.key("XXXClub","https://t.test/t/147","m1","d1","Title"),
            FavoritesStore.key(" xxxclub ","HTTPS://T.TEST/T/147","m2","d2","Different title"));
    }
    @Test public void identicalTitleFromDifferentSourcesIsDistinct(){
        assertNotEquals(
            FavoritesStore.key("xxxclub","","","","Film A"),
            FavoritesStore.key("pornolab","","","","Film A"));
    }
    @Test public void detailsUriHasPriorityOverTransientMagnet(){
        assertEquals(
            FavoritesStore.key("xxxclub","https://x/item/34","magnet:?a","/a","Film"),
            FavoritesStore.key("xxxclub","https://x/item/34","magnet:?b","/b","Film"));
    }
    @Test public void fallbackToTitleWhenNoUris(){
        assertEquals("source\nmy film",
            FavoritesStore.key("SOURCE","","","","My Film"));
    }
    @Test public void handlesNullValues(){
        assertEquals("\n",FavoritesStore.key(null,null,null,null,null));
    }
}
