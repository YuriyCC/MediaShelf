package org.mediashelf.tv;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Display-only title order. Never rewrites tracker titles or upstream pages. */
final class SmartTitleSort {
    private static final Pattern WORD=Pattern.compile("[\\p{L}\\p{N}]+");
    // "Mega Pack", "MegaPack", "Mega-Pack", or "[MEGA PACK]".
    private static final Pattern MEGAPACK=Pattern.compile(
        "(?iu)^\\s*[\\[({]?\\s*mega[\\s._-]*pa(?:ck|k)\\b\\s*[\\])}]?\\s*[-–—:|.\\s]*");
    private SmartTitleSort(){}

    static String withoutPack(String name){
        String original=name==null?"":name.trim();
        String trimmed=MEGAPACK.matcher(original).replaceFirst("").trim();
        return trimmed.isEmpty()?original:trimmed;
    }

    private static List<String> words(String name){
        List<String> tokens=new ArrayList<>();
        Matcher matcher=WORD.matcher(withoutPack(name).toLowerCase(Locale.ROOT));
        while(matcher.find())tokens.add(matcher.group());
        return tokens;
    }

    /**
     * Detect an initial phrase repeated across most displayed titles,
     * e.g. "Studio Mega -", without removing titles' actual identities.
     * A prefix must occur in >=60% of loaded entries and contain fewer
     * tokens than at least one matching title.
     */
    static List<String> sharedPrefix(List<String> titles){
        if(titles.size()<3)return Collections.emptyList();
        final int threshold=Math.max(3,(int)Math.ceil(titles.size()*0.60));
        List<String> winner=Collections.emptyList();
        for(int length=1;length<=4;length++){
            Map<String,Integer> count=new HashMap<>();
            for(String title:titles){
                List<String> tokens=words(title);
                if(tokens.size()<=length)continue;
                String key=String.join("\u001f",tokens.subList(0,length));
                count.put(key,count.getOrDefault(key,0)+1);
            }
            for(Map.Entry<String,Integer> entry:count.entrySet()){
                if(entry.getValue()>=threshold && length>winner.size()){
                    winner=new ArrayList<>();
                    Collections.addAll(winner,entry.getKey().split("\u001f",-1));
                }
            }
        }
        return winner;
    }

    static String smartKey(String original,List<String> common){
        String title=withoutPack(original);
        if(common.isEmpty())return title;
        Matcher matcher=WORD.matcher(title);
        int matched=0;
        int end=0;
        while(matcher.find() && matched<common.size()){
            if(!matcher.group().equalsIgnoreCase(common.get(matched)))
                return title;
            matched++;
            end=matcher.end();
        }
        if(matched!=common.size())return title;
        String remainder=title.substring(end).replaceFirst("^[\\s.,;:_/|–—-]+","").trim();
        return remainder.isEmpty()?title:remainder;
    }

    /** 0 site, 1 literal A-Z, 2 literal Z-A, 3 smart A-Z, 4 smart Z-A. */
    static <T> List<T> sorted(List<T> input,Function<T,String> title,int mode){
        if(mode<=0 || input.size()<2)return input;
        List<T> out=new ArrayList<>(input);
        List<String> labels=new ArrayList<>();
        for(T element:out)labels.add(title.apply(element));
        List<String> shared=(mode>=3)?sharedPrefix(labels):Collections.emptyList();
        Collator collator=Collator.getInstance(Locale.getDefault());
        collator.setStrength(Collator.PRIMARY);
        out.sort((left,right)->{
            String first=title.apply(left),second=title.apply(right);
            String a=mode>=3?smartKey(first,shared):first;
            String b=mode>=3?smartKey(second,shared):second;
            int comparison=collator.compare(a,b);
            if(comparison==0)comparison=collator.compare(first,second);
            return mode==2 || mode==4?-comparison:comparison;
        });
        return out;
    }
}