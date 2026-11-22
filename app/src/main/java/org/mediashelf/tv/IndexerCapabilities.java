package org.mediashelf.tv;

import android.net.Uri;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Category capabilities live at EACH individual indexer's Torznab endpoint.
 * This intentionally does not require Jackett's /api/v2.0/indexers listing,
 * which can return HTTP 400 even while search is operational.
 */
final class IndexerCapabilities {
    private IndexerCapabilities(){}

    static List<SourceRegistry.Category> fetch(String base, String apiKey, String indexer) throws Exception {
        if(!indexer.matches("[A-Za-z0-9_-]{1,80}"))
            throw new IllegalArgumentException("Неверный ID индексатора");
        String uri=base.replaceAll("/+$","") + "/api/v2.0/indexers/" +
            Uri.encode(indexer) + "/results/torznab/api?apikey=" + Uri.encode(apiKey) + "&t=caps";
        HttpURLConnection c=(HttpURLConnection)new URL(uri).openConnection();
        try {
            c.setConnectTimeout(8000); c.setReadTimeout(14000);
            c.setRequestProperty("Accept","application/xml,application/rss+xml,text/xml,*/*");
            if(c.getResponseCode()!=200)
                throw new IllegalStateException("Категории Jackett: HTTP "+c.getResponseCode());
            XmlPullParser parser=Xml.newPullParser();
            List<SourceRegistry.Category> list=new ArrayList<>();
            Set<Integer> seen=new HashSet<>();
            try(InputStream stream=c.getInputStream()) {
                parser.setInput(stream,"UTF-8");
                int state=parser.getEventType();
                boolean inCategories=false;
                while(state!=XmlPullParser.END_DOCUMENT) {
                    if(state==XmlPullParser.START_TAG) {
                        String tag=parser.getName();
                        if(tag.equals("categories"))inCategories=true;
                        else if(inCategories && (tag.equals("category")||tag.equals("subcat"))) {
                            String raw=parser.getAttributeValue(null,"id");
                            String name=parser.getAttributeValue(null,"name");
                            try {
                                int id=Integer.parseInt(raw);
                                if(id>0 && id<100000 && seen.add(id))
                                    list.add(new SourceRegistry.Category(id,
                                        name==null||name.isEmpty()?"Категория "+id:name));
                            }catch(Exception ignored){}
                        }
                    } else if(state==XmlPullParser.END_TAG && parser.getName().equals("categories"))
                        inCategories=false;
                    state=parser.next();
                }
            }
            return list;
        } finally {c.disconnect();}
    }

    static List<SourceRegistry.Category> known(String source) {
        List<SourceRegistry.Category> cats=new ArrayList<>();
        if(source.equals("xxxclub")) {
            // All eight native categories (0..7) from Jackett xxxclub.yml.
            // Its common Torznab 6040 maps BOTH 720p and 1080p; the native
            // browser must keep them separate, using local IDs 10001/10002.
            cats.add(new SourceRegistry.Category(6090,"Полные фильмы · DVD / WEB"));
            cats.add(new SourceRegistry.Category(10001,"HD · 720p"));
            cats.add(new SourceRegistry.Category(10002,"FullHD · 1080p"));
            cats.add(new SourceRegistry.Category(6045,"4K / UHD"));
            cats.add(new SourceRegistry.Category(6080,"SD · 480p"));
            cats.add(new SourceRegistry.Category(6050,"Сборники / Packs"));
            cats.add(new SourceRegistry.Category(6060,"Фотосеты / Images"));
            cats.add(new SourceRegistry.Category(6070,"VR / Другое"));
        }else if(source.equals("torrent-pirat")) {
            // Jackett maps all native site subforums to 6000. Do NOT promise
            // that its full-length forum can be selected through the generic API.
            cats.add(new SourceRegistry.Category(6000,"Все XXX-раздачи (общая категория Jackett)"));
        }
        return cats;
    }
}
