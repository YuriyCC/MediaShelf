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
    private static final String[] INDEXERS = {"xxxclub","all","mypornclub","xxxtor","0magnet","bigfangroup","freejavtorrent","pornotorrent","sosulki","sukebeinyaasi"};
    private final ExecutorService worker = Executors.newFixedThreadPool(4);
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
        ui();
        if (prefs.getString("api_key","").isEmpty()) settings(true);
        else load("","latest");
    }
    private void ui() {
        LinearLayout root = column(); root.setBackgroundColor(BACK);
        root.setPadding(dp(18),dp(14),dp(18),0); setContentView(root);
        LinearLayout top = line();
        TextView title = label("MediaShelf",26,WHITE,true);
        top.addView(title,new LinearLayout.LayoutParams(0,dp(54),1));
        Button settingBtn = button("Настройки");
        settingBtn.setOnClickListener(v -> settings(false));
        top.addView(settingBtn,lp(130,48)); root.addView(top);
        status = label("Jackett → TorrServer → плеер",14,MUTED,false);
        root.addView(status,lp(-1,38));
        LinearLayout searchRow = line();
        queryInput = new EditText(this);
        queryInput.setTextColor(WHITE); queryInput.setHintTextColor(MUTED); queryInput.setSingleLine(true);
        queryInput.setHint("Поиск фильма или темы"); queryInput.setTextSize(18);
        queryInput.setBackground(panel(PANEL,false));queryInput.setPadding(dp(12),0,dp(12),0);
        queryInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        queryInput.setOnEditorActionListener((v,action,event) -> {
            if(action == EditorInfo.IME_ACTION_SEARCH) {load(queryInput.getText().toString().trim(),"search");return true;}
            return false;
        });
        searchRow.addView(queryInput,new LinearLayout.LayoutParams(0,dp(48),1));
        Button go = button("Найти");
        LinearLayout.LayoutParams gp=lp(98,48);gp.leftMargin=dp(10);
        searchRow.addView(go,gp);
        go.setOnClickListener(v -> load(queryInput.getText().toString().trim(),"search"));
        root.addView(searchRow);
        ScrollView strip = new ScrollView(this);
        LinearLayout controls = line();
        for (String category : new String[]{"Новинки","Популярное","Gangbang","Double","Fisting"}) {
            Button b = button(category);
            LinearLayout.LayoutParams p=lp(-2,44);p.setMargins(0,dp(8),dp(7),dp(8));
            controls.addView(b,p);
            b.setOnClickListener(v -> {
                String q = category.equals("Новинки") || category.equals("Популярное") ? "" : category;
                String mode = category.equals("Новинки") ? "latest" : category.equals("Популярное") ? "popular" : "search";
                queryInput.setText(q);load(q,mode);
            });
        }
        android.widget.HorizontalScrollView h = new android.widget.HorizontalScrollView(this);
        h.setHorizontalScrollBarEnabled(false);h.addView(controls);
        root.addView(h);
        ScrollView list = new ScrollView(this);list.setFillViewport(true);
        cards=column();cards.setPadding(0,dp(10),0,dp(25));
        list.addView(cards);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
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
            if(!item.poster.isEmpty()) loadPoster(img,item.poster);
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
    private void loadPoster(ImageView view,String link) {
        if(!link.startsWith("http://") && !link.startsWith("https://"))return;
        view.setTag(link);
        worker.execute(()->{
            HttpURLConnection c=null;
            try {
                c=(HttpURLConnection)new URL(link).openConnection();
                c.setConnectTimeout(7000);c.setReadTimeout(9000);
                c.setRequestProperty("User-Agent","Mozilla/5.0");
                if(c.getResponseCode()!=200)return;
                BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=2;
                Bitmap bitmap;
                try(InputStream in=c.getInputStream()) {bitmap=BitmapFactory.decodeStream(in,null,opts);}
                if(bitmap!=null) main.post(()->{if(link.equals(view.getTag()))view.setImageBitmap(bitmap);});
            }catch(Exception ignored){}finally{if(c!=null)c.disconnect();}
        });
    }
    private void details(Item item) {
        String info="Источник: "+item.tracker+"\nРазмер: "+item.size()+"\nСиды: "+item.seeders+"\nДата: "+item.date;
        if(!item.description.isEmpty())info += "\n\n"+Html.fromHtml(item.description,Html.FROM_HTML_MODE_COMPACT);
        AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle(item.title).setMessage(info).setNegativeButton("Назад",null);
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
    @Override protected void onDestroy() {serial++;worker.shutdownNow();super.onDestroy();}
    private static String read(InputStream input) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        byte[] b=new byte[8192];int n;
        while((n=input.read(b))!=-1){out.write(b,0,n);if(out.size()>12_000_000)throw new IllegalStateException("Слишком большой ответ");}
        return out.toString(StandardCharsets.UTF_8.name());
    }
    static final class Item {
        final String title,tracker,magnet,download,details,poster,description,date;
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
