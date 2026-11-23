package org.mediashelf.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.graphics.drawable.Drawable;
import android.content.pm.ActivityInfo;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ReplacementSpan;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.InputType;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.Future;

public final class MainActivity extends Activity {
    private static final int BACK = MediaDesign.BG, PANEL = MediaDesign.SURFACE,
        WHITE = MediaDesign.TEXT, MUTED = MediaDesign.MUTED, BLUE = MediaDesign.ACCENT;
    // Source IDs are loaded from Jackett dynamically; "all" is the virtual combined source.
    private final List<SourceRegistry.Source> sources = new ArrayList<>();
    private final java.util.Map<String,String> probeResults = new java.util.concurrent.ConcurrentHashMap<>();
    private LinearLayout sourceButtons;
    private TextView sourceSummary;
    private Button sourceSelector;
    private Button topSearchButton;
    private Button sidebarToggleButton;
    private LinearLayout sidebarPanel;
    private LinearLayout sidebarNavigation;
    private Button navHomeButton,navPopularButton,navSourceButton,navFavoritesButton;
    private Drawable popularFlameIcon;
    private Button navSectionsButton,navActiveCategoryButton;
    private boolean sidebarSectionsExpanded=false;
    private String sidebarFetchedSource="";
    private boolean sidebarFetchingCategories=false;
    private String sidebarCategoriesError="";
    private final List<SourceRegistry.Category> sidebarFetchedCategories=new ArrayList<>();
    private TextView currentSectionLabel;
    private boolean sidebarCollapsed;
    private int sidebarWidthDp;
    private Button bannerSettingsButton;
    private View firstCatalogTile;
    private boolean tvLayout;
    private boolean tvPreviewMode;
    private boolean compactTvPreview;
    private boolean hardwareTv;
    private int currentCategory = 0;
    private String currentCategoryLabel = "";
    private String activeSource = "xxxclub";
    private boolean loadingSources = false;
    private String sourceListError = "";
    private final List<Item> catalogItems = new ArrayList<>();
    private SitePages.Cursor sitePageCursor;
    private MultiWordSession multiWordSession;
    private int visibleCount=60;
    private boolean loadingNextPage=false;
    private String pageInfo="";
    private ScrollView catalogScroll;
    private final ExecutorService worker = Executors.newFixedThreadPool(4);
    private final ExecutorService metadataWorker = Executors.newFixedThreadPool(4);
    private final java.util.Set<Item> pendingPosterMetadata=
        java.util.Collections.newSetFromMap(
            new java.util.concurrent.ConcurrentHashMap<Item,Boolean>());
    private static final int IMAGE_REQUEST_TAG=0x7f0e0012;
    private final ExecutorService imageWorker = Executors.newFixedThreadPool(5);
    private final ExecutorService fullImageWorker = Executors.newSingleThreadExecutor();
    private View catalogPage;
    private View detailsPage;
    private View fullPhotoPage;
    private View galleryReturnFocus;
    private PopupWindow cardActionPopup;
    private View cardReturnFocus;
    private int cardReturnScroll;
    private static final int SAVE_VIDEO_REQUEST=9301;
    private DownloadSelection pendingDownload;
    private AtomicBoolean activeDownload;
    private volatile HttpURLConnection activeDownloadConnection;
    private final Handler main = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private FavoritesStore favorites;
    private SavedSearches savedSearches;
    private boolean favoritesMode=false;
    private LinearLayout cards;
    private TextView status;
    private EditText queryInput;
    private int serial = 0;
    private String lastQuery = "";
    private String lastSort = "latest";

    private int dp(float value) { return (int)(getResources().getDisplayMetrics().density * value + 0.5f); }
    private GradientDrawable panel(int bg, boolean focus) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg); d.setCornerRadius(dp(14));
        if (focus) d.setStroke(dp(2), BLUE);
        return d;
    }
    private ImageView mediaShelfLogo(){
        ImageView logo=new ImageView(this);
        logo.setImageResource(R.drawable.mediashelf_icon);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setFocusable(false);
        logo.setContentDescription("MediaShelf");
        return logo;
    }
    private TextView label(String text, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text); v.setTextSize(size); v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }
    private LinearLayout line() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }
    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }
    private Button button(String title) {
        Button b = new Button(this);
        b.setText(title); b.setAllCaps(false); b.setTextColor(WHITE);
        b.setBackground(MediaDesign.round(this,PANEL,13));
        b.setFocusable(true);
        b.setMinHeight(0);b.setMinimumHeight(0);
        b.setOnFocusChangeListener((v,f) -> {
            b.setBackground(f?MediaDesign.outlined(this,0xff44306d,
                MediaDesign.ACCENT,13,2):MediaDesign.round(this,PANEL,13));
            MediaDesign.animateFocus(b,f);
        });
        return b;
    }
    private LinearLayout.LayoutParams lp(int w,int h) {
        return new LinearLayout.LayoutParams(w < 0 ? w : dp(w),h < 0 ? h : dp(h));
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs=getSharedPreferences("local_settings",MODE_PRIVATE);
        favorites=new FavoritesStore(prefs);
        savedSearches=new SavedSearches(prefs);
        int mode=getResources().getConfiguration().uiMode &
            android.content.res.Configuration.UI_MODE_TYPE_MASK;
        hardwareTv=mode==android.content.res.Configuration.UI_MODE_TYPE_TELEVISION;
        if(!hardwareTv && prefs.getBoolean("tv_preview",false))
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        activeSource=prefs.getString("indexer","xxxclub");
        sources.addAll(SourceRegistry.fromCache(prefs.getString("source_cache","[]")));
        if(activeSource.isEmpty())activeSource="xxxclub";
        if(saved!=null){
            lastQuery=saved.getString("last_query","");
            favoritesMode=saved.getBoolean("favorites_mode",false);
            lastSort=saved.getString("last_sort","latest");
            currentCategory=saved.getInt("current_category",0);
            currentCategoryLabel=saved.getString("category_label","");
        }
        ui();
        if(queryInput!=null && !lastQuery.isEmpty())queryInput.setText(lastQuery);
        if(!prefs.getString("api_key","").isEmpty())loadSources();
        if(favoritesMode)openFavorites();
        else load(lastQuery,lastSort);
    }
    @Override protected void onSaveInstanceState(Bundle out){
        out.putString("last_query",lastQuery);
        out.putBoolean("favorites_mode",favoritesMode);
        out.putString("last_sort",lastSort);
        out.putInt("current_category",currentCategory);
        out.putString("category_label",currentCategoryLabel);
        super.onSaveInstanceState(out);
    }
    private void toggleTvPreview(){
        if(hardwareTv)return;
        boolean enabled=!prefs.getBoolean("tv_preview",false);
        prefs.edit().putBoolean("tv_preview",enabled).apply();
        setRequestedOrientation(enabled?
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        recreate();
    }
    private final List<Button> filterButtons=new ArrayList<>();
    private final List<Integer> filterCodes=new ArrayList<>();
    private final android.util.LruCache<String,Bitmap> pictureCache =
        new android.util.LruCache<String,Bitmap>(24*1024*1024){
            @Override protected int sizeOf(String key,Bitmap bitmap){return bitmap.getByteCount();}
        };
    private int focusItemAfterLoad=-1;
    private boolean resetCatalogScroll=false;

    // Homepage is an editorial carousel driven by actual items in XXXClub's
    // native Movies category, rather than an arbitrary first torrent.
    private final List<Item> featuredItems=new ArrayList<>();
    private boolean featuredLoading=false;
    private int featuredIndex=0;
    private android.widget.FrameLayout featuredHost;
    private ImageView featuredPoster;
    private TextView featuredTitle,featuredMeta,featuredDots,featuredEyebrow;
    private LinearLayout featuredControls;
    private boolean carouselActive=false;
    private final Runnable carouselAdvance=new Runnable(){
        @Override public void run(){
            if(!carouselActive || featuredHost==null ||
                featuredHost.getParent()==null || featuredItems.size()<2)return;
            if(featuredHost.findFocus()==null)advanceCarousel(1);
            scheduleCarousel();
        }
    };

    private boolean isHomepage(){
        return currentCategory==0 && lastQuery.trim().isEmpty() &&
            (lastSort.equals("latest")||lastSort.equals("popular")) &&
            (activeSource.equals("xxxclub")||activeSource.equals("all"));
    }
    private void scheduleCarousel(){
        main.removeCallbacks(carouselAdvance);
        if(carouselActive && featuredHost!=null &&
            featuredHost.getParent()!=null && featuredItems.size()>1){
            main.postDelayed(carouselAdvance,7500L);
        }
    }
    @Override protected void onResume(){
        super.onResume();
        carouselActive=true;
        scheduleCarousel();
    }
    @Override protected void onPause(){
        carouselActive=false;
        main.removeCallbacks(carouselAdvance);
        super.onPause();
    }
    private void advanceCarousel(int delta){
        if(featuredItems.size()<2)return;
        featuredIndex=(featuredIndex+delta+featuredItems.size())%featuredItems.size();
        renderCarouselSlide();
        scheduleCarousel();
    }
    private String bannerPreferenceSuffix(){
        // At present the only native direct site is XXXClub; "all" resolves
        // to it. The preference key remains per tracker for future modules.
        return activeSource.equals("all")?"xxxclub":activeSource;
    }
    private String bannerCats(){
        return prefs.getString("banner_categories_"+bannerPreferenceSuffix(),
            BannerSettings.DEFAULT_CATEGORIES);
    }
    private String bannerTopic(){
        return prefs.getString("banner_query_"+bannerPreferenceSuffix(),
            BannerSettings.DEFAULT_QUERY);
    }

    private void configureBanner(){
        if(!activeSource.equals("xxxclub")&&!activeSource.equals("all")){
            themedDialog().setTitle("Настройка баннера")
                .setMessage("Пока настраивается прямой источник XXXClub. "+
                    "Для других сайтов настройки появятся с их модулями.")
                .setPositiveButton("Закрыть",null).show();
            return;
        }
        List<Integer> initial=BannerSettings.parse(bannerCats());
        LinearLayout body=column();
        body.setPadding(dp(20),dp(12),dp(20),dp(12));
        TextView intro=label("Выбери разделы, из которых брать новинки. "+
            "Можно выбрать несколько. «Все разделы» отключает остальные.",
            13,MediaDesign.MUTED,false);
        body.addView(intro,spaced(-1,-2,0,11));
        android.widget.CheckBox[] checked=new android.widget.CheckBox[
            BannerSettings.CODES.length];
        final boolean[] updating={false};
        for(int i=0;i<checked.length;i++){
            final int index=i;
            android.widget.CheckBox c=new android.widget.CheckBox(this);
            c.setText(BannerSettings.NAMES[i]);
            c.setTextSize(tvLayout?15:14);
            c.setTextColor(MediaDesign.TEXT);
            c.setButtonTintList(android.content.res.ColorStateList.valueOf(
                MediaDesign.ACCENT));
            c.setChecked(initial.contains(BannerSettings.CODES[i]));
            c.setPadding(dp(4),0,0,0);
            c.setFocusable(true);
            c.setOnCheckedChangeListener((button,value)->{
                if(updating[0]||!value)return;
                updating[0]=true;
                if(index==0){
                    for(int j=1;j<checked.length;j++)checked[j].setChecked(false);
                }else checked[0].setChecked(false);
                updating[0]=false;
            });
            checked[i]=c;
            body.addView(c,lp(-1,tvLayout?40:42));
        }
        TextView keywordTitle=label("ТЕМА ДЛЯ БАННЕРА (НЕОБЯЗАТЕЛЬНО)",
            11,MediaDesign.ACCENT_LIGHT,true);
        keywordTitle.setPadding(0,dp(14),0,dp(7));
        body.addView(keywordTitle);
        EditText query=new EditText(this);
        query.setSingleLine(true);
        query.setTextColor(MediaDesign.TEXT);
        query.setHintTextColor(MediaDesign.MUTED);
        query.setTextSize(15);
        query.setHint("Например, Gangbang");
        query.setText(bannerTopic());
        query.setSelectAllOnFocus(true);
        query.setImeOptions(EditorInfo.IME_ACTION_DONE);
        query.setBackground(MediaDesign.outlined(this,0xff1a263b,
            0xff483376,12,1));
        query.setPadding(dp(14),0,dp(12),0);
        body.addView(query,lp(-1,49));
        TextView example=label("Пример: «Новинки — все разделы» + Gangbang "+
            "покажет новые результаты поиска Gangbang. "+
            "Пустая тема означает обычные новинки выбранных разделов.",
            12,MediaDesign.MUTED,false);
        example.setPadding(0,dp(11),0,0);
        body.addView(example);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.addView(body);
        themedDialog().setTitle("Баннер · XXXClub")
            .setView(scroll)
            .setNegativeButton("Отмена",null)
            .setNeutralButton("Сбросить",(dialog,which)->{
                prefs.edit().putString(
                    "banner_categories_"+bannerPreferenceSuffix(),"0")
                    .putString("banner_query_"+bannerPreferenceSuffix(),"")
                    .apply();
                refreshBannerFromPreferences();
            })
            .setPositiveButton("Сохранить",(dialog,which)->{
                boolean[] selected=new boolean[checked.length];
                for(int i=0;i<checked.length;i++)
                    selected[i]=checked[i].isChecked();
                String categories=BannerSettings.serialize(selected);
                String topic=BannerSettings.normalizeQuery(
                    query.getText().toString());
                prefs.edit().putString(
                    "banner_categories_"+bannerPreferenceSuffix(),categories)
                    .putString("banner_query_"+bannerPreferenceSuffix(),topic)
                    .apply();
                refreshBannerFromPreferences();
            }).show();
    }
    private void refreshBannerFromPreferences(){
        if(!isHomepage())return;
        featuredItems.clear();
        featuredIndex=0;
        featuredLoading=true;
        renderCarouselSlide();
        fetchFeaturedFilms(serial);
    }
    private void fetchFeaturedFilms(int requestId){
        if(!isHomepage())return;
        final List<Integer> categories=BannerSettings.parse(bannerCats());
        final String topic=BannerSettings.normalizeQuery(bannerTopic());
        final boolean includeCollections=categories.contains(6050) ||
            categories.contains(6060)||categories.contains(6070);
        worker.execute(()->{
            List<Item> picked=new ArrayList<>();
            try{
                List<List<Item>> groups=new ArrayList<>();
                for(int cat:categories){
                    SitePages.Cursor cursor=SitePages.start(topic,cat);
                    List<Item> entries=new ArrayList<>();
                    for(int page=0;page<2&&!cursor.finished;page++){
                        SitePages.Page response=SitePages.next(cursor,cat);
                        entries.addAll(response.items);
                        if(selectFeaturedEntries(entries,5,includeCollections)
                            .size()>=5)break;
                    }
                    groups.add(entries);
                }
                // Round robin mixes simultaneously selected categories. Each
                // individual site's original page order is still newest first.
                List<Item> combined=new ArrayList<>();
                int max=0;
                for(List<Item> group:groups)max=Math.max(max,group.size());
                for(int pos=0;pos<max;pos++)
                    for(List<Item> group:groups)
                        if(group.size()>pos)combined.add(group.get(pos));
                picked.addAll(selectFeaturedEntries(combined,5,
                    includeCollections));
            }catch(Exception ignored){
                // Main catalog must remain usable if the banner source fails.
            }
            main.post(()->{
                if(isFinishing() || requestId!=serial || !isHomepage())return;
                featuredItems.clear();
                featuredItems.addAll(picked);
                featuredIndex=0;
                featuredLoading=false;
                renderCarouselSlide();
            });
        });
    }
    private static List<Item> selectFeaturedEntries(
            List<Item> entries,int limit,boolean includeCollections){
        List<FeaturedSelector.Candidate> scored=new ArrayList<>();
        for(Item item:entries)scored.add(new FeaturedSelector.Candidate(
            item.title,item.poster,item.details,item.seeders));
        List<Item> result=new ArrayList<>();
        for(int index:FeaturedSelector.selectNewest(scored,limit,
            includeCollections))result.add(entries.get(index));
        return result;
    }

    private TextView eyebrow(String value){
        TextView t=label(value,11,MediaDesign.ACCENT_LIGHT,true);
        t.setLetterSpacing(0.14f);
        return t;
    }
    private Button accentButton(String name){
        Button b=button(name);
        b.setBackground(MediaDesign.gradient(this,
            new int[]{0xffb65cff,0xff7529dc},14));
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setOnFocusChangeListener((v,focused)->{
            b.setBackground(focused?
                MediaDesign.outlined(this,0xffaa65f0,Color.WHITE,14,2):
                MediaDesign.gradient(this,new int[]{0xffb65cff,0xff7529dc},14));
            MediaDesign.animateFocus(b,focused);
        });
        return b;
    }
    private LinearLayout.LayoutParams spaced(int w,int h,int right,int bottom){
        LinearLayout.LayoutParams p=lp(w,h);
        p.rightMargin=dp(right);p.bottomMargin=dp(bottom);
        return p;
    }

    private void ui() {
        int screenWidthDp=getResources().getConfiguration().screenWidthDp;
        int mode=getResources().getConfiguration().uiMode &
            android.content.res.Configuration.UI_MODE_TYPE_MASK;
        int screenHeightDp=getResources().getConfiguration().screenHeightDp;
        hardwareTv=mode==android.content.res.Configuration.UI_MODE_TYPE_TELEVISION;
        tvPreviewMode=!hardwareTv && prefs.getBoolean("tv_preview",false);
        compactTvPreview=tvPreviewMode &&
            (screenWidthDp<960 || screenHeightDp<650);
        tvLayout=hardwareTv || tvPreviewMode ||
            (screenWidthDp>=840 && screenHeightDp>=550);
        sidebarCollapsed=tvLayout && prefs.getBoolean("sidebar_hidden",true);
        sidebarSectionsExpanded=prefs.getBoolean("sections_expanded",currentCategory>0);
        sidebarWidthDp=compactTvPreview?
            Math.max(120,Math.min(146,screenWidthDp/5)):176;
        sidebarPanel=null;
        sidebarToggleButton=null;
        filterButtons.clear();filterCodes.clear();
        getWindow().setStatusBarColor(MediaDesign.BG);
        getWindow().setNavigationBarColor(MediaDesign.BG);
        if(hardwareTv || tvPreviewMode)
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        LinearLayout outer=line();
        outer.setGravity(Gravity.TOP);
        outer.setBackgroundColor(MediaDesign.BG);
        outer.setFocusableInTouchMode(true);
        catalogPage=outer;
        detailsPage=null;
        fullPhotoPage=null;
        setContentView(outer);
        outer.requestFocus();
        if(tvLayout){
            LinearLayout sidebar=column();
            sidebarPanel=sidebar;
            sidebarNavigation=column();
            outer.setClipChildren(false);
            sidebar.setClipChildren(false);
            sidebar.setClipToPadding(false);
            sidebarNavigation.setClipChildren(false);
            sidebarNavigation.setClipToPadding(false);
            sidebar.setBackground(MediaDesign.horizontal(this,
                new int[]{0xff111628,0xff0c1424},0));
            ScrollView navScroll=new ScrollView(this);
            navScroll.setClipChildren(false);
            navScroll.setClipToPadding(false);
            navScroll.setVerticalScrollBarEnabled(false);
            navScroll.setFillViewport(false);
            navScroll.addView(sidebarNavigation);
            sidebar.addView(navScroll,new LinearLayout.LayoutParams(-1,0,1));
            outer.addView(sidebar,lp(sidebarCollapsed?
                (compactTvPreview?46:54):sidebarWidthDp,-1));
            renderSidebarNavigation();
        }
        LinearLayout app=column();
        app.setPadding(dp(compactTvPreview?9:tvLayout?18:16),
            dp(compactTvPreview?7:tvLayout?11:14),
            dp(compactTvPreview?9:tvLayout?15:16),0);
        outer.addView(app,new LinearLayout.LayoutParams(0,-1,1));

        // Touch phones retain the original search field and a compact header.
        // Television uses the permanent navigation rail instead.
        if(!tvLayout){
            LinearLayout header=line();
            header.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout heading=column();
            heading.addView(label("MediaShelf",22,MediaDesign.TEXT,true));
            heading.addView(eyebrow("КАТАЛОГ  /  18+"));
            header.addView(mediaShelfLogo(),spaced(54,54,0,0));
            header.addView(heading,new LinearLayout.LayoutParams(0,dp(60),1));
            sourceSelector=button("XXXClub ▾");
            sourceSelector.setTextSize(12);
            sourceSelector.setOnClickListener(v->chooseSource());
            header.addView(sourceSelector,spaced(111,43,6,0));
            Button options=button("⋮");
            options.setOnClickListener(v->moreOptions());
            header.addView(options,lp(43,43));
            app.addView(header,spaced(-1,65,0,7));
        }else{
            currentSectionLabel=label("",compactTvPreview?12:14,
                MediaDesign.MUTED,false);
            currentSectionLabel.setGravity(Gravity.CENTER_VERTICAL);
            currentSectionLabel.setSingleLine(true);
            currentSectionLabel.setEllipsize(TextUtils.TruncateAt.END);
            currentSectionLabel.setPadding(dp(5),0,0,0);
            app.addView(currentSectionLabel,spaced(-1,
                compactTvPreview?27:35,0,2));
            updateCurrentSection();
        }

        status=label("Выбери категорию или найди раздачу",13,MediaDesign.MUTED,false);
        status.setSingleLine(true);status.setEllipsize(TextUtils.TruncateAt.END);
        if(!tvLayout)app.addView(status,spaced(-1,24,0,8));

        // The full-width search remains on the touch interface only.
        // On television the top-bar magnifier opens a compact search dialog.
        LinearLayout searchRow=line();
        queryInput=new EditText(this);
        queryInput.setTextColor(MediaDesign.TEXT);
        queryInput.setHintTextColor(0xff8d9dbb);
        queryInput.setSingleLine(true);
        queryInput.setHint("Название фильма, студии или темы");
        queryInput.setTextSize(15);
        queryInput.setBackground(MediaDesign.outlined(this,
            0xff1a263b,0xff324463,11,1));
        queryInput.setPadding(dp(14),0,dp(12),0);
        queryInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        queryInput.setOnEditorActionListener((v,action,event)->{
            if(action==EditorInfo.IME_ACTION_SEARCH){
                currentCategory=0;currentCategoryLabel="";
                load(queryInput.getText().toString().trim(),"search");
                return true;
            }
            return false;
        });
        searchRow.addView(queryInput,new LinearLayout.LayoutParams(0,dp(47),1));
        Button go=accentButton("⌕  Найти");
        LinearLayout.LayoutParams gp=lp(88,47);
        gp.leftMargin=dp(7);
        searchRow.addView(go,gp);
        go.setOnClickListener(v->{
            currentCategory=0;currentCategoryLabel="";
            load(queryInput.getText().toString().trim(),"search");
        });
        if(!tvLayout)app.addView(searchRow,spaced(-1,50,0,6));

        LinearLayout tabs=line();
        String[] titles={"Новинки","Популярное","Все разделы",
            "Фильмы","720p","1080p","4K","Сборники","Фото","VR"};
        int[] codes={-1,-2,-3,6090,10001,10002,6045,6050,6060,6070};
        for(int i=0;i<titles.length;i++){
            final int code=codes[i];
            final String name=titles[i];
            Button chip=button(name);
            chip.setTextSize(compactTvPreview?11:tvLayout?13:12);
            chip.setMinHeight(0);chip.setMinimumHeight(0);
            chip.setId(View.generateViewId());
            chip.setOnKeyListener((v,key,event)->{
                if(event.getAction()!=android.view.KeyEvent.ACTION_DOWN)
                    return false;
                if(key==android.view.KeyEvent.KEYCODE_DPAD_UP){
                    focusTopSearch();return true;
                }
                if(key==android.view.KeyEvent.KEYCODE_DPAD_DOWN){
                    if(featuredHost!=null && featuredHost.getParent()!=null)
                        featuredHost.requestFocus();
                    else if(firstCatalogTile!=null)
                        firstCatalogTile.requestFocus();
                    return true;
                }
                return false;
            });
            chip.setPadding(dp(compactTvPreview?10:15),0,
                dp(compactTvPreview?10:15),0);
            chip.setOnClickListener(v->{
                if(code==-3){categories();return;}
                if(code==-1 || code==-2){
                    currentCategory=0;currentCategoryLabel="";
                    queryInput.setText("");
                    load("",code==-1?"latest":"popular");
                }else{
                    currentCategory=code;currentCategoryLabel=name;
                    queryInput.setText("");
                    load("","latest");
                }
            });
            tabs.addView(chip,spaced(-2,
                compactTvPreview?35:tvLayout?42:39,compactTvPreview?5:8,0));
            filterButtons.add(chip);filterCodes.add(code);
        }
        android.widget.HorizontalScrollView strip=new android.widget.HorizontalScrollView(this);
        strip.setHorizontalScrollBarEnabled(false);
        strip.setFillViewport(false);
        strip.addView(tabs);
        LinearLayout.LayoutParams strips=lp(-1,
            compactTvPreview?44:tvLayout?69:60);
        strips.topMargin=dp(6);
        if(!tvLayout)app.addView(strip,strips);

        ScrollView results=new ScrollView(this);
        catalogScroll=results;
        results.setFillViewport(true);
        results.setVerticalScrollBarEnabled(false);
        results.setDescendantFocusability(android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS);
        cards=column();
        cards.setPadding(0,dp(5),0,dp(35));
        results.addView(cards);
        app.addView(results,new LinearLayout.LayoutParams(-1,0,1));
        refreshSourceMenu();
        updateFilters();
    }

    @Override public boolean onKeyDown(int keyCode,android.view.KeyEvent event){
        if(tvLayout && keyCode==android.view.KeyEvent.KEYCODE_MENU){
            setSidebarCollapsed(!sidebarCollapsed,true);
            return true;
        }
        return super.onKeyDown(keyCode,event);
    }
    private void updateCurrentSection(){
        if(currentSectionLabel==null)return;
        SiteCatalog.Site site=SiteCatalog.byId(activeSource);
        String src=activeSource.equals("all")?"Все сайты":
            site!=null?site.title:activeSource;
        String section=favoritesMode?"Избранное":!lastQuery.isEmpty()?"Поиск: "+lastQuery:
            currentCategory>0?currentCategoryLabel:
            lastSort.equals("popular")?"Популярное":"Новинки";
        currentSectionLabel.setText(src+"   /   "+section);
    }

    /**
     * Inline icon occupies one text character. This lets the Button's normal
     * CENTER gravity position the flame on precisely the same horizontal axis
     * as the home and favorite symbols. A compound left drawable cannot do so.
     * Draw at the vertical centre of the text line at every screen density.
     */
    private static final class CenteredDrawableSpan extends ReplacementSpan {
        private final Drawable icon;
        CenteredDrawableSpan(Drawable drawable){icon=drawable;}
        @Override public int getSize(Paint paint,CharSequence text,int start,
                int end,Paint.FontMetricsInt metrics){
            return icon.getBounds().width();
        }
        @Override public void draw(Canvas canvas,CharSequence text,int start,int end,
                float x,int top,int y,int bottom,Paint paint){
            int height=icon.getBounds().height();
            int centre=(top+bottom)/2;
            canvas.save();
            canvas.translate(x,centre-height/2f);
            icon.draw(canvas);
            canvas.restore();
        }
    }
    /** A vector is drawn at the precise button centre, independently of glyph metrics. */
    private static class CenterIconButton extends Button {
        private Drawable symbol;
        private final int iconDp;
        CenterIconButton(android.content.Context context,Drawable image,int sizeDp){
            super(context);
            symbol=image;
            iconDp=sizeDp;
            setAllCaps(false);
            setText("");
            setMinHeight(0);setMinimumHeight(0);
        }
        void replaceIcon(Drawable image){symbol=image;invalidate();}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            if(symbol==null)return;
            int size=(int)(getResources().getDisplayMetrics().density*iconDp+0.5f);
            int left=(getWidth()-size)/2;
            int top=(getHeight()-size)/2;
            symbol.setBounds(left,top,left+size,top+size);
            symbol.draw(canvas);
        }
    }
    private CenterIconButton actionSymbol(int resId,boolean primary){
        CenterIconButton b=new CenterIconButton(this,getDrawable(resId),primary?26:27);
        b.setFocusable(true);
        b.setBackground(primary?
            MediaDesign.gradient(this,new int[]{0xffb65cff,0xff7529dc},12):
            MediaDesign.round(this,0xff28334e,12));
        b.setOnFocusChangeListener((v,focused)->{
            b.setBackground(focused?
                MediaDesign.outlined(this,primary?0xff8d42dc:0xff38255a,
                    MediaDesign.ACCENT_LIGHT,12,2):
                primary?MediaDesign.gradient(this,
                    new int[]{0xffb65cff,0xff7529dc},12):
                    MediaDesign.round(this,0xff28334e,12));
        });
        return b;
    }
    private final class FlameNavigationButton extends Button {
        private final Drawable icon;
        FlameNavigationButton(){
            super(MainActivity.this);
            icon=getDrawable(R.drawable.ic_flame).mutate();
            popularFlameIcon=icon;
            setText(sidebarCollapsed?"":"     Популярное");
            setAllCaps(false);
            setMinHeight(0);setMinimumHeight(0);
            setFocusable(true);
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            int size=dp(17);
            int left=sidebarCollapsed?(getWidth()-size)/2:getPaddingLeft()+dp(1);
            int top=(getHeight()-size)/2;
            icon.setBounds(left,top,left+size,top+size);
            icon.draw(canvas);
        }
    }
    private Button addSidebarEntry(String symbol,String label,
        Runnable action,boolean active){
        Button b=label.equals("Популярное")?
            new FlameNavigationButton():
            button(sidebarCollapsed?symbol:symbol+"   "+label);
        b.setTextSize(sidebarCollapsed?
            (compactTvPreview?17:20):(compactTvPreview?11:14));
        b.setGravity(sidebarCollapsed?Gravity.CENTER:
            Gravity.CENTER_VERTICAL|Gravity.LEFT);
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setTextColor(active?MediaDesign.ACCENT_LIGHT:MediaDesign.TEXT);
        b.setContentDescription(label);
        b.setSelected(active);
        b.setPadding(dp(sidebarCollapsed?0:compactTvPreview?6:10),0,
            dp(sidebarCollapsed?0:5),0);
        b.setBackground(active?
            MediaDesign.outlined(this,0xff35205f,MediaDesign.ACCENT,11,1):
            MediaDesign.round(this,0xff121a2c,11));
        b.setOnFocusChangeListener((v,f)->{
            b.setBackground(f?
                MediaDesign.outlined(this,0xff402568,
                    MediaDesign.ACCENT_LIGHT,11,2):
                b.isSelected()?
                    MediaDesign.outlined(this,0xff35205f,
                        MediaDesign.ACCENT,11,1):
                    MediaDesign.round(this,0xff121a2c,11));
            // Keep TV rail buttons at scale 1.0: 1.035 scaling cut focused
            // corners and strokes against the left edge of the sidebar.
            b.animate().cancel();
            b.setScaleX(1f);
            b.setScaleY(1f);
            b.setElevation(f?dp(4):0);
        });
        b.setOnKeyListener((v,key,event)->{
            if(event.getAction()!=android.view.KeyEvent.ACTION_DOWN)
                return false;
            if(key==android.view.KeyEvent.KEYCODE_DPAD_RIGHT){
                View target=featuredHost!=null && featuredHost.getParent()!=null?
                    featuredHost:firstCatalogTile;
                if(target!=null){target.requestFocus();return true;}
            }
            if(key==android.view.KeyEvent.KEYCODE_DPAD_LEFT &&
                !sidebarCollapsed){
                setSidebarCollapsed(true,true);
                return true;
            }
            return false;
        });
        b.setOnClickListener(v->action.run());
        sidebarNavigation.addView(b,spaced(-1,
            compactTvPreview?34:42,0,compactTvPreview?3:5));
        return b;
    }

    private void renderSidebarNavigation(){
        if(!tvLayout||sidebarNavigation==null)return;
        sidebarNavigation.removeAllViews();
        sidebarPanel.setPadding(dp(sidebarCollapsed?5:compactTvPreview?8:12),
            dp(compactTvPreview?5:11),
            dp(sidebarCollapsed?5:compactTvPreview?8:12),
            dp(compactTvPreview?4:9));
        sidebarToggleButton=addSidebarEntry("☰","MediaShelf",
            ()->setSidebarCollapsed(!sidebarCollapsed,true),false);
        sidebarToggleButton.setContentDescription(sidebarCollapsed?
            "Развернуть меню MediaShelf":"Свернуть меню MediaShelf");
        View divider=new View(this);
        divider.setBackgroundColor(0xff30284a);
        sidebarNavigation.addView(divider,spaced(-1,1,0,
            compactTvPreview?5:11));
        // Brand mark in the expanded TV rail; omit in compact preview to keep navigation visible.
        if(!sidebarCollapsed && !compactTvPreview)
            sidebarNavigation.addView(mediaShelfLogo(),spaced(-1,102,0,8));
        topSearchButton=addSidebarEntry("⌕","Поиск",
            ()->openSearchDialog(),false);
        navHomeButton=addSidebarEntry("⌂","Новинки",()->{
            currentCategory=0;currentCategoryLabel="";
            if(queryInput!=null)queryInput.setText("");
            load("","latest");
        },!favoritesMode&&lastQuery.isEmpty()&&currentCategory==0&&
            !lastSort.equals("popular"));
        navPopularButton=addSidebarEntry("","Популярное",()->{
            currentCategory=0;currentCategoryLabel="";
            if(queryInput!=null)queryInput.setText("");
            load("","popular");
        },!favoritesMode&&lastQuery.isEmpty()&&currentCategory==0&&
            lastSort.equals("popular"));
        if(popularFlameIcon!=null)popularFlameIcon.setTint(
            lastQuery.isEmpty()&&currentCategory==0&&
            lastSort.equals("popular")&&!favoritesMode?
                MediaDesign.ACCENT_LIGHT:MediaDesign.TEXT);
        navFavoritesButton=addSidebarEntry("★","Избранное",
            ()->openFavorites(),favoritesMode);
        navSectionsButton=addSidebarEntry(
            sidebarSectionsExpanded && !sidebarCollapsed?"▾":"▦",
            "Разделы",()->toggleSidebarSections(),currentCategory>0);
        if(sidebarSectionsExpanded && !sidebarCollapsed)
            renderInlineSidebarSections();
        navSourceButton=addSidebarEntry("◈","Источники",
            ()->chooseSource(),false);
        bannerSettingsButton=addSidebarEntry("✦","Баннер",
            ()->configureBanner(),false);
        addSidebarEntry("⚙","Настройки",()->settings(false),false);
        addSidebarEntry("◇","Проверка",()->diagnostics(),false);
        if(tvPreviewMode)addSidebarEntry("⇄","Режим телефона",
            ()->toggleTvPreview(),false);
    }

    /**
     * TV categories are children of the sidebar entry, not a blocking dialog.
     * On a phone the existing category selection screen remains unchanged.
     */
    private void toggleSidebarSections(){
        if(!tvLayout){categories();return;}
        if(sidebarCollapsed){
            sidebarSectionsExpanded=true;
            prefs.edit().putBoolean("sections_expanded",true).apply();
            setSidebarCollapsed(false,true);
            return;
        }
        sidebarSectionsExpanded=!sidebarSectionsExpanded;
        prefs.edit().putBoolean("sections_expanded",sidebarSectionsExpanded).apply();
        renderSidebarNavigation();
        if(navSectionsButton!=null)navSectionsButton.requestFocus();
    }
    private void inlineSidebarNotice(String text){
        TextView notice=label(text,compactTvPreview?10:11,MediaDesign.MUTED,false);
        notice.setPadding(dp(compactTvPreview?13:19),dp(5),dp(5),dp(9));
        sidebarNavigation.addView(notice,lp(-1,-2));
    }
    private String inlineCategoryLabel(SourceRegistry.Category category){
        if(activeSource.equals("xxxclub")){
            switch(category.id){
                case 6090:return "Фильмы";
                case 10001:return "720p";
                case 10002:return "1080p";
                case 6045:return "4K";
                case 6080:return "480p";
                case 6050:return "Сборники";
                case 6060:return "Фото";
                case 6070:return "VR";
            }
        }
        return category.name;
    }
    private void inlineCategoryEntry(String text,int code){
        boolean active=lastQuery.isEmpty() && currentCategory==code;
        Button entry=addSidebarEntry("›",text,()->{
            currentCategory=code;
            currentCategoryLabel=code==0?"":text;
            if(queryInput!=null)queryInput.setText("");
            load("","latest");
            renderSidebarNavigation();
            if(navActiveCategoryButton!=null)navActiveCategoryButton.requestFocus();
        },active);
        entry.setPadding(dp(compactTvPreview?13:22),0,dp(4),0);
        entry.setTextSize(compactTvPreview?10:12);
        if(active)navActiveCategoryButton=entry;
    }
    private void renderInlineSidebarSections(){
        navActiveCategoryButton=null;
        if(activeSource.equals("all")){
            inlineSidebarNotice("Выбери конкретный сайт в «Источники»: категории у сайтов разные.");
            return;
        }
        SiteCatalog.Site site=SiteCatalog.byId(activeSource);
        if(site!=null && !site.direct &&
            prefs.getString("api_key","").isEmpty()){
            inlineSidebarNotice("Для этого сайта требуется настройка Jackett или прямой модуль.");
            return;
        }
        List<SourceRegistry.Category> list=IndexerCapabilities.known(activeSource);
        if(list.isEmpty()){
            SourceRegistry.Source source=SourceRegistry.byId(sources,activeSource);
            if(source!=null && !source.categories.isEmpty())
                list=new ArrayList<>(source.categories);
        }
        if(list.isEmpty() && activeSource.equals(sidebarFetchedSource))
            list=new ArrayList<>(sidebarFetchedCategories);
        if(list.isEmpty()){
            inlineSidebarNotice(sidebarFetchingCategories &&
                activeSource.equals(sidebarFetchedSource)?
                "Загружаем категории…":
                !sidebarCategoriesError.isEmpty() &&
                activeSource.equals(sidebarFetchedSource)?
                sidebarCategoriesError:"Получаем категории сайта…");
            fetchInlineSidebarSections(activeSource);
            return;
        }
        inlineCategoryEntry("Все категории",0);
        for(SourceRegistry.Category category:list)
            inlineCategoryEntry(inlineCategoryLabel(category),category.id);
    }
    private void fetchInlineSidebarSections(String id){
        if(id.equals("all") ||
            (sidebarFetchingCategories && id.equals(sidebarFetchedSource)) ||
            (!sidebarCategoriesError.isEmpty() && id.equals(sidebarFetchedSource)) ||
            (id.equals(sidebarFetchedSource) && !sidebarFetchedCategories.isEmpty()))
            return;
        if(prefs.getString("api_key","").isEmpty()){
            sidebarFetchedSource=id;
            sidebarCategoriesError="Для категорий нужен Jackett.";
            return;
        }
        sidebarFetchedSource=id;
        sidebarFetchingCategories=true;
        sidebarCategoriesError="";
        sidebarFetchedCategories.clear();
        String base=prefs.getString("jackett_url","http://192.168.50.2:9117");
        String key=prefs.getString("api_key","");
        worker.execute(()->{
            List<SourceRegistry.Category> fetched=new ArrayList<>();
            String error="";
            try{
                fetched=IndexerCapabilities.fetch(base,key,id);
                if(fetched.isEmpty())error="Сайт не объявляет категории через Jackett.";
            }catch(Exception exception){
                error="Категории недоступны. Поиск работает отдельно.";
            }
            final List<SourceRegistry.Category> result=fetched;
            final String message=error;
            main.post(()->{
                if(isFinishing()||!sidebarFetchedSource.equals(id))return;
                sidebarFetchingCategories=false;
                sidebarFetchedCategories.clear();
                sidebarFetchedCategories.addAll(result);
                sidebarCategoriesError=message;
                if(tvLayout && !sidebarCollapsed && sidebarSectionsExpanded &&
                    activeSource.equals(id)){
                    renderSidebarNavigation();
                    if(navSectionsButton!=null)navSectionsButton.requestFocus();
                }
            });
        });
    }

    @Override public void onBackPressed(){
        if(fullPhotoPage!=null){
            fullPhotoPage=null;
            if(detailsPage!=null){
                setContentView(detailsPage);
                if(galleryReturnFocus!=null){
                    View previous=galleryReturnFocus;
                    previous.post(()->{
                        if(detailsPage!=null && previous.isAttachedToWindow())
                            previous.requestFocus();
                    });
                }
            }
            return;
        }
        if(detailsPage!=null){
            detailsPage=null;
            if(catalogPage!=null)setContentView(catalogPage);
            if(favoritesMode){
                openFavorites();
            }else if(catalogScroll!=null){
                catalogScroll.post(()->catalogScroll.scrollTo(0,cardReturnScroll));
                if(cardReturnFocus!=null)
                    cardReturnFocus.post(cardReturnFocus::requestFocus);
            }
            return;
        }
        if(tvLayout && !sidebarCollapsed){
            setSidebarCollapsed(true,true);
            return;
        }
        super.onBackPressed();
    }
    private void setSidebarCollapsed(boolean hide,boolean persist){
        if(!tvLayout)return;
        if(persist)prefs.edit().putBoolean("sidebar_hidden",hide).apply();
        sidebarCollapsed=hide;
        if(sidebarPanel!=null){
            android.view.ViewGroup.LayoutParams params=sidebarPanel.getLayoutParams();
            params.width=dp(hide?(compactTvPreview?46:54):sidebarWidthDp);
            sidebarPanel.setLayoutParams(params);
        }
        renderSidebarNavigation();
        final int previousScroll=catalogScroll==null?0:catalogScroll.getScrollY();
        if(catalogScroll!=null&&!catalogItems.isEmpty()){
            catalogScroll.post(()->{
                if(isFinishing())return;
                show(catalogItems);
                catalogScroll.post(()->catalogScroll.scrollTo(0,previousScroll));
                Button target=(!hide && sidebarSectionsExpanded && navSectionsButton!=null)?
                    navSectionsButton:sidebarToggleButton;
                if(target!=null)target.requestFocus();
            });
        }else{
            Button target=(!hide && sidebarSectionsExpanded && navSectionsButton!=null)?
                navSectionsButton:sidebarToggleButton;
            if(target!=null)target.requestFocus();
        }
    }
    private void updateFilters(){
        boolean nativeSource=activeSource.equals("xxxclub")||activeSource.equals("all");
        for(int i=0;i<filterButtons.size();i++){
            Button chip=filterButtons.get(i);
            int code=filterCodes.get(i);
            chip.setVisibility(code>0 && !nativeSource?View.GONE:View.VISIBLE);
            boolean active=code==-1 && currentCategory==0 && lastSort.equals("latest")&&
                    lastQuery.isEmpty() ||
                code==-2 && currentCategory==0 && lastSort.equals("popular")&&
                    lastQuery.isEmpty() ||
                code>0 && code==currentCategory && lastQuery.isEmpty();
            chip.setSelected(active);
            chip.setTextColor(active?MediaDesign.TEXT:MediaDesign.MUTED);
            chip.setBackground(active?
                MediaDesign.outlined(this,0xff39235e,0xffad66f4,12,1):
                MediaDesign.round(this,0xff1a2436,12));
            chip.setOnFocusChangeListener((v,f)->{
                chip.setBackground(f?MediaDesign.outlined(this,0xff523579,
                    MediaDesign.ACCENT,12,2):
                    chip.isSelected()?MediaDesign.outlined(this,0xff39235e,
                        0xffad66f4,12,1):
                        MediaDesign.round(this,0xff1a2436,12));
                MediaDesign.animateFocus(chip,f);
            });
        }
    }
    private void openSearchDialog(){
        EditText search=new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(MediaDesign.TEXT);
        search.setHintTextColor(MediaDesign.MUTED);
        search.setTextSize(tvLayout?18:16);
        search.setText(lastQuery);
        search.setSelectAllOnFocus(true);
        search.setHint("Название, тема, студия");
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setPadding(dp(18),dp(12),dp(18),dp(12));
        search.setBackground(MediaDesign.outlined(this,0xff172239,
            MediaDesign.ACCENT,12,1));
        LinearLayout wrap=column();
        wrap.setPadding(dp(19),dp(8),dp(19),dp(4));
        wrap.addView(search,lp(-1,53));
        Button saveQuery=button("☆  Сохранить поиск");
        saveQuery.setTextSize(14);
        saveQuery.setTextColor(MediaDesign.ACCENT_LIGHT);
        wrap.addView(saveQuery,spaced(-1,45,0,6));
        saveQuery.setOnClickListener(v->{
            String query=search.getText().toString().trim();
            if(query.isEmpty()){
                Toast.makeText(this,"Сначала введи слова поиска",Toast.LENGTH_SHORT).show();
                return;
            }
            boolean added=savedSearches.add(query,activeSource,currentCategory,currentCategoryLabel);
            Toast.makeText(this,added?"Сохранено в «Моих поисках»":
                "Такой поиск уже сохранён",Toast.LENGTH_SHORT).show();
        });
        AlertDialog dialog=themedDialog()
            .setTitle("Поиск · "+activeSource)
            .setView(wrap)
            .setNegativeButton("Отмена",null)
            .setNeutralButton("Новинки",(d,w)->{
                currentCategory=0;currentCategoryLabel="";
                queryInput.setText("");
                load("","latest");
            })
            .setPositiveButton("Найти",(d,w)->{
                String q=search.getText().toString().trim();
                currentCategory=0;currentCategoryLabel="";
                queryInput.setText(q);
                load(q,q.isEmpty()?"latest":"search");
            }).create();
        search.setOnEditorActionListener((v,action,event)->{
            if(action==EditorInfo.IME_ACTION_SEARCH){
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                return true;
            }
            return false;
        });
        dialog.show();
        search.requestFocus();
    }
    private void focusTopSearch(){
        if(topSearchButton!=null)topSearchButton.requestFocus();
        else if(queryInput!=null)queryInput.requestFocus();
    }
    private void focusCurrentCategory(){
        if(tvLayout){focusTopSearch();return;}
        for(int i=0;i<filterCodes.size();i++){
            int code=filterCodes.get(i);
            if(code==currentCategory && code>0 &&
                filterButtons.get(i).getVisibility()==View.VISIBLE){
                filterButtons.get(i).requestFocus();return;
            }
            if(currentCategory==0 &&
                ((code==-1&&!lastSort.equals("popular"))||
                 (code==-2&&lastSort.equals("popular")))){
                filterButtons.get(i).requestFocus();return;
            }
        }
        focusTopSearch();
    }

    private AlertDialog.Builder themedDialog(){
        return new AlertDialog.Builder(this,R.style.MediaShelfDialogTheme);
    }
    private TextView selectionRow(String text){
        TextView option=label(text,tvLayout?16:15,MediaDesign.TEXT,false);
        option.setFocusable(true);
        option.setClickable(true);
        option.setSingleLine(false);
        option.setMaxLines(2);
        option.setPadding(dp(17),dp(10),dp(12),dp(10));
        option.setMinHeight(dp(tvLayout?51:49));
        option.setBackground(MediaDesign.round(this,0xff192238,12));
        option.setOnFocusChangeListener((v,focused)->{
            option.setBackground(focused?
                MediaDesign.outlined(this,0xff462569,MediaDesign.ACCENT,12,2):
                MediaDesign.round(this,0xff192238,12));
            option.setTextColor(focused?MediaDesign.ACCENT_LIGHT:MediaDesign.TEXT);
        });
        return option;
    }
    private void showSelectionDialog(String title,String[] options,
            DialogInterface.OnClickListener selected,String cancelText){
        LinearLayout body=column();
        body.setPadding(dp(14),dp(6),dp(14),dp(8));
        ScrollView scrolling=new ScrollView(this);
        scrolling.setVerticalScrollBarEnabled(false);
        scrolling.setFillViewport(false);
        scrolling.addView(body);
        AlertDialog dialog=themedDialog().setTitle(title).setView(scrolling)
            .setNegativeButton(cancelText==null?"Закрыть":cancelText,null).create();
        for(int i=0;i<options.length;i++){
            final int index=i;
            TextView row=selectionRow(options[i]);
            body.addView(row,spaced(-1,-2,0,2));
            row.setOnClickListener(v->{
                dialog.dismiss();
                selected.onClick(dialog,index);
            });
        }
        dialog.show();
        if(tvLayout && body.getChildCount()>0)
            body.getChildAt(0).post(body.getChildAt(0)::requestFocus);
    }
    private void dismissCardActionPopup(){
        if(cardActionPopup!=null){
            cardActionPopup.dismiss();
            cardActionPopup=null;
        }
    }
    private TextView contextAction(String label,boolean primary){
        TextView action=selectionRow(label);
        action.setTextSize(15);
        action.setMinHeight(dp(47));
        action.setPadding(dp(14),dp(7),dp(10),dp(7));
        action.setTextColor(primary?MediaDesign.ACCENT_LIGHT:MediaDesign.TEXT);
        action.setBackground(primary?
            MediaDesign.round(this,0xff342051,11):
            MediaDesign.round(this,0xff171e32,11));
        action.setOnFocusChangeListener((v,focused)->{
            action.setBackground(focused?
                MediaDesign.outlined(this,0xff422262,MediaDesign.ACCENT,11,2):
                MediaDesign.round(this,primary?0xff342051:0xff171e32,11));
            action.setTextColor(focused?MediaDesign.TEXT:
                primary?MediaDesign.ACCENT_LIGHT:MediaDesign.TEXT);
        });
        return action;
    }

    private void moreOptions() {
        String[] choices={"Выбор сайта","Категории сайта",
            "Настроить баннер","Диагностика источников","Настройки",
            prefs.getBoolean("tv_preview",false)?
                "Выключить ТВ-режим":"Включить ТВ-режим (горизонтально)"};
        showSelectionDialog("MediaShelf",choices,(d,i)->{
                if(i==0)chooseSource();
                else if(i==1)categories();
                else if(i==2)configureBanner();
                else if(i==3)diagnostics();
                else if(i==4)settings(false);
                else toggleTvPreview();
            },"Закрыть");
    }
    private void refreshSourceMenu() {
        SiteCatalog.Site current=SiteCatalog.byId(activeSource);
        String title=activeSource.equals("all")?"Все источники":
            current!=null?current.title:activeSource;
        if(sourceSelector!=null)sourceSelector.setText(title+"  ▾");
        updateCurrentSection();
        if(sourceButtons==null)return;
        sourceButtons.removeAllViews();
        if(sourceSummary!=null)sourceSummary.setText("ИСТОЧНИКИ");
        for(SiteCatalog.Site site:SiteCatalog.all()){
            boolean selected=activeSource.equals(site.id);
            Button b=button((site.direct?"●  ":"○  ")+site.title);
            b.setTextSize(compactTvPreview?11:12);
            b.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
            b.setPadding(dp(compactTvPreview?7:15),0,0,0);
            b.setTextColor(selected?MediaDesign.TEXT:
                site.direct?0xffd6e0f2:0xff8292ae);
            b.setBackground(selected?
                MediaDesign.outlined(this,0xff35205f,0xff9c4af2,13,1):
                MediaDesign.round(this,MediaDesign.SIDEBAR,13));
            b.setOnFocusChangeListener((v,f)->{
                b.setBackground(f?
                    MediaDesign.outlined(this,0xff483376,MediaDesign.ACCENT,13,2):
                    selected?
                        MediaDesign.outlined(this,0xff35205f,0xff9c4af2,13,1):
                        MediaDesign.round(this,MediaDesign.SIDEBAR,13));
                MediaDesign.animateFocus(b,f);
            });
            b.setContentDescription(site.title+", "+site.mode());
            b.setOnClickListener(v->selectSource(site.id));
            sourceButtons.addView(b,spaced(-1,
                compactTvPreview?34:39,0,compactTvPreview?2:4));
        }
        Button all=button("▦  Все подключённые");
        all.setTextSize(13);
        all.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
        all.setPadding(dp(15),0,0,0);
        all.setOnClickListener(v->selectSource("all"));
        all.setTextSize(compactTvPreview?11:12);
        sourceButtons.addView(all,spaced(-1,
            compactTvPreview?34:39,0,compactTvPreview?2:4));
        Button add=button("+  Управление сайтами");
        add.setTextSize(13);
        add.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
        add.setPadding(dp(15),0,0,0);
        add.setOnClickListener(v->chooseSource());
        add.setTextSize(compactTvPreview?11:12);
        sourceButtons.addView(add,lp(-1,compactTvPreview?34:39));
        updateFilters();
    }
    private void loadSources() {
        if(loadingSources)return;
        String key=prefs.getString("api_key","");
        if(key.isEmpty())return;
        loadingSources=true;
        sourceListError="";
        refreshSourceMenu();
        String address=prefs.getString("jackett_url","http://192.168.50.2:9117");
        worker.execute(()->{
            try {
                List<SourceRegistry.Source> found=SourceRegistry.fetch(address,key);
                main.post(()->{
                    if(isFinishing())return;
                    loadingSources=false;
                    if(!found.isEmpty()){
                        sources.clear();
                        sources.addAll(found);
                        prefs.edit().putString("source_cache",SourceRegistry.toCache(found)).apply();
                    }else{
                        sourceListError="Jackett вернул пустой список";
                    }
                    refreshSourceMenu();
                    // The optional Jackett indexer list does not affect native browsing.
                });
            }catch(Exception e) {
                main.post(()->{
                    if(isFinishing())return;
                    loadingSources=false;
                    sourceListError=error(e);
                    refreshSourceMenu();
                    // Avoid distracting errors when the native source catalog is working.
                });
            }
        });
    }
    private void chooseSource() {
        List<String> titles=new ArrayList<>();
        List<String> ids=new ArrayList<>();
        titles.add("Все подключённые источники");ids.add("all");
        for(SiteCatalog.Site site:SiteCatalog.all()){
            titles.add(site.title+" · "+(site.direct?"прямое подключение":"модуль в разработке"));
            ids.add(site.id);
        }
        titles.add("Дополнительно: индексатор Jackett по ID");ids.add("__manual");
        showSelectionDialog("Каталог источников 18+",titles.toArray(new String[0]),(dialog,index)->{
                String id=ids.get(index);
                if(id.equals("__manual"))manualSource();
                else selectSource(id);
            },"Закрыть");
    }
    private void manualSource() {
        EditText field=new EditText(this);
        field.setSingleLine(true);
        field.setTextColor(WHITE);
        field.setHint("например: mypornclub");
        field.setHintTextColor(MUTED);
        field.setBackground(MediaDesign.outlined(this,0xff1a263b,0xff483376,12,1));
        field.setPadding(dp(14),dp(9),dp(12),dp(9));
        field.setText(activeSource.equals("all")?"":activeSource);
        field.setSelectAllOnFocus(true);
        LinearLayout wrapper=column();
        wrapper.setPadding(dp(22),0,dp(22),0);
        wrapper.addView(field);
        AlertDialog dialog=themedDialog()
            .setTitle("ID индексатора Jackett")
            .setMessage("Можно выбрать любой настроенный в Jackett сайт. Введи его ID из настроек Jackett. Это не означает, что сайт уже проверен.")
            .setView(wrapper).setPositiveButton("Выбрать",null)
            .setNegativeButton("Назад",(d,w)->chooseSource()).create();
        dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String id=field.getText().toString().trim().toLowerCase(Locale.ROOT);
            if(!id.matches("[a-z0-9_-]{1,80}")) {
                field.setError("Нужен ID сайта без пробелов, например xxxclub");
                return;
            }
            dialog.dismiss();
            selectSource(id);
        }));
        dialog.show();
    }
    private void selectSource(String id) {
        if(!id.equals("all") && !id.matches("[a-zA-Z0-9_-]{1,80}"))return;
        activeSource=id;
        if(!id.equals("xxxclub") && !id.equals("all") &&
            prefs.getString("api_key","").isEmpty()){
            SiteCatalog.Site site=SiteCatalog.byId(id);
            if(site!=null && !site.direct){
                themedDialog().setTitle(site.title)
                    .setMessage("Прямой модуль этого сайта ещё разрабатывается. "+
                        "Пока возможен поиск через Jackett при наличии настроенного индексатора. "+
                        "Для XXXClub Jackett больше не нужен.")
                    .setPositiveButton("Открыть настройки",(dialog,which)->settings(false))
                    .setNegativeButton("Продолжить",null).show();
            }
        }
        currentCategory=0;
        currentCategoryLabel="";
        prefs.edit().putString("indexer",id).apply();
        refreshSourceMenu();
        if(tvLayout && sidebarNavigation!=null)renderSidebarNavigation();
        updateFilters();
        load(lastQuery,lastSort);
    }
    private void mergeDiscoveredSources(List<SourceRegistry.Source> observed) {
        boolean changed=false;
        for(SourceRegistry.Source item:observed) {
            if(SourceRegistry.byId(sources,item.id)==null) {
                sources.add(item);
                changed=true;
            }
        }
        if(changed){
            sources.sort(Comparator.comparing(x->x.name.toLowerCase(Locale.ROOT)));
            prefs.edit().putString("source_cache",SourceRegistry.toCache(sources)).apply();
            refreshSourceMenu();
        }
    }
    private void categories() {
        SiteCatalog.Site selected=SiteCatalog.byId(activeSource);
        if(selected!=null && !selected.direct &&
            prefs.getString("api_key","").isEmpty()) {
            themedDialog().setTitle("Разделы · "+selected.title)
                .setMessage("Собственный модуль сайта ещё не готов. "+
                    "После его разработки здесь появятся реальные разделы сайта. "+
                    "Временный просмотр через Jackett требует настройки индексатора.")
                .setPositiveButton("Закрыть",null).show();
            return;
        }
        if(activeSource.equals("all")) {
            themedDialog().setTitle("Категории")
                .setMessage("Категории у сайтов различаются. Сначала выбери один сайт; MediaShelf получит доступные категории именно для него. Общий поиск работает по всем сайтам.")
                .setPositiveButton("Выбрать сайт",(d,w)->chooseSource())
                .setNegativeButton("Закрыть",null).show();
            return;
        }
        String id=activeSource;
        List<SourceRegistry.Category> fallback=IndexerCapabilities.known(id);
        if(!fallback.isEmpty()) {
            showCategoryDialog(id,fallback,true);
            return;
        }
        SourceRegistry.Source src=SourceRegistry.byId(sources,id);
        if(src!=null && !src.categories.isEmpty()) {
            showCategoryDialog(id,new ArrayList<>(src.categories),false);
            return;
        }
        fetchSiteCategories(id);
    }
    private void fetchSiteCategories(String id) {
        Toast.makeText(this,"Получаем категории "+id+"...",Toast.LENGTH_SHORT).show();
        final String base=prefs.getString("jackett_url","http://192.168.50.2:9117");
        final String key=prefs.getString("api_key","");
        worker.execute(()->{
            try {
                List<SourceRegistry.Category> cats=IndexerCapabilities.fetch(base,key,id);
                main.post(()->{
                    if(isFinishing()||!activeSource.equals(id))return;
                    if(cats.isEmpty()) {
                        themedDialog().setTitle("Категории "+id)
                            .setMessage("Сайт отвечает, но Jackett не объявил отдельных категорий. Это не значит, что на самом сайте их нет: для него потребуется отдельный модуль каталога.")
                            .setPositiveButton("Закрыть",null).show();
                        return;
                    }
                    showCategoryDialog(id,cats,false);
                });
            }catch(Exception e){
                main.post(()->{
                    if(isFinishing()||!activeSource.equals(id))return;
                    themedDialog().setTitle("Категории "+id)
                        .setMessage("Категории не получены напрямую от индексатора Jackett: "+error(e)+
                            "\n\nПоиск сайта остаётся доступен. Для его собственных разделов нужен отдельный модуль.")
                        .setPositiveButton("Закрыть",null).show();
                });
            }
        });
    }
    private void showCategoryDialog(String id,List<SourceRegistry.Category> categories,boolean adapter) {
        List<SourceRegistry.Category> list=new ArrayList<>(categories);
        List<String> labels=new ArrayList<>();
        labels.add("Все категории сайта");
        for(SourceRegistry.Category c:list)labels.add(c.name);
        labels.add(adapter?"Категории от прямого модуля сайта":"Обновить категории из Jackett");
        String title=(id.equals("xxxclub")?"XXXClub":id)+" — категории"+
            (adapter?" (адаптер сайта)":"");
        showSelectionDialog(title,labels.toArray(new String[0]),(d,pos)->{
                if(pos==labels.size()-1) {
                    if(adapter) {
                        themedDialog().setTitle("Категории")
                            .setMessage("Этот список взят из адаптера сайта, а не из Jackett. "+
                                "Обновление категорий будет частью отдельного модуля.")
                            .setPositiveButton("Закрыть",null).show();
                    }else fetchSiteCategories(id);
                    return;
                }
                currentCategory=pos==0?0:list.get(pos-1).id;
                currentCategoryLabel=pos==0?"":list.get(pos-1).name;
                queryInput.setText("");
                load("","latest");
            },"Назад");
    }
    private void diagnostics() {
        final List<String> titles=new ArrayList<>();
        final List<String> ids=new ArrayList<>();
        for(SiteCatalog.Site site:SiteCatalog.all()) {
            titles.add(site.title+" · "+(site.direct?"прямой модуль":"через Jackett"));
            ids.add(site.id);
        }
        // Android AlertDialog does not reliably combine message + item list.
        showSelectionDialog("Источники для диагностики",titles.toArray(new String[0]),(d,i)->{
                String id=ids.get(i);
                if(id.equals("xxxclub"))probeNativeXxxclub();
                else probeSource(new SourceRegistry.Source(id,
                    SiteCatalog.byId(id).title,SiteCatalog.byId(id).address,
                    "",new ArrayList<SourceRegistry.Category>()));
            },"Закрыть");
    }
    private void probeNativeXxxclub() {
        Toast.makeText(this,"Проверка XXXClub без Jackett",Toast.LENGTH_SHORT).show();
        worker.execute(()->{
            String outcome;
            try {
                NativeSource source=NativeSources.get("xxxclub");
                SitePages.Cursor cursor=source.begin("",0);
                SitePages.Page first=source.fetchNext(cursor,0);
                StringBuilder report=new StringBuilder();
                report.append("Прямое подключение: работает\n")
                    .append("Раздач на первой странице: ").append(first.items.size())
                    .append("\nКатегорий адаптера: ").append(source.categories().size())
                    .append("\nСайт показал следующую страницу: ")
                    .append(first.more?"да":"не удалось найти")
                    .append("\nЭто не проверка всего каталога.");
                if(!first.items.isEmpty()){
                    Item one=first.items.get(0);
                    if(one.poster.isEmpty())source.enrich(one);
                    report.append("\nПроверка постера: ")
                        .append(SiteDetails.verifyImage(one.poster,referer(one)));
                }
                outcome=report.toString();
            }catch(Exception e){outcome="Прямой модуль недоступен: "+error(e);}
            String result=outcome;
            main.post(()->{
                if(isFinishing())return;
                themedDialog().setTitle("XXXClub · диагностика")
                    .setMessage(result).setPositiveButton("Закрыть",null).show();
            });
        });
    }
    private void probeSource(SourceRegistry.Source source) {
        Toast.makeText(this,"Проверяем "+source.name,Toast.LENGTH_SHORT).show();
        worker.execute(()->{
            String result=checkSource(source);
            main.post(()->{
                if(isFinishing())return;
                probeResults.put(source.id,result);
                refreshSourceMenu();
                themedDialog().setTitle(source.name).setMessage(result)
                    .setPositiveButton("Другие источники",(d,w)->diagnostics()).setNegativeButton("Закрыть",null).show();
            });
        });
    }
    private String checkSource(SourceRegistry.Source source) {
        String base=prefs.getString("jackett_url","http://192.168.50.2:9117");
        String key=prefs.getString("api_key","");
        StringBuilder reportText=new StringBuilder();
        List<SourceRegistry.Category> cats=IndexerCapabilities.known(source.id);
        if(!cats.isEmpty())reportText.append("Категории: ").append(cats.size())
            .append(" (адаптер сайта)\n");
        else {
            try {
                cats=IndexerCapabilities.fetch(base,key,source.id);
                reportText.append("Категории Jackett: ").append(cats.size()).append('\n');
            }catch(Exception e){
                reportText.append("Категории: ошибка ").append(error(e)).append('\n');
            }
        }
        try {
            Jackett.SearchReport report=Jackett.searchReport(base,key,source.id,"",0,13000);
            int posterCount=0, adapted=0;
            for(Item item:report.items){
                if(!item.poster.isEmpty())posterCount++;
                if(SiteDetails.supported(item))adapted++;
            }
            reportText.append("Раздач: ").append(report.items.size())
                .append(" · ссылок на обложки: ").append(posterCount).append('\n');
            if(report.items.isEmpty()){
                reportText.append("По пустому запросу раздач нет; это ещё не доказывает сбой поиска.");
                return "Проверено (без раздач)\n"+reportText;
            }
            Item candidate=null;
            for(Item item:report.items)if(!item.poster.isEmpty()){candidate=item;break;}
            if(candidate==null)for(Item item:report.items)
                if(SiteDetails.supported(item)){candidate=item;break;}
            if(candidate!=null && candidate.poster.isEmpty() && SiteDetails.supported(candidate))
                SiteDetails.enrich(candidate);
            if(candidate!=null && !candidate.poster.isEmpty()){
                String imageResult=SiteDetails.verifyImage(candidate.poster,referer(candidate));
                reportText.append("Проверка изображения: ").append(imageResult).append('\n');
            }else {
                reportText.append("Обложка: Jackett не передал, адаптер пока не нашёл.\n");
            }
            reportText.append("Поддержка страниц: ").append(adapted>0?"есть":"пока нет");
            if(!report.warning.isEmpty())reportText.append("\n").append(report.warning);
            return "OK: поиск отвечает\n"+reportText;
        }catch(Exception e){
            return "Ошибка поиска: "+error(e)+"\n"+reportText;
        }
    }
    private void probeAll() {
        LinearLayout view=column();
        view.setPadding(dp(18),dp(18),dp(18),dp(12));
        TextView progress=label("0 / "+sources.size()+" — проверяем источники",16,WHITE,false);
        view.addView(progress);
        TextView detail=label("Результаты появляются по мере проверки.",13,MUTED,false);
        detail.setPadding(0,dp(14),0,dp(14));
        view.addView(detail);
        AlertDialog dlg=themedDialog().setTitle("Диагностика всех сайтов")
            .setView(view).setPositiveButton("Закрыть",null).create();
        dlg.show();
        List<SourceRegistry.Source> copy=new ArrayList<>(sources);
        worker.execute(()->{
            int n=0,ok=0;
            for(SourceRegistry.Source src:copy) {
                if(Thread.currentThread().isInterrupted())break;
                String outcome=checkSource(src);
                probeResults.put(src.id,outcome);
                if(outcome.startsWith("OK"))ok++;
                int finished=++n,success=ok;
                main.post(()->{
                    if(isFinishing())return;
                    progress.setText(finished+" / "+copy.size()+" · ответили: "+success);
                    detail.setText(src.name+": "+outcome);
                    refreshSourceMenu();
                });
            }
            main.post(()->{
                if(isFinishing())return;
                detail.setText("Проверка завершена. Открой «Диагностика», чтобы увидеть результат каждого сайта.");
            });
        });
    }
    private void message(String text) {
        if(cards==null)return;
        cards.removeAllViews();
        LinearLayout empty=column();
        empty.setGravity(Gravity.CENTER);
        empty.setBackground(MediaDesign.gradient(this,
            new int[]{0xff1b2941,0xff131b2b},22));
        empty.setPadding(dp(18),dp(28),dp(18),dp(32));
        empty.addView(mediaShelfLogo(),spaced(-1,85,0,6));
        TextView head=label("MediaShelf",19,MediaDesign.TEXT,true);
        head.setGravity(Gravity.CENTER);
        empty.addView(head,spaced(-1,31,0,5));
        TextView detail=label(text,14,MediaDesign.MUTED,false);
        detail.setGravity(Gravity.CENTER);
        empty.addView(detail);
        cards.addView(empty,lp(-1,tvLayout?230:195));
    }
    private void addSavedSearchRow(){
        TextView caption=eyebrow("МОИ ПОИСКИ");
        cards.addView(caption,spaced(-1,26,0,3));
        List<SavedSearches.Entry> searches=savedSearches.all();
        android.widget.HorizontalScrollView horizontal=new android.widget.HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        LinearLayout strip=line();
        strip.setPadding(dp(3),dp(4),dp(8),dp(5));
        if(searches.isEmpty()){
            TextView empty=label("Добавь любимые слова через кнопку «Сохранить поиск»",
                tvLayout?12:13,MediaDesign.MUTED,false);
            strip.addView(empty,spaced(-1,42,0,0));
        }
        for(int i=0;i<searches.size();i++){
            final int pos=i;
            SavedSearches.Entry entry=searches.get(i);
            Button chip=button("⌕  "+entry.query);
            chip.setTextSize(tvLayout?13:12);
            chip.setMaxWidth(dp(tvLayout?235:160));
            chip.setSingleLine(true);
            chip.setEllipsize(TextUtils.TruncateAt.END);
            chip.setBackground(MediaDesign.outlined(this,0xff27203f,0xff684191,12,1));
            chip.setOnClickListener(v->{
                activeSource=entry.source;
                prefs.edit().putString("indexer",activeSource).apply();
                currentCategory=entry.category;
                currentCategoryLabel=entry.categoryName;
                if(tvLayout)renderSidebarNavigation();
                if(queryInput!=null)queryInput.setText(entry.query);
                load(entry.query,"search");
            });
            chip.setOnLongClickListener(v->{
                String[] actions={"Переименовать","Сдвинуть влево","Сдвинуть вправо","Удалить"};
                showSelectionDialog(entry.query,actions,(dlg,choice)->{
                    if(choice==0){
                        EditText name=new EditText(this);
                        name.setText(entry.query);
                        name.setSingleLine(true);
                        name.setTextColor(MediaDesign.TEXT);
                        themedDialog().setTitle("Переименовать поиск").setView(name)
                            .setPositiveButton("Сохранить",(d,w)->{
                                savedSearches.rename(pos,name.getText().toString());
                                openFavorites();
                            }).setNegativeButton("Отмена",null).show();
                    }else if(choice==1||choice==2){
                        savedSearches.move(pos,pos+(choice==1?-1:1));
                        openFavorites();
                    }else if(choice==3){
                        savedSearches.remove(pos);
                        openFavorites();
                    }
                },"Закрыть");
                return true;
            });
            strip.addView(chip,spaced(tvLayout?205:140,43,7,0));
        }
        horizontal.addView(strip);
        cards.addView(horizontal,spaced(-1,53,0,9));
    }
    private void openFavorites(){
        favoritesMode=true;
        ++serial; // invalidate any running tracker request
        sitePageCursor=null;
        multiWordSession=null;
        loadingNextPage=false;
        visibleCount=60;
        lastQuery="";
        if(queryInput!=null)queryInput.setText("");
        currentCategory=0;
        currentCategoryLabel="";
        main.removeCallbacks(carouselAdvance);
        featuredItems.clear();
        featuredHost=null;
        catalogItems.clear();
        for(JSONObject saved:favorites.all())catalogItems.add(new Item(saved));
        pageInfo="";
        if(status!=null)status.setText("Избранное · "+catalogItems.size());
        updateCurrentSection();
        if(tvLayout&&sidebarNavigation!=null)renderSidebarNavigation();
        if(catalogScroll!=null)catalogScroll.scrollTo(0,0);
        show(catalogItems);
        // The selected Favorites entry was removed and rebuilt above. Without
        // restoring focus, Android chooses an unrelated button (e.g. Settings).
        if(tvLayout && navFavoritesButton!=null)
            navFavoritesButton.post(()->{
                if(!isFinishing() && favoritesMode &&
                    navFavoritesButton!=null && navFavoritesButton.isAttachedToWindow())
                    navFavoritesButton.requestFocus();
            });
    }
    private JSONObject favoriteRecord(Item item){
        JSONObject record=new JSONObject();
        try{
            record.put("Title",item.title);
            record.put("Tracker",item.tracker);
            record.put("Details",item.details);
            record.put("MagnetUri",item.magnet);
            record.put("Link",item.download);
            record.put("Poster",item.poster);
            record.put("Description",item.description);
            record.put("PublishDate",item.date);
            record.put("Size",item.bytes);
            record.put("Seeders",item.seeders);
        }catch(org.json.JSONException ignored){}
        return record;
    }
    /**
     * Scan independent queries for each word. A tracker can match "Blacked"
     * and "Gangbang" on different pages; filtering a single first page loses
     * valid results. Keep cursors to resume scanning without a page cap.
     */
    private static final class MultiWordSession {
        final String query;
        final List<SitePages.Cursor> cursors=new ArrayList<>();
        final List<Item> results=new ArrayList<>();
        final Set<String> found=new HashSet<>();
        final int category;
        final int id;
        int scanned=0;
        int nextIndex=0;
        boolean running=false;
        String lastError="";
        MultiWordSession(String q,NativeSource source,int category,int id){
            query=q;this.category=category;this.id=id;
            for(String term:SearchTerms.words(q))
                cursors.add(source.begin(term,category));
        }
        boolean hasMore(){
            for(SitePages.Cursor c:cursors)if(!c.finished)return true;
            return false;
        }
    }
    private void scanWords(MultiWordSession session,boolean initial){
        if(session.running)return;
        session.running=true;
        loadingNextPage=true;
        worker.execute(()->{
            long deadline=android.os.SystemClock.elapsedRealtime()+
                (initial?36000:28000);
            int pageBudget=initial?20:14;
            NativeSource source=NativeSources.get(
                activeSource.equals("all")?"xxxclub":activeSource);
            if(source==null)return;
            int attempts=0;
            while(session.id==serial && session.hasMore() &&
                  attempts<pageBudget &&
                  android.os.SystemClock.elapsedRealtime()<deadline){
                SitePages.Cursor cursor=null;
                for(int seek=0;seek<session.cursors.size();seek++){
                    int pos=(session.nextIndex+seek)%session.cursors.size();
                    if(!session.cursors.get(pos).finished){
                        cursor=session.cursors.get(pos);
                        session.nextIndex=(pos+1)%session.cursors.size();
                        break;
                    }
                }
                if(cursor==null)break;
                try{
                    SitePages.Page page=source.fetchNext(cursor,session.category);
                    session.scanned++;
                    for(Item item:page.items){
                        if(SearchTerms.matchesTitle(item.title,session.query) &&
                            session.found.add(catalogKey(item)))
                            session.results.add(item);
                    }
                }catch(Exception ex){
                    session.lastError=error(ex);
                    // A single provider failure must not leave an endless loop.
                    cursor.finished=true;
                }
                attempts++;
                // Streaming status once per page ensures the user never has to
                // guess whether the search is still checking later pages.
                final List<Item> snapshot=new ArrayList<>(session.results);
                final int pages=session.scanned;
                main.post(()->{
                    if(isFinishing()||session.id!=serial||
                       multiWordSession!=session)return;
                    catalogItems.clear();
                    catalogItems.addAll(snapshot);
                    pageInfo="Поиск по всем словам · проверено страниц: "+pages+
                        " · найдено: "+snapshot.size()+
                        (session.hasMore()?" · поиск продолжается":" · поиск завершён");
                    if(status!=null)status.setText("Поиск: "+session.query+
                        " · страниц "+pages+" · найдено "+snapshot.size());
                    show(catalogItems);
                });
                if(session.results.size()>=35)break;
            }
            main.post(()->{
                if(isFinishing()||session.id!=serial||
                    multiWordSession!=session)return;
                session.running=false;
                loadingNextPage=false;
                pageInfo="Проверено страниц: "+session.scanned+
                    " · найдено раздач с каждым словом: "+session.results.size()+
                    (session.hasMore()?
                        " · можно продолжить поиск по следующим страницам":
                        " · доступные страницы проверены")+
                    (session.lastError.isEmpty()?"":" · "+session.lastError);
                show(catalogItems);
            });
        });
    }
    private static List<Item> filterByAndTerms(List<Item> incoming,String query){
        if(!SearchTerms.needsAndFilter(query))return incoming;
        List<Item> accepted=new ArrayList<>();
        for(Item item:incoming)
            if(SearchTerms.matchesTitle(item.title,query))accepted.add(item);
        return accepted;
    }
    private void load(String q,String order) {
        boolean wasFavorites=favoritesMode;
        favoritesMode=false;
        if(wasFavorites && tvLayout && sidebarNavigation!=null)renderSidebarNavigation();
        lastQuery=q;
        lastSort=order;
        focusItemAfterLoad=-1;
        updateFilters();
        updateCurrentSection();
        if(tvLayout&&navHomeButton!=null){
            boolean latest=lastQuery.isEmpty()&&currentCategory==0&&
                !lastSort.equals("popular");
            boolean popular=lastQuery.isEmpty()&&currentCategory==0&&
                lastSort.equals("popular");
            navHomeButton.setSelected(latest);
            navPopularButton.setSelected(popular);
            navHomeButton.setTextColor(latest?
                MediaDesign.ACCENT_LIGHT:MediaDesign.TEXT);
            navPopularButton.setTextColor(popular?
                MediaDesign.ACCENT_LIGHT:MediaDesign.TEXT);
            if(popularFlameIcon!=null){
                popularFlameIcon.setTint(popular?
                    MediaDesign.ACCENT_LIGHT:MediaDesign.TEXT);
                navPopularButton.invalidate();
            }
        }
        final String source=activeSource;
        final String providerQuery=SearchTerms.upstreamQuery(q);
        final int category=currentCategory;
        final int requestId=++serial;
        if(catalogScroll!=null)catalogScroll.scrollTo(0,0);
        SiteCatalog.Site configuredSite=SiteCatalog.byId(source);
        String friendly=source.equals("all")?"Все подключённые":
            configuredSite!=null?configuredSite.title:source;
        String section=order.equals("latest")?"Новинки":order.equals("popular")?"Популярное":"Поиск: "+q;
        status.setText(section+" · "+friendly+
            (category>0?" · "+currentCategoryLabel:""));
        catalogItems.clear();sitePageCursor=null;multiWordSession=null;
        loadingNextPage=false;visibleCount=60;
        main.removeCallbacks(carouselAdvance);
        featuredHost=null;
        featuredItems.clear();
        featuredIndex=0;
        featuredLoading=isHomepage();
        pageInfo="";
        resetCatalogScroll=true;
        message("Идёт поиск…");
        final String api=prefs.getString("api_key","");
        final String base=prefs.getString("jackett_url","http://192.168.50.2:9117");

        NativeSource direct=NativeSources.get(source.equals("all")?"xxxclub":source);
        if(direct!=null && SearchTerms.needsAndFilter(q)){
            MultiWordSession session=new MultiWordSession(q,direct,
                source.equals("all")?0:category,requestId);
            multiWordSession=session;
            scanWords(session,true);
            return;
        }
        if(direct!=null) {
            worker.execute(()->{
                SitePages.Cursor cursor=direct.begin(providerQuery,source.equals("all")?0:category);
                try {
                    SitePages.Page first=direct.fetchNext(cursor,source.equals("all")?0:category);
                    List<Item> results=new ArrayList<>(filterByAndTerms(first.items,q));
                    // Check a second page only when the first has no AND matches;
                    // otherwise show results immediately without extra network delay.
                    if(SearchTerms.needsAndFilter(q)){
                        Set<String> found=new HashSet<>();
                        for(Item item:results)found.add(catalogKey(item));
                        for(int attempt=0;attempt<1 && !cursor.finished &&
                                results.isEmpty();attempt++){
                            SitePages.Page next;
                            try {
                                next=direct.fetchNext(cursor,source.equals("all")?0:category);
                            }catch(Exception secondaryError){
                                break; // Keep results from previously loaded pages.
                            }
                            for(Item item:filterByAndTerms(next.items,q))
                                if(found.add(catalogKey(item)))results.add(item);
                        }
                    }
                    if(order.equals("popular"))
                        results.sort(Comparator.comparingInt((Item item)->item.seeders).reversed());
                    main.post(()->{
                        if(isFinishing() || requestId!=serial)return;
                        catalogItems.clear();catalogItems.addAll(results);
                        sitePageCursor=cursor;
                        visibleCount=60;
                        pageInfo=(source.equals("all")
                            ?"Общий каталог пока включает только прямой модуль XXXClub. "+
                              "Другие сайты подключим после разработки их модулей. "
                            :"Прямой каталог XXXClub · без Jackett. ")+
                            first.message+" · полнота каталога зависит от страниц сайта";
                        show(catalogItems);
                        fetchFeaturedFilms(requestId);
                    });
                }catch(Exception directError) {
                    if(api.isEmpty()) {
                        main.post(()->{
                            if(isFinishing() || requestId!=serial)return;
                            sitePageCursor=cursor;
                            pageInfo="Сайт не ответил напрямую: "+error(directError)+
                                ". Можно повторить загрузку или настроить Jackett как резерв.";
                            show(catalogItems);
                        });
                        return;
                    }
                    // Bridge fallback ONLY when direct parsing fails, not the default.
                    try {
                        Jackett.SearchReport bridged=Jackett.searchReport(base,api,"xxxclub",providerQuery,
                                source.equals("all")?0:category,25000);
                        List<Item> results=filterByAndTerms(bridged.items,q);
                        main.post(()->{
                            if(isFinishing() || requestId!=serial)return;
                            catalogItems.clear();catalogItems.addAll(results);
                            sitePageCursor=cursor;
                            pageInfo="Прямой сайт недоступен: "+error(directError)+
                                ". Результаты временно получены через Jackett; "+
                                "возможна неполная первая страница.";
                            show(catalogItems);
                        });
                    }catch(Exception fallbackError){
                        main.post(()->{
                            if(isFinishing() || requestId!=serial)return;
                            sitePageCursor=cursor;
                            pageInfo="Прямой сайт: "+error(directError)+
                                "; резервный Jackett: "+error(fallbackError)+
                                ". Попробуй загрузить страницу ещё раз.";
                            show(catalogItems);
                        });
                    }
                }
            });
            return;
        }

        if(api.isEmpty()) {
            pageInfo="Для "+friendly+" прямой модуль ещё не готов. "+
                "Пока можно подключить сайт через Jackett в настройках. "+
                "XXXClub уже работает без Jackett.";
            show(catalogItems);
            return;
        }
        worker.execute(()->{
            try {
                Jackett.SearchReport report=Jackett.searchReport(base,api,source,providerQuery,category,35000);
                List<Item> results=filterByAndTerms(report.items,q);
                if(order.equals("popular"))
                    results.sort(Comparator.comparingInt((Item x)->x.seeders).reversed());
                main.post(()->{
                    if(requestId!=serial||isFinishing())return;
                    catalogItems.clear();catalogItems.addAll(results);
                    sitePageCursor=null;visibleCount=60;
                    pageInfo="Временный режим Jackett · первые результаты индексатора. "+
                        "Полная пагинация будет в прямом модуле этого сайта. "+
                        report.warning;
                    show(catalogItems);
                });
            }catch(Exception e){
                main.post(()->{
                    if(requestId!=serial || isFinishing())return;
                    pageInfo="Нет результатов: "+error(e)+
                        ". Проверь поддержку и настройки этого индексатора в Jackett.";
                    show(catalogItems);
                });
            }
        });
    }
    private static String error(Exception e) {
        if (e instanceof java.net.ConnectException) return "нет соединения";
        if (e instanceof java.net.SocketTimeoutException) return "превышено время ожидания";
        String m=e.getMessage();
        if (m==null || m.contains("http://") || m.contains("https://") || m.contains("apikey")) return e.getClass().getSimpleName();
        return m;
    }
    private void show(List<Item> items) {
        firstCatalogTile=null;
        int oldScroll=resetCatalogScroll?0:
            catalogScroll==null?0:catalogScroll.getScrollY();
        boolean resetThisDraw=resetCatalogScroll;
        resetCatalogScroll=false;
        main.removeCallbacks(carouselAdvance);
        cards.removeAllViews();
        featuredHost=null;

        if(!favoritesMode && !items.isEmpty() && isHomepage())
            addFeaturedCarousel();

        LinearLayout heading=line();
        LinearLayout left=column();
        String category=favoritesMode?"ИЗБРАННОЕ":currentCategory>0?
            currentCategoryLabel:lastQuery.isEmpty()?
                lastSort.equals("popular")?"ПОПУЛЯРНОЕ":"НОВЫЕ РАЗДАЧИ":
                "ПОИСК ПО КАТАЛОГУ";
        if(!tvLayout)left.addView(eyebrow(category.toUpperCase(Locale.ROOT)));
        TextView caption=label(favoritesMode?"Избранное":lastQuery.isEmpty()?
                (currentCategory>0?currentCategoryLabel:
                    lastSort.equals("popular")?"Популярное":"Новинки"):
                "Поиск: "+lastQuery,tvLayout?17:19,MediaDesign.TEXT,true);
        caption.setMaxLines(2);
        caption.setEllipsize(TextUtils.TruncateAt.END);
        caption.setTextSize(compactTvPreview?14:tvLayout?17:19);
        left.addView(caption);
        heading.addView(left,new LinearLayout.LayoutParams(0,
            dp(compactTvPreview?30:tvLayout?35:56),1));
        TextView number=MediaDesign.tag(this,items.size()+" найдено",
            MediaDesign.ACCENT_LIGHT,0xff39245b);
        heading.addView(number);
        cards.addView(heading,spaced(-1,
            compactTvPreview?32:tvLayout?37:60,0,compactTvPreview?2:5));
        if(favoritesMode)addSavedSearchRow();
        TextView line=label("Показано "+Math.min(items.size(),visibleCount)+
            " · Страниц сайта: "+(multiWordSession!=null?
                multiWordSession.scanned:
                sitePageCursor!=null?sitePageCursor.pageIndex:"—"),
            12,MediaDesign.MUTED,false);
        if(!tvLayout)cards.addView(line,spaced(-1,24,0,8));

        if(!pageInfo.isEmpty() && (!tvLayout || items.isEmpty())){
            TextView note=label(pageInfo,12,0xff8fa8d1,false);
            note.setMaxLines(2);
            note.setEllipsize(TextUtils.TruncateAt.END);
            note.setPadding(dp(13),dp(8),dp(13),dp(8));
            note.setBackground(MediaDesign.round(this,0xff172238,10));
            cards.addView(note,spaced(-1,-2,0,18));
        }
        if(items.isEmpty() && sitePageCursor!=null &&
            SearchTerms.needsAndFilter(lastQuery)){
            TextView guidance=label("Пока нет названий с каждым из введённых слов. "+
                "Можно загрузить следующую страницу поиска.",13,MediaDesign.MUTED,false);
            guidance.setPadding(dp(10),dp(14),dp(10),dp(12));
            cards.addView(guidance,spaced(-1,-2,0,12));
        }
        if(items.isEmpty() && sitePageCursor==null && multiWordSession==null){
            LinearLayout state=column();
            state.setGravity(Gravity.CENTER);
            state.setPadding(dp(12),dp(33),dp(12),dp(33));
            TextView mark=label("⌕",44,0xff6d88b9,true);
            mark.setGravity(Gravity.CENTER);
            state.addView(mark);
            TextView info=label(favoritesMode?"Избранное пока пусто":"Здесь пока нет раздач",20,MediaDesign.TEXT,true);
            info.setGravity(Gravity.CENTER);
            state.addView(info,spaced(-1,50,0,2));
            TextView help=label(favoritesMode?"Открой карточку и нажми ★ Добавить в избранное":
                "Попробуй другое слово или выбери раздел сайта",13,
                MediaDesign.MUTED,false);
            help.setGravity(Gravity.CENTER);
            state.addView(help);
            cards.addView(state);
            return;
        }
        PosterGrid.Size geometry=PosterGrid.calculate(
            getResources().getConfiguration().screenWidthDp,
            sidebarWidthDp,sidebarCollapsed,compactTvPreview,tvLayout,
            prefs.getInt("poster_columns",0));
        int spacing=geometry.spacingDp;
        int columns=geometry.columns;
        int cardWidth=geometry.cardDp;
        int coverHeight=(int)(cardWidth*
            (compactTvPreview?1.05f:tvLayout?1.29f:1.12f));
        GridLayout grid=new GridLayout(this);
        final List<Integer> tileIds=new ArrayList<>();
        grid.setColumnCount(columns);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setPadding(dp(2),dp(4),dp(2),dp(5));
        cards.addView(grid);
        int limit=Math.min(visibleCount,items.size());

        for(int i=0;i<limit;i++){
            Item item=items.get(i);
            LinearLayout tile=column();
            tile.setBackground(MediaDesign.round(this,MediaDesign.SURFACE,10));
            tile.setPadding(dp(compactTvPreview?3:tvLayout?4:6),
                dp(compactTvPreview?3:tvLayout?4:6),
                dp(compactTvPreview?3:tvLayout?4:6),
                dp(compactTvPreview?4:tvLayout?5:10));
            tile.setClickable(true);tile.setFocusable(true);
            tile.setId(View.generateViewId());
            if(i>=columns)tile.setNextFocusUpId(tileIds.get(i-columns));
            else if(featuredHost!=null)tile.setNextFocusUpId(featuredHost.getId());
            else if(!filterButtons.isEmpty())
                tile.setNextFocusUpId(filterButtons.get(0).getId());
            tileIds.add(tile.getId());
            if(i==0)firstCatalogTile=tile;
            tile.setContentDescription(item.title+"; "+item.size()+
                "; сидов "+item.seeders+"; "+item.tracker);
            tile.setOnFocusChangeListener((v,f)->{
                tile.setBackground(f?
                    MediaDesign.outlined(this,0xff2a3d5f,
                        MediaDesign.ACCENT,10,2):
                    MediaDesign.round(this,MediaDesign.SURFACE,10));
                MediaDesign.animateFocus(tile,f);
            });
            android.widget.FrameLayout cover=new android.widget.FrameLayout(this);
            cover.setBackground(MediaDesign.gradient(this,
                new int[]{0xff314566,0xff151d31,0xff11192a},14));
            cover.setClipToOutline(true);
            TextView placeholder=label("▶\n\nНет обложки",12,0xff8396b8,false);
            placeholder.setGravity(Gravity.CENTER);
            cover.addView(placeholder,new android.widget.FrameLayout.LayoutParams(-1,-1));
            ImageView image=new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.addView(image,new android.widget.FrameLayout.LayoutParams(-1,-1));

            String quality=MediaDesign.quality(item.title);
            if(!quality.isEmpty()){
                TextView qualityLabel=MediaDesign.tag(this,quality,
                    0xfff5eddd,0xd8243047);
                android.widget.FrameLayout.LayoutParams qualityParams=
                    new android.widget.FrameLayout.LayoutParams(-2,-2,
                        Gravity.TOP|Gravity.RIGHT);
                qualityParams.setMargins(0,dp(9),dp(9),0);
                cover.addView(qualityLabel,qualityParams);
            }
            if(item.seeders>0){
                TextView seeds=MediaDesign.tag(this,"● "+item.seeders,
                    MediaDesign.SUCCESS,0xdd182b35);
                android.widget.FrameLayout.LayoutParams seedParams=
                    new android.widget.FrameLayout.LayoutParams(-2,-2,
                        Gravity.BOTTOM|Gravity.LEFT);
                seedParams.setMargins(dp(8),0,0,dp(8));
                cover.addView(seeds,seedParams);
            }
            tile.addView(cover,lp(-1,coverHeight));
            // Every currently shown card is eligible for its original poster.
            // Network work is deduplicated and performed off the UI thread.
            loadPoster(image,item,true);

            TextView title=label(item.title,
                compactTvPreview?10:tvLayout?12:13,
                MediaDesign.TEXT,true);
            title.setMaxLines(tvLayout?2:3);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setPadding(dp(compactTvPreview?3:tvLayout?4:7),
                dp(compactTvPreview?4:tvLayout?5:10),dp(3),0);
            tile.addView(title,new LinearLayout.LayoutParams(-1,0,1));
            TextView facts=label(item.size()+"  ·  "+item.tracker,
                compactTvPreview?9:tvLayout?10:11,MediaDesign.MUTED,false);
            facts.setSingleLine(true);
            facts.setEllipsize(TextUtils.TruncateAt.END);
            facts.setPadding(dp(compactTvPreview?3:7),dp(compactTvPreview?2:5),0,0);
            tile.addView(facts);
            tile.setOnClickListener(v->details(item));
            tile.setLongClickable(true);
            tile.setOnLongClickListener(v->{
                showTileActions(v,item);
                return true;
            });
            final int tileRow=i/columns;
            final int tileColumn=i%columns;
            tile.setOnKeyListener((v,key,event)->{
                if(event.getAction()!=android.view.KeyEvent.ACTION_DOWN)
                    return false;
                if(key==android.view.KeyEvent.KEYCODE_DPAD_UP && tileRow==0){
                    if(featuredHost!=null && featuredHost.getParent()!=null)
                        featuredHost.requestFocus();
                    else focusCurrentCategory();
                    return true;
                }
                // TV convention: left from the first poster column reveals
                // the collapsed navigation menu without using a touchscreen.
                if(tvLayout && tileColumn==0 &&
                    key==android.view.KeyEvent.KEYCODE_DPAD_LEFT){
                    focusTopSearch();
                    return true;
                }
                // Let Android deliver the native short/long click on ENTER/CENTER.
                // Intercepting ACTION_DOWN here used to open details before a long press.
                return false;
            });
            GridLayout.LayoutParams p=new GridLayout.LayoutParams(
                GridLayout.spec(i/columns),GridLayout.spec(i%columns));
            p.width=dp(cardWidth);
            p.height=dp(coverHeight+(compactTvPreview?49:tvLayout?61:88));
            p.rightMargin=dp(spacing);
            p.bottomMargin=dp(compactTvPreview?7:tvLayout?11:12);
            grid.addView(tile,p);
            if(i==focusItemAfterLoad){
                focusItemAfterLoad=-1;
                tile.post(tile::requestFocus);
            }
        }
        if(items.size()>limit){
            int next=Math.min(24,items.size()-limit);
            Button reveal=accentButton("Показать ещё "+next+"  ↓");
            reveal.setOnClickListener(v->{
                focusItemAfterLoad=limit;
                visibleCount+=24;
                show(catalogItems);
            });
            cards.addView(reveal,spaced(-1,54,0,12));
        }
        if(sitePageCursor!=null || multiWordSession!=null){
            if(multiWordSession!=null?multiWordSession.hasMore():
                !sitePageCursor.finished){
                Button more=button(loadingNextPage?
                    "Получаем следующую страницу…":
                    "Загрузить следующую страницу  ↓");
                more.setEnabled(!loadingNextPage);
                more.setTextColor(MediaDesign.ACCENT_LIGHT);
                more.setBackground(MediaDesign.outlined(this,
                    0xff202f49,0xff7044a0,14,1));
                more.setOnFocusChangeListener((v,f)->{
                    more.setBackground(f?
                        MediaDesign.outlined(this,0xff482b72,
                            MediaDesign.ACCENT,14,2):
                        MediaDesign.outlined(this,0xff202f49,0xff7044a0,14,1));
                    MediaDesign.animateFocus(more,f);
                });
                more.setOnClickListener(v->loadNextPage());
                cards.addView(more,spaced(-1,57,0,14));
            }else{
                TextView end=label("Это последняя обнаруженная страница. "+
                    "Если на сайте есть ещё, сообщи об этом — проверим навигацию.",
                    12,MediaDesign.MUTED,false);
                end.setGravity(Gravity.CENTER);
                end.setPadding(dp(12),dp(10),dp(12),dp(10));
                cards.addView(end,lp(-1,-2));
            }
        }
        if(catalogScroll!=null){
            catalogScroll.post(()->{
                if(focusItemAfterLoad>=0)return;
                catalogScroll.scrollTo(0,resetThisDraw?0:oldScroll);
            });
        }
    }

    private void addFeaturedCarousel(){
        final android.widget.FrameLayout hero=new android.widget.FrameLayout(this);
        featuredHost=hero;
        hero.setBackground(MediaDesign.horizontal(this,
            new int[]{0xff3b1c6d,0xff261741,0xff13132b},20));
        hero.setClipToOutline(true);
        hero.setFocusable(true);
        hero.setClickable(true);
        hero.setId(View.generateViewId());
        hero.setOnKeyListener((v,key,event)->{
            if(event.getAction()!=android.view.KeyEvent.ACTION_DOWN)
                return false;
            if(key==android.view.KeyEvent.KEYCODE_DPAD_UP){
                focusCurrentCategory();return true;
            }
            if(key==android.view.KeyEvent.KEYCODE_DPAD_DOWN){
                if(firstCatalogTile!=null)firstCatalogTile.requestFocus();
                return true;
            }
            if(key==android.view.KeyEvent.KEYCODE_DPAD_LEFT){
                advanceCarousel(-1);return true;
            }
            if(key==android.view.KeyEvent.KEYCODE_DPAD_RIGHT){
                advanceCarousel(1);return true;
            }
            return false;
        });
        hero.setContentDescription("Рекомендованные фильмы, карусель");
        hero.setOnFocusChangeListener((v,f)->{
            hero.setBackground(f?
                MediaDesign.outlined(this,0xff42226f,MediaDesign.ACCENT,20,2):
                MediaDesign.horizontal(this,
                    new int[]{0xff3b1c6d,0xff261741,0xff13132b},20));
            MediaDesign.animateFocus(hero,f);
        });
        hero.setOnClickListener(v->{
            if(!featuredItems.isEmpty())
                details(featuredItems.get(featuredIndex));
        });

        int height=compactTvPreview?98:tvLayout?132:160;
        featuredPoster=new ImageView(this);
        featuredPoster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        android.widget.FrameLayout.LayoutParams artParams=
            new android.widget.FrameLayout.LayoutParams(
                dp(compactTvPreview?180:tvLayout?300:180),-1,
                Gravity.RIGHT|Gravity.TOP);
        hero.addView(featuredPoster,artParams);
        View tint=new View(this);
        tint.setBackground(MediaDesign.horizontal(this,
            new int[]{0xff3b1c6d,0xee352057,0x882b1b4a,0x00221a36},20));
        hero.addView(tint,new android.widget.FrameLayout.LayoutParams(-1,-1));

        LinearLayout copy=column();
        copy.setGravity(Gravity.CENTER_VERTICAL);
        copy.setPadding(dp(compactTvPreview?12:tvLayout?30:17),
            dp(compactTvPreview?8:14),dp(3),dp(compactTvPreview?11:17));
        featuredEyebrow=eyebrow("НОВИНКИ · ПОДБОРКА MEDIASHELF");
        featuredEyebrow.setTextSize(compactTvPreview?8:tvLayout?9:11);
        copy.addView(featuredEyebrow,spaced(-1,
            compactTvPreview?15:tvLayout?18:25,0,1));
        featuredTitle=label("Загружаем новинки",
            compactTvPreview?14:tvLayout?17:19,
            MediaDesign.TEXT,true);
        featuredTitle.setMaxLines(2);
        featuredTitle.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(featuredTitle,new LinearLayout.LayoutParams(-1,0,1));
        featuredMeta=label("Проверяем новинки",
            compactTvPreview?9:tvLayout?11:13,
            0xffd7d2ea,false);
        featuredMeta.setMaxLines(1);
        featuredMeta.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(featuredMeta,spaced(-1,
            compactTvPreview?16:tvLayout?18:26,0,2));
        // The entire banner itself is clickable; no oversized CTA button.
        android.widget.FrameLayout.LayoutParams textParams=
            new android.widget.FrameLayout.LayoutParams(
                dp(compactTvPreview?
                    Math.max(182,(getResources().getConfiguration().screenWidthDp-150)/2):
                    tvLayout?355:245),-1,Gravity.LEFT|Gravity.TOP);
        hero.addView(copy,textParams);

        LinearLayout navigation=line();
        navigation.setGravity(Gravity.CENTER_VERTICAL);
        navigation.setPadding(dp(3),dp(1),dp(3),dp(1));
        navigation.setBackground(MediaDesign.round(this,0xdc18152b,13));
        Button back=button("‹");
        back.setTextSize(compactTvPreview?18:tvLayout?20:26);
        back.setContentDescription("Предыдущий фильм");
        back.setPadding(0,0,0,0);
        back.setOnClickListener(v->advanceCarousel(-1));
        back.setOnKeyListener((v,key,event)->{
            if(event.getAction()!=android.view.KeyEvent.ACTION_DOWN)return false;
            if(key==android.view.KeyEvent.KEYCODE_DPAD_UP){
                focusCurrentCategory();return true;
            }
            if(key==android.view.KeyEvent.KEYCODE_DPAD_DOWN &&
                firstCatalogTile!=null){firstCatalogTile.requestFocus();return true;}
            return false;
        });
        navigation.addView(back,lp(
            compactTvPreview?24:tvLayout?29:39,compactTvPreview?23:tvLayout?27:36));
        featuredDots=label("",compactTvPreview?12:13,MediaDesign.ACCENT_LIGHT,true);
        featuredDots.setGravity(Gravity.CENTER);
        featuredDots.setContentDescription("Номер фильма в карусели");
        navigation.addView(featuredDots,lp(
            compactTvPreview?60:tvLayout?74:93,
            compactTvPreview?23:tvLayout?27:36));
        Button forward=button("›");
        forward.setTextSize(compactTvPreview?18:tvLayout?20:26);
        forward.setContentDescription("Следующий фильм");
        forward.setPadding(0,0,0,0);
        forward.setOnClickListener(v->advanceCarousel(1));
        forward.setOnKeyListener((v,key,event)->{
            if(event.getAction()!=android.view.KeyEvent.ACTION_DOWN)return false;
            if(key==android.view.KeyEvent.KEYCODE_DPAD_UP){
                focusCurrentCategory();return true;
            }
            if(key==android.view.KeyEvent.KEYCODE_DPAD_DOWN &&
                firstCatalogTile!=null){firstCatalogTile.requestFocus();return true;}
            return false;
        });
        navigation.addView(forward,lp(
            compactTvPreview?24:tvLayout?29:39,compactTvPreview?23:tvLayout?27:36));
        featuredControls=navigation;
        android.widget.FrameLayout.LayoutParams navParams=
            new android.widget.FrameLayout.LayoutParams(-2,-2,
                Gravity.RIGHT|Gravity.BOTTOM);
        navParams.setMargins(0,0,dp(compactTvPreview?7:13),
            dp(compactTvPreview?5:tvLayout?7:11));
        hero.addView(navigation,navParams);

        cards.addView(hero,spaced(-1,height,0,
            compactTvPreview?8:tvLayout?12:18));
        renderCarouselSlide();
    }

    private void renderCarouselSlide(){
        if(featuredHost==null || featuredTitle==null || featuredMeta==null ||
            featuredHost.getParent()==null)return;
        if(featuredItems.isEmpty()){
            featuredTitle.setText(featuredLoading?
                "Загружаем новинки":
                "Новинки для баннера не найдены");
            featuredMeta.setText(featuredLoading?
                "Настройки баннера: ✦":
                "Выбери другую рубрику или тему: ✦");
            featuredEyebrow.setText("НОВИНКИ · ПОДБОРКА MEDIASHELF");
            if(featuredPoster!=null){
                featuredPoster.setTag(null);
                featuredPoster.setImageDrawable(null);
            }
            featuredControls.setVisibility(View.GONE);
            scheduleCarousel();
            return;
        }
        if(featuredIndex<0 || featuredIndex>=featuredItems.size())featuredIndex=0;
        Item movie=featuredItems.get(featuredIndex);
        featuredTitle.setText(movie.title);
        String quality=MediaDesign.quality(movie.title);
        featuredEyebrow.setText("НОВИНКИ · "+BannerSettings.summary(bannerCats(),bannerTopic()));
        featuredMeta.setText((quality.isEmpty()?"Новинка":quality)+"  ·  "+
            movie.seeders+" раздающих  ·  "+movie.size());
        featuredControls.setVisibility(featuredItems.size()>1?View.VISIBLE:View.GONE);
        StringBuilder dots=new StringBuilder();
        for(int i=0;i<featuredItems.size();i++){
            if(i>0)dots.append(' ');
            dots.append(i==featuredIndex?'●':'○');
        }
        featuredDots.setText(dots.toString());
        featuredDots.setContentDescription("Фильм "+(featuredIndex+1)+
            " из "+featuredItems.size());
        if(featuredPoster!=null){
            featuredPoster.setTag(movie);
            featuredPoster.setImageDrawable(null);
            featuredPoster.setAlpha(0.6f);
            loadImage(featuredPoster,movie.poster,referer(movie),movie,true);
            featuredPoster.animate().alpha(1f).setDuration(270).start();
        }
        scheduleCarousel();
    }
    private static String catalogKey(Item item){
        if(item.details!=null && !item.details.isEmpty()){
            String clean=item.details.replaceFirst("/+$","").toLowerCase(Locale.ROOT);
            return clean;
        }
        return item.title.toLowerCase(Locale.ROOT)+"|"+item.bytes;
    }
    private void loadNextPage(){
        if(multiWordSession!=null){
            if(!multiWordSession.running && multiWordSession.hasMore())
                scanWords(multiWordSession,false);
            return;
        }
        if(loadingNextPage || sitePageCursor==null || sitePageCursor.finished)return;
        loadingNextPage=true;
        int job=serial;
        final SitePages.Cursor cursor=sitePageCursor;
        final String fullSearch=lastQuery;
        final int category=currentCategory;
        final Set<String> existing=new HashSet<>();
        for(Item item:catalogItems)existing.add(catalogKey(item));
        focusItemAfterLoad=-1;
        pageInfo="Получаем следующую страницу каталога XXXClub…";
        show(catalogItems);
        worker.execute(()->{
            List<Item> additional=new ArrayList<>();
            String last="";
            try {
                // First site page can duplicate Jackett's one-page output:
                // walk up to two real pages on this click before reporting no new items.
                for(int attempt=0;attempt<2 && !cursor.finished;attempt++){
                    NativeSource provider=NativeSources.get("xxxclub");
                    SitePages.Page p=provider.fetchNext(cursor,category);
                    last=p.message;
                    for(Item item:p.items){
                        if(SearchTerms.matchesTitle(item.title,fullSearch) &&
                            existing.add(catalogKey(item)))additional.add(item);
                    }
                    if(!additional.isEmpty())break;
                }
                final String outcome=last;
                main.post(()->{
                    if(isFinishing() || job!=serial || sitePageCursor!=cursor)return;
                    loadingNextPage=false;
                    focusItemAfterLoad=additional.isEmpty()?-1:catalogItems.size();
                    catalogItems.addAll(additional);
                    visibleCount=Math.max(visibleCount,
                        Math.min(catalogItems.size(),visibleCount+Math.max(30,additional.size())));
                    pageInfo=outcome+" · новых: "+additional.size()+
                        (cursor.finished?" · других страниц сайт не показал":"");
                    show(catalogItems);
                });
            }catch(Exception e){
                String failure=error(e);
                main.post(()->{
                    if(isFinishing() || job!=serial || sitePageCursor!=cursor)return;
                    loadingNextPage=false;
                    focusItemAfterLoad=-1;
                    pageInfo="Не удалось получить страницу XXXClub: "+failure+
                        ". Повтори попытку или проверь страницу в браузере.";
                    show(catalogItems);
                });
            }
        });
    }
    private static String referer(Item item) {
        try {
            Uri url=Uri.parse(item.details);
            if(url.getScheme()!=null && url.getAuthority()!=null)
                return url.getScheme()+"://"+url.getAuthority()+"/";
        }catch(Exception ignored){}
        return "https://xxxclub.to/";
    }
    private void loadPoster(ImageView view, Item item, boolean prefetch) {
        view.setTag(item);
        // Show Jackett's thumbnail quickly when present. Then replace it with the
        // actual poster from a site adapter (XXXClub, torrent-pirat, MyPornClub).
        if(!item.poster.isEmpty())loadImage(view,item.poster,referer(item),item,true);
        if(prefetch && SiteDetails.supported(item))refreshMetadata(item,view);
    }
    private void refreshMetadata(Item item, ImageView image) {
        if(!SiteDetails.supported(item) || item.detailsFetched ||
            !pendingPosterMetadata.add(item))return;
        metadataWorker.execute(()->{
            try{
                SiteDetails.enrich(item);
                main.post(()->{
                    if(isFinishing())return;
                    if(image!=null && image.getTag()==item && !item.poster.isEmpty())
                        loadImage(image,item.poster,referer(item),item,false);
                });
            }finally{pendingPosterMetadata.remove(item);}
        });
    }
    private void loadImage(ImageView view,String link,String referer,Item item,boolean fallBackToDetails){
        loadImage(view,link,referer,item,fallBackToDetails,false);
    }
    private void loadImage(ImageView view,String link,String referrer,Item item,
                           boolean fallBackToDetails,boolean fullResolution) {
        if(link==null || (!link.startsWith("http://") && !link.startsWith("https://"))) {
            if(fallBackToDetails && item!=null)refreshMetadata(item,view);
            return;
        }
        final String cacheKey=(fullResolution?"full:":"poster:")+link;
        // Old thumbnail downloads must never overwrite a later full-size poster.
        view.setTag(IMAGE_REQUEST_TAG,cacheKey);
        Bitmap cached=pictureCache.get(cacheKey);
        if(cached!=null && !cached.isRecycled()){
            if(cacheKey.equals(view.getTag(IMAGE_REQUEST_TAG)) &&
                (item==null || view.getTag()==item))view.setImageBitmap(cached);
            return;
        }
        (fullResolution?fullImageWorker:imageWorker).execute(()->{
            HttpURLConnection connection=null;
            try{
                connection=(HttpURLConnection)new URL(link).openConnection();
                connection.setConnectTimeout(8500);
                connection.setReadTimeout(fullResolution?25000:13000);
                connection.setInstanceFollowRedirects(true);
                connection.setUseCaches(true);
                connection.setRequestProperty("User-Agent",SiteDetails.USER_AGENT);
                connection.setRequestProperty("Referer",referrer);
                connection.setRequestProperty("Accept","image/webp,image/jpeg,image/png,image/*;q=0.8");
                if(connection.getResponseCode()!=200)
                    throw new IllegalStateException("Изображение HTTP "+connection.getResponseCode());

                ByteArrayOutputStream buffer=new ByteArrayOutputStream();
                try(InputStream in=connection.getInputStream()){
                    byte[] part=new byte[16384];
                    int count;
                    while((count=in.read(part))!=-1){
                        buffer.write(part,0,count);
                        if(buffer.size()>20_000_000)
                            throw new IllegalStateException("Слишком большое изображение");
                    }
                }
                byte[] bytes=buffer.toByteArray();
                BitmapFactory.Options bounds=new BitmapFactory.Options();
                bounds.inJustDecodeBounds=true;
                BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
                int maxSide=fullResolution?3200:1000;
                int sample=1;
                while(Math.max(bounds.outWidth,bounds.outHeight)>maxSide*sample)
                    sample*=2;
                BitmapFactory.Options decode=new BitmapFactory.Options();
                decode.inSampleSize=sample;
                Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,decode);
                if(bitmap==null)throw new IllegalStateException("Формат изображения не поддерживается");
                // Fullscreen bitmaps may exceed the thumbnail cache; do not evict all posters.
                if(bitmap.getByteCount()<pictureCache.maxSize()/2)
                    pictureCache.put(cacheKey,bitmap);
                if(item!=null)item.imageError="";
                main.post(()->{
                    if(!isFinishing() &&
                       cacheKey.equals(view.getTag(IMAGE_REQUEST_TAG)) &&
                       (item==null || view.getTag()==item))
                        view.setImageBitmap(bitmap);
                });
            }catch(Exception error){
                if(item!=null)item.imageError=error(error);
                if(fallBackToDetails && item!=null)refreshMetadata(item,view);
            }finally{
                if(connection!=null)connection.disconnect();
            }
        });
    }
    private void showTileActions(View anchor,Item item){
        dismissCardActionPopup();
        LinearLayout body=column();
        body.setPadding(dp(7),dp(7),dp(7),dp(7));
        body.setBackground(MediaDesign.outlined(this,0xff101626,
            0xff603d8e,14,1));
        TextView playAction=contextAction("▶   Смотреть",true);
        TextView favoriteAction=contextAction(
            favorites.contains(favoriteRecord(item))?
                "★   Убрать из избранного":"☆   Добавить в избранное",false);
        body.addView(playAction,spaced(-1,47,0,5));
        body.addView(favoriteAction,lp(-1,47));
        PopupWindow popup=new PopupWindow(body,dp(compactTvPreview?230:258),
            dp(111),true);
        cardActionPopup=popup;
        popup.setBackgroundDrawable(MediaDesign.round(this,0xff101626,14));
        popup.setElevation(dp(14));
        popup.setOutsideTouchable(true);
        popup.setClippingEnabled(true);
        playAction.setOnClickListener(v->{
            dismissCardActionPopup();
            if(item.link().isEmpty() && SiteDetails.supported(item) &&
                !item.detailsFetched){
                metadataWorker.execute(()->{
                    SiteDetails.enrich(item);
                    main.post(()->{if(!isFinishing())play(item);});
                });
            }else play(item);
        });
        favoriteAction.setOnClickListener(v->{
            dismissCardActionPopup();
            boolean added=favorites.toggle(favoriteRecord(item));
            Toast.makeText(this,added?"Добавлено в избранное":"Удалено из избранного",
                Toast.LENGTH_SHORT).show();
            if(favoritesMode)openFavorites();
        });
        popup.showAsDropDown(anchor,0,-dp(2),Gravity.START);
        playAction.post(playAction::requestFocus);
    }
    private void details(Item item) {
        dismissCardActionPopup();
        cardReturnFocus=getCurrentFocus();
        cardReturnScroll=catalogScroll==null?0:catalogScroll.getScrollY();
        if(SiteDetails.supported(item) && !item.detailsFetched) {
            Toast.makeText(this,"Загружаем описание и кадры...",Toast.LENGTH_SHORT).show();
            metadataWorker.execute(()->{
                SiteDetails.enrich(item);
                main.post(()->{if(!isFinishing())showDetails(item);});
            });
        }else showDetails(item);
    }
    /**
     * TV-oriented detail page: poster, compact actions and release facts
     * together; screenshot preview to the right; technical MediaInfo collapsed.
     */
    private void showDetails(Item item) {
        LinearLayout page=column();
        page.setBackgroundColor(MediaDesign.BG);
        LinearLayout toolbar=line();
        toolbar.setPadding(dp(tvLayout?22:12),dp(8),dp(12),dp(8));
        Button back=button("‹  Назад");
        back.setTextSize(14);
        back.setOnClickListener(v->onBackPressed());
        toolbar.addView(back,spaced(tvLayout?120:93,43,12,0));
        TextView path=label("MediaShelf   /   "+item.tracker,14,MediaDesign.MUTED,false);
        path.setSingleLine(true);
        path.setEllipsize(TextUtils.TruncateAt.END);
        toolbar.addView(path,new LinearLayout.LayoutParams(0,dp(43),1));
        page.addView(toolbar,spaced(-1,59,0,0));

        View divider=new View(this);
        divider.setBackgroundColor(0xff30284a);
        page.addView(divider,lp(-1,1));
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root=column();
        root.setPadding(dp(tvLayout?20:13),dp(tvLayout?18:13),
                        dp(tvLayout?20:13),dp(30));
        root.setBackground(MediaDesign.gradient(this,
            new int[]{0xff24163d,0xff15172b,0xff080e1b},18));
        int screenDp=getResources().getConfiguration().screenWidthDp;
        int contentDp=tvLayout?Math.min(1160,screenDp-60):screenDp-12;
        android.widget.FrameLayout.LayoutParams body=new android.widget.FrameLayout.LayoutParams(
            dp(contentDp),-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        scroll.addView(root,body);

        boolean hasPhotos=!item.screenshots.isEmpty();
        // On normal TVs the gallery fits alongside the poster and title.
        // Narrow layouts put it directly below the title, before MediaInfo.
        boolean sideGallery=tvLayout && hasPhotos && contentDp>=780;
        int posterW=tvLayout?(sideGallery?185:218):106;
        int posterH=tvLayout?(sideGallery?254:296):148;
        int galleryW=sideGallery?Math.max(225,Math.min(302,contentDp/3)):0;
        LinearLayout header=line();
        header.setGravity(Gravity.TOP);
        header.setClipChildren(false);

        ImageView poster=new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackground(MediaDesign.gradient(this,
            new int[]{0xff304567,0xff1a2940},13));
        poster.setClipToOutline(true);
        poster.setTag(item);
        header.addView(poster,spaced(posterW,posterH,tvLayout?18:12,0));
        loadPoster(poster,item,false);

        LinearLayout facts=column();
        facts.addView(eyebrow("РАЗДАЧА  /  "+item.tracker.toUpperCase(Locale.ROOT)),
            spaced(-1,24,0,8));
        TextView title=label(item.title,tvLayout?24:18,MediaDesign.TEXT,true);
        title.setMaxLines(tvLayout?6:5);
        title.setEllipsize(TextUtils.TruncateAt.END);
        facts.addView(title,spaced(-1,-2,0,tvLayout?16:11));

        LinearLayout tags=line();
        String quality=MediaDesign.quality(item.title);
        if(!quality.isEmpty())
            tags.addView(MediaDesign.tag(this,quality,MediaDesign.ACCENT_LIGHT,
                0xff39245b),spaced(-2,-2,7,0));
        tags.addView(MediaDesign.tag(this,item.size(),MediaDesign.TEXT,0xff30466b),
            spaced(-2,-2,7,0));
        if(item.seeders>0)
            tags.addView(MediaDesign.tag(this,"● "+item.seeders+" сидов",
                MediaDesign.SUCCESS,0xff233a3f));
        facts.addView(tags,spaced(-1,36,0,13));
        TextView origin=label("ИСТОЧНИК  ·  "+item.tracker+"\n"+
            (item.date.isEmpty()?"":"ДОБАВЛЕНО  ·  "+item.date),
            tvLayout?13:11,MediaDesign.MUTED,false);
        origin.setMaxLines(3);
        facts.addView(origin,spaced(-1,-2,0,15));

        // One row inside the text column prevents the website action from
        // overlapping the poster and gives Play/Favorite the same dimensions.
        LinearLayout actions=line();
        actions.setClipChildren(false);
        actions.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        CenterIconButton watch=actionSymbol(R.drawable.ic_play_action,true);
        watch.setContentDescription("Смотреть");
        watch.setEnabled(!item.link().isEmpty());
        watch.setAlpha(watch.isEnabled()?1:0.55f);
        actions.addView(watch,spaced(tvLayout?54:49,44,0,0));

        JSONObject record=favoriteRecord(item);
        CenterIconButton bookmark=actionSymbol(favorites.contains(record)?
            R.drawable.ic_star_filled_action:R.drawable.ic_star_outline_action,false);
        bookmark.setContentDescription(favorites.contains(record)?
            "Убрать из избранного":"Добавить в избранное");
        bookmark.setTextColor(MediaDesign.ACCENT_LIGHT);
        actions.addView(bookmark,spaced(tvLayout?54:49,44,8,0));

        Button site=button("На сайте ↗");
        site.setGravity(Gravity.CENTER);
        site.setTextSize(tvLayout?12:11);
        site.setEnabled(!item.details.isEmpty());
        actions.addView(site,spaced(tvLayout?117:97,44,8,0));
        facts.addView(actions,spaced(-1,52,0,0));

        header.addView(facts,new LinearLayout.LayoutParams(0,-2,1));
        if(sideGallery)header.addView(
            buildDetailsGallery(item,galleryW),spaced(galleryW,-2,14,0));
        root.addView(header,spaced(-1,-2,0,18));
        if(hasPhotos && !sideGallery)
            root.addView(buildDetailsGallery(item,contentDp-(tvLayout?42:24)),
                spaced(-1,-2,0,15));

        View line=new View(this);
        line.setBackgroundColor(0xff40305a);
        root.addView(line,spaced(-1,1,0,12));
        Button technical=button("▸  О раздаче");
        technical.setTextSize(tvLayout?14:13);
        technical.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
        technical.setPadding(dp(15),0,dp(8),0);
        root.addView(technical,spaced(-1,44,0,6));
        TextView description=label(item.description.isEmpty()?
            "Дополнительные технические сведения пока не опубликованы.":
            readableMediaInfo(item.description),tvLayout?13:12,
            MediaDesign.MUTED,false);
        description.setLineSpacing(dp(3),1.05f);
        description.setPadding(dp(13),dp(10),dp(13),dp(11));
        description.setBackground(MediaDesign.round(this,0xff141e32,11));
        description.setVisibility(View.GONE);
        root.addView(description,spaced(-1,-2,0,8));
        technical.setOnClickListener(v->{
            boolean opening=description.getVisibility()!=View.VISIBLE;
            description.setVisibility(opening?View.VISIBLE:View.GONE);
            technical.setText(opening?"▾  О раздаче":"▸  О раздаче");
        });

        watch.setOnClickListener(v->play(item));
        bookmark.setOnClickListener(v->{
            boolean saved=favorites.toggle(favoriteRecord(item));
            bookmark.replaceIcon(getDrawable(saved?
                R.drawable.ic_star_filled_action:R.drawable.ic_star_outline_action));
            bookmark.setContentDescription(saved?
                "Убрать из избранного":"Добавить в избранное");
        });
        site.setOnClickListener(v->{
            if(item.details.isEmpty())return;
            try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(item.details)));}
            catch(Exception e){
                Toast.makeText(this,"Не удалось открыть сайт: "+error(e),
                    Toast.LENGTH_LONG).show();
            }
        });
        page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        detailsPage=page;
        fullPhotoPage=null;
        setContentView(page);
        if(tvLayout){
            Button focusTarget=watch.isEnabled()?watch:bookmark;
            focusTarget.post(focusTarget::requestFocus);
        }
    }

    private static String readableMediaInfo(String raw){
        if(raw==null)return "";
        return raw.replaceAll(
            "(?i)\\s+(Release Name|Screenshots|MediaInfo|File name|File size|Duration|Video|Audio|Resolution|Frame rate|Bitrate)\\s*:",
            "\n$1:").trim();
    }

    /** A remote-friendly photo navigator with preview directly on the movie page. */
    private LinearLayout buildDetailsGallery(Item item,int widthDp){
        LinearLayout gallery=column();
        gallery.setBackground(MediaDesign.round(this,0xff111a2b,13));
        gallery.setPadding(dp(8),dp(9),dp(8),dp(8));
        TextView headline=eyebrow("ДОПОЛНИТЕЛЬНЫЕ ФОТО");
        gallery.addView(headline,spaced(-1,25,0,5));
        ImageView preview=new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackground(MediaDesign.round(this,0xff09101f,12));
        preview.setClipToOutline(true);
        preview.setFocusable(true);
        preview.setClickable(true);
        final String[] selected={item.screenshots.get(0)};
        final int[] selectedPosition={0};
        int previewHeight=tvLayout?188:155;
        gallery.addView(preview,spaced(-1,previewHeight,0,7));

        TextView index=label("",12,MediaDesign.MUTED,false);
        index.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        gallery.addView(index,spaced(-1,19,0,5));
        Runnable update=()->{
            index.setText((selectedPosition[0]+1)+" / "+item.screenshots.size()+
                "   ·   OK — открыть");
            preview.setImageDrawable(null);
            loadImage(preview,selected[0],referer(item),null,false,true);
        };
        preview.setOnClickListener(v->showFullPhoto(item,selected[0]));
        preview.setOnFocusChangeListener((v,focused)->{
            preview.setForeground(focused?
                MediaDesign.outlined(this,0x00000000,MediaDesign.ACCENT_LIGHT,12,2):null);
        });
        android.widget.HorizontalScrollView strip=new android.widget.HorizontalScrollView(this);
        strip.setHorizontalScrollBarEnabled(false);
        strip.setClipToPadding(false);
        strip.setClipChildren(false);
        LinearLayout thumbs=line();
        thumbs.setClipChildren(false);
        int thumbWidth=Math.max(60,Math.min(79,(widthDp-18)/3));
        int thumbHeight=tvLayout?57:53;
        for(int i=0;i<item.screenshots.size();i++){
            final int position=i;
            final String url=item.screenshots.get(i);
            ImageView thumb=new ImageView(this);
            thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.setClipToOutline(true);
            thumb.setFocusable(true);
            thumb.setClickable(true);
            thumb.setContentDescription("Фото "+(i+1)+" из "+item.screenshots.size());
            thumb.setBackground(MediaDesign.round(this,0xff263149,8));
            thumbs.addView(thumb,spaced(thumbWidth,thumbHeight,6,0));
            loadImage(thumb,url,referer(item),null,false);
            thumb.setOnFocusChangeListener((v,focused)->{
                // The focus ring must be drawn OVER the photo, not behind it.
                thumb.setForeground(focused?
                    MediaDesign.outlined(this,0x00000000,MediaDesign.ACCENT_LIGHT,8,3):null);
                if(focused){
                    selected[0]=url;
                    selectedPosition[0]=position;
                    update.run();
                }
            });
            thumb.setOnClickListener(v->showFullPhoto(item,url));
        }
        strip.addView(thumbs);
        gallery.addView(strip,spaced(-1,thumbHeight+8,0,0));
        update.run();
        return gallery;
    }

    private void showFullPhoto(Item item,String photoUrl){
        galleryReturnFocus=getCurrentFocus();
        LinearLayout page=column();
        page.setBackgroundColor(0xff03050c);
        LinearLayout header=line();
        header.setPadding(dp(12),dp(5),dp(12),dp(5));
        Button back=button("‹  К фильму");
        back.setTextSize(13);
        back.setOnClickListener(v->onBackPressed());
        header.addView(back,spaced(140,39,10,0));
        TextView hint=label("Дополнительное фото  ·  Назад — вернуться",13,
            MediaDesign.MUTED,false);
        header.addView(hint,new LinearLayout.LayoutParams(0,dp(39),1));
        page.addView(header,spaced(-1,50,0,0));
        android.widget.FrameLayout imageHost=new android.widget.FrameLayout(this);
        TextView loading=label("Загрузка изображения в полном размере…",14,
            MediaDesign.MUTED,false);
        loading.setGravity(Gravity.CENTER);
        imageHost.addView(loading,new android.widget.FrameLayout.LayoutParams(-1,-1));
        ImageView full=new ImageView(this);
        full.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageHost.addView(full,new android.widget.FrameLayout.LayoutParams(-1,-1));
        page.addView(imageHost,new LinearLayout.LayoutParams(-1,0,1));
        fullPhotoPage=page;
        setContentView(page);
        loadImage(full,photoUrl,referer(item),null,false,true);
        back.requestFocus();
    }
    private static final class DownloadSelection {
        final String stream,name;
        final long size;
        DownloadSelection(String stream,String name,long size){
            this.stream=stream;this.name=name;this.size=size;
        }
    }
    private static final class PlaybackProgress {
        final AlertDialog dialog;
        final TextView stage,speed,peers;
        final ProgressBar progress;
        final AtomicBoolean active=new AtomicBoolean(true);
        PlaybackProgress(AlertDialog d,TextView stage,TextView speed,
                         TextView peers,ProgressBar progress){
            this.dialog=d;this.stage=stage;this.speed=speed;
            this.peers=peers;this.progress=progress;
        }
        void update(String title,String rate,String detail,int pct){
            stage.setText(title);
            speed.setText(rate);
            peers.setText(detail);
            progress.setIndeterminate(pct<0);
            if(pct>=0)progress.setProgress(Math.min(100,Math.max(0,pct)));
        }
        void close(){
            active.set(false);
            dialog.dismiss();
        }
    }
    private static String dataRate(double bytes){
        if(bytes<0)return "—";
        return String.format(Locale.US,"%.2f МБ/с (%.1f Мбит/с)",
            bytes/1048576.0,bytes*8.0/1000000.0);
    }
    private static String dataSize(long bytes){
        if(bytes<=0)return "—";
        return bytes>=1073741824L?
            String.format(Locale.US,"%.2f ГБ",bytes/1073741824.0):
            String.format(Locale.US,"%.0f МБ",bytes/1048576.0);
    }
    private PlaybackProgress progressWindow(String title){
        LinearLayout body=column();
        body.setPadding(dp(22),dp(9),dp(22),dp(16));
        TextView stage=label(title,16,MediaDesign.TEXT,true);
        body.addView(stage,spaced(-1,-2,0,10));
        ProgressBar progress=new ProgressBar(this,null,
            android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setMax(100);
        progress.setProgressTintList(
            android.content.res.ColorStateList.valueOf(MediaDesign.ACCENT));
        body.addView(progress,spaced(-1,11,0,12));
        TextView speed=label("Скорость: ожидаем соединение…",
            14,MediaDesign.ACCENT_LIGHT,false);
        body.addView(speed,spaced(-1,-2,0,8));
        TextView peers=label("TorrServer: получение данных",
            12,MediaDesign.MUTED,false);
        peers.setMaxLines(3);
        body.addView(peers,spaced(-1,-2,0,8));
        AlertDialog dialog=themedDialog().setTitle("MediaShelf · загрузка")
            .setView(body).setNegativeButton("Отмена",null).create();
        PlaybackProgress state=new PlaybackProgress(dialog,stage,speed,peers,progress);
        dialog.setOnShowListener(d->{
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->state.close());
        });
        dialog.show();
        return state;
    }
    private void play(Item item) {
        showSelectionDialog("Режим запуска",new String[]{
            "▶  Смотреть сразу",
            "▣  С предзагрузкой (рекомендуется)",
            "↓  Скачать полностью на устройство"
        },(d,choice)->prepareVideo(item,choice),"Отмена");
    }
    private void prepareVideo(Item item,int mode){
        String server=prefs.getString("torr_url","http://127.0.0.1:8090");
        PlaybackProgress state=progressWindow("Получаем список файлов через TorrServer…");
        worker.execute(()->{
            try{
                Torr.Opened opened=Torr.open(server,item);
                if(!state.active.get())return;
                main.post(()->{
                    if(!state.active.get()||isFinishing())return;
                    state.close();
                    if(opened.files.size()==1)
                        choosePlaybackFile(server,opened,opened.files.get(0),mode);
                    else{
                        String[] names=new String[opened.files.size()];
                        for(int i=0;i<names.length;i++)
                            names[i]=opened.files.get(i).path+"  ·  "+
                                dataSize(opened.files.get(i).size);
                        showSelectionDialog("Выбери видеофайл",names,
                            (d,n)->choosePlaybackFile(server,opened,
                                opened.files.get(n),mode),"Назад");
                    }
                });
            }catch(Exception e){
                main.post(()->{
                    if(!state.active.get()||isFinishing())return;
                    state.close();
                    themedDialog().setTitle("Ошибка TorrServer")
                        .setMessage(error(e)).setPositiveButton("ОК",null).show();
                });
            }
        });
    }
    private void choosePlaybackFile(String server,Torr.Opened opened,
                                    Torr.Video video,int mode){
        String stream=Torr.url(server,opened.hash,video);
        if(mode==0){launch(stream);return;}
        if(mode==2){
            String name=video.path.substring(video.path.lastIndexOf('/')+1);
            if(name.trim().isEmpty())name="MediaShelf-video.mp4";
            pendingDownload=new DownloadSelection(stream,name,video.size);
            Intent picker=new Intent(Intent.ACTION_CREATE_DOCUMENT);
            picker.addCategory(Intent.CATEGORY_OPENABLE);
            picker.setType("video/*");
            picker.putExtra(Intent.EXTRA_TITLE,name);
            picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try{startActivityForResult(picker,SAVE_VIDEO_REQUEST);}
            catch(ActivityNotFoundException e){
                pendingDownload=null;
                themedDialog().setTitle("Скачивание недоступно")
                    .setMessage("На телевизоре нет системного выбора папки. "+
                        "Подключи USB-накопитель или файловый менеджер "+
                        "с поддержкой Android Storage Access Framework.")
                    .setPositiveButton("ОК",null).show();
            }
            return;
        }
        PlaybackProgress state=progressWindow("Предзагрузка в буфер TorrServer…");
        // TorrServer's preload target is configured in the server, not in
        // individual streaming URLs. Display the real reported target.
        state.peers.setText("Объём буфера задаётся в TorrServer. "+
            "Скорость и процент обновляются автоматически.");
        state.dialog.setButton(AlertDialog.BUTTON_NEUTRAL,"Смотреть сейчас",
            (d,w)->{
                if(state.active.getAndSet(false))launch(stream);
            });
        AtomicBoolean finished=new AtomicBoolean(false);
        final String[] failed={""};
        worker.execute(()->{
            try{Torr.triggerPreload(stream);}
            catch(Exception e){failed[0]=error(e);}
            finally{finished.set(true);}
        });
        worker.execute(()->{
            final long start=android.os.SystemClock.elapsedRealtime();
            while(state.active.get() && !isFinishing() &&
                android.os.SystemClock.elapsedRealtime()-start<180000){
                try{
                    JSONObject status=Torr.status(server,opened.hash);
                    long size=status.optLong("preload_size",0);
                    long done=status.optLong("preloaded_bytes",0);
                    double rate=status.optDouble("download_speed",0);
                    int peers=status.optInt("active_peers",0);
                    int pct=size>0?(int)Math.min(100,done*100L/size):-1;
                    main.post(()->{
                        if(!state.active.get())return;
                        state.update("Предзагрузка: "+dataSize(done)+" / "+
                            (size>0?dataSize(size):"буфер TorrServer"),
                            "Скорость: "+dataRate(rate),
                            "Подключено пиров: "+peers+
                                (pct>=0?" · "+pct+"%":" · подготовка"),pct);
                    });
                    if(finished.get()){
                        if(!failed[0].isEmpty())throw new IOException(failed[0]);
                        main.post(()->{
                            if(state.active.get() && !isFinishing()){
                                state.close();
                                launch(stream);
                            }
                        });
                        return;
                    }
                    Thread.sleep(1100);
                }catch(InterruptedException interrupted){
                    Thread.currentThread().interrupt();
                    return;
                }catch(Exception failure){
                    final String problem=error(failure);
                    main.post(()->{
                        if(!state.active.get()||isFinishing())return;
                        state.close();
                        themedDialog().setTitle("Предзагрузка не удалась")
                            .setMessage(problem+"\nМожно начать воспроизведение сразу.")
                            .setPositiveButton("Смотреть сейчас",(d,w)->launch(stream))
                            .setNegativeButton("Закрыть",null).show();
                    });
                    return;
                }
            }
            if(state.active.get()){
                main.post(()->{
                    if(!state.active.get()||isFinishing())return;
                    state.close();
                    themedDialog().setTitle("Предзагрузка занимает слишком долго")
                        .setMessage("Скорость сети низкая. Смотреть без "+
                            "предзагрузки или повторить попытку?")
                        .setPositiveButton("Смотреть сейчас",(d,w)->launch(stream))
                        .setNegativeButton("Отмена",null).show();
                });
            }
        });
    }

    @Override protected void onActivityResult(int code,int result,Intent data){
        super.onActivityResult(code,result,data);
        if(code!=SAVE_VIDEO_REQUEST)return;
        DownloadSelection job=pendingDownload;
        pendingDownload=null;
        if(result!=Activity.RESULT_OK || data==null || data.getData()==null ||
            job==null)return;
        android.net.Uri destination=data.getData();
        startVideoDownload(job,destination);
    }
    private void startVideoDownload(DownloadSelection video,android.net.Uri destination){
        PlaybackProgress state=progressWindow("Скачиваем фильм на выбранный накопитель…");
        activeDownload=state.active;
        getWindow().addFlags(
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        worker.execute(()->{
            HttpURLConnection c=null;
            long transferred=0;
            boolean completed=false;
            try(OutputStream output=getContentResolver().openOutputStream(destination,"w")){
                if(output==null)throw new IOException("Нет доступа к выбранному файлу");
                c=(HttpURLConnection)new URL(video.stream).openConnection();
                activeDownloadConnection=c;
                c.setConnectTimeout(12000);
                c.setReadTimeout(45000);
                c.setRequestProperty("User-Agent",SiteDetails.USER_AGENT);
                int status=c.getResponseCode();
                if(status!=200 && status!=206)
                    throw new IOException("TorrServer HTTP "+status);
                final long length=video.size>0?video.size:c.getContentLengthLong();
                try(InputStream in=c.getInputStream()){
                    byte[] buffer=new byte[65536];
                    int n;
                    long tick=android.os.SystemClock.elapsedRealtime(),bytesInTick=0;
                    while(state.active.get() && (n=in.read(buffer))!=-1){
                        output.write(buffer,0,n);
                        transferred+=n;
                        bytesInTick+=n;
                        long now=android.os.SystemClock.elapsedRealtime();
                        if(now-tick>=900){
                            final long loaded=transferred;
                            final double rate=bytesInTick*1000.0/
                                Math.max(1,now-tick);
                            final int pct=length>0?(int)Math.min(100,
                                loaded*100L/length):-1;
                            main.post(()->{
                                if(state.active.get())
                                    state.update("Скачано "+dataSize(loaded)+
                                        (length>0?" / "+dataSize(length):""),
                                        "Скорость: "+dataRate(rate),
                                        "Сохраняется на выбранный накопитель "+
                                            (pct>=0?"· "+pct+"%":""),pct);
                            });
                            tick=now;bytesInTick=0;
                        }
                    }
                }
                output.flush();
                completed=state.active.get() &&
                    (length<=0 || transferred>=length);
                if(!completed && state.active.get())
                    throw new IOException("Получены не все данные фильма");
            }catch(Exception error){
                final String details=error(error);
                main.post(()->{
                    if(!isFinishing() && state.active.get())
                        themedDialog().setTitle("Скачивание прервано")
                            .setMessage(details).setPositiveButton("ОК",null).show();
                });
            }finally{
                if(c!=null)c.disconnect();
                activeDownloadConnection=null;
                getWindow().getDecorView().post(()->{
                    if(!isFinishing())getWindow().clearFlags(
                        android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                });
            }
            final boolean success=completed;
            main.post(()->{
                boolean wasOpen=state.active.get();
                state.close();
                if(!success){
                    // Avoid leaving a file that looks playable but is truncated.
                    try{
                        if(android.provider.DocumentsContract.isDocumentUri(this,destination))
                            android.provider.DocumentsContract.deleteDocument(
                                getContentResolver(),destination);
                    }catch(Exception ignored){}
                    return;
                }
                if(!isFinishing() && wasOpen)
                    themedDialog().setTitle("Скачивание завершено")
                        .setMessage("Фильм сохранён. Его можно смотреть без торрента.")
                        .setPositiveButton("Смотреть",(d,w)->{
                            Intent open=new Intent(Intent.ACTION_VIEW);
                            open.setDataAndType(destination,"video/*");
                            open.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            try{startActivity(open);}catch(Exception e){
                                Toast.makeText(this,"Не найден локальный видеоплеер",
                                    Toast.LENGTH_LONG).show();
                            }
                        }).setNegativeButton("Закрыть",null).show();
            });
        });
    }
    private void launch(String stream) {
        Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(Uri.parse(stream),"video/*");
        try {startActivity(i);}catch(ActivityNotFoundException e){
            themedDialog().setMessage("Установи видеоплеер, который принимает HTTP-видео (например VLC).").setPositiveButton("ОК",null).show();
        }
    }
    private EditText field(LinearLayout parent,String caption,String value,boolean secret) {
        TextView label=label(caption,14,MUTED,true);parent.addView(label);
        EditText field=new EditText(this);field.setText(value);field.setTextColor(WHITE);field.setSingleLine(true);
        field.setInputType(secret ? (InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD) : (InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI));
        field.setBackground(MediaDesign.outlined(this,0xff1a263b,0xff483376,12,1));
        field.setPadding(dp(14),0,dp(12),0);
        parent.addView(field,spaced(-1,49,0,10));return field;
    }
    private void settings(boolean first) {
        LinearLayout root=column();
        root.setPadding(dp(20),dp(12),dp(20),dp(18));
        ImageView brand=mediaShelfLogo();
        root.addView(brand,spaced(-1,tvLayout?98:80,0,9));
        TextView heading=label("ВНЕШНИЙ ВИД",15,MediaDesign.ACCENT_LIGHT,true);
        root.addView(heading,spaced(-1,27,0,3));
        TextView description=label("Количество постеров в одном ряду. Их размер подстроится автоматически под ширину телевизора.",13,MediaDesign.MUTED,false);
        root.addView(description,spaced(-1,-2,0,9));
        TextView amount=label("",15,MediaDesign.TEXT,true);
        root.addView(amount,spaced(-1,29,0,0));
        SeekBar columns=new SeekBar(this);
        columns.setMax(8); // 0 = automatic, 1...8 = 3...10 columns
        columns.setProgress(Math.max(0,Math.min(8,
            prefs.getInt("poster_columns",0)==0?0:prefs.getInt("poster_columns",0)-2)));
        columns.setProgressTintList(android.content.res.ColorStateList.valueOf(MediaDesign.ACCENT));
        columns.setThumbTintList(android.content.res.ColorStateList.valueOf(MediaDesign.ACCENT_LIGHT));
        columns.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){
                int count=progress==0?0:progress+2;
                amount.setText(count==0?"Размер: автоматически":"Плиток в ряд: "+count);
            }
            @Override public void onStartTrackingTouch(SeekBar bar){}
            @Override public void onStopTrackingTouch(SeekBar bar){}
        });
        amount.setText(columns.getProgress()==0?
            "Размер: автоматически":"Плиток в ряд: "+(columns.getProgress()+2));
        root.addView(columns,spaced(-1,42,0,7));
        Button automatic=button("Вернуть автоматический размер");
        automatic.setTextSize(12);
        automatic.setOnClickListener(v->columns.setProgress(0));
        root.addView(automatic,spaced(-1,42,0,14));
        TextView connectHead=label("ПОДКЛЮЧЕНИЯ",15,MediaDesign.ACCENT_LIGHT,true);
        root.addView(connectHead,spaced(-1,28,0,4));
        Button connect=button("TorrServer и Jackett  ›");
        connect.setTextSize(15);
        root.addView(connect,spaced(-1,48,0,7));
        Button source=button("Источники сайтов  ›");
        source.setTextSize(15);
        root.addView(source,spaced(-1,48,0,7));
        Button banner=button("Настройки баннера  ›");
        banner.setTextSize(15);
        root.addView(banner,spaced(-1,48,0,15));
        TextView version=label("MediaShelf 2.3 • Jackett необязателен для сайтов с прямым подключением.",
            12,MediaDesign.MUTED,false);
        version.setGravity(Gravity.CENTER);
        root.addView(version,spaced(-1,-2,0,6));
        ScrollView scroll=new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(root);
        AlertDialog dialog=themedDialog()
            .setTitle("Настройки")
            .setView(scroll)
            .setNegativeButton("Закрыть",null)
            .setPositiveButton("Применить",(d,w)->{
                int saved=columns.getProgress()==0?0:columns.getProgress()+2;
                prefs.edit().putInt("poster_columns",saved).apply();
                if(tvLayout && catalogScroll!=null && !catalogItems.isEmpty()){
                    int prev=catalogScroll.getScrollY();
                    show(catalogItems);
                    catalogScroll.post(()->catalogScroll.scrollTo(0,prev));
                }
            }).create();
        connect.setOnClickListener(v->{dialog.dismiss();connectionSettings(false);});
        source.setOnClickListener(v->{dialog.dismiss();chooseSource();});
        banner.setOnClickListener(v->{dialog.dismiss();configureBanner();});
        dialog.show();
    }

    private void connectionSettings(boolean first) {
        LinearLayout root=column();
        root.setPadding(dp(18),dp(10),dp(18),dp(10));
        EditText jackett=field(root,"Резервный Jackett (необязательно)",prefs.getString("jackett_url","http://192.168.50.2:9117"),false);
        EditText key=field(root,"API-ключ Jackett (необязательно)",prefs.getString("api_key",""),true);
        EditText torr=field(root,"TorrServer",prefs.getString("torr_url","http://127.0.0.1:8090"),false);
        TextView selected=label("XXXClub подключается напрямую. Jackett необязателен и используется только для источников, у которых ещё нет собственного модуля. \nУчётные данные сайтов не нужно вводить в MediaShelf до появления безопасного модуля входа.",13,MUTED,false);
        selected.setPadding(0,dp(15),0,dp(10));
        root.addView(selected);
        ScrollView scroll=new ScrollView(this);scroll.addView(root);
        AlertDialog.Builder b=themedDialog().setTitle("Подключения").setView(scroll)
            .setPositiveButton("Сохранить",(d,w)->{
                prefs.edit().putString("jackett_url",jackett.getText().toString().trim())
                    .putString("api_key",key.getText().toString().trim())
                    .putString("torr_url",torr.getText().toString().trim())
                    .putString("indexer",activeSource).apply();
                if(!key.getText().toString().trim().isEmpty())loadSources();
                load(lastQuery,lastSort);
            });
        if(!first)b.setNegativeButton("Отмена",null);
        b.show();
    }
    @Override protected void onDestroy() {
        dismissCardActionPopup();
        main.removeCallbacks(carouselAdvance);
        featuredHost=null;
        serial++;worker.shutdownNow();metadataWorker.shutdownNow();
        imageWorker.shutdownNow();fullImageWorker.shutdownNow();
        super.onDestroy();
    }
    private static String read(InputStream input) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] b=new byte[8192];int n;
        while((n=input.read(b))!=-1){out.write(b,0,n);if(out.size()>12_000_000)throw new IllegalStateException("Слишком большой ответ");}
        return out.toString(StandardCharsets.UTF_8.name());
    }
    static final class Item {
        final String title,tracker,details,date;
        volatile String magnet,download;
        volatile String poster,description,imageError="";
        volatile boolean detailsFetched=false;
        volatile java.util.List<String> screenshots=java.util.Collections.emptyList();
        final long bytes;final int seeders;
        Item(JSONObject j) {
            title=get(j,"Title","title");tracker=get(j,"Tracker","tracker");
            magnet=get(j,"MagnetUri","magnetUri");download=get(j,"Link","link");
            details=get(j,"Details","details");poster=get(j,"Poster","CoverUrl","coverurl");
            description=get(j,"Description","description");date=get(j,"PublishDate","publishDate");
            bytes=j.optLong("Size",0);seeders=j.optInt("Seeders",0);
        }
        String link(){return !magnet.isEmpty()?magnet:download;}
        String size() {return bytes>0 ? String.format(Locale.US,"%.2f ГБ",bytes/1073741824.0) : "?";}
        static String get(JSONObject j,String... keys) {
            for(String key:keys) {
                String val=j.optString(key,"");
                if(!val.isEmpty() && !val.equals("null"))return val;
            }
            return "";
        }
    }
    static final class Jackett {
        static final class SearchReport {
            final List<Item> items;
            final String warning;
            final List<SourceRegistry.Source> discovered;
            SearchReport(List<Item> items,String warning,List<SourceRegistry.Source> discovered){
                this.items=items;this.warning=warning;this.discovered=discovered;
            }
        }
        static SearchReport searchReport(String base,String key,String indexer,
                                         String query,int category,int timeoutMs) throws Exception {
            if(!base.startsWith("http://") && !base.startsWith("https://"))
                throw new IllegalArgumentException("Укажи адрес Jackett с http://");
            if(!indexer.matches("[a-zA-Z0-9_-]{1,80}"))
                throw new IllegalArgumentException("Неверный ID сайта");
            String uri=base.replaceAll("/+$","")+
                "/api/v2.0/indexers/"+Uri.encode(indexer)+"/results?apikey="+Uri.encode(key)+
                "&Query="+Uri.encode(query);
            if(category>0)uri+="&Category%5B%5D="+category;
            HttpURLConnection c=(HttpURLConnection)new URL(uri).openConnection();
            try {
                c.setConnectTimeout(Math.min(timeoutMs,10000));
                c.setReadTimeout(timeoutMs);
                c.setRequestProperty("Accept","application/json");
                int status=c.getResponseCode();
                if(status!=200) throw new IllegalStateException("Jackett HTTP "+status);
                JSONObject response;
                try(InputStream in=c.getInputStream()){response=new JSONObject(read(in));}
                JSONArray raw=response.optJSONArray("Results");
                if(raw==null)raw=response.optJSONArray("results");
                List<Item> out=new ArrayList<>();Set<String> seen=new HashSet<>();
                if(raw!=null) for(int i=0;i<raw.length();i++){
                    JSONObject itemJson=raw.optJSONObject(i);
                    if(itemJson==null)continue;
                    Item item=new Item(itemJson);
                    if(item.title.isEmpty())continue;
                    String id=!item.magnet.isEmpty()?item.magnet:item.title+"|"+item.bytes+"|"+item.tracker;
                    if(seen.add(id))out.add(item);
                }
                JSONArray indexers=response.optJSONArray("Indexers");
                if(indexers==null)indexers=response.optJSONArray("indexers");
                int ok=0,errors=0,unknown=0;
                List<String> failed=new ArrayList<>();
                List<SourceRegistry.Source> discovered=new ArrayList<>();
                if(indexers!=null)for(int i=0;i<indexers.length();i++){
                    JSONObject ind=indexers.optJSONObject(i);
                    if(ind==null)continue;
                    String sourceId=ind.optString("ID",ind.optString("id","")).trim();
                    String sourceName=ind.optString("Name",ind.optString("name",sourceId));
                    if(sourceId.matches("[a-zA-Z0-9_-]{1,80}") && !sourceId.equals("all"))
                        discovered.add(new SourceRegistry.Source(sourceId,sourceName,"","",
                                new ArrayList<SourceRegistry.Category>()));
                    int code=ind.optInt("Status",ind.optInt("status",0));
                    if(code==2)ok++;
                    else if(code==1){
                        errors++;
                        String name=ind.optString("Name",ind.optString("name","Сайт"));
                        failed.add(name);
                    }else unknown++;
                }
                String warning="";
                if(indexers!=null && indexers.length()>0){
                    warning="Сайтов ответило: "+ok+" / "+indexers.length();
                    if(errors>0) warning+=" · ошибки "+errors+" ("+
                        android.text.TextUtils.join(", ",failed.subList(0,Math.min(3,failed.size())))+
                        (errors>3?" …":"")+")";
                    if(unknown>0)warning+=" · статус неизвестен "+unknown;
                }else if(indexer.equals("all"))warning="Jackett не прислал состояние отдельных сайтов.";
                return new SearchReport(out,warning,discovered);
            }finally {c.disconnect();}
        }
    }
    static final class Torr {
        static final class Video {
            final int id;final String path;final long size;
            Video(int id,String path,long size){this.id=id;this.path=path;this.size=size;}
        }
        static final class Opened {
            final String hash;final List<Video> files;
            Opened(String hash,List<Video> files){this.hash=hash;this.files=files;}
        }
        static JSONObject post(String base,JSONObject j) throws Exception {
            HttpURLConnection c=(HttpURLConnection)new URL(base.replaceAll("/+$","")+"/torrents").openConnection();
            try {
                c.setConnectTimeout(9000);c.setReadTimeout(25000);c.setRequestMethod("POST");
                c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=UTF-8");
                try(OutputStream o=c.getOutputStream()){o.write(j.toString().getBytes(StandardCharsets.UTF_8));}
                if(c.getResponseCode()!=200)throw new IllegalStateException("TorrServer HTTP "+c.getResponseCode());
                try(InputStream in=c.getInputStream()){return new JSONObject(read(in));}
            } finally {c.disconnect();}
        }
        static Opened open(String base,Item item) throws Exception {
            if(!base.startsWith("http"))throw new IllegalArgumentException("Неверный адрес TorrServer");
            String link=item.link();if(link.isEmpty())throw new IllegalArgumentException("У раздачи нет magnet или torrent-ссылки");
            JSONObject data=post(base,new JSONObject().put("action","add").put("link",link).put("title",item.title).put("poster",item.poster));
            String hash=data.optString("hash","");
            if(hash.isEmpty())throw new IllegalStateException("Сервер не вернул хеш");
            List<Video> videos=videos(data.optJSONArray("file_stats"));
            for(int retry=0;retry<12 && videos.isEmpty();retry++){
                Thread.sleep(1400);
                JSONObject response=post(base,new JSONObject().put("action","get").put("hash",hash));
                videos=videos(response.optJSONArray("file_stats"));
            }
            if(videos.isEmpty())throw new IllegalStateException("Список видеофайлов не получен. Проверь сиды.");
            return new Opened(hash,videos);
        }
        static List<Video> videos(JSONArray arr){
            List<Video> result=new ArrayList<>();if(arr==null)return result;
            for(int n=0;n<arr.length();n++){
                JSONObject j=arr.optJSONObject(n);if(j==null)continue;
                String path=j.optString("path","").toLowerCase(Locale.ROOT);
                if(!(path.endsWith(".mp4")||path.endsWith(".mkv")||path.endsWith(".avi")||path.endsWith(".ts")||path.endsWith(".webm")||path.endsWith(".m4v")))continue;
                result.add(new Video(j.optInt("id",-1),j.optString("path",""),j.optLong("length",0)));
            }
            result.removeIf(f->f.id<0);
            result.sort(Comparator.comparingLong((Video f)->f.size).reversed());
            return result;
        }
        static String url(String base,String hash,Video f){
            String name=f.path.substring(f.path.lastIndexOf('/')+1);
            return base.replaceAll("/+$","")+"/stream/"+Uri.encode(name)+"?link="+Uri.encode(hash)+"&index="+f.id+"&play";
        }
    }
}
