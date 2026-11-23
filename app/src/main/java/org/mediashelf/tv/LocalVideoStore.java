package org.mediashelf.tv;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * App-owned Downloads; works without ACTION_CREATE_DOCUMENT or storage
 * permissions on Android TV. Removable storage is exposed only when Android
 * grants the app a writable app-specific directory on the USB/SD volume.
 */
final class LocalVideoStore {
    static final String AUTHORITY="org.mediashelf.tv.videos";
    private LocalVideoStore(){}

    static final class Volume {
        final File folder;
        final String label;
        Volume(File folder,String label){this.folder=folder;this.label=label;}
    }
    static List<Volume> volumes(Context context){
        List<Volume> result=new ArrayList<>();
        File[] external=context.getExternalFilesDirs(Environment.DIRECTORY_MOVIES);
        if(external!=null){
            for(File dir:external){
                if(dir==null || (!dir.exists() && !dir.mkdirs()) ||
                    !dir.isDirectory() || !dir.canWrite())continue;
                boolean duplicate=false;
                for(Volume other:result)
                    if(other.folder.equals(dir)){duplicate=true;break;}
                if(duplicate)continue;
                boolean removable=false;
                try{removable=Environment.isExternalStorageRemovable(dir);}
                catch(Exception ignored){}
                result.add(new Volume(dir,removable?"USB / SD":"Память телевизора"));
            }
        }
        if(result.isEmpty()){
            File fallback=new File(context.getFilesDir(),"Movies");
            if((fallback.exists()||fallback.mkdirs()) && fallback.canWrite())
                result.add(new Volume(fallback,"Память приложения"));
        }
        return result;
    }
    static String safeName(String raw){
        String name=raw==null?"":raw.replace('\\','/')
            .substring(raw==null?0:raw.replace('\\','/').lastIndexOf('/')+1);
        name=name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_").trim()
            .replaceAll("^\\.+","_");
        if(name.isEmpty())name="MediaShelf-video.mp4";
        if(name.length()>110){
            int lastDot=name.lastIndexOf('.');
            String extension=lastDot>=0 && name.length()-lastDot<=8?
                name.substring(lastDot):".mp4";
            name=name.substring(0,Math.min(100,name.length()))+extension;
        }
        return name;
    }
    static File uniqueTarget(File folder,String raw) throws IOException{
        if(!folder.isDirectory() || !folder.canWrite())
            throw new IOException("Накопитель недоступен для записи");
        String name=safeName(raw);
        int period=name.lastIndexOf('.');
        String base=period>=0?name.substring(0,period):name;
        String ext=period>=0?name.substring(period):"";
        for(int n=0;n<1000;n++){
            File candidate=new File(folder,n==0?name:base+" ("+n+")"+ext);
            if(!candidate.exists() && !new File(candidate+".part").exists())
                return candidate;
        }
        throw new IOException("Слишком много одинаковых файлов");
    }
    static boolean isVideo(File file){
        if(file==null || !file.isFile() || file.getName().endsWith(".part"))
            return false;
        String name=file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".mp4")||name.endsWith(".mkv")||
            name.endsWith(".avi")||name.endsWith(".ts")||
            name.endsWith(".webm")||name.endsWith(".m4v");
    }
    static List<File> completed(Context context){
        List<File> files=new ArrayList<>();
        for(Volume v:volumes(context)){
            File[] list=v.folder.listFiles();
            if(list!=null)for(File file:list)
                if(isVideo(file))files.add(file);
        }
        files.sort(Comparator.comparingLong(File::lastModified).reversed());
        return files;
    }
    static Uri uri(Context context,File file) throws IOException{
        int index=0;
        for(Volume v:volumes(context)){
            if(file.getCanonicalFile().getParentFile().equals(
                v.folder.getCanonicalFile()))
                return new Uri.Builder().scheme("content")
                    .authority(AUTHORITY).appendPath("v")
                    .appendPath(String.valueOf(index))
                    .appendPath(file.getName()).build();
            index++;
        }
        throw new IOException("Файл вне папок MediaShelf");
    }
    static File resolve(Context context,Uri uri) throws IOException{
        if(uri==null || !AUTHORITY.equals(uri.getAuthority()) ||
            uri.getPathSegments().size()!=3 ||
            !"v".equals(uri.getPathSegments().get(0)))
            throw new IOException("Недопустимая ссылка на видео");
        int index;
        try{index=Integer.parseInt(uri.getPathSegments().get(1));}
        catch(Exception ignored){throw new IOException("Неверный накопитель");}
        List<Volume> roots=volumes(context);
        if(index<0||index>=roots.size())throw new IOException("Накопитель отключён");
        String name=uri.getPathSegments().get(2);
        if(!safeName(name).equals(name))throw new IOException("Неверное имя файла");
        File file=new File(roots.get(index).folder,name).getCanonicalFile();
        if(!file.getParentFile().equals(roots.get(index).folder.getCanonicalFile()) ||
            !isVideo(file))throw new IOException("Файл недоступен");
        return file;
    }
    static String mime(File file){
        String n=file.getName().toLowerCase(Locale.ROOT);
        if(n.endsWith(".mkv"))return "video/x-matroska";
        if(n.endsWith(".avi"))return "video/x-msvideo";
        if(n.endsWith(".ts"))return "video/mp2t";
        if(n.endsWith(".webm"))return "video/webm";
        return "video/mp4";
    }
}