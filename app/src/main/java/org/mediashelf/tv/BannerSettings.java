package org.mediashelf.tv;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Declarative per-source carousel categories. No Jackett IDs are exposed in UI. */
final class BannerSettings {
    static final int[] CODES = {0, 6090, 10001, 10002, 6045, 6080, 6050, 6060, 6070};
    static final String[] NAMES = {
        "Новинки — все разделы", "Фильмы · DVD / WEB",
        "HD · 720p", "FullHD · 1080p", "4K / UHD", "SD · 480p",
        "Сборники", "Фотосеты", "VR"
    };
    static final String DEFAULT_CATEGORIES="0";
    static final String DEFAULT_QUERY="";
    static final int MAX_QUERY_LENGTH=90;

    /** Accept only known native category IDs, keeping the UI ordering. */
    static List<Integer> parse(String raw) {
        Set<Integer> input=new LinkedHashSet<>();
        if(raw!=null) {
            for(String entry:raw.split(",")) {
                try{input.add(Integer.parseInt(entry.trim()));}
                catch(Exception ignored){}
            }
        }
        List<Integer> chosen=new ArrayList<>();
        for(int code:CODES)if(input.contains(code))chosen.add(code);
        if(chosen.isEmpty()||chosen.contains(0))return Arrays.asList(0);
        return chosen;
    }
    static String serialize(boolean[] checked) {
        if(checked==null||checked.length!=CODES.length||checked[0])
            return DEFAULT_CATEGORIES;
        StringBuilder builder=new StringBuilder();
        for(int i=1;i<CODES.length;i++) {
            if(checked[i]) {
                if(builder.length()>0)builder.append(',');
                builder.append(CODES[i]);
            }
        }
        return builder.length()==0?DEFAULT_CATEGORIES:builder.toString();
    }
    static String normalizeQuery(String input) {
        if(input==null)return DEFAULT_QUERY;
        String result=input.trim().replaceAll("\\s+"," ");
        return result.length()>MAX_QUERY_LENGTH?
            result.substring(0,MAX_QUERY_LENGTH):result;
    }
    static String summary(String raw,String query) {
        List<Integer> categories=parse(raw);
        StringBuilder name=new StringBuilder();
        for(int code:categories) {
            for(int i=0;i<CODES.length;i++)if(CODES[i]==code){
                if(name.length()>0)name.append(", ");
                name.append(NAMES[i].replace(" · DVD / WEB","").replace(" — все разделы",""));
                break;
            }
        }
        String keyword=normalizeQuery(query);
        return keyword.isEmpty()?name.toString():name+" · тема: "+keyword;
    }
    private BannerSettings(){}
}
