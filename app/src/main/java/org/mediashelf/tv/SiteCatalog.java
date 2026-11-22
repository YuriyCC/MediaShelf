package org.mediashelf.tv;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Adult-only source catalog. Each site has a stable ID, independent capabilities
 * and an explicit implementation phase. Source definitions never imply a tracker
 * is online, authenticated or fully searchable.
 *
 * "direct" means app-owned HTML/API adapter. "jackett" is a temporary bridge.
 * New source implementations should be separate classes, not conditional
 * branches spread around the UI.
 */
final class SiteCatalog {
    static final class Site {
        final String id;
        final String title;
        final String address;
        final boolean direct;
        final boolean requiresAccount;
        final String stage;
        Site(String id,String title,String address,boolean direct,boolean requiresAccount,String stage){
            this.id=id;
            this.title=title;
            this.address=address;
            this.direct=direct;
            this.requiresAccount=requiresAccount;
            this.stage=stage;
        }
        String mode() {
            return direct ? "Прямой модуль (тест)" : "Через Jackett до появления прямого модуля";
        }
    }
    private static final List<Site> KNOWN=Collections.unmodifiableList(Arrays.asList(
        new Site("xxxclub","XXXClub","https://xxxclub.to/",true,false,
            "Прямой каталог и постеры; полнота пагинации ещё проверяется"),
        new Site("pornolab","PornoLab","https://pornolab.net/",false,true,
            "Прямой модуль и вход в разработке"),
        new Site("mypornclub","MyPornClub","https://myporn.club/",false,false,
            "Прямой модуль в разработке"),
        new Site("torrent-pirat","torrent-pirat","http://www.torrent-pirat.com/",false,false,
            "Прямой модуль в разработке"),
        new Site("xxxtor","XXXTor","",false,false,
            "Прямой модуль в разработке")
    ));
    static List<Site> all(){ return KNOWN; }
    static Site byId(String id){
        for(Site s:KNOWN)if(s.id.equals(id))return s;
        return null;
    }
    static boolean direct(String id){
        Site site=byId(id);
        return site!=null && site.direct;
    }
    static boolean adult(String id){
        return byId(id)!=null;
    }
    private SiteCatalog(){}
}
