package org.mediashelf.tv;

/**
 * A phone stays touch-first regardless of landscape size or stale "tv_preview"
 * preference. Only a real Android TV device receives the remote-first rail.
 */
final class ScreenMode {
    private ScreenMode(){}

    static boolean useTvLayout(boolean isAndroidTv, boolean legacyTvPreview,
                               int widthDp, int heightDp){
        return isAndroidTv;
    }

    static boolean lockPortrait(boolean isAndroidTv,int smallestWidthDp){
        return !isAndroidTv && smallestWidthDp<600;
    }
}
