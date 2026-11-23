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

    /** Always pass the user's full phrase to native site search. */
    static String upstreamQuery(String query) {
        return query==null?"":query.trim();
    }

    /** A match may span title, distribution details, genre and tag metadata. */
    static boolean matchesContent(String title,String description,String tags,String query){
        List<String> terms=words(query);
        if(terms.isEmpty())return true;
        String searchable=(title==null?"":title)+" "+
            (description==null?"":description)+" "+
            (tags==null?"":tags);
        for(String term:terms){
            Pattern wholeWord=Pattern.compile("(?<![\\p{L}\\p{N}])"+
                Pattern.quote(term)+"(?![\\p{L}\\p{N}])",
                Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);
            if(!wholeWord.matcher(searchable).find())return false;
        }
        return true;
    }
    static boolean matchesContent(MainActivity.Item item,String query){
        return item!=null && matchesContent(item.title,item.description,item.searchMetadata,query);
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
