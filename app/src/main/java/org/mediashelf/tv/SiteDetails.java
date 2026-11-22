package org.mediashelf.tv;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Optional detail-page enrichment, separate from the Jackett search engine. */
final class SiteDetails {
    static final String USER_AGENT="Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36";
    private SiteDetails(){}

    static boolean supported(MainActivity.Item item) {
        if(item.details==null)return false;
        try {
            java.net.URI url=new java.net.URI(item.details);
            String host=url.getHost();
            if(host==null)return false;
            host=host.toLowerCase(Locale.ROOT);
            return (host.equals("xxxclub.to") || host.equals("xxxclub.cc") || host.equals("xxxclub.me") || host.startsWith("www.xxxclub."))
                    && url.getPath()!=null && url.getPath().startsWith("/torrents/details/");
        } catch(Exception e){return false;}
    }
    static void enrich(MainActivity.Item item) {
        if(!supported(item))return;
        synchronized(item) {
            if(item.detailsFetched)return;
            item.detailsFetched=true;
            try {
                java.net.URI uri=new java.net.URI(item.details);
                String origin=uri.getScheme()+"://"+uri.getAuthority()+"/";
                Document d=Jsoup.connect(item.details).userAgent(USER_AGENT).referrer(origin)
                    .timeout(15000).maxBodySize(5_000_000).followRedirects(true).get();
                Element poster=d.selectFirst("img.detailsposter");
                if(poster!=null) {
                    String url=imageSrc(poster);
                    if(!url.isEmpty())item.poster=url;
                }
                if(item.poster.isEmpty()) {
                    Element og=d.selectFirst("meta[property=og:image]");
                    if(og!=null) {
                        String url=og.attr("abs:content");
                        if(!url.isEmpty())item.poster=url;
                    }
                }
                Element description=d.selectFirst("div.description");
                if(description!=null) {
                    String text=description.text();
                    if(!text.isEmpty())item.description=text.substring(0,Math.min(4000,text.length()));
                    List<String> shots=new ArrayList<>();
                    for(Element pic:description.select("img")) {
                        String url=imageSrc(pic);
                        if(url.isEmpty()||url.equals(item.poster)||shots.contains(url))continue;
                        shots.add(url);
                        if(shots.size()>=12)break;
                    }
                    item.screenshots=shots;
                }
            }catch(Exception e) {
                item.imageError="Страница недоступна: "+e.getClass().getSimpleName();
            }
        }
    }
    private static String imageSrc(Element picture) {
        for(String key:new String[]{"abs:src","abs:data-src","abs:data-original","abs:data-lazy-src"}) {
            String url=picture.attr(key);
            if(url.startsWith("http://")||url.startsWith("https://"))return url;
        }
        return "";
    }
}
