package org.mediashelf.tv;

import org.junit.Test;
import static org.junit.Assert.*;

public class PosterGridTest {
    @Test public void hidingMenuAddsTelevisionColumns(){
        PosterGrid.Size open=PosterGrid.calculate(960,176,false,false,true);
        PosterGrid.Size hidden=PosterGrid.calculate(960,176,true,false,true);
        assertTrue(hidden.columns>open.columns);
        assertTrue(open.cardDp*open.columns+open.spacingDp*open.columns<=open.usableDp);
        assertTrue(hidden.cardDp*hidden.columns+hidden.spacingDp*hidden.columns<=hidden.usableDp);
    }
    @Test public void hidingMenuAddsLandscapePhoneColumns(){
        PosterGrid.Size open=PosterGrid.calculate(690,138,false,true,true);
        PosterGrid.Size hidden=PosterGrid.calculate(690,138,true,true,true);
        assertTrue(hidden.columns>open.columns);
        assertTrue(hidden.cardDp*hidden.columns+hidden.spacingDp*hidden.columns<=hidden.usableDp);
    }
    @Test public void regularPhoneRemainsTwoColumns(){
        assertEquals(2,PosterGrid.calculate(390,0,false,false,false).columns);
    }
    @Test public void widescreenCapsColumns(){
        assertEquals(11,PosterGrid.calculate(2400,176,true,false,true).columns);
    }
}
