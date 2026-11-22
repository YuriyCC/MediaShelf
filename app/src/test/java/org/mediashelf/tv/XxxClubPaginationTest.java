package org.mediashelf.tv;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

/**
 * Regression tests for user-observed XXXClub "Next Page" pagination.
 * No login, network, user content or live tracker access required.
 */
public class XxxClubPaginationTest {
    private static final String FIRST="https://xxxclub.to/torrents/search/all/Gangbang";
    private static final String SECOND="https://xxxclub.to/torrents/search/all/Gangbang/7296816424212307464";
    private static Document page(String html){
        return Jsoup.parse(html,FIRST);
    }
    private static String next(String html){
        return SitePages.findNext(page(html),FIRST,new HashSet<>(),1);
    }
    @Test public void nextPageTextOutsidePaginationIsDetected(){
        String html="<html><body><div class='bottom-nav'>"+
            "<a href='/torrents/search/all/Gangbang/7296816424212307464'>Next Page</a>"+
            "</div></body></html>";
        assertEquals(SECOND,next(html));
    }
    @Test public void longCursorWorksWithoutPageNumbers(){
        String html="<div class='nav'><a href='"+SECOND+"'>More results</a></div>";
        assertEquals(SECOND,next(html));
    }
    @Test public void nextPageButtonWithDataHrefWorks(){
        String html="<button data-href='/torrents/search/all/Gangbang/7296816424212307464'>Next Page</button>";
        assertEquals(SECOND,next(html));
    }
    @Test public void relativeJavascriptNavigationWorks(){
        String html="<button onclick=\"location.href='/torrents/search/all/Gangbang/7296816424212307464'\">NEXT PAGE »</button>";
        assertEquals(SECOND,next(html));
    }
    @Test public void previousPageIsNeverSelected(){
        String other="https://xxxclub.to/torrents/search/all/Gangbang/7296816424212307465";
        String html="<a href='"+other+"'>Previous Page</a>"+
             "<a href='"+SECOND+"'>Next Page</a>";
        assertEquals(SECOND,next(html));
    }
    @Test public void unrelatedCategoriesAreNotFollowed(){
        String html="<a href='/torrents/browse/7/7296816424212307464'>Next Page</a>";
        assertNull(next(html));
    }
    @Test public void repeatedCursorIsSkipped(){
        Set<String> visited=new HashSet<>();
        visited.add(SECOND);
        Document document=page("<a href='"+SECOND+"'>Next Page</a>");
        assertNull(SitePages.findNext(document,FIRST,visited,1));
    }
    @Test public void secondPageFindsThirdOpaqueCursor(){
        String third="https://xxxclub.to/torrents/search/all/Gangbang/7296816424212307440";
        Document document=Jsoup.parse(
            "<a href='"+FIRST+"'>Previous Page</a><a href='"+third+"'>Next Page</a>",SECOND);
        Set<String> visited=new HashSet<>();
        visited.add(FIRST);
        assertEquals(third,SitePages.findNext(document,SECOND,visited,2));
    }
    @Test public void simpleScriptReferenceWorksWhenOnlyOneCursorPresent(){
        String html="<script>var next='/torrents/search/all/Gangbang/7296816424212307464';</script>";
        assertEquals(SECOND,next(html));
    }
}
