package org.mediashelf.tv;

import android.net.Uri;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Independent XXXClub HTML catalog pager.
 *
 * Jackett's current xxxclub.yml only extracts the first site result page.
 * This adapter follows the site's own pagination hrefs instead of inventing
 * a universal offset supported by all trackers.
 */
final class SitePages {
    private SitePages(){}

    static final class Cursor {
        String url;
        int pageIndex;
        boolean finished;
        String message="";
        final Set<String> visited=new HashSet<>();
        Cursor(String start){this.url=start;}
    }
    static final class Page {
        final List<MainActivity.Item> items;
        final String message;
        final int pageNumber;
        final boolean more;
        Page(List<MainActivity.Item> items,String message,int pageNumber,boolean more){
            this.items=items;this.message=message;this.pageNumber=pageNumber;this.more=more;
        }
    }
    static boolean supported(String source) {return "xxxclub".equals(source);}

    static Cursor start(String query,int category){
        int cat=nativeCategory(category);
        String scope=cat<0?"all":Integer.toString(cat);
        String cleaned=query==null?"":query.trim();
        String path;
        if(cleaned.isEmpty())path="torrents/browse/"+scope;
        else path="torrents/search/"+scope+"/"+Uri.encode(cleaned);
        return new Cursor("https://xxxclub.to/"+path);
    }
    private static int nativeCategory(int id){
        switch(id){
            case 6080:return 0; // SD
            case 6040:return 2; // default 1080p; HD 720p also exists (id=1)
            case 6045:return 4;
            case 6090:return 3; // full movies, DVD and WEB
            case 6060:return 5;
            case 6070:return 6;
            case 6050:return 7;
            default:return -1;
        }
    }
    static Page next(Cursor cursor,int category) throws Exception{
        if(cursor.finished || cursor.url==null || cursor.url.isEmpty())
            return new Page(new ArrayList<>(),"На сайте больше нет известных страниц.",
                cursor.pageIndex,false);
        String request=cursor.url;
        if(!safeUrl(request))throw new IllegalArgumentException("Недопустимая ссылка следующей страницы");
        if(!cursor.visited.add(request)) {
            cursor.finished=true;
            return new Page(new ArrayList<>(),"Сайт повторно выдал уже загруженную страницу.",
                cursor.pageIndex,false);
        }
        Document doc;
        try {
            doc=Jsoup.connect(request).userAgent(SiteDetails.USER_AGENT)
                .referrer("https://xxxclub.to/")
                .header("Accept-Language","en-US,en;q=0.9")
                .timeout(18000).maxBodySize(3_000_000).followRedirects(true).get();
        }catch(Exception e){
            cursor.visited.remove(request); // allow user to retry a temporary error
            throw e;
        }
        if(doc.select("div.browsetableinside,div.divtableinside").isEmpty())
            throw new IllegalStateException("XXXClub изменил разметку или вернул защитную страницу");
        List<MainActivity.Item> results=new ArrayList<>();
        Set<String> urls=new HashSet<>();
        Elements rows=doc.select("div.browsetableinside > ul > li,div.divtableinside > ul > li");
        int localCat=nativeCategory(category);
        for(Element row:rows){
            Element a=row.selectFirst("a[href*=/torrents/details/]");
            if(a==null)continue;
            String detail=a.attr("abs:href");
            if(!safeDetail(detail) || !urls.add(detail))continue;
            String title=a.text().trim();
            if(title.isEmpty())continue;
            if(localCat>=0) {
                Element cat=row.selectFirst("a.catah[href]");
                if(cat!=null){
                    int actual=extractNativeCategory(cat.attr("href"));
                    if(actual>=0 && actual!=localCat &&
                        !(category==6040 && actual==1))continue;
                }
            }
            String poster="";
            Element image=row.selectFirst("img[src],img[data-src]");
            if(image!=null){
                poster=image.attr("abs:src");
                if(poster.isEmpty())poster=image.attr("abs:data-src");
            }
            String size=own(row.selectFirst("span.siz"));
            long bytes=toBytes(size);
            int seed=parseInt(own(row.selectFirst("span.see")));
            String date=own(row.selectFirst("span.adde"));
            String download="";
            Element direct=row.selectFirst("a[href^=/torrents/download/],a[href^=magnet:?]");
            if(direct!=null)download=direct.attr("abs:href");
            JSONObject data=new JSONObject();
            data.put("Title",title);
            data.put("Tracker","XXXClub");
            data.put("Details",detail);
            data.put("Poster",poster);
            data.put("Size",bytes);
            data.put("Seeders",seed);
            data.put("PublishDate",date);
            if(download.startsWith("magnet:"))data.put("MagnetUri",download);
            else if(!download.isEmpty())data.put("Link",download);
            results.add(new MainActivity.Item(data));
        }
        cursor.pageIndex++;
        String next=findNext(doc,request,cursor.visited,cursor.pageIndex);
        cursor.url=next;
        cursor.finished=next==null;
        String message="XXXClub · страница "+cursor.pageIndex+": "+results.size()+" раздач";
        if(cursor.finished)message+=". Ссылка Next Page не обнаружена — это НЕ означает, что каталог закончился.";
        return new Page(results,message,cursor.pageIndex,!cursor.finished);
    }
    /**
     * XXXClub uses an opaque cursor, not page numbers:
     * /torrents/search/all/Gangbang/7296816424212307464.
     *
     * A user-verified "Next Page" button may appear OUTSIDE .pagination.
     * Search every page-navigation anchor/button, then check same-listing
     * numeric cursor links. Never guess or manufacture a cursor.
     */
    static String findNext(Document d,String current,Set<String> visited,int pageIndex){
        List<String> cursorLinks=new ArrayList<>();
        for(Element el:d.select("a[href],a[onclick],button[onclick],button[data-href],"+
                "button[data-url],input[onclick],input[data-href],input[data-url]")) {
            String next=extractLink(el,d.baseUri());
            if(!sameListing(current,next) || visited.contains(next) || sameUrl(next,current))
                continue;
            String label=(el.text()+" "+el.attr("title")+" "+el.attr("aria-label")+
                " "+el.attr("rel")+" "+el.className()+" "+el.attr("value"))
                .toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim();
            boolean nextButton=label.contains("next page") ||
                label.matches(".*\\bnext\\b.*") ||
                label.contains("следующ") ||
                label.contains("дальше") ||
                label.contains("далее") ||
                label.contains("›") || label.contains("»") ||
                label.contains("→");
            boolean previous=label.contains("prev") ||
                label.contains("previous") || label.contains("назад") ||
                label.contains("предыдущ");
            if(nextButton && !previous)return next;
            if(previous)continue;
            if(hasOpaqueCursor(next)) {
                if(!cursorLinks.contains(next))cursorLinks.add(next);
            }
        }
        // Some templates render an unlabelled next-cursor anchor without
        // pagination classes; it is safe only if exactly ONE new cursor exists.
        if(cursorLinks.size()==1)return cursorLinks.get(0);

        // Last resort: a site template can put the Next Page URL into a script,
        // rather than a visible anchor. Only accept a single URL with the
        // current search/browse prefix and a long, opaque numeric cursor.
        if(cursorLinks.isEmpty()){
            try {
                String currentPath=new URI(current).getPath();
                String base=listingPath(currentPath);
                java.util.regex.Pattern pattern=java.util.regex.Pattern.compile(
                    java.util.regex.Pattern.quote(base+"/")+"[0-9]{8,25}");
                java.util.regex.Matcher matches=pattern.matcher(d.html().replace("\\/","/"));
                List<String> found=new ArrayList<>();
                String host=new URI(current).getHost();
                while(matches.find()){
                    String u="https://"+host+matches.group();
                    if(sameListing(current,u) && !visited.contains(u) &&
                        !sameUrl(current,u) && !found.contains(u))found.add(u);
                }
                if(found.size()==1)return found.get(0);
            }catch(Exception ignored){}
        }
        return null;
    }

