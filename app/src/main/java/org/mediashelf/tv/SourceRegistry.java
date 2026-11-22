package org.mediashelf.tv;

import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Jackett configured indexer registry. No hardcoded tracker IDs or claims of live availability.
 * "Configured" and "last_error" are metadata, not evidence of a successful live search.
 */
final class SourceRegistry {
    static final class Category {
        final int id;
        final String name;
        Category(int id, String name) { this.id=id; this.name=name; }
    }
    static final class Source {
        final String id;
        final String name;
        final String siteUrl;
        final String lastError;
        final List<Category> categories;
        Source(String id, String name, String siteUrl, String lastError, List<Category> categories) {
            this.id=id; this.name=name; this.siteUrl=siteUrl; this.lastError=lastError;
            this.categories=categories;
        }
    }
    private SourceRegistry() { }

    static List<Source> fetch(String base, String key) throws Exception {
        String root=base.replaceAll("/+$", "");
        if (!root.startsWith("http://") && !root.startsWith("https://"))
            throw new IllegalArgumentException("Неверный адрес Jackett");
        URL url=new URL(root+"/api/v2.0/indexers?configured=true&apikey="+Uri.encode(key));
        HttpURLConnection conn=(HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(16000);
        conn.setRequestProperty("Accept", "application/json");
        try {
            int response=conn.getResponseCode();
            if (response!=200) throw new IllegalStateException("HTTP "+response);
            String json;
            try (InputStream in=conn.getInputStream()) {
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                byte[] buf=new byte[8192]; int n;
                while ((n=in.read(buf))>=0) {
                    if(n==0)continue;
                    bytes.write(buf,0,n);
                    if(bytes.size()>2_000_000) throw new IllegalStateException("Список индексаторов слишком большой");
                }
                json=bytes.toString(StandardCharsets.UTF_8.name());
            }
            JSONArray arr=new JSONArray(json);
            List<Source> sources=new ArrayList<>();
            for (int i=0;i<arr.length();i++) {
                JSONObject o=arr.optJSONObject(i);
                if(o==null || !o.optBoolean("configured",false)) continue;
                String id=o.optString("id","").trim();
                if(!id.matches("[a-zA-Z0-9_-]{1,80}") || id.equals("all")) continue;
                String name=o.optString("name",id);
                String site=o.optString("site_link","");
                String error=o.optString("last_error","");
                List<Category> categories=new ArrayList<>();
                JSONArray caps=o.optJSONArray("caps");
                if(caps!=null) for(int j=0;j<caps.length();j++) {
                    JSONObject c=caps.optJSONObject(j);
                    if(c==null)continue;
                    try {
                        int cat=Integer.parseInt(c.optString("ID",""));
                        if(cat>0 && cat<100000) categories.add(new Category(cat,c.optString("Name","Категория "+cat)));
                    }catch(NumberFormatException ignored){}
                }
                sources.add(new Source(id,name,site,error,categories));
            }
            sources.sort(Comparator.comparing(s->s.name.toLowerCase(java.util.Locale.ROOT)));
            return sources;
        } finally {
            conn.disconnect();
        }
    }

    static Source byId(List<Source> list,String id) {
        for(Source s:list)if(s.id.equals(id))return s;
        return null;
    }
}
