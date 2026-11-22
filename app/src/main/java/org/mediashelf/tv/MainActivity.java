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
    private static final int BACK = 0xff10131a, PANEL = 0xff202738, WHITE = 0xfff0f3fa, MUTED = 0xffacb6c9, BLUE = 0xff6699ff;
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
        d.setColor(bg); d.setCornerRadius(dp(11));
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
        b.setBackground(panel(PANEL,false));
        b.setFocusable(true);
        b.setOnFocusChangeListener((v,f) -> v.setBackground(panel(f ? 0xff34415c : PANEL,f)));
        return b;
    }
    private LinearLayout.LayoutParams lp(int w,int h) {
        return new LinearLayout.LayoutParams(w < 0 ? w : dp(w),h < 0 ? h : dp(h));
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        prefs = getSharedPreferences("local_settings",MODE_PRIVATE);
        activeSource=prefs.getString("indexer","xxxclub");
        ui();
        if (prefs.getString("api_key","").isEmpty()) settings(true);
        else { loadSources(); load("","latest"); }
    }
    private void ui() {
        int screenWidthDp=getResources().getConfiguration().screenWidthDp;
        tvLayout=screenWidthDp>=680;
        LinearLayout outer=line();
        outer.setBackgroundColor(BACK);
        outer.setGravity(Gravity.TOP);
        setContentView(outer);
        if(tvLayout) {
            LinearLayout sidebar=column();
            sidebar.setBackgroundColor(0xff151a25);
            sidebar.setPadding(dp(12),dp(22),dp(12),dp(10));
            TextView brand=label("MediaShelf",24,WHITE,true);
            brand.setPadding(dp(6),0,0,dp(17));
            sidebar.addView(brand);
            Button all=button("Все сайты");
            all.setOnClickListener(v->selectSource("all"));
            sidebar.addView(all,lp(-1,46));
            sourceSummary=label("Загрузка источников...",12,MUTED,false);
            sourceSummary.setPadding(dp(7),dp(17),0,dp(8));
            sidebar.addView(sourceSummary);
            ScrollView sourceScroll=new ScrollView(this);
            sourceScroll.setVerticalScrollBarEnabled(false);
            sourceButtons=column();
            sourceScroll.addView(sourceButtons);
            sidebar.addView(sourceScroll,new LinearLayout.LayoutParams(-1,0,1));
            Button check=button("Диагностика");
            check.setOnClickListener(v->diagnostics());
            sidebar.addView(check,lp(-1,44));
            Button settings=button("Настройки");
            settings.setOnClickListener(v->settings(false));
            sidebar.addView(settings,lp(-1,44));
            outer.addView(sidebar,lp(218,-1));
        }
        LinearLayout app=column();
        app.setPadding(dp(tvLayout?24:14),dp(tvLayout?20:10),dp(tvLayout?24:14),0);
        outer.addView(app,new LinearLayout.LayoutParams(0,-1,1));
        LinearLayout header=line();
        if(!tvLayout) {
            TextView title=label("MediaShelf",24,WHITE,true);
            header.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
        } else {
            TextView title=label("Каталог фильмов",26,WHITE,true);
            header.addView(title,new LinearLayout.LayoutParams(0,dp(54),1));
        }
        sourceSelector=button("Источник: "+activeSource);
        sourceSelector.setOnClickListener(v->chooseSource());
        header.addView(sourceSelector,lp(tvLayout?220:172,48));
        if(!tvLayout) {
            Button options=button("⋮");
            options.setOnClickListener(v->moreOptions());
            LinearLayout.LayoutParams op=lp(48,48);op.leftMargin=dp(7);
            header.addView(options,op);
        }
        app.addView(header);
        status=label("Подключение к Jackett...",14,MUTED,false);
        app.addView(status,lp(-1,42));
        LinearLayout searchRow=line();
        queryInput=new EditText(this);
        queryInput.setTextColor(WHITE);
        queryInput.setHintTextColor(MUTED);
        queryInput.setSingleLine(true);
        queryInput.setHint("Поиск фильма, темы или студии");
        queryInput.setTextSize(tvLayout?19:16);
        queryInput.setBackground(panel(PANEL,false));
        queryInput.setPadding(dp(12),0,dp(12),0);
        queryInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        queryInput.setOnEditorActionListener((v,action,event)->{
            if(action==EditorInfo.IME_ACTION_SEARCH) {
                currentCategory=0;currentCategoryLabel="";
                load(queryInput.getText().toString().trim(),"search");
                return true;
            }
            return false;
        });
        searchRow.addView(queryInput,new LinearLayout.LayoutParams(0,dp(50),1));
        Button go=button("Найти");
        LinearLayout.LayoutParams gp=lp(tvLayout?112:85,50);
        gp.leftMargin=dp(10);
        searchRow.addView(go,gp);
        go.setOnClickListener(v->{
            currentCategory=0;currentCategoryLabel="";
            load(queryInput.getText().toString().trim(),"search");
        });
        app.addView(searchRow);
        LinearLayout tabs=line();
        String[] titles={"Новинки","Популярное","Категории сайта","Gangbang","Double","Fisting"};
        for(String text:titles) {
            Button button=button(text);
            LinearLayout.LayoutParams p=lp(-2,44);
            p.setMargins(0,dp(10),dp(8),dp(10));
            tabs.addView(button,p);
            button.setOnClickListener(v->{
                if(text.equals("Категории сайта")) {categories();return;}
                currentCategory=0;currentCategoryLabel="";
                String q=text.equals("Новинки")||text.equals("Популярное")?"":text;
                String sort=text.equals("Новинки")?"latest":text.equals("Популярное")?"popular":"search";
                queryInput.setText(q);
                load(q,sort);
            });
        }
        android.widget.HorizontalScrollView strip=new android.widget.HorizontalScrollView(this);
        strip.setHorizontalScrollBarEnabled(false);
        strip.addView(tabs);
        app.addView(strip);
        ScrollView results=new ScrollView(this);
        results.setFillViewport(true);
        cards=column();
        cards.setPadding(0,dp(12),0,dp(28));
        results.addView(cards);
        app.addView(results,new LinearLayout.LayoutParams(-1,0,1));
        refreshSourceMenu();
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
        String title=activeSource.equals("all")?"Все сайты":activeSource;
        SourceRegistry.Source active=SourceRegistry.byId(sources,activeSource);
        if(active!=null)title=active.name;
        if(sourceSelector!=null)sourceSelector.setText(title+"  ▾");
        if(sourceButtons==null)return;
        sourceButtons.removeAllViews();
        if(sourceSummary!=null)sourceSummary.setText(sources.isEmpty()
            ? "Список пока не загружен" : "Источники · "+sources.size());
        for(SourceRegistry.Source src:sources) {
            String text=src.name;
            String previous=probeResults.get(src.id);
            if(previous!=null)text+=(previous.startsWith("OK")?" ✓":" !");
            else if(!src.lastError.isEmpty())text+=" · ?";
            Button b=button(text);
            b.setTextSize(12);
            if(activeSource.equals(src.id))b.setTextColor(0xffa8c6ff);
            b.setOnClickListener(v->selectSource(src.id));
            sourceButtons.addView(b,lp(-1,43));
        }
    }
    private void loadSources() {
        if(loadingSources)return;
        String key=prefs.getString("api_key","");
        if(key.isEmpty())return;
        loadingSources=true;
        String address=prefs.getString("jackett_url","http://192.168.50.2:9117");
        worker.execute(()->{
            try {
                List<SourceRegistry.Source> found=SourceRegistry.fetch(address,key);
                main.post(()->{
                    if(isFinishing())return;
                    loadingSources=false;
                    sources.clear();
                    sources.addAll(found);
                    refreshSourceMenu();
                    if(found.isEmpty())Toast.makeText(this,"Jackett не вернул настроенные источники",Toast.LENGTH_LONG).show();
                });
            }catch(Exception e) {
                main.post(()->{
                    if(isFinishing())return;
                    loadingSources=false;
                    refreshSourceMenu();
                    Toast.makeText(this,"Список сайтов: "+error(e),Toast.LENGTH_LONG).show();
                });
            }
        });
    }
    private void chooseSource() {
        if(sources.isEmpty()) {
            loadSources();
            new AlertDialog.Builder(this).setTitle("Источники")
                .setMessage("Список сайтов загружается из Jackett. Сейчас доступны «Все сайты» и сохранённый источник.")
                .setPositiveButton("Все сайты",(d,w)->selectSource("all"))
                .setNeutralButton("Текущий сайт",(d,w)->selectSource(activeSource))
                .setNegativeButton("Закрыть",null).show();
            return;
        }
        String[] choices=new String[sources.size()+1];
        choices[0]="Все сайты — общий поиск";
        for(int i=0;i<sources.size();i++)
            choices[i+1]=sources.get(i).name;
        new AlertDialog.Builder(this).setTitle("Выбери источник ("+sources.size()+")")
            .setItems(choices,(d,i)->selectSource(i==0?"all":sources.get(i-1).id))
            .setNegativeButton("Назад",null).show();
    }
    private void selectSource(String id) {
        if(!id.equals("all") && !id.matches("[a-zA-Z0-9_-]{1,80}"))return;
        activeSource=id;
        currentCategory=0;
        currentCategoryLabel="";
        prefs.edit().putString("indexer",id).apply();
        refreshSourceMenu();
        load(lastQuery,lastSort);
    }
    private void categories() {
        if(activeSource.equals("all")) {
            new AlertDialog.Builder(this).setTitle("Категории")
                .setMessage("Категории сайтов отличаются. Выбери конкретный сайт, чтобы видеть его реальные категории из Jackett. Общие тематические запросы доступны над каталогом.")
                .setPositiveButton("Выбрать сайт",(d,w)->chooseSource())
                .setNegativeButton("Закрыть",null).show();
            return;
        }
        SourceRegistry.Source src=SourceRegistry.byId(sources,activeSource);
        if(src==null || src.categories.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Категории "+activeSource)
                .setMessage("Jackett ещё не загрузил категории этого сайта или не предоставил их. Поиск по названию доступен.")
                .setPositiveButton("Обновить список",(d,w)->loadSources())
                .setNegativeButton("Закрыть",null).show();
            return;
        }
        List<SourceRegistry.Category> available=new ArrayList<>(src.categories);
        String[] options=new String[available.size()+1];
        options[0]="Все категории сайта";
        for(int i=0;i<available.size();i++)options[i+1]=available.get(i).name;
        new AlertDialog.Builder(this).setTitle(src.name+" — категории")
            .setItems(options,(d,i)->{
                currentCategory=i==0?0:available.get(i-1).id;
                currentCategoryLabel=i==0?"":available.get(i-1).name;
                queryInput.setText("");
                load("","latest");
            }).setNegativeButton("Закрыть",null).show();
    }
    private void diagnostics() {
        if(sources.isEmpty()) {loadSources();Toast.makeText(this,"Сначала загрузим список Jackett",Toast.LENGTH_SHORT).show();return;}
        String[] names=new String[sources.size()];
        for(int i=0;i<sources.size();i++) {
            SourceRegistry.Source src=sources.get(i);
            String status=probeResults.get(src.id);
            if(status==null)status=src.lastError.isEmpty()?"Не проверен":"Ошибка в Jackett (последняя): "+src.lastError;
            if(status.length()>60)status=status.substring(0,60)+"…";
            names[i]=src.name+" — "+status;
        }
        new AlertDialog.Builder(this).setTitle("Диагностика · "+sources.size()+" сайтов")
            .setItems(names,(d,i)->probeSource(sources.get(i)))
            .setPositiveButton("Проверить все",(d,w)->probeAll())
            .setNegativeButton("Закрыть",null).show();
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
        try {
            Jackett.SearchReport report=Jackett.searchReport(
                prefs.getString("jackett_url","http://192.168.50.2:9117"),
                prefs.getString("api_key",""),source.id,"",0,13000);
            int withPosters=0;
            for(Item item:report.items)if(!item.poster.isEmpty())withPosters++;
            return "OK: раздач "+report.items.size()+", постеров от Jackett "+withPosters+
                "\nОписание/кадры требуют отдельного парсера сайта."+
                (report.warning.isEmpty()?"":"\n"+report.warning);
        }catch(Exception e){return "Ошибка поиска: "+error(e);}
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
        lastQuery=q;lastSort=order;
        String api = prefs.getString("api_key","");
        if (api.isEmpty()) {settings(true);return;}
        String base=prefs.getString("jackett_url","http://192.168.50.2:9117");
        String idx=prefs.getString("indexer","xxxclub");
        int req=++serial;
        status.setText((order.equals("latest")?"Новинки":order.equals("popular")?"Популярное":"Поиск: "+q)+" · "+idx);
        message("Ищем раздачи...");
        worker.execute(() -> {
            try {
                List<Item> result = Jackett.search(base,api,idx,q);
                if(order.equals("popular")) result.sort(Comparator.comparingInt((Item x)->x.seeders).reversed());
                else if(order.equals("latest")) result.sort(Comparator.comparing((Item x)->x.date).reversed());
                main.post(() -> {if(req==serial && !isFinishing()) show(result);});
            } catch(Exception e) {
                main.post(() -> {if(req==serial && !isFinishing()) message("Не удалось получить раздачи: "+error(e)+"\nПроверь настройки и доступность Jackett.");});
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
        cards.removeAllViews();
        if(items.isEmpty()) { message("Результатов нет. Попробуй другой запрос или источник.");return; }
        TextView count=label("Найдено: "+items.size()+" · изображения показываются, если их передаёт источник",13,MUTED,false);
        count.setPadding(0,0,0,dp(12));cards.addView(count);
        int width=getResources().getDisplayMetrics().widthPixels;
        int cols=Math.max(2,Math.min(5,width / dp(215)));
        int cardW=Math.max(140,(width/dp(1)-52-(cols-1)*10)/cols);
        GridLayout grid = new GridLayout(this);grid.setColumnCount(cols);
        cards.addView(grid);
        for(int i=0;i<Math.min(120,items.size());i++) {
            Item item=items.get(i);
            LinearLayout tile=column();
            tile.setBackground(panel(PANEL,false));tile.setPadding(dp(6),dp(6),dp(6),dp(8));
            tile.setClickable(true);tile.setFocusable(true);
            tile.setOnFocusChangeListener((v,f)->v.setBackground(panel(f?0xff34415c:PANEL,f)));
            ImageView img=new ImageView(this);img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setBackground(panel(0xff34425e,false));
            tile.addView(img,lp(-1,132));
            loadPoster(img,item,i<18);
            TextView name=label(item.title,13,WHITE,true);
            name.setMaxLines(3);name.setEllipsize(TextUtils.TruncateAt.END);
            name.setPadding(dp(5),dp(7),dp(5),0);
            tile.addView(name,new LinearLayout.LayoutParams(-1,0,1));
            TextView meta=label(item.tracker+" · "+item.size()+" · сиды "+item.seeders,11,MUTED,false);
            meta.setSingleLine(true);meta.setEllipsize(TextUtils.TruncateAt.END);
            tile.addView(meta);
            tile.setOnClickListener(v->details(item));
            GridLayout.LayoutParams p=new GridLayout.LayoutParams(GridLayout.spec(i/cols),GridLayout.spec(i%cols));
            p.width=dp(cardW);p.height=dp(232);p.rightMargin=dp(10);p.bottomMargin=dp(10);
            grid.addView(tile,p);
        }
    }
    private static String referer(Item item) {
        try { Uri url=Uri.parse(item.details); return url.getScheme()+"://"+url.getAuthority()+"/"; }
        catch(Exception e) { return "https://xxxclub.to/"; }
    }
    private void loadPoster(ImageView view, Item item, boolean prefetch) {
        view.setTag(item);
        if(!item.poster.isEmpty()) loadImage(view,item.poster,referer(item),item,true);
        else if(prefetch) refreshMetadata(item,view);
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
        LinearLayout root=column();root.setPadding(dp(18),dp(10),dp(18),dp(10));
        EditText jackett=field(root,"Jackett",prefs.getString("jackett_url","http://192.168.50.2:9117"),false);
        EditText key=field(root,"Jackett API key",prefs.getString("api_key",""),true);
        EditText torr=field(root,"TorrServer",prefs.getString("torr_url","http://127.0.0.1:8090"),false);
        root.addView(label("Источник",14,MUTED,true));
        Spinner source=new Spinner(this);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,INDEXERS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        source.setAdapter(adapter);
        for(int n=0;n<INDEXERS.length;n++)if(INDEXERS[n].equals(prefs.getString("indexer","xxxclub")))source.setSelection(n);
        root.addView(source);
        TextView note=label("Источник 'all' ищет по всем индексаторам Jackett. Ключ хранится только в настройках Android.",12,MUTED,false);
        note.setPadding(0,dp(10),0,0);root.addView(note);
        ScrollView scroll=new ScrollView(this);scroll.addView(root);
        AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle("Настройки подключения").setView(scroll)
            .setPositiveButton("Сохранить",(d,w)->{
                prefs.edit().putString("jackett_url",jackett.getText().toString().trim())
                    .putString("api_key",key.getText().toString().trim())
                    .putString("torr_url",torr.getText().toString().trim())
                    .putString("indexer",source.getSelectedItem().toString()).apply();
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
        final String title,tracker,magnet,download,details,date;
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
        static List<Item> search(String base,String key,String indexer,String query) throws Exception {
            if(!base.startsWith("http"))throw new IllegalArgumentException("Укажи адрес Jackett с http://");
            String uri=base.replaceAll("/+$","")+"/api/v2.0/indexers/"+Uri.encode(indexer)+"/results?apikey="+Uri.encode(key)+"&Query="+Uri.encode(query);
            HttpURLConnection c=(HttpURLConnection)new URL(uri).openConnection();
            try {
                c.setConnectTimeout(10000);c.setReadTimeout(35000);
                c.setRequestProperty("Accept","application/json");
                if(c.getResponseCode()!=200)throw new IllegalStateException("Jackett HTTP "+c.getResponseCode());
                JSONObject response;
                try(InputStream in=c.getInputStream()){response=new JSONObject(read(in));}
                JSONArray raw=response.optJSONArray("Results");
                if(raw==null)raw=response.optJSONArray("results");
                List<Item> out=new ArrayList<>();Set<String> seen=new HashSet<>();
                if(raw!=null)for(int i=0;i<raw.length();i++){
                    JSONObject j=raw.optJSONObject(i);if(j==null)continue;
                    Item item=new Item(j);if(item.title.isEmpty())continue;
                    String id=!item.magnet.isEmpty()?item.magnet:item.title+"|"+item.bytes;
                    if(seen.add(id))out.add(item);
                }
                return out;
            } finally {c.disconnect();}
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
