package org.mediashelf.tv;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.Test;
import static org.junit.Assert.*;

public final class SiteDetailsImageTest {
    private Element picture(String html) {
        return Jsoup.parse(html,"https://example.org/release/42").selectFirst("img");
    }

    @Test public void fullPhotoLinkBeatsSmallThumbnail(){
        Element img=picture("<a href='/photos/original_scene.jpg'>"+
            "<img src='/thumbs/scene.jpg'></a>");
        assertEquals("https://example.org/photos/original_scene.jpg",
            SiteDetails.imageSrc(img));
    }

    @Test public void largerSrcsetWinsDespiteReverseOrder(){
        Element img=picture("<img src='/thumbs/scene.jpg' "+
            "srcset='/photos/scene_big.jpg 1800w, /photos/scene_small.jpg 300w'>");
        assertEquals("https://example.org/photos/scene_big.jpg",
            SiteDetails.imageSrc(img));
    }

    @Test public void linkedThumbnailLosesToFullResolutionDataAttribute(){
        Element img=picture("<a href='/thumbs/image.jpg'>"+
            "<img data-original='/photos/original_image.jpg' src='/thumbs/image.jpg'></a>");
        assertEquals("https://example.org/photos/original_image.jpg",
            SiteDetails.imageSrc(img));
    }

    @Test public void pictureSourceFullImageIsConsidered(){
        Element img=picture("<picture><source srcset='/photos/full_img.webp 2048w'>"+
            "<img src='/thumbs/scene.jpg'></picture>");
        assertEquals("https://example.org/photos/full_img.webp",
            SiteDetails.imageSrc(img));
    }

    @Test public void legacyPlainImageStillWorks(){
        Element img=picture("<img src='/photos/plain.jpg'>");
        assertEquals("https://example.org/photos/plain.jpg",SiteDetails.imageSrc(img));
    }

    @Test public void externalWebImageLinkCanBeUsed(){
        Element img=picture("<a href='https://cdn.example.net/a/fullsize.png?token=abc'>"+
            "<img src='/thumbs/a.jpg'></a>");
        assertEquals("https://cdn.example.net/a/fullsize.png?token=abc",
            SiteDetails.imageSrc(img));
    }
}
