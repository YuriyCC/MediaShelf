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
    @Test public void twoWordsCanBeSplitAcrossTitleDescriptionAndTags(){
        assertTrue(SearchTerms.matchesContent("Blacked 2026 release",
            "A group scene, Gangbang.",null,"Blacked Gangbang"));
        assertTrue(SearchTerms.matchesContent("Exclusive new movie",
            "Blacked premium studio","Tags: Gangbang HD","blacked gangbang"));
        assertTrue(SearchTerms.matchesContent("Gangbang film",
            null,"Studio: Blacked","blacked gangbang"));
    }
    @Test public void onlyOneWordAcrossAllFieldsDoesNotMatch(){
        assertFalse(SearchTerms.matchesContent("Blacked new movie",
            "Romantic scene","Studio Blacked","Blacked Gangbang"));
        assertFalse(SearchTerms.matchesContent("Gangbang scene",
            "",null,"Blacked Gangbang"));
    }
    @Test public void preserveWholeWordMatchingAcrossFields(){
        assertFalse(SearchTerms.matchesContent("Blacked scene",
            "Gangbanging only",null,"blacked gangbang"));
        assertTrue(SearchTerms.matchesContent("Scene",
            "GANGBANG","blacked","blacked gangbang"));
    }
}
