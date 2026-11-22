package org.mediashelf.tv;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

/** No live site or Android UI needed: validate homepage carousel selection. */
public class FeaturedSelectorTest {
    private FeaturedSelector.Candidate film(String title,int seeds,String poster,String id){
        return new FeaturedSelector.Candidate(title,poster,id,seeds);
    }
    private FeaturedSelector.Candidate film(String title,int seeds){
        return film(title,seeds,"https://example.com/poster.jpg",title);
    }
    @Test public void firstMegaPackIsNotTheFeaturedFilm(){
        List<FeaturedSelector.Candidate> rows=Arrays.asList(
            film("MegaPACK - 329 Movie Pack 1080p",999),
            film("Sample Movie 1080p",14),
            film("Classic Film 4K",3));
        List<Integer> ids=FeaturedSelector.select(rows,5);
        assertEquals(2,ids.size());
        assertFalse(ids.contains(0));
        assertEquals(Integer.valueOf(1),ids.get(0));
    }
    @Test public void removesPacksImagesVrAndCollections(){
        String[] excluded={"Movie Pack","1000 photos","Clip collection",
            "Studio Siterip","Virtual Reality VR","Фотосет",
            "Большой сборник","MegaPACK"};
        for(String title:excluded)
            assertFalse(title,FeaturedSelector.eligible(film(title,9)));
    }
    @Test public void rejectsDeadOrCoverlessEntries(){
        assertFalse(FeaturedSelector.eligible(film("Movie 1080p",0)));
        assertFalse(FeaturedSelector.eligible(film("Movie 1080p",20,"","1")));
        assertFalse(FeaturedSelector.eligible(film("Movie 1080p",20,"data:image/abc","1")));
    }
    @Test public void picksFiveCandidatesAndNoMore(){
        List<FeaturedSelector.Candidate> list=new ArrayList<>();
        for(int i=0;i<12;i++)list.add(film("Movie "+i+" 1080p",i+1));
        assertEquals(5,FeaturedSelector.select(list,5).size());
    }
    @Test public void doesNotDuplicateTheSameTorrentDetail(){
        List<FeaturedSelector.Candidate> rows=Arrays.asList(
            film("Film 1080p",22,"https://img.net/1.jpg","https://site/details/one"),
            film("Film copy 1080p",30,"https://img.net/2.jpg","https://site/details/one"),
            film("Another film 720p",10,"https://img.net/3.jpg","https://site/details/two"));
        assertEquals(2,FeaturedSelector.select(rows,5).size());
    }
    @Test public void prioritizesQualityAndAvailableSeeds(){
        List<FeaturedSelector.Candidate> list=Arrays.asList(
            film("Single Movie 720p",2),
            film("Single Film 1080p",22),
            film("Single Cinema 4K UHD",16));
        assertEquals(Integer.valueOf(2),FeaturedSelector.select(list,3).get(0));
    }
    @Test public void ordinaryWordsContainingPackAreNotExcluded(){
        assertTrue(FeaturedSelector.eligible(film("Backpacker Adventure 1080p",12)));
    }
    @Test public void emptyInputsDoNotCrash(){
        assertTrue(FeaturedSelector.select(null,5).isEmpty());
        assertTrue(FeaturedSelector.select(new ArrayList<>(),5).isEmpty());
        assertTrue(FeaturedSelector.select(Arrays.asList(film("Film",2)),0).isEmpty());
    }
    @Test public void rankingIsDeterministic(){
        List<FeaturedSelector.Candidate> rows=Arrays.asList(
            film("Movie A 1080p",10),film("Movie B 1080p",10),
            film("Movie C 1080p",10));
        assertEquals(FeaturedSelector.select(rows,3),FeaturedSelector.select(rows,3));
    }
}
