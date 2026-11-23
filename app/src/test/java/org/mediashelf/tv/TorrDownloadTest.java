package org.mediashelf.tv;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class TorrDownloadTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();

    /** Minimal HTTP responder; Android Gradle excludes jdk.httpserver. */
    private static final class TestHttpServer implements AutoCloseable {
        final ServerSocket server;
        final Thread thread;
        final AtomicInteger calls=new AtomicInteger();
        final byte[] data;
        final boolean rejectResume;
        final boolean firstFailure;
        volatile boolean running=true;
        TestHttpServer(byte[] data,boolean firstFailure,boolean rejectResume)
            throws Exception{
            this.data=data;this.rejectResume=rejectResume;
            this.firstFailure=firstFailure;
            server=new ServerSocket(0,8,
                java.net.InetAddress.getByName("127.0.0.1"));
            thread=new Thread(()->{
                while(running){
                    try(Socket client=server.accept()){
                        client.setSoTimeout(12000);
                        BufferedReader input=new BufferedReader(new InputStreamReader(
                            client.getInputStream(),StandardCharsets.US_ASCII));
                        String request=input.readLine();
                        String range=null,line;
                        while((line=input.readLine())!=null && !line.isEmpty()){
                            if(line.toLowerCase(java.util.Locale.ROOT)
                                .startsWith("range:"))
                                range=line.substring(6).trim();
                        }
                        int call=calls.incrementAndGet();
                        OutputStream output=client.getOutputStream();
                        if(firstFailure && call==1){
                            output.write(("HTTP/1.1 503 Busy\r\n"+
                                "Content-Length: 0\r\nConnection: close\r\n\r\n")
                                    .getBytes(StandardCharsets.US_ASCII));
                            output.flush();continue;
                        }
                        int start=0;
                        if(range!=null && range.startsWith("bytes=")){
                            int delimiter=range.indexOf('-');
                            start=Integer.parseInt(range.substring(6,delimiter));
                        }
                        if(rejectResume)range=null;
                        if(range==null){
                            String header="HTTP/1.1 200 OK\r\nContent-Length: "+
                                data.length+"\r\nConnection: close\r\n\r\n";
                            output.write(header.getBytes(StandardCharsets.US_ASCII));
                            output.write(data);
                        }else{
                            String header="HTTP/1.1 206 Partial Content\r\n"+
                                "Content-Range: bytes "+start+"-"+(data.length-1)+
                                "/"+data.length+"\r\nContent-Length: "+
                                (data.length-start)+"\r\nConnection: close\r\n\r\n";
                            output.write(header.getBytes(StandardCharsets.US_ASCII));
                            output.write(data,start,data.length-start);
                        }
                        output.flush();
                    }catch(Exception ex){if(!running)return;}
                }
            },"torr-test-server");
            thread.setDaemon(true);
            thread.start();
        }
        String url(){return "http://127.0.0.1:"+server.getLocalPort()+"/stream";}
        @Override public void close() throws Exception {
            running=false;
            server.close();
            thread.join(2500);
        }
    }

    private static final TorrDownload.Observer OBSERVER=new TorrDownload.Observer(){
        @Override public void onConnection(HttpURLConnection conn){}
        @Override public void onProgress(String stage,long done,long total,
            double rate,int retries){}
    };

    @Test public void rangesAreBoundedAndValidated() throws Exception{
        assertEquals("bytes=0-99",TorrDownload.range(0,100));
        assertEquals("bytes=27-99",TorrDownload.range(27,100));
        assertEquals(27,TorrDownload.checkedRangeStart("bytes 27-99/100"));
        assertNull(TorrDownload.range(0,-1));
        try {
            TorrDownload.checkedRangeStart("invalid");
            fail("Bad Content-Range must not be accepted");
        }catch(TorrDownload.Unrecoverable expected){}
    }

    @Test public void retriesAfterTemporary503AndResumesPartialBytes()
        throws Exception{
        byte[] data=new byte[163840];
        for(int i=0;i<data.length;i++)data[i]=(byte)(i%251);
        File partial=new File(temp.getRoot(),"movie.mp4.part");
        try(FileOutputStream out=new FileOutputStream(partial)){
            out.write(data,0,4096);
        }
        try(TestHttpServer server=new TestHttpServer(data,true,false)){
            long saved=TorrDownload.transfer(server.url(),partial,
                data.length,new AtomicBoolean(true),OBSERVER);
            assertEquals(data.length,saved);
            assertArrayEquals(data,Files.readAllBytes(partial.toPath()));
            assertTrue(server.calls.get()>=2);
        }
    }

    @Test public void preventsCorruptionWhenServerIgnoresRange() throws Exception{
        File partial=new File(temp.getRoot(),"partial.mkv.part");
        Files.write(partial.toPath(),new byte[]{1,2,3});
        try(TestHttpServer server=new TestHttpServer(
            new byte[]{1,2,3,4,5},false,true)){
            try{
                TorrDownload.transfer(server.url(),partial,
                    5,new AtomicBoolean(true),OBSERVER);
                fail("Must reject HTTP 200 when resuming");
            }catch(TorrDownload.Unrecoverable expected){}
            assertArrayEquals(new byte[]{1,2,3},
                Files.readAllBytes(partial.toPath()));
        }
    }
}
