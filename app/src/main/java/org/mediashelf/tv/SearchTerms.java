package org.mediashelf.tv;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * AND search across title words. Site engines do not necessarily interpret
 * multi-word queries as AND, so query with one seed term and verify every
 * requested whole word locally.
 */
final class SearchTerms {
    private SearchTerms(){}

    static List<String> words(String query) {
        Set<String> unique=new LinkedHashSet<>();
        if(query!=null)for(String raw:query.trim().split("\\s+")){
            String token=raw.trim();
            if(!token.isEmpty())unique.add(token.toLowerCase(java.util.Locale.ROOT));
        }
        return new ArrayList<>(unique);
    }

    static boolean needsAndFilter(String query){
        return words(query).size()>1;
    }

    static String upstreamQuery(String query) {
        List<String> terms=words(query);
        if(terms.size()>1)return terms.get(0);
        return query==null?"":query.trim();
    }

    static boolean matchesTitle(String title,String query) {
        List<String> terms=words(query);
        if(terms.size()<2)return true; // Preserve existing single-term source search.
        if(title==null || title.isEmpty())return false;
        for(String term:terms){
            Pattern wholeWord=Pattern.compile("(?<![\\p{L}\\p{N}])"+
                Pattern.quote(term)+"(?![\\p{L}\\p{N}])",
                Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
            if(!wholeWord.matcher(title).find())return false;
        }
        return true;
    }
}
