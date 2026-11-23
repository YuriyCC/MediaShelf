package org.mediashelf.tv;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/** A local view filter: only loaded card titles are inspected. */
final class CatalogTitleFilter {
    private CatalogTitleFilter(){}
    static String normalize(String value){
        return (value==null?"":value.trim()).toLowerCase(Locale.ROOT)
            .replace('ё','е');
    }
    static boolean matches(String title,String keyword){
        String target=normalize(keyword);
        return target.isEmpty() || normalize(title).contains(target);
    }
    static <T> List<T> apply(List<T> items,Function<T,String> title,String keyword){
        if(normalize(keyword).isEmpty())return items;
        List<T> results=new ArrayList<>();
        for(T item:items)if(matches(title.apply(item),keyword))results.add(item);
        return results;
    }
}
