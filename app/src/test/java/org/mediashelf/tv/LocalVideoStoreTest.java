package org.mediashelf.tv;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.io.File;

public final class LocalVideoStoreTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    @Test public void downloadedNameStripsDirectoriesAndInvalidCharacters(){
        assertEquals("video.mkv",LocalVideoStore.safeName("shows/2026/video.mkv"));
        assertEquals("movie_.mp4",LocalVideoStore.safeName("movie?.mp4"));
        assertEquals("MediaShelf-video.mp4",LocalVideoStore.safeName(""));
    }
    @Test public void targetNeverOverwritesExistingDownloadedMovie() throws Exception{
        File folder=temp.newFolder("Movies");
        assertTrue(new File(folder,"my-film.mkv").createNewFile());
        File next=LocalVideoStore.uniqueTarget(folder,"my-film.mkv");
        assertEquals("my-film (1).mkv",next.getName());
        assertFalse(next.exists());
    }
    @Test public void incompleteFileIsNeverVisibleAsDownload() throws Exception {
        File dir=temp.newFolder();
        File incomplete=new File(dir,"my-film.mkv.part");
        assertTrue(incomplete.createNewFile());
        assertFalse(LocalVideoStore.isVideo(incomplete));
        File done=new File(dir,"my-film.mkv");
        assertTrue(done.createNewFile());
        assertTrue(LocalVideoStore.isVideo(done));
    }
}