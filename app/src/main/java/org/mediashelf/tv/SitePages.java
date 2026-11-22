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
        if(cursor.finished)message+=". Следующая страница не найдена.";
        return new Page(results,message,cursor.pageIndex,!cursor.finished);
    }
    private static String findNext(Document d,String current,Set<String> visited,int pageIndex){
        Element direct=d.selectFirst("a[rel~=next][href],li.next a[href],a.next[href],a[aria-label*=Next][href]");
        if(direct!=null){
            String url=direct.attr("abs:href");
            if(safeUrl(url)&&!visited.contains(url))return url;
        }
        Elements pager=d.select("div.pagination a[href],ul.pagination a[href],nav.pagination a[href],"+
                ".pagination a[href],.pagenavi a[href],.pager a[href],"+
                ".paging a[href],div.pages a[href],.page-links a[href]");
        // Only anchors actually marked by the website as pagination links.
        // This intentionally does not derive page=2 or /2 without evidence.
        List<Element> options=new ArrayList<>(pager);
        for(Element a:options){
            String label=a.text().trim().toLowerCase(Locale.ROOT);
            if(label.equals("next")||label.equals("next »")||label.equals("следующая")||
               label.equals("»")||label.equals("›")||label.equals(">>")){
                String url=a.attr("abs:href");
                if(safeUrl(url)&&!visited.contains(url))return url;
            }
        }
        int bestNumber=Integer.MAX_VALUE;
        String best=null;
        for(Element a:options){
            String label=a.text().trim();
            int target=parseInt(label);
            if(target>pageIndex && target<bestNumber) {
                String url=a.attr("abs:href");
                if(safeUrl(url)&&!visited.contains(url)){
                    bestNumber=target;best=url;
                }
            }
        }
        return best;
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
