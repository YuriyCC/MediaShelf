package org.mediashelf.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public final class SearchTermsTest {
    @Test public void twoWordsCanAppearInEitherOrderAndApart(){
        assertTrue(SearchTerms.matchesTitle("Dorcel presents a new Gangbang feature","dorcel gangbang"));
        assertTrue(SearchTerms.matchesTitle("GANGBANG 2026 - MARC DORCEL","dorcel gangbang"));
        assertTrue(SearchTerms.matchesTitle("Dorcel - Exclusive - Gangbang","dorcel gangbang"));
    }
    @Test public void onlyOneWordDoesNotPass(){
        assertFalse(SearchTerms.matchesTitle("Dorcel presents a new scene","dorcel gangbang"));
        assertFalse(SearchTerms.matchesTitle("Gangbang starring performers","dorcel gangbang"));
        assertFalse(SearchTerms.matchesTitle("Something completely different","dorcel gangbang"));
    }
    @Test public void matchCompleteWordsNotSubstrings(){
        assertFalse(SearchTerms.matchesTitle("Dorcel Gangbanging video","dorcel gangbang"));
        assertFalse(SearchTerms.matchesTitle("SuperDorcel Gangbang","dorcel gangbang"));
        assertTrue(SearchTerms.matchesTitle("Dorcel/Gangbang 1080p","dorcel gangbang"));
    }
    @Test public void whitespaceAndRepeatedWordsAreHandled(){
        assertTrue(SearchTerms.matchesTitle("DORCEL 2026 GANGBANG","  dorcel   gangbang "));
        assertTrue(SearchTerms.matchesTitle("Dorcel Gangbang","dorcel dorcel gangbang"));
        assertFalse(SearchTerms.needsAndFilter("dorcel dorcel"));
    }
    @Test public void upstreamSearchUsesOnlySeedWordForMultiTermQueries(){
        assertEquals("dorcel",SearchTerms.upstreamQuery("Dorcel Gangbang"));
        assertEquals("Gangbang",SearchTerms.upstreamQuery("  Gangbang  "));
        assertEquals("",SearchTerms.upstreamQuery(null));
    }
    @Test public void singleWordDoesNotChangeCurrentSearchBehavior(){
        assertTrue(SearchTerms.matchesTitle("Unrelated title","Dorcel"));
        assertTrue(SearchTerms.matchesTitle("Unrelated title",""));
    }
}
