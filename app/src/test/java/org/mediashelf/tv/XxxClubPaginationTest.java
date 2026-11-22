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
    /**
     * This replicates a captured search page (November 2026): six off-screen
     * fake "Next Page" anchors and one actual visible data-no-instant anchor.
     * The fake and real links have different opaque cursor values.
     */
    @Test public void searchSkipsSixHiddenBaitLinksAndUsesRealNextPage(){
        String fake="/torrents/search/all/Gangbang/839183183190101";
        StringBuilder html=new StringBuilder("<div class='browsepagination'>");
        for(int i=0;i<3;i++){
            html.append("<a class='page-link' href='").append(fake)
                .append("' title='Next Page' style='position:absolute; left:-9999px;'>Next Page</a>");
        }
        html.append("<a href='/torrents/search/all/Gangbang/7296816424212307464' ")
            .append("title='Next Page' data-no-instant>Next Page</a>");
        for(int i=0;i<3;i++){
            html.append("<a class='page-link' href='").append(fake)
                .append("' title='Next Page' style='position:absolute; left:-9999px;'>Next Page</a>");
        }
        html.append("</div>");
        assertEquals(SECOND,next(html.toString()));
    }
    @Test public void hiddenNextPageIsNotARealNextPage(){
        String html="<a href='"+SECOND+"' title='Next Page' style='left:-9999px;position:absolute'>Next Page</a>";
        assertNull(next(html));
    }
    @Test public void visibleLinkPreferredOverHiddenEvenIfHiddenFirst(){
        String html="<div class='browsepagination'>"+
            "<a href='/torrents/search/all/Gangbang/839183183190101' "+
            "style='position:absolute;left:-99999px' title='Next Page'>Next Page</a>"+
            "<a href='"+SECOND+"' title='Next Page' data-no-instant>Next Page</a>"+
            "</div>";
        assertEquals(SECOND,next(html));
    }
    @Test public void categoryCursorWorksForAllEightNativeSections(){
        final int[] nativeCodes={0,1,2,3,4,5,6,7};
        for(int code:nativeCodes){
            String first="https://xxxclub.to/torrents/browse/"+code;
            String second=first+"/7296816424212307464";
            String bait=first+"/839183183190101";
            String html="<div class='browsepagination'>"+
                "<a title='Next Page' style='left:-9999px' href='"+bait+"'>Next Page</a>"+
                "<a data-no-instant title='Next Page' href='"+second+"'>Next Page</a>"+
                "</div>";
            assertEquals("category code "+code,second,
                SitePages.findNext(Jsoup.parse(html,first),first,new HashSet<>(),1));
        }
    }
    @Test public void nativeCategoryIdsCoverTheEightDistinctSiteSections(){
        int[] ids={6080,10001,10002,6090,6045,6060,6070,6050};
        for(int nativeId=0;nativeId<ids.length;nativeId++){
            assertEquals("site category "+nativeId,nativeId,
                SitePages.nativeCategory(ids[nativeId]));
        }
    }
    @Test public void searchPaginationRejectsOtherQueryCursors(){
        String html="<a href='/torrents/search/all/Different/7296816424212307464' title='Next Page'>Next Page</a>";
        assertNull(next(html));
    }

}
