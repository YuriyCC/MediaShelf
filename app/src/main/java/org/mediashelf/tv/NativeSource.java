package org.mediashelf.tv;

import java.util.List;

/**
 * Contract for app-native tracker integration.
 *
 * A new site becomes first-class only after its own implementation exists.
 * Merely adding its domain to a list does not imply support.
 */
interface NativeSource {
    String id();
    String displayName();
    List<SourceRegistry.Category> categories();
    SitePages.Cursor begin(String query,int category);
    SitePages.Page fetchNext(SitePages.Cursor cursor,int category) throws Exception;
    boolean canEnrich(MainActivity.Item item);
    void enrich(MainActivity.Item item);
}

final class NativeSources {
    private static final NativeSource XXXCLUB=new XxxClubSource();

    static NativeSource get(String id){
        if("xxxclub".equals(id))return XXXCLUB;
        return null;
    }
    static boolean isNative(String id){ return get(id)!=null; }

    private NativeSources(){}

    private static final class XxxClubSource implements NativeSource {
        public String id(){return "xxxclub";}
        public String displayName(){return "XXXClub";}
        public List<SourceRegistry.Category> categories(){
            return IndexerCapabilities.known("xxxclub");
        }
        public SitePages.Cursor begin(String query,int category){
            return SitePages.start(query,category);
        }
        public SitePages.Page fetchNext(SitePages.Cursor cursor,int category) throws Exception{
            return SitePages.next(cursor,category);
        }
        public boolean canEnrich(MainActivity.Item item){
            return SiteDetails.supported(item);
        }
        public void enrich(MainActivity.Item item){SiteDetails.enrich(item);}
    }
}
