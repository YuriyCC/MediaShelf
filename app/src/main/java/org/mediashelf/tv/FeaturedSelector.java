package org.mediashelf.tv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Homepage hero selection. Candidates come from the site's actual "Movies"
 * category, never from a random first item in a mixed adult torrent feed.
 * Heuristics can't verify a film's actual quality, and never label it as such.
 */
final class FeaturedSelector {
    static final class Candidate {
        final String title,poster,details;
        final int seeders;
        Candidate(String title,String poster,String details,int seeders){
            this.title=title==null?"":title;
            this.poster=poster==null?"":poster;
            this.details=details==null?"":details;
            this.seeders=seeders;
        }
    }

    static List<Integer> select(List<Candidate> candidates,int limit){
        if(candidates==null || candidates.isEmpty() || limit<=0)
            return Collections.emptyList();
        List<Integer> indices=new ArrayList<>();
        for(int i=0;i<candidates.size();i++){
            if(eligible(candidates.get(i)))indices.add(i);
        }
        indices.sort((i,j)->{
            int scoreCompare=Integer.compare(score(candidates.get(j)),
                                              score(candidates.get(i)));
            return scoreCompare!=0?scoreCompare:Integer.compare(i,j);
        });
        List<Integer> picked=new ArrayList<>();
        Set<String> seen=new HashSet<>();
        for(int index:indices){
            Candidate c=candidates.get(index);
            String key=!c.details.isEmpty()?c.details.toLowerCase(Locale.ROOT):
                c.title.trim().toLowerCase(Locale.ROOT);
            if(!seen.add(key))continue;
            picked.add(index);
            if(picked.size()>=limit)break;
        }
        return picked;
    }

    static boolean eligible(Candidate c){
        if(c==null || c.title.trim().isEmpty())return false;
        if(!c.poster.startsWith("https://") &&
           !c.poster.startsWith("http://"))return false;
        if(c.seeders<=0)return false;
        String text=c.title.toLowerCase(Locale.ROOT)
            .replaceAll("[\\[\\]()._+/-]"," ");
        // A category might contain mislabeled packs or image sets. Keep them
        // in the regular catalog; never make them a homepage hero.
        return !text.matches("(?s).*(?:\\bmega\\s*pack\\b|\\bpack(?:s)?\\b|"+
            "\\bcompilation\\b|\\bcollection\\b|\\bsiterip\\b|\\bsite\\s*rip\\b|"+
            "\\bphoto(?:s|set)?\\b|\\bimage(?:s|set)?\\b|\\bgallery\\b|"+
            "\\bpreview\\b|\\bvirtual\\s*reality\\b|\\bvr\\b|"+
            "\\bсборник\\b|\\bподборка\\b|\\bфотосет\\b).*");
    }


    /**
     * Home carousel defaults to the site's newest releases, in site order.
     * Allow packs/photos/VR only when the user explicitly selected those
     * categories in banner settings, not on the all-site default feed.
     */
    static List<Integer> selectNewest(List<Candidate> candidates,int limit,
                                      boolean includeCollections) {
        if(candidates==null||limit<=0)return Collections.emptyList();
        List<Integer> result=new ArrayList<>();
        Set<String> keys=new HashSet<>();
        for(int i=0;i<candidates.size();i++){
            Candidate c=candidates.get(i);
            if(c==null||c.title.trim().isEmpty()||c.seeders<=0)continue;
            if(!c.poster.startsWith("https://")&&
                !c.poster.startsWith("http://"))continue;
            if(!includeCollections&&!eligible(c))continue;
            String key=!c.details.isEmpty()?c.details.toLowerCase(Locale.ROOT):
                c.title.toLowerCase(Locale.ROOT).trim();
            if(!keys.add(key))continue;
            result.add(i);
            if(result.size()>=limit)break;
        }
        return result;
    }

    private static int score(Candidate c){
        String t=c.title.toLowerCase(Locale.ROOT);
        int quality=0;
        if(t.contains("2160")||t.matches("(?s).*\\b4k\\b.*")||
           t.contains("uhd"))quality=50;
        else if(t.contains("1080")||t.contains("fullhd")||
                t.contains("full hd"))quality=40;
        else if(t.contains("720")||t.matches("(?s).*\\bhd\\b.*"))
            quality=24;
        int availability=(int)Math.min(55,
            Math.round(Math.log1p(c.seeders)*12));
        int titleBonus=c.title.length()<=100?9:0;
        return quality+availability+titleBonus;
    }
    private FeaturedSelector(){}
}
