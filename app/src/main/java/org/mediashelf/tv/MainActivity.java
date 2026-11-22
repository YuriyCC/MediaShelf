package org.mediashelf.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
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
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
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

public final class MainActivity extends Activity {
    private static final int BACK = MediaDesign.BG, PANEL = MediaDesign.SURFACE,
        WHITE = MediaDesign.TEXT, MUTED = MediaDesign.MUTED, BLUE = MediaDesign.ACCENT;
    // Source IDs are loaded from Jackett dynamically; "all" is the virtual combined source.
    private final List<SourceRegistry.Source> sources = new ArrayList<>();
    private final java.util.Map<String,String> probeResults = new java.util.concurrent.ConcurrentHashMap<>();
    private LinearLayout sourceButtons;
    private TextView sourceSummary;
    private Button sourceSelector;
    private boolean tvLayout;
    private int currentCategory = 0;
    private String currentCategoryLabel = "";
    private String activeSource = "xxxclub";
    private boolean loadingSources = false;
    private String sourceListError = "";
    private final List<Item> catalogItems = new ArrayList<>();
    private SitePages.Cursor sitePageCursor;
    private int visibleCount=60;
    private boolean loadingNextPage=false;
    private String pageInfo="";
    private ScrollView catalogScroll;
    private final ExecutorService worker = Executors.newFixedThreadPool(4);
    private final ExecutorService metadataWorker = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
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
            b.setBackground(f?MediaDesign.outlined(this,0xff314768,
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
        prefs = getSharedPreferences("local_settings",MODE_PRIVATE);
        activeSource=prefs.getString("indexer","xxxclub");
        sources.addAll(SourceRegistry.fromCache(prefs.getString("source_cache","[]")));
        if(activeSource.isEmpty())activeSource="xxxclub";
        ui();
        if (!prefs.getString("api_key","").isEmpty())loadSources();
        load("","latest");
    }
    private final List<Button> filterButtons=new ArrayList<>();
    private final List<Integer> filterCodes=new ArrayList<>();
    private final android.util.LruCache<String,Bitmap> pictureCache =
        new android.util.LruCache<String,Bitmap>(24*1024*1024){
            @Override protected int sizeOf(String key,Bitmap bitmap){return bitmap.getByteCount();}
        };
    private int focusItemAfterLoad=-1;

    private TextView eyebrow(String value){
        TextView t=label(value,11,MediaDesign.ACCENT_LIGHT,true);
        t.setLetterSpacing(0.14f);
        return t;
    }
    private Button accentButton(String name){
        Button b=button(name);
        b.setBackground(MediaDesign.gradient(this,
            new int[]{0xff7298ed,0xff5675c8},14));
        b.setTextColor(Color.WHITE);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setOnFocusChangeListener((v,focused)->{
            b.setBackground(focused?
                MediaDesign.outlined(this,0xff789ef7,Color.WHITE,14,2):
                MediaDesign.gradient(this,new int[]{0xff7298ed,0xff5675c8},14));
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
        tvLayout=(mode==android.content.res.Configuration.UI_MODE_TYPE_TELEVISION)||
            screenWidthDp>=680;
        getWindow().setStatusBarColor(MediaDesign.BG);
        getWindow().setNavigationBarColor(MediaDesign.BG);
        if(mode==android.content.res.Configuration.UI_MODE_TYPE_TELEVISION)
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        LinearLayout outer=line();
        outer.setGravity(Gravity.TOP);
        outer.setBackgroundColor(MediaDesign.BG);
        setContentView(outer);
        if(tvLayout){
            LinearLayout sidebar=column();
            sidebar.setPadding(dp(16),dp(23),dp(16),dp(18));
            sidebar.setBackground(MediaDesign.horizontal(this,
                new int[]{0xff141e31,0xff111725},0));
            LinearLayout brandRow=line();
            TextView brandLogo=label("▶",27,Color.WHITE,true);
            brandLogo.setGravity(Gravity.CENTER);
            brandLogo.setBackground(MediaDesign.gradient(this,
                new int[]{0xff7497e8,0xff425fbc},15));
            brandRow.addView(brandLogo,spaced(48,48,12,0));
            LinearLayout brandText=column();
            brandText.addView(label("MediaShelf",23,MediaDesign.TEXT,true));
            TextView sub=label("ТВОЯ МЕДИАТЕКА",10,MediaDesign.MUTED,true);
            sub.setLetterSpacing(0.13f);
            brandText.addView(sub);
            brandRow.addView(brandText);
            sidebar.addView(brandRow,spaced(-1,58,0,34));
            TextView section=eyebrow("НАВИГАЦИЯ");
            section.setPadding(dp(11),0,0,dp(11));
            sidebar.addView(section);
            Button home=button("▦   Весь каталог");
            home.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
            home.setPadding(dp(16),0,dp(14),0);
            home.setOnClickListener(v->selectSource("all"));
            sidebar.addView(home,spaced(-1,52,0,18));
            sourceSummary=eyebrow("ИСТОЧНИКИ");
            sourceSummary.setPadding(dp(11),0,0,dp(11));
            sidebar.addView(sourceSummary);
            ScrollView sourceScroll=new ScrollView(this);
            sourceScroll.setVerticalScrollBarEnabled(false);
            sourceScroll.setFillViewport(false);
            sourceButtons=column();
            sourceScroll.addView(sourceButtons);
            sidebar.addView(sourceScroll,new LinearLayout.LayoutParams(-1,0,1));
            View divider=new View(this);
            divider.setBackgroundColor(0xff2c3852);
            sidebar.addView(divider,spaced(-1,1,0,14));
            Button check=button("◈  Проверка сайтов");
            check.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
            check.setPadding(dp(14),0,0,0);
            check.setOnClickListener(v->diagnostics());
            sidebar.addView(check,spaced(-1,47,0,7));
            Button settings=button("⚙  Настройки");
            settings.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
            settings.setPadding(dp(14),0,0,0);
            settings.setOnClickListener(v->settings(false));
            sidebar.addView(settings,lp(-1,47));
            TextView foot=label("OK — открыть     ◀ — назад",10,0xff7585a4,false);
            foot.setPadding(dp(10),dp(19),0,0);
            sidebar.addView(foot);
            outer.addView(sidebar,lp(240,-1));
        }
        LinearLayout app=column();
        app.setPadding(dp(tvLayout?30:16),dp(tvLayout?23:14),dp(tvLayout?27:16),0);
        outer.addView(app,new LinearLayout.LayoutParams(0,-1,1));

        LinearLayout header=line();
        LinearLayout headerTitles=column();
        TextView trail=eyebrow(tvLayout?"ВЗРОСЛЫЙ КАТАЛОГ  /  MEDIASHELF":"MEDIASHELF  /  18+");
        headerTitles.addView(trail);
        TextView title=label(tvLayout?"Медиатека":"MediaShelf",tvLayout?30:24,
            MediaDesign.TEXT,true);
        title.setPadding(0,dp(1),0,0);
        headerTitles.addView(title);
        header.addView(headerTitles,new LinearLayout.LayoutParams(0,dp(tvLayout?70:65),1));
        sourceSelector=button("XXXClub  ▾");
        sourceSelector.setTextSize(tvLayout?15:13);
        sourceSelector.setOnClickListener(v->chooseSource());
        header.addView(sourceSelector,lp(tvLayout?176:116,47));
        if(!tvLayout){
            Button options=button("⋮");
            options.setOnClickListener(v->moreOptions());
            LinearLayout.LayoutParams op=lp(47,47);op.leftMargin=dp(7);
            header.addView(options,op);
        }
        app.addView(header);

        status=label("Выбери категорию или найди раздачу",13,MediaDesign.MUTED,false);
        status.setSingleLine(true);status.setEllipsize(TextUtils.TruncateAt.END);
        app.addView(status,spaced(-1,tvLayout?28:25,0,12));

        LinearLayout searchRow=line();
        queryInput=new EditText(this);
        queryInput.setTextColor(MediaDesign.TEXT);
        queryInput.setHintTextColor(0xff8d9dbb);
        queryInput.setSingleLine(true);
        queryInput.setHint("Название фильма, студии или темы");
        queryInput.setTextSize(tvLayout?18:15);
        queryInput.setBackground(MediaDesign.outlined(this,
            0xff1a263b,0xff324463,14,1));
        queryInput.setPadding(dp(17),0,dp(12),0);
        queryInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        queryInput.setOnFocusChangeListener((v,f)->queryInput.setBackground(
            MediaDesign.outlined(this,f?0xff243756:0xff1a263b,
                f?MediaDesign.ACCENT:0xff324463,14,f?2:1)));
        queryInput.setOnEditorActionListener((v,action,event)->{
            if(action==EditorInfo.IME_ACTION_SEARCH){
                currentCategory=0;currentCategoryLabel="";
                load(queryInput.getText().toString().trim(),"search");
                return true;
            }
            return false;
        });
        searchRow.addView(queryInput,new LinearLayout.LayoutParams(0,dp(tvLayout?57:52),1));
        Button go=accentButton("⌕  Найти");
        LinearLayout.LayoutParams gp=lp(tvLayout?124:94,tvLayout?57:52);
        gp.leftMargin=dp(10);
        searchRow.addView(go,gp);
        go.setOnClickListener(v->{
            currentCategory=0;currentCategoryLabel="";
            load(queryInput.getText().toString().trim(),"search");
        });
        app.addView(searchRow);

        LinearLayout tabs=line();
        String[] titles={"Новинки","Популярное","Все разделы",
            "Фильмы","720p","1080p","4K","Сборники","Фото","VR"};
        int[] codes={-1,-2,-3,6090,10001,10002,6045,6050,6060,6070};
        for(int i=0;i<titles.length;i++){
            final int code=codes[i];
            final String name=titles[i];
            Button chip=button(name);
            chip.setTextSize(tvLayout?13:12);
            chip.setMinHeight(0);chip.setMinimumHeight(0);
            chip.setPadding(dp(15),0,dp(15),0);
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
            tabs.addView(chip,spaced(-2,tvLayout?42:39,8,0));
            filterButtons.add(chip);filterCodes.add(code);
        }
        android.widget.HorizontalScrollView strip=new android.widget.HorizontalScrollView(this);
        strip.setHorizontalScrollBarEnabled(false);
        strip.setFillViewport(false);
        strip.addView(tabs);
        LinearLayout.LayoutParams strips=lp(-1,tvLayout?69:60);
        strips.topMargin=dp(6);
        app.addView(strip,strips);

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
                MediaDesign.outlined(this,0xff314b79,0xff668fe2,12,1):
                MediaDesign.round(this,0xff1a2436,12));
            chip.setOnFocusChangeListener((v,f)->{
                chip.setBackground(f?MediaDesign.outlined(this,0xff38547c,
                    MediaDesign.ACCENT,12,2):
                    chip.isSelected()?MediaDesign.outlined(this,0xff314b79,
                        0xff668fe2,12,1):
                        MediaDesign.round(this,0xff1a2436,12));
                MediaDesign.animateFocus(chip,f);
            });
        }
    }
    private void moreOptions() {
        String[] choices={"Выбор сайта","Категории сайта","Диагностика источников","Настройки"};
        new AlertDialog.Builder(this).setTitle("MediaShelf")
            .setItems(choices,(d,i)->{
                if(i==0)chooseSource();
                else if(i==1)categories();
                else if(i==2)diagnostics();
                else settings(false);
            }).show();
    }
    private void refreshSourceMenu() {
        SiteCatalog.Site current=SiteCatalog.byId(activeSource);
        String title=activeSource.equals("all")?"Все источники":
            current!=null?current.title:activeSource;
        if(sourceSelector!=null)sourceSelector.setText(title+"  ▾");
        if(sourceButtons==null)return;
        sourceButtons.removeAllViews();
        if(sourceSummary!=null)sourceSummary.setText("ИСТОЧНИКИ");
        for(SiteCatalog.Site site:SiteCatalog.all()){
            boolean selected=activeSource.equals(site.id);
            Button b=button((site.direct?"●  ":"○  ")+site.title);
            b.setTextSize(14);
            b.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
            b.setPadding(dp(15),0,0,0);
            b.setTextColor(selected?MediaDesign.TEXT:
                site.direct?0xffd6e0f2:0xff8292ae);
            b.setBackground(selected?
                MediaDesign.outlined(this,0xff2b4269,0xff567bc4,13,1):
                MediaDesign.round(this,MediaDesign.SIDEBAR,13));
            b.setOnFocusChangeListener((v,f)->{
                b.setBackground(f?
                    MediaDesign.outlined(this,0xff38527b,MediaDesign.ACCENT,13,2):
                    selected?
                        MediaDesign.outlined(this,0xff2b4269,0xff567bc4,13,1):
                        MediaDesign.round(this,MediaDesign.SIDEBAR,13));
                MediaDesign.animateFocus(b,f);
            });
            b.setContentDescription(site.title+", "+site.mode());
            b.setOnClickListener(v->selectSource(site.id));
            sourceButtons.addView(b,spaced(-1,49,0,5));
        }
        Button all=button("▦  Все подключённые");
        all.setTextSize(13);
        all.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
        all.setPadding(dp(15),0,0,0);
        all.setOnClickListener(v->selectSource("all"));
        sourceButtons.addView(all,spaced(-1,48,0,5));
        Button add=button("+  Управление сайтами");
        add.setTextSize(13);
        add.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
        add.setPadding(dp(15),0,0,0);
        add.setOnClickListener(v->chooseSource());
        sourceButtons.addView(add,lp(-1,48));
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
        new AlertDialog.Builder(this).setTitle("Каталог источников 18+")
            .setItems(titles.toArray(new String[0]),(dialog,index)->{
                String id=ids.get(index);
                if(id.equals("__manual"))manualSource();
                else selectSource(id);
            }).setNegativeButton("Закрыть",null).show();
    }
    private void manualSource() {
        EditText field=new EditText(this);
        field.setSingleLine(true);
        field.setTextColor(WHITE);
        field.setHint("например: mypornclub");
        field.setHintTextColor(MUTED);
        field.setText(activeSource.equals("all")?"":activeSource);
        field.setSelectAllOnFocus(true);
        LinearLayout wrapper=column();
        wrapper.setPadding(dp(22),0,dp(22),0);
        wrapper.addView(field);
        AlertDialog dialog=new AlertDialog.Builder(this)
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
                new AlertDialog.Builder(this).setTitle(site.title)
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
            new AlertDialog.Builder(this).setTitle("Разделы · "+selected.title)
                .setMessage("Собственный модуль сайта ещё не готов. "+
                    "После его разработки здесь появятся реальные разделы сайта. "+
                    "Временный просмотр через Jackett требует настройки индексатора.")
                .setPositiveButton("Закрыть",null).show();
            return;
        }
        if(activeSource.equals("all")) {
            new AlertDialog.Builder(this).setTitle("Категории")
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
                        new AlertDialog.Builder(this).setTitle("Категории "+id)
                            .setMessage("Сайт отвечает, но Jackett не объявил отдельных категорий. Это не значит, что на самом сайте их нет: для него потребуется отдельный модуль каталога.")
                            .setPositiveButton("Закрыть",null).show();
                        return;
                    }
                    showCategoryDialog(id,cats,false);
                });
            }catch(Exception e){
                main.post(()->{
                    if(isFinishing()||!activeSource.equals(id))return;
                    new AlertDialog.Builder(this).setTitle("Категории "+id)
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
        new AlertDialog.Builder(this).setTitle(title)
            .setItems(labels.toArray(new String[0]),(d,pos)->{
                if(pos==labels.size()-1) {
                    if(adapter) {
                        new AlertDialog.Builder(this).setTitle("Категории")
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
            }).setNegativeButton("Назад",null).show();
    }
    private void diagnostics() {
        final List<String> titles=new ArrayList<>();
        final List<String> ids=new ArrayList<>();
        for(SiteCatalog.Site site:SiteCatalog.all()) {
            titles.add(site.title+" · "+(site.direct?"прямой модуль":"через Jackett"));
            ids.add(site.id);
        }
        // Android AlertDialog does not reliably combine message + item list.
        new AlertDialog.Builder(this).setTitle("Источники для диагностики")
            .setItems(titles.toArray(new String[0]),(d,i)->{
                String id=ids.get(i);
                if(id.equals("xxxclub"))probeNativeXxxclub();
                else probeSource(new SourceRegistry.Source(id,
                    SiteCatalog.byId(id).title,SiteCatalog.byId(id).address,
                    "",new ArrayList<SourceRegistry.Category>()));
            }).setNegativeButton("Закрыть",null).show();
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
                new AlertDialog.Builder(this).setTitle("XXXClub · диагностика")
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
                new AlertDialog.Builder(this).setTitle(source.name).setMessage(result)
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
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle("Диагностика всех сайтов")
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
    private void message(String m) {
        cards.removeAllViews();
        TextView t=label(m,17,MUTED,false);t.setPadding(dp(8),dp(20),dp(8),dp(20));
        cards.addView(t);
    }
    private void load(String q,String order) {
        lastQuery=q;
        lastSort=order;
        final String source=activeSource;
        final int category=currentCategory;
        final int requestId=++serial;
        if(catalogScroll!=null)catalogScroll.scrollTo(0,0);
        SiteCatalog.Site configuredSite=SiteCatalog.byId(source);
        String friendly=source.equals("all")?"Все подключённые":
            configuredSite!=null?configuredSite.title:source;
        String section=order.equals("latest")?"Новинки":order.equals("popular")?"Популярное":"Поиск: "+q;
        status.setText(section+" · "+friendly+
            (category>0?" · "+currentCategoryLabel:""));
        catalogItems.clear();sitePageCursor=null;loadingNextPage=false;visibleCount=60;
        pageInfo="";
        message("Идёт поиск…");
        final String api=prefs.getString("api_key","");
        final String base=prefs.getString("jackett_url","http://192.168.50.2:9117");

        NativeSource direct=NativeSources.get(source.equals("all")?"xxxclub":source);
        if(direct!=null) {
            worker.execute(()->{
                SitePages.Cursor cursor=direct.begin(q,source.equals("all")?0:category);
                try {
                    SitePages.Page first=direct.fetchNext(cursor,source.equals("all")?0:category);
                    List<Item> results=new ArrayList<>(first.items);
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
                        Jackett.SearchReport bridged=Jackett.searchReport(base,api,"xxxclub",q,
                                source.equals("all")?0:category,25000);
                        List<Item> results=new ArrayList<>(bridged.items);
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
                Jackett.SearchReport report=Jackett.searchReport(base,api,source,q,category,35000);
                List<Item> results=new ArrayList<>(report.items);
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
        int y=catalogScroll==null?0:catalogScroll.getScrollY();
        cards.removeAllViews();
        TextView count=label(
            "Получено: "+items.size()+" · показано: "+Math.min(items.size(),visibleCount)+
            (sitePageCursor==null?"":" · страницы XXXClub: "+sitePageCursor.pageIndex),
            13,MUTED,false);
        count.setPadding(0,0,0,dp(12));cards.addView(count);
        if(!pageInfo.isEmpty()) {
            TextView note=label(pageInfo,12,MUTED,false);
            note.setPadding(0,0,0,dp(10));
            cards.addView(note);
        }
        if(items.isEmpty() && sitePageCursor==null) {
            TextView empty=label("Поиск не вернул результатов. Попробуй другой запрос или источник.",15,MUTED,false);
            cards.addView(empty);
            return;
        }
        int usableDp=getResources().getConfiguration().screenWidthDp-(tvLayout?280:34);
        int cols=tvLayout ? Math.max(3,Math.min(6,Math.max(480,usableDp)/200)) : 2;
        int cardW=Math.max(130,(usableDp-(cols-1)*10)/cols);
        GridLayout grid=new GridLayout(this);
        grid.setColumnCount(cols);
        cards.addView(grid);
        int limit=Math.min(visibleCount,items.size());
        for(int i=0;i<limit;i++) {
            Item item=items.get(i);
            LinearLayout tile=column();
            tile.setBackground(panel(PANEL,false));tile.setPadding(dp(6),dp(6),dp(6),dp(8));
            tile.setClickable(true);tile.setFocusable(true);
            tile.setOnFocusChangeListener((v,f)->v.setBackground(panel(f?0xff34415c:PANEL,f)));
            android.widget.FrameLayout cover=new android.widget.FrameLayout(this);
            cover.setBackground(panel(0xff34425e,false));
            TextView noCover=label("Обложка не получена",12,MUTED,false);
            noCover.setGravity(Gravity.CENTER);
            cover.addView(noCover,new android.widget.FrameLayout.LayoutParams(-1,-1));
            ImageView img=new ImageView(this);img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cover.addView(img,new android.widget.FrameLayout.LayoutParams(-1,-1));
            tile.addView(cover,lp(-1,tvLayout?166:132));
            loadPoster(img,item,i<25);
            TextView name=label(item.title,13,WHITE,true);
            name.setMaxLines(3);name.setEllipsize(TextUtils.TruncateAt.END);
            name.setPadding(dp(5),dp(7),dp(5),0);
            tile.addView(name,new LinearLayout.LayoutParams(-1,0,1));
            TextView meta=label(item.tracker+" · "+item.size()+" · сиды "+item.seeders,11,MUTED,false);
            meta.setSingleLine(true);meta.setEllipsize(TextUtils.TruncateAt.END);
            tile.addView(meta);
            tile.setOnClickListener(v->details(item));
            tile.setOnKeyListener((v,key,event)->{
                if(event.getAction()==android.view.KeyEvent.ACTION_DOWN &&
                    (key==android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                     key==android.view.KeyEvent.KEYCODE_ENTER)){
                    details(item);return true;
                }
                return false;
            });
            GridLayout.LayoutParams p=new GridLayout.LayoutParams(
                GridLayout.spec(i/cols),GridLayout.spec(i%cols));
            p.width=dp(cardW);p.height=dp(tvLayout?270:232);
            p.rightMargin=dp(10);p.bottomMargin=dp(10);
            grid.addView(tile,p);
        }
        if(items.size()>limit){
            Button reveal=button("Показать ещё "+Math.min(60,items.size()-limit)+" из уже полученных");
            reveal.setOnClickListener(v->{
                visibleCount+=60;
                show(catalogItems);
            });
            cards.addView(reveal,lp(-1,52));
        }
        if(sitePageCursor!=null){
            if(!sitePageCursor.finished){
                Button more=button(loadingNextPage?"Загрузка страницы…":"Загрузить ещё с XXXClub");
                more.setEnabled(!loadingNextPage);
                more.setOnClickListener(v->loadNextPage());
                cards.addView(more,lp(-1,56));
            }else {
                TextView end=label("Дальнейшие страницы не обнаружены на сайте. Это не гарантирует, что фильмов больше нет.",12,MUTED,false);
                cards.addView(end);
            }
        }
        if(catalogScroll!=null)catalogScroll.post(()->catalogScroll.scrollTo(0,y));
    }
    private static String catalogKey(Item item){
        if(item.details!=null && !item.details.isEmpty()){
            String clean=item.details.replaceFirst("/+$","").toLowerCase(Locale.ROOT);
            return clean;
        }
        return item.title.toLowerCase(Locale.ROOT)+"|"+item.bytes;
    }
    private void loadNextPage(){
        if(loadingNextPage || sitePageCursor==null || sitePageCursor.finished)return;
        loadingNextPage=true;
        int job=serial;
        final SitePages.Cursor cursor=sitePageCursor;
        final int category=currentCategory;
        final Set<String> existing=new HashSet<>();
        for(Item item:catalogItems)existing.add(catalogKey(item));
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
                        if(existing.add(catalogKey(item)))additional.add(item);
                    }
                    if(!additional.isEmpty())break;
                }
                final String outcome=last;
                main.post(()->{
                    if(isFinishing() || job!=serial || sitePageCursor!=cursor)return;
                    loadingNextPage=false;
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
        if(!SiteDetails.supported(item) || item.detailsFetched) return;
        metadataWorker.execute(() -> {
            SiteDetails.enrich(item);
            main.post(() -> {
                if(isFinishing())return;
                if(image != null && image.getTag()==item && !item.poster.isEmpty())
                    loadImage(image,item.poster,referer(item),item,false);
            });
        });
    }
    private void loadImage(ImageView view,String link,String referer,Item item,boolean fallBackToDetails) {
        if(!link.startsWith("http://") && !link.startsWith("https://")) {
            if(fallBackToDetails && item!=null)refreshMetadata(item,view);
            return;
        }
        worker.execute(()->{
            HttpURLConnection c=null;
            try {
                c=(HttpURLConnection)new URL(link).openConnection();
                c.setConnectTimeout(9000);c.setReadTimeout(14000);
                c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent",SiteDetails.USER_AGENT);
                c.setRequestProperty("Referer",referer);
                c.setRequestProperty("Accept","image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
                int status=c.getResponseCode();
                if(status!=200)throw new IllegalStateException("Изображение HTTP "+status);
                BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=2;
                Bitmap bitmap;
                try(InputStream in=c.getInputStream()) { bitmap=BitmapFactory.decodeStream(in,null,opts); }
                if(bitmap==null)throw new IllegalStateException("Неверный формат изображения");
                if(item!=null)item.imageError="";
                main.post(()->{if(!isFinishing() && (item==null || view.getTag()==item))view.setImageBitmap(bitmap);});
            }catch(Exception e) {
                if(item!=null)item.imageError=error(e);
                if(fallBackToDetails && item!=null)refreshMetadata(item,view);
            }finally{if(c!=null)c.disconnect();}
        });
    }
    private void details(Item item) {
        if(SiteDetails.supported(item) && !item.detailsFetched) {
            Toast.makeText(this,"Загружаем описание и кадры...",Toast.LENGTH_SHORT).show();
            metadataWorker.execute(()->{
                SiteDetails.enrich(item);
                main.post(()->{if(!isFinishing())showDetails(item);});
            });
        } else showDetails(item);
    }
    private void showDetails(Item item) {
        LinearLayout stack=column();
        stack.setPadding(dp(16),dp(8),dp(16),dp(15));
        String info="Источник: "+item.tracker+"\\nРазмер: "+item.size()+"\\nСиды: "+item.seeders+"\\nДата: "+item.date;
        TextView facts=label(info,14,WHITE,false);stack.addView(facts);
        TextView description=label(item.description.isEmpty()?"Описание на сайте пока не получено.":item.description,14,MUTED,false);
        description.setPadding(0,dp(16),0,dp(12));stack.addView(description);
        if(item.screenshots.size()>0) {
            TextView header=label("Кадры из фильма",16,WHITE,true);
            stack.addView(header);
            android.widget.HorizontalScrollView gallery=new android.widget.HorizontalScrollView(this);
            LinearLayout row=line();
            for(String pic:item.screenshots) {
                ImageView frame=new ImageView(this);
                frame.setScaleType(ImageView.ScaleType.CENTER_CROP);
                frame.setBackground(panel(PANEL,false));
                LinearLayout.LayoutParams p=lp(142,108);
                p.setMargins(0,dp(8),dp(9),dp(8));
                row.addView(frame,p);
                loadImage(frame,pic,referer(item),null,false);
                frame.setOnClickListener(v->{
                    ImageView full=new ImageView(this);
                    full.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    loadImage(full,pic,referer(item),null,false);
                    new AlertDialog.Builder(this).setView(full).setPositiveButton("Закрыть",null).show();
                });
            }
            gallery.addView(row);
            stack.addView(gallery);
        }
        if(!item.imageError.isEmpty()) {
            TextView diagnostic=label("Диагностика обложки: "+item.imageError,12,MUTED,false);
            stack.addView(diagnostic);
        }
        ScrollView scroll=new ScrollView(this);scroll.addView(stack);
        AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle(item.title).setView(scroll)
                .setNegativeButton("Назад",null);
        if(!item.link().isEmpty())b.setPositiveButton("Смотреть",(d,w)->play(item));
        if(!item.details.isEmpty())b.setNeutralButton("Страница",(d,w)->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(item.details))));
        b.show();
    }
    private void play(Item item) {
        String server=prefs.getString("torr_url","http://127.0.0.1:8090");
        Toast.makeText(this,"Получаем файлы через TorrServer...",Toast.LENGTH_LONG).show();
        worker.execute(()->{
            try {
                Torr.Opened opened=Torr.open(server,item);
                main.post(()->{
                    if(isFinishing())return;
                    if(opened.files.size()==1){launch(Torr.url(server,opened.hash,opened.files.get(0)));return;}
                    String[] names=new String[opened.files.size()];
                    for(int i=0;i<names.length;i++)names[i]=opened.files.get(i).path;
                    new AlertDialog.Builder(this).setTitle("Выбери видеофайл")
                        .setItems(names,(d,n)->launch(Torr.url(server,opened.hash,opened.files.get(n))))
                        .setNegativeButton("Отмена",null).show();
                });
            }catch(Exception e){
                main.post(()->{if(!isFinishing())new AlertDialog.Builder(this).setTitle("Ошибка TorrServer").setMessage(error(e)).setPositiveButton("ОК",null).show();});
            }
        });
    }
    private void launch(String stream) {
        Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(Uri.parse(stream),"video/*");
        try {startActivity(i);}catch(ActivityNotFoundException e){
            new AlertDialog.Builder(this).setMessage("Установи видеоплеер, который принимает HTTP-видео (например VLC).").setPositiveButton("ОК",null).show();
        }
    }
    private EditText field(LinearLayout parent,String caption,String value,boolean secret) {
        TextView label=label(caption,14,MUTED,true);parent.addView(label);
        EditText field=new EditText(this);field.setText(value);field.setTextColor(WHITE);field.setSingleLine(true);
        field.setInputType(secret ? (InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD) : (InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI));
        parent.addView(field,lp(-1,49));return field;
    }
    private void settings(boolean first) {
        LinearLayout root=column();
        root.setPadding(dp(18),dp(10),dp(18),dp(10));
        EditText jackett=field(root,"Резервный Jackett (необязательно)",prefs.getString("jackett_url","http://192.168.50.2:9117"),false);
        EditText key=field(root,"API-ключ Jackett (необязательно)",prefs.getString("api_key",""),true);
        EditText torr=field(root,"TorrServer",prefs.getString("torr_url","http://127.0.0.1:8090"),false);
        TextView selected=label("XXXClub подключается напрямую. Jackett необязателен и используется только для источников, у которых ещё нет собственного модуля. \nУчётные данные сайтов не нужно вводить в MediaShelf до появления безопасного модуля входа.",13,MUTED,false);
        selected.setPadding(0,dp(15),0,dp(10));
        root.addView(selected);
        ScrollView scroll=new ScrollView(this);scroll.addView(root);
        AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle("Подключения").setView(scroll)
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
    @Override protected void onDestroy() {serial++;worker.shutdownNow();metadataWorker.shutdownNow();super.onDestroy();}
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
