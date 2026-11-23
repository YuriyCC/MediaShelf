package org.mediashelf.tv;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Range-based TorrServer video transfer. Never append HTTP 200 responses
 * while resuming: that would silently corrupt video files. Partial bytes are
 * kept on failures and can be resumed with the same torrent hash.
 */
final class TorrDownload {
    private static final long CHUNK=64L*1024*1024;
    private static final int READ_TIMEOUT=90000;
    private static final Pattern CONTENT_RANGE=Pattern.compile(
        "bytes\\s+(\\d+)-(\\d+)/(\\d+|\\*)",Pattern.CASE_INSENSITIVE);
    interface Observer {
        void onConnection(HttpURLConnection connection);
        void onProgress(String stage,long done,long total,double bytesPerSecond,int retries);
    }
    static final class Unrecoverable extends IOException {
        Unrecoverable(String message){super(message);}
    }
    private TorrDownload(){}

    static String range(long position,long total){
        if(total<=0)return null;
        return "bytes="+position+"-"+Math.min(total-1,position+CHUNK-1);
    }
    static long checkedRangeStart(String header) throws IOException {
        if(header==null)throw new Unrecoverable(
            "TorrServer вернул часть файла без Content-Range");
        Matcher matcher=CONTENT_RANGE.matcher(header.trim());
        if(!matcher.matches())throw new Unrecoverable(
            "Некорректный диапазон TorrServer");
        try{return Long.parseLong(matcher.group(1));}
        catch(NumberFormatException ex){throw new Unrecoverable(
            "Некорректное начало диапазона");}
    }

    static long transfer(String url,File incomplete,long total,
        AtomicBoolean active,Observer observer) throws IOException{
        if(total==0)total=-1;
        if(total>0 && incomplete.exists() && incomplete.length()>total)
            throw new Unrecoverable("Частичный файл больше исходного");
        long done=incomplete.exists()?incomplete.length():0;
        int failuresWithoutProgress=0;
        int retries=0;
        try(RandomAccessFile output=new RandomAccessFile(incomplete,"rw")){
            if(output.length()!=done)output.setLength(done);
            while(active.get() && (total<0 || done<total)){
                HttpURLConnection connection=null;
                long checkpoint=done;
                try{
                    observer.onProgress("Подключаем TorrServer…",done,total,0,retries);
                    connection=(HttpURLConnection)new URL(url).openConnection();
                    observer.onConnection(connection);
                    connection.setConnectTimeout(20000);
                    connection.setReadTimeout(READ_TIMEOUT);
                    connection.setRequestProperty("User-Agent","MediaShelf/2.8");
                    connection.setRequestProperty("Accept-Encoding","identity");
                    String requested=range(done,total);
                    if(requested!=null)connection.setRequestProperty("Range",requested);
                    int status=connection.getResponseCode();
                    if(status!=200 && status!=206){
                        if(status>=400 && status<500 && status!=408 && status!=429)
                            throw new Unrecoverable("TorrServer HTTP "+status);
                        throw new IOException("TorrServer HTTP "+status);
                    }
                    long stop=total;
                    if(status==206){
                        long start=checkedRangeStart(
                            connection.getHeaderField("Content-Range"));
                        if(start!=done)throw new Unrecoverable(
                            "TorrServer прислал данные не с нужной позиции");
                        // Each request is capped to a reasonable piece size.
                        String contentRange=connection.getHeaderField("Content-Range");
                        Matcher m=CONTENT_RANGE.matcher(contentRange.trim());
                        if(!m.matches())throw new Unrecoverable("Неверный Content-Range");
                        long remoteEnd=Long.parseLong(m.group(2));
                        if(remoteEnd<done)throw new Unrecoverable(
                            "Неверная длина блока TorrServer");
                        stop=Math.min(stop>0?stop:Long.MAX_VALUE,remoteEnd+1);
                    }else if(done>0){
                        throw new Unrecoverable(
                            "TorrServer не поддерживает продолжение с позиции "+
                            done+". Частичный файл сохранён");
                    }
                    final long end=stop;
                    output.seek(done);
                    observer.onProgress("Получаем данные…",done,total,0,retries);
                    try(java.io.InputStream input=connection.getInputStream()){
                        byte[] bytes=new byte[64*1024];
                        long window=System.nanoTime(),transferredInWindow=0;
                        boolean eof=false;
                        while(active.get() && (end<0 || done<end)){
                            int max=bytes.length;
                            if(end>0)max=(int)Math.min(max,end-done);
                            int count=input.read(bytes,0,max);
                            if(count<0){eof=true;break;}
                            if(count==0)continue;
                            try{output.write(bytes,0,count);}
                            catch(IOException diskError){throw new Unrecoverable(
                                "Ошибка записи на накопитель: "+diskError.getMessage());}
                            done+=count;
                            transferredInWindow+=count;
                            long now=System.nanoTime();
                            if(now-window>850_000_000L){
                                double seconds=(now-window)/1_000_000_000.0;
                                observer.onProgress("Скачивание…",done,total,
                                    transferredInWindow/seconds,retries);
                                window=now;transferredInWindow=0;
                            }
                        }
                        if(!active.get())break;
                        if(end>0 && done<end)
                            throw new IOException("TorrServer рано закрыл поток");
                        if(total<0 && eof){
                            observer.onProgress("Скачивание завершено",done,done,0,retries);
                            return done;
                        }
                    }
                    output.getFD().sync();
                    failuresWithoutProgress=0;
                    // Avoid rapidly opening a new connection against the same
                    // TorrServer reader after a completed range.
                }catch(Unrecoverable err){
                    throw err;
                }catch(IOException err){
                    if(!active.get())break;
                    if(done==checkpoint)failuresWithoutProgress++;
                    else failuresWithoutProgress=0;
                    retries++;
                    if(failuresWithoutProgress>=6 || retries>=35)
                        throw new IOException("Не удалось получить блок фильма: "+
                            err.getMessage()+". Частичный файл сохранён",err);
                    observer.onProgress("Переподключение "+retries+
                        " · "+(err instanceof SocketTimeoutException?
                            "ожидание данных":"сбой соединения"),done,total,0,retries);
                    try{
                        Thread.sleep(Math.min(5000L,700L+retries*350L));
                    }catch(InterruptedException interrupted){
                        Thread.currentThread().interrupt();
                        throw new IOException("Скачивание остановлено",interrupted);
                    }
                }finally{
                    if(connection!=null)connection.disconnect();
                    observer.onConnection(null);
                }
            }
            if(!active.get())throw new IOException("Загрузка отменена. Уже скачанное сохранено");
            if(total>0 && done<total)
                throw new IOException("Фильм получен не полностью");
            output.getFD().sync();
            observer.onProgress("Фильм загружен",done,total,0,retries);
            return done;
        }
    }
}