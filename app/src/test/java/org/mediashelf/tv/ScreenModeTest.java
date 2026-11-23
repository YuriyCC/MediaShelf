package org.mediashelf.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ScreenModeTest {
    @Test public void oldPreviewCannotForcePhoneIntoTvLayout(){
        assertFalse(ScreenMode.useTvLayout(false,true,853,384));
        assertFalse(ScreenMode.useTvLayout(false,true,1100,600));
        assertFalse(ScreenMode.useTvLayout(false,false,400,800));
    }
    @Test public void androidTelevisionAlwaysKeepsRemoteNavigation(){
        assertTrue(ScreenMode.useTvLayout(true,false,384,853));
        assertTrue(ScreenMode.useTvLayout(true,true,1920,1080));
    }
    @Test public void portraitIsLockedOnlyOnPhones(){
        assertTrue(ScreenMode.lockPortrait(false,393));
        assertFalse(ScreenMode.lockPortrait(false,700));
        assertFalse(ScreenMode.lockPortrait(true,393));
    }
}
