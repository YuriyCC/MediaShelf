package org.mediashelf.tv;

final class PosterGrid {
    static final class Size {
        final int columns;
        final int cardDp;
        final int usableDp;
        final int spacingDp;
        Size(int columns,int cardDp,int usableDp,int spacingDp){
            this.columns=columns;this.cardDp=cardDp;
            this.usableDp=usableDp;this.spacingDp=spacingDp;
        }
    }
    static Size calculate(int screenWidthDp,int sidebarWidthDp,
        boolean sidebarHidden,boolean compactPreview,boolean tvLayout) {
        int side=tvLayout&&!sidebarHidden?sidebarWidthDp:0;
        int padding=compactPreview?25:tvLayout?43:48;
        int usable=Math.max(238,screenWidthDp-side-padding);
        int spacing=compactPreview?7:tvLayout?9:10;
        int target=compactPreview?92:tvLayout?119:160;
        int max=compactPreview?8:tvLayout?11:2;
        int columns=tvLayout?Math.max(3,Math.min(max,usable/target)):2;
        int card=Math.max(compactPreview?58:tvLayout?86:126,
            (usable-columns*spacing-8)/columns);
        return new Size(columns,card,usable,spacing);
    }
    private PosterGrid(){}
}
