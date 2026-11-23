package org.mediashelf.tv;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * Persistent, device-local bookmarks shared across ALL configured sites.
 * No cloud account or network traffic. Entries are stored in order added
 * (newest first); tracker + details URI provide stable identifiers.
 */
final class FavoritesStore {
    static final String PREF_KEY="favorite_releases_v1";
    static final int MAX_ITEMS=350;
    private final SharedPreferences prefs;
    private final LinkedHashMap<String,JSONObject> entries=new LinkedHashMap<>();

    FavoritesStore(SharedPreferences prefs){
        this.prefs=prefs;
        try{
            JSONArray saved=new JSONArray(prefs.getString(PREF_KEY,"[]"));
            for(int i=0;i<saved.length() && entries.size()<MAX_ITEMS;i++){
                JSONObject o=saved.optJSONObject(i);
                if(o==null)continue;
                String title=o.optString("Title","");
                if(title.trim().isEmpty())continue;
                String id=key(o.optString("Tracker",""),o.optString("Details",""),
                    o.optString("MagnetUri",""),o.optString("Link",""),title);
                if(!entries.containsKey(id))entries.put(id,o);
            }
        }catch(Exception ignored){
            // Corrupt JSON should never crash MediaShelf. Subsequent saves
            // safely recreate the list.
        }
    }

    static String key(String tracker,String details,String magnet,
                      String download,String title){
        String source=trim(tracker).toLowerCase(Locale.ROOT);
        String ref=trim(details);
        if(ref.isEmpty())ref=trim(magnet);
        if(ref.isEmpty())ref=trim(download);
        if(ref.isEmpty())ref=trim(title);
        return source+"\n"+ref.toLowerCase(Locale.ROOT);
    }
    private static String trim(String s){return s==null?"":s.trim();}
    static String key(JSONObject j){
        return key(j.optString("Tracker",""),j.optString("Details",""),
            j.optString("MagnetUri",""),j.optString("Link",""),
            j.optString("Title",""));
    }
    synchronized boolean contains(JSONObject item){
        return entries.containsKey(key(item));
    }
    synchronized int size(){return entries.size();}
    synchronized List<JSONObject> all(){return new ArrayList<>(entries.values());}

    /**
     * Returns true when the item was added; false when it was removed.
     * Each user's click writes atomically to SharedPreferences.
     */
    synchronized boolean toggle(JSONObject record){
        String id=key(record);
        if(entries.containsKey(id)){
            entries.remove(id);
            save();
            return false;
        }
        if(record.optString("Title","").trim().isEmpty())return false;
        LinkedHashMap<String,JSONObject> updated=new LinkedHashMap<>();
        updated.put(id,record);
        for(java.util.Map.Entry<String,JSONObject> e:entries.entrySet()){
            if(updated.size()>=MAX_ITEMS)break;
            updated.put(e.getKey(),e.getValue());
        }
        entries.clear();entries.putAll(updated);
        save();
        return true;
    }
    private void save(){
        JSONArray json=new JSONArray();
        for(JSONObject j:entries.values())json.put(j);
        prefs.edit().putString(PREF_KEY,json.toString()).apply();
    }
}