    private static String extractLink(Element el,String base){
        // Plain href (including relative site URLs) is the preferred source.
        for(String key:new String[]{"abs:href","abs:data-href","abs:data-url",
                                     "href","data-href","data-url"}){
            String value=el.attr(key).trim();
            String link=resolveLink(value,base);
            if(safeUrl(link))return link;
        }
        // Explicit navigation in onclick e.g. location.href='/torrents/...'.
        String js=el.attr("onclick");
        if(!js.isEmpty()){
            java.util.regex.Matcher m=java.util.regex.Pattern.compile(
                "['\\\"](https?://[^'\\\"]+/torrents/[^'\\\"]+|/torrents/[^'\\\"]+)['\\\"]")
                .matcher(js);
            while(m.find()){
                String link=resolveLink(m.group(1),base);
                if(safeUrl(link))return link;
            }
        }
        return "";
    }
    private static String resolveLink(String raw,String base){
        if(raw==null||raw.isEmpty() || raw.startsWith("#") ||
            raw.startsWith("javascript:"))return "";
        try{
            return new URI(base).resolve(raw.replace("&amp;","&")).toString();
        }catch(Exception ignored){return "";}
    }
    private static boolean sameUrl(String a,String b){
        return a.equals(b) || a.replaceFirst("/+$","").equals(b.replaceFirst("/+$",""));
    }
    private static boolean sameListing(String previous,String candidate){
        if(!safeUrl(candidate))return false;
        try {
            URI before=new URI(previous),after=new URI(candidate);
            String left=listingPath(before.getPath());
            String right=listingPath(after.getPath());
            return left.equals(right) && !right.isEmpty() &&
                after.getPath().startsWith("/torrents/") &&
                // For this site, a later page must have a cursor (or an
                // explicit changed query), never another category's URL.
                (!sameUrl(previous,candidate)) &&
                (hasOpaqueCursor(candidate) || after.getRawQuery()!=null);
        }catch(Exception e){return false;}
    }
    private static String listingPath(String path){
        if(path==null)return "";
        return path.replaceFirst("/[0-9]{8,25}/?$","").replaceFirst("/+$","");
    }
    private static boolean hasOpaqueCursor(String url){
        try{return new URI(url).getPath().matches(".*/[0-9]{8,25}/?");}
        catch(Exception ignored){return false;}
    }
    private static boolean safeUrl(String url){
        try{
            URI u=new URI(url);
            String h=u.getHost();
            String p=u.getPath();
            return "https".equalsIgnoreCase(u.getScheme()) &&
                h!=null && (h.equalsIgnoreCase("xxxclub.to") ||
                    h.equalsIgnoreCase("xxxclub.cc") || h.equalsIgnoreCase("xxxclub.me")) &&
                p!=null && p.startsWith("/torrents/");
        }catch(Exception ignored){return false;}
    }
    private static boolean safeDetail(String u){
        return safeUrl(u) && u.contains("/torrents/details/");
    }
    private static int extractNativeCategory(String text){
        try {
            java.util.regex.Matcher m=java.util.regex.Pattern.compile(
                    "(?:/|=)([0-7])(?:/|$|\\?|&)").matcher(text);
            int found=-1;
            while(m.find())found=Integer.parseInt(m.group(1));
            return found;
        }catch(Exception e){return -1;}
    }
    private static String own(Element el){return el==null?"":el.text().trim();}
    private static int parseInt(String text){
        try{return Integer.parseInt(text.replaceAll("[^0-9]",""));}
        catch(Exception e){return 0;}
    }
    private static long toBytes(String human){
        try{
            String cleaned=human.toUpperCase(Locale.ROOT).replace(',','.');
            java.util.regex.Matcher m=java.util.regex.Pattern.compile(
                "([0-9]+(?:\\.[0-9]+)?)\\s*(TB|GB|MB|KB|ТБ|ГБ|МБ|КБ|B)\\b").matcher(cleaned);
            if(m.find()){
                double n=Double.parseDouble(m.group(1));
                String unit=m.group(2);
                int power=unit.equals("TB")||unit.equals("ТБ")?4:
                    unit.equals("GB")||unit.equals("ГБ")?3:
                    unit.equals("MB")||unit.equals("МБ")?2:
                    unit.equals("KB")||unit.equals("КБ")?1:0;
                return (long)(n*Math.pow(1024,power));
            }
        }catch(Exception ignored){}
        return 0;
    }
}
