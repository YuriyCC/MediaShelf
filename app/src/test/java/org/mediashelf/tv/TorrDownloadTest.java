package org.mediashelf.tv;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.Arrays;

public final class TorrDownloadTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();

    @Test public void byteRangesAreBoundedAndResumable() throws Exception {
        assertEquals("bytes=0-99",TorrDownload.range(0,100));
        assertEquals("bytes=27-99",TorrDownload.range(27,100));
        assertEquals(27,TorrDownload.checkedRangeStart("bytes 27-99/100"));
        assertNull(TorrDownload.range(0,-1));
        try {
            TorrDownload.checkedRangeStart("invalid");
            fail("Bad Content-Range must not be accepted");
        } catch(TorrDownload.Unrecoverable expected){}
    }

    @Test public void resumesFromPartialAfterTransientServerFailure() throws Exception {
        final byte[] data=new byte[163840];
        for(int i=0;i<data.length;i++)data[i]=(byte)(i%251);
        File partial=new File(temp.getRoot(),"movie.mp4.part");
        try(FileOutputStream o=new FileOutputStream(partial)){
            o.write(data,0,4096);
        }
        AtomicInteger calls=new AtomicInteger();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/stream",exchange->{
            int call=calls.incrementAndGet();
            String requested=exchange.getRequestHeaders().getFirst("Range");
            if(call==1){
                exchange.sendResponseHeaders(503,-1);
                exchange.close();
                return;
            }
            int start=Integer.parseInt(requested.substring(
                "bytes=".length(),requested.indexOf('-')));
            int end=data.length-1;
            exchange.getResponseHeaders().add("Content-Range",
                "bytes "+start+"-"+end+"/"+data.length);
            exchange.sendResponseHeaders(206,end-start+1);
            exchange.getResponseBody().write(data,start,end-start+1);
            exchange.close();
        });
        server.start();
        try{
            long length=TorrDownload.transfer(
                "http://127.0.0.1:"+server.getAddress().getPort()+"/stream",
                partial,data.length,new AtomicBoolean(true),
                new TorrDownload.Observer(){
                    @Override public void onConnection(HttpURLConnection connection){}
                    @Override public void onProgress(String stage,long done,long total,
                        double rate,int retries){}
                });
            assertEquals(data.length,length);
            assertArrayEquals(data,Files.readAllBytes(partial.toPath()));
            assertTrue(calls.get()>=2);
        }finally{server.stop(0);}
    }

    @Test public void refusesToAppendDuplicateFullResponseOnResume() throws Exception{
        File partial=new File(temp.getRoot(),"partial.mkv.part");
        Files.write(partial.toPath(),new byte[]{1,2,3});
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/stream",exchange->{
            exchange.sendResponseHeaders(200,5);
            exchange.getResponseBody().write(new byte[]{1,2,3,4,5});
            exchange.close();
        });
        server.start();
        try{
            try {
                TorrDownload.transfer(
                    "http://127.0.0.1:"+server.getAddress().getPort()+"/stream",
                    partial,5,new AtomicBoolean(true),
                    new TorrDownload.Observer(){
                        @Override public void onConnection(HttpURLConnection c){}
                        @Override public void onProgress(String s,long d,long t,
                            double speed,int retry){}
                    });
                fail("Server must honor Range for resumed content");
            }catch(TorrDownload.Unrecoverable expected){}
            assertArrayEquals(new byte[]{1,2,3},
                Files.readAllBytes(partial.toPath()));
        }finally{server.stop(0);}
    }
}
