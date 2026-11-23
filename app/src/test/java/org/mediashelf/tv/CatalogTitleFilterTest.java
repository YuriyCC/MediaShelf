package org.mediashelf.tv;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public final class CatalogTitleFilterTest {
    @Test public void onlyRelevantLoadedCardsRemain(){
        List<String> titles=Arrays.asList("Mega Pack - Maria",
            "Mega Pack - Anna","MegaPack Anna special",
            "Mega Pack - Vera");
        assertEquals(Arrays.asList("Mega Pack - Anna","MegaPack Anna special"),
            CatalogTitleFilter.apply(titles,s->s,"anna"));
        assertEquals(Arrays.asList("Mega Pack - Maria"),
            CatalogTitleFilter.apply(titles,s->s,"MARIA"));
    }
    @Test public void resetRestoresOriginalList(){
        List<String> titles=Arrays.asList("A","B");
        assertSame(titles,CatalogTitleFilter.apply(titles,s->s,""));
        assertSame(titles,CatalogTitleFilter.apply(titles,s->s,null));
    }
    @Test public void cyrillicAndYoAreMatched(){
        assertTrue(CatalogTitleFilter.matches("Сборник Алёна","АЛЕНА"));
        assertFalse(CatalogTitleFilter.matches("Сборник Алина","Алёна"));
        assertFalse(CatalogTitleFilter.matches(null,"Anna"));
    }
}
