package org.mediashelf.tv;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Persistent quick searches. Movies remain in FavoritesStore. */
final class SavedSearches {
    static final String KEY="saved_searches_v1";
    static final class Entry {
        final String query,source,categoryName;
        final int category;
        Entry(String q,String s,int c,String name){
            query=q;source=s;category=c;categoryName=name;
        }
        JSONObject json(){
            JSONObject j=new JSONObject();
            try{
                j.put("q",query);
                j.put("s",source);
                j.put("c",category);
                j.put("n",categoryName);
            }catch(Exception ignored){}
            return j;
        }
    }
    private final SharedPreferences prefs;
    SavedSearches(SharedPreferences p){prefs=p;}
    synchronized List<Entry> all(){
        List<Entry> result=new ArrayList<>();
        try{
            JSONArray a=new JSONArray(prefs.getString(KEY,"[]"));
            for(int i=0;i<a.length();i++){
                JSONObject j=a.optJSONObject(i);
                if(j==null)continue;
                String q=j.optString("q","").trim();
                if(q.isEmpty())continue;
                result.add(new Entry(q,j.optString("s","xxxclub"),
                    j.optInt("c",0),j.optString("n","")));
            }
        }catch(Exception ignored){}
        return result;
    }
    synchronized boolean add(String query,String source,int category,String name){
        String q=query==null?"":query.trim().replaceAll("\\s+"," ");
        if(q.isEmpty())return false;
        List<Entry> list=all();
        for(Entry item:list)if(item.query.equalsIgnoreCase(q) &&
            item.source.equals(source) && item.category==category)return false;
        if(list.size()>=40)list.remove(0);
        list.add(new Entry(q,source,category,name));
        store(list);
        return true;
    }
    synchronized void remove(int index){
        List<Entry> list=all();
        if(index>=0 && index<list.size()){list.remove(index);store(list);}
    }
    synchronized void move(int from,int to){
        List<Entry> list=all();
        if(from<0||from>=list.size()||to<0||to>=list.size()||from==to)return;
        Entry item=list.remove(from);list.add(to,item);store(list);
    }
    synchronized void rename(int index,String replacement){
        List<Entry> list=all();
        if(index<0||index>=list.size())return;
        String q=replacement==null?"":replacement.trim().replaceAll("\\s+"," ");
        if(q.isEmpty())return;
        Entry old=list.get(index);
        list.set(index,new Entry(q,old.source,old.category,old.categoryName));
        store(list);
    }
    private void store(List<Entry> list){
        JSONArray a=new JSONArray();
        for(Entry item:list)a.put(item.json());
        prefs.edit().putString(KEY,a.toString()).apply();
    }
}
