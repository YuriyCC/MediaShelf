package org.mediashelf.tv;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 
 * Per-site enrichment, deliberately isolated: no assumption that another
 * site's markup is the same as XXXClub. This module does not affect Jackett search.
 */
final class SiteDetails {
    static final String USER_AGENT="Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36";
    private SiteDetails() { }

    static String adapter(MainActivity.Item item) {
        if(item.details==null||item.details.isEmpty())return "";
        try {
            URI url=new URI(item.details);
            String scheme=url.getScheme(),host=url.getHost(),path=url.getPath();
            if(scheme==null||host==null||path==null ||
                (!scheme.equalsIgnoreCase("http")&&!scheme.equalsIgnoreCase("https")))return "";
            host=host.toLowerCase(Locale.ROOT);
            if(allowed(host,"xxxclub.to")||allowed(host,"xxxclub.cc")||allowed(host,"xxxclub.me")) {
                return path.startsWith("/torrents/details/")?"xxxclub":"";
            }
            if(allowed(host,"torrent-pirat.com")) {
                return path.endsWith("viewtopic.php")?"torrent-pirat":"";
            }
            if(allowed(host,"myporn.club")) {
                return path.startsWith("/t/")?"mypornclub":"";
            }
            return "";
        } catch(Exception ignored){return "";}
    }
    private static boolean allowed(String host,String expected){
        return host.equals(expected)||host.endsWith("."+expected);
    }
    static boolean supported(MainActivity.Item item) {
        return !adapter(item).isEmpty();
    }
    static void enrich(MainActivity.Item item) {
        String site=adapter(item);
        if(site.isEmpty())return;
        synchronized(item) {
            if(item.detailsFetched)return;
            try {
                URI u=new URI(item.details);
                String base=u.getScheme()+"://"+u.getAuthority()+"/";
                Document d=Jsoup.connect(item.details).userAgent(USER_AGENT).referrer(base)
                    .timeout(12000).maxBodySize(3_500_000).followRedirects(true).get();
                if(site.equals("xxxclub")) {
                    Element magnet=d.selectFirst("a[href^=magnet:?xt=],a[href^=magnet:?]");
                    if(magnet!=null) {
                        String value=magnet.attr("href");
                        if(value.startsWith("magnet:?"))item.magnet=value;
                    }
                    if(item.magnet.isEmpty()){
                        Element torrent=d.selectFirst("a[href^=/torrents/download/]");
                        if(torrent!=null)item.download=torrent.attr("abs:href");
                    }
                }
                Element portrait=null;
                Element description=null;
                if(site.equals("xxxclub")) {
                    // Listing images are small preview frames, not the real cover:
                    // the poster comes from the details page.
                    portrait=d.selectFirst("img.detailsposter");
                    description=d.selectFirst("div.description");
                } else if(site.equals("torrent-pirat")) {
                    // phpBB: extract ONLY from the release author's post,
                    // not global headers, logos, avatars and ad banners.
                    portrait=firstContentImage(d,"div.postbody img, div.postbody .postimage, .postbody img");
                    description=d.selectFirst("div.postbody div.content, div.postbody, td.postbody");
                } else if(site.equals("mypornclub")) {
                    portrait=firstContentImage(d,"img.poster, .torrent_info_div img, .torrent_details img, .torrent_description img");
                    description=d.selectFirst("div.torrent_description, div.torrent_info_div");
                }
                String picture=portrait==null?"":imageSrc(portrait);
                if(picture.isEmpty()) {
                    Element og=d.selectFirst("meta[property=og:image], meta[name=twitter:image]");
                    if(og!=null)picture=og.attr("abs:content");
                }
                if(validImageUrl(picture))item.poster=picture;
                if(description!=null){
                    String val=description.text();
                    if(!val.isEmpty() && (item.description==null||item.description.isEmpty()))
                        item.description=val.substring(0,Math.min(3800,val.length()));
                    List<String> shots=new ArrayList<>();
                    for(Element el:description.select("img")){
                        String src=imageSrc(el);
                        if(!validImageUrl(src)||src.equals(item.poster)||shots.contains(src))continue;
                        shots.add(src);
                        if(shots.size()>=10)break;
                    }
                    item.screenshots=shots;
                }
                // Do not mark a network failure as successfully fetched metadata.
                item.detailsFetched=true;
            } catch(Exception e) {
                item.imageError="Карточка "+site+": "+e.getClass().getSimpleName();
            }
        }
    }
    private static Element firstContentImage(Document d,String selector){
        for(Element img:d.select(selector)) {
            String u=imageSrc(img).toLowerCase(Locale.ROOT);
            if(!validImageUrl(u))continue;
            if(u.matches(".*(avatar|smil|emoticon|favicon|logo|banner|counter|sprite|pixel|button).*"))continue;
            try {
                int width=Integer.parseInt(img.attr("width").replaceAll("[^0-9]",""));
                if(width>0 && width<90)continue;
            }catch(Exception ignored){}
            return img;
        }
        return null;
    }
    private static boolean validImageUrl(String value) {
        return value!=null && (value.startsWith("https://")||value.startsWith("http://"));
    }
    // Prefer the original linked image, not the image embedded as a thumbnail.
    // Many release pages wrap 200px previews in <a href=".../full.jpg">.
    static String imageSrc(Element image) {
        if(image==null)return "";
        ImageCandidate best=null;
        Element link=image.closest("a[href]");
        if(link!=null){
            String href=link.attr("abs:href");
            if(isDirectImage(href))
                best=pickImage(best,image,href,1050);
            for(String key:new String[]{"data-full","data-original","data-image"}){
                String value=link.attr(key);
                if(!value.isEmpty())
                    best=pickImage(best,link,value,920);
            }
        }
        String[] attrs={"data-full","data-full-src","data-large","data-zoom",
            "data-zoom-url","data-original","data-src","data-lazy-src",
            "data-echo","src"};
        int[] weights={1000,980,970,960,950,900,520,510,505,340};
        for(int i=0;i<attrs.length;i++)
            best=pickImage(best,image,image.attr(attrs[i]),weights[i]);

        best=pickSrcset(best,image,image.attr("srcset"));
        Element picture=image.closest("picture");
        if(picture!=null)
            for(Element source:picture.select("source[srcset]"))
                best=pickSrcset(best,source,source.attr("srcset"));
        return best==null?"":best.url;
    }
    private static final class ImageCandidate {
        final String url;
        final int score;
        ImageCandidate(String url,int score){this.url=url;this.score=score;}
    }
    private static boolean isDirectImage(String url){
        if(!validImageUrl(url))return false;
        try{
            String path=new URI(url).getPath();
            return path!=null && path.matches("(?i).*\\.(jpg|jpeg|png|webp|avif|gif)$");
        }catch(Exception ignored){return false;}
    }
    private static String imageUrl(Element element,String raw) {
        if(raw==null || raw.trim().isEmpty())return "";
        String text=raw.trim();
        if(validImageUrl(text))return text;
        if(text.startsWith("data:") || text.startsWith("blob:"))return "";
        try {
            String full=new URL(new URL(element.baseUri()),text).toString();
            return validImageUrl(full)?full:"";
        }catch(Exception ignored){return "";}
    }
    private static ImageCandidate pickImage(ImageCandidate best,Element element,
                                            String value,int score){
        String url=imageUrl(element,value);
        if(url.isEmpty())return best;
        String lower=url.toLowerCase(Locale.ROOT);
        if(lower.matches(".*(?:[/_-]thumbs?[/_.-]|[/_-]tiny[/_.-]|[/_-]small[/_.-]).*"))
            score-=480;
        else if(lower.matches(".*(?:[/_-]preview[/_.-]|[/_-]resize[/_.-]).*"))
            score-=200;
        if(lower.matches(".*(?:[/_-]full[/_.-]|[/_-]original[/_.-]|[/_-]large[/_.-]).*"))
            score+=130;
        if(best==null || score>best.score)return new ImageCandidate(url,score);
        return best;
    }
    private static ImageCandidate pickSrcset(ImageCandidate best,Element element,String srcset){
        if(srcset==null || srcset.isEmpty())return best;
        for(String candidate:srcset.split(",")){
            String[] terms=candidate.trim().split("\\s+");
            if(terms.length==0)continue;
            int score=620;
            if(terms.length>1){
                String suffix=terms[terms.length-1].toLowerCase(Locale.ROOT);
                try{
                    if(suffix.endsWith("w"))score+=Math.min(420,
                        Integer.parseInt(suffix.substring(0,suffix.length()-1))/4);
                    else if(suffix.endsWith("x"))score+=Math.min(420,
                        (int)(Float.parseFloat(suffix.substring(0,suffix.length()-1))*120));
                }catch(NumberFormatException ignored){}
            }
            best=pickImage(best,element,terms[0],score);
        }
        return best;
    }
    static String verifyImage(String link,String referrer) {
        if(!validImageUrl(link))return "нет URL";
        HttpURLConnection c=null;
        try {
            c=(HttpURLConnection)new URL(link).openConnection();
            c.setConnectTimeout(5000);c.setReadTimeout(6500);
            c.setRequestProperty("User-Agent",USER_AGENT);
            if(referrer!=null&&!referrer.isEmpty())c.setRequestProperty("Referer",referrer);
            c.setRequestProperty("Accept","image/*,*/*;q=0.8");
            c.setRequestProperty("Range","bytes=0-16383");
            int code=c.getResponseCode();
            if(code!=200 && code!=206)return "HTTP "+code;
            String type=c.getContentType();
            try(InputStream in=c.getInputStream()){
                byte[] start=new byte[12];int count=in.read(start);
                if(count<3)return "пустой файл";
                boolean jpeg=(start[0]&0xff)==0xff && (start[1]&0xff)==0xd8;
                boolean png=start[0]==(byte)0x89 && start[1]==(byte)'P' && start[2]==(byte)'N';
                boolean gif=start[0]=='G' && start[1]=='I' && start[2]=='F';
                boolean webp=count>=12 && start[0]=='R' && start[1]=='I' && start[8]=='W';
                if(jpeg||png||gif||webp)return "доступно";
            }
            return type!=null&&type.startsWith("image/")?"изображение доступно (другой формат)":"не изображение";
        }catch(Exception e){return e.getClass().getSimpleName();}
        finally{if(c!=null)c.disconnect();}
    }
}
