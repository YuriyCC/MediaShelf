package org.mediashelf.tv;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared pagination controller for all future native site adapters.
 *
 * A provider supplies URL validation for its own category/search context;
 * the controller discovers and RANKS actual navigation controls. In
 * particular, it discards CSS-hidden lookalikes (some trackers emit fake
 * links to poison naive crawlers), never invents cursor tokens, and refuses
 * "previous" links. This class makes no network calls.
 */
final class PageNavigation {
    interface LinkFilter {
        boolean accepts(String url);
    }

    static final class Finding {
        final String nextUrl;
        final int candidates;
        final int rejectedHidden;
        final String reason;
        Finding(String nextUrl,int candidates,int rejectedHidden,String reason) {
            this.nextUrl=nextUrl;this.candidates=candidates;
            this.rejectedHidden=rejectedHidden;this.reason=reason;
        }
    }

    private static final Pattern JS_LINK=Pattern.compile(
        "['\\\"](https?://[^'\\\"]+/torrents/[^'\\\"]+|/torrents/[^'\\\"]+)['\\\"]");
    private static final Pattern NEGATIVE_POSITION=Pattern.compile(
        "(?:^|;)\\s*(?:left|right|top|bottom)\\s*:\\s*-\\s*(?:[1-9][0-9]{2,})px",
        Pattern.CASE_INSENSITIVE);

    static Finding find(Document doc,Set<String> visited,LinkFilter filter) {
        if(doc==null)return new Finding(null,0,0,"Нет HTML-страницы");
        int count=0, hiddenCount=0, bestScore=Integer.MIN_VALUE;
        String best=null;
        Set<String> unique=new HashSet<>();
        for(Element element:doc.select("a[href],a[onclick],button[data-href],"+
                "button[data-url],button[onclick],input[data-href],"+
                "input[data-url],input[onclick]")) {
            String url=extractLink(element,doc.baseUri());
            if(url.isEmpty()||visited.contains(url)||!filter.accepts(url))continue;
            if(isHidden(element)) {
                hiddenCount++;
                continue;
            }
            if(!unique.add(url))continue;
            String label=(element.text()+" "+element.attr("title")+" "+
                element.attr("aria-label")+" "+element.attr("rel")+" "+
                element.attr("value")).toLowerCase(Locale.ROOT).trim();
            String cls=element.className().toLowerCase(Locale.ROOT);
            boolean previous=label.contains("previous")||label.contains("prev")||
                label.contains("назад")||label.contains("предыдущ");
            if(previous)continue;
            int score=0;
            if(label.contains("next page"))score+=130;
            else if(label.matches("(?s).*\\bnext\\b.*")||
                label.contains("следующ")||label.contains("дальше")||
                label.contains("далее"))score+=100;
            else if(label.contains("›")||label.contains("»")||
                label.contains("→"))score+=65;
            if(element.hasAttr("data-no-instant"))score+=70;
            if(element.attr("rel").matches("(?i).*\\bnext\\b.*"))score+=90;
            if(cls.contains("next"))score+=35;
            if(element.parents().stream().anyMatch(parent->{
                String classes=parent.className().toLowerCase(Locale.ROOT);
                return classes.contains("pagination")||classes.contains("pager")||
                    classes.contains("page-nav");
            }))score+=24;
            if(element.hasAttr("href")&&element.tagName().equals("a"))score+=10;
            if(element.attr("style").toLowerCase(Locale.ROOT).contains("position:absolute"))
                score-=15;
            if(score>bestScore){
                best=url;
                bestScore=score;
            }
            count++;
        }
        // Without an actual NEXT label or navigation metadata, follow only
        // ONE unambiguous valid cursor URL; never pick an arbitrary page link.
        if(best!=null && (bestScore>=55 || count==1))
            return new Finding(best,count,hiddenCount,
                "Найдена кнопка Next Page; скрытых ссылок: "+hiddenCount);
        return new Finding(null,count,hiddenCount,
            count>0?"Нет однозначной кнопки Next Page":
                "В HTML не обнаружено следующей страницы");
    }

    static boolean isHidden(Element el){
        for(Element node=el;node!=null;node=node.parent()){
            if(node.hasAttr("hidden")||
                node.attr("aria-hidden").equalsIgnoreCase("true"))return true;
            String style=node.attr("style").toLowerCase(Locale.ROOT)
                .replaceAll("\\s+","");
            if(style.contains("display:none")||
                style.contains("visibility:hidden")||
                style.contains("opacity:0")||
                style.contains("left:-999")||
                style.contains("right:-999")||
                style.contains("top:-999")||
                style.contains("left:-10000")||
                style.contains("left:-99999"))return true;
            if(NEGATIVE_POSITION.matcher(node.attr("style")).find())return true;
        }
        return false;
    }

    private static String extractLink(Element element,String base) {
        for(String attr:new String[]{"href","data-href","data-url"}) {
            String candidate=resolve(element.attr(attr),base);
            if(!candidate.isEmpty())return candidate;
        }
        String javascript=element.attr("onclick");
        if(!javascript.isEmpty()) {
            Matcher match=JS_LINK.matcher(javascript);
            while(match.find()) {
                String candidate=resolve(match.group(1),base);
                if(!candidate.isEmpty())return candidate;
            }
        }
        return "";
    }

    private static String resolve(String raw,String base) {
        if(raw==null||raw.isEmpty()||raw.startsWith("#")||
            raw.startsWith("javascript:")||raw.startsWith("data:"))return "";
        try {
            URI link=new URI(base).resolve(raw.replace("&amp;","&"));
            String scheme=link.getScheme();
            return ("http".equalsIgnoreCase(scheme)||
                "https".equalsIgnoreCase(scheme))?link.toString():"";
        }catch(Exception e){return "";}
    }

    private PageNavigation(){}
}
