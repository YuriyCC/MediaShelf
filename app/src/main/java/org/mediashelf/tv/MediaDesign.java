package org.mediashelf.tv;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

/**
 * Shared visual language for MediaShelf's television and phone screens.
 * Android framework only: no WebView, launcher frameworks or remote assets.
 */
final class MediaDesign {
    static final int BG=0xff0b101b;
    static final int SIDEBAR=0xff111928;
    static final int SURFACE=0xff1b2538;
    static final int SURFACE_UP=0xff253450;
    static final int TEXT=0xfff5f7ff;
    static final int MUTED=0xff9eaec8;
    static final int ACCENT=0xff8caaff;
    static final int ACCENT_LIGHT=0xffb5c9ff;
    static final int GOLD=0xffefc28b;
    static final int SUCCESS=0xff83d5b4;

    static int dp(Context c,float n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable round(Context c,int color,int radius){
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c,radius));
        return d;
    }
    static GradientDrawable outlined(Context c,int color,int border,int radius,int thickness){
        GradientDrawable d=round(c,color,radius);
        d.setStroke(dp(c,thickness),border);
        return d;
    }
    static GradientDrawable gradient(Context c,int[] colors,int radius){
        GradientDrawable d=new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,colors);
        d.setCornerRadius(dp(c,radius));
        return d;
    }
    static GradientDrawable horizontal(Context c,int[] colors,int radius){
        GradientDrawable d=new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,colors);
        d.setCornerRadius(dp(c,radius));
        return d;
    }
    static TextView tag(Context c,String label,int textColor,int background){
        TextView t=new TextView(c);
        t.setText(label);
        t.setTextSize(11);
        t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        t.setTextColor(textColor);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c,10),dp(c,5),dp(c,10),dp(c,5));
        t.setBackground(round(c,background,9));
        return t;
    }
    static void animateFocus(View view,boolean focus){
        view.animate().cancel();
        view.animate().scaleX(focus?1.035f:1f).scaleY(focus?1.035f:1f)
            .setDuration(125).start();
        view.setElevation(focus?dp(view.getContext(),12):0);
    }
    static String quality(String title){
        if(title==null)return "";
        String u=title.toUpperCase(java.util.Locale.ROOT);
        if(u.contains("2160")||u.matches(".*(?:^|[^A-Z0-9])4K(?:[^A-Z0-9]|$).*")||u.contains("UHD"))
            return "4K UHD";
        if(u.contains("1080")||u.contains("FULLHD")||u.contains("FULL HD"))return "FULL HD";
        if(u.contains("720")||u.contains("HD"))return "HD";
        return "";
    }
    private MediaDesign(){}
}
