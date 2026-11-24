package com.cubapincha.job.util;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
/** Caché de miniaturas en memoria y disco para uso offline. */
public final class NetworkImageLoader {
 private static final ExecutorService EXECUTOR=Executors.newFixedThreadPool(3);
 private static final Handler MAIN=new Handler(Looper.getMainLooper());
 private static final LruCache<String,Bitmap> MEMORY=new LruCache<String,Bitmap>(12*1024){ protected int sizeOf(String k,Bitmap v){return v.getByteCount()/1024;} };
 private NetworkImageLoader(){}
 public static void load(ImageView target,String url){
  if(target==null)return; target.setTag(url); if(url==null||url.trim().isEmpty())return;
  Bitmap m=MEMORY.get(url); if(m!=null){target.setImageBitmap(m);return;}
  File file=cacheFile(target.getContext(),url);
  if(file.exists()){EXECUTOR.execute(()->{Bitmap b=decode(file);if(b!=null)MEMORY.put(url,b);post(target,url,b);});return;}
  EXECUTOR.execute(()->{Bitmap b=null;HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(10000);c.setReadTimeout(15000);c.connect();if(c.getResponseCode()>=200&&c.getResponseCode()<300){try(InputStream in=c.getInputStream()){b=BitmapFactory.decodeStream(in);}}if(b!=null){MEMORY.put(url,b);try(FileOutputStream out=new FileOutputStream(file)){b.compress(Bitmap.CompressFormat.JPEG,88,out);}}}catch(Exception ignored){}finally{if(c!=null)c.disconnect();}post(target,url,b);});
 }
 private static void post(ImageView v,String url,Bitmap b){MAIN.post(()->{if(b!=null&&url.equals(v.getTag()))v.setImageBitmap(b);});}
 private static Bitmap decode(File f){try(InputStream in=new FileInputStream(f)){return BitmapFactory.decodeStream(in);}catch(Exception e){return null;}}
 private static File cacheFile(Context c,String url){File d=new File(c.getCacheDir(),"cubapincha_images");if(!d.exists())d.mkdirs();return new File(d,hash(url)+".jpg");}
 private static String hash(String s){try{byte[] d=MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"));StringBuilder b=new StringBuilder();for(byte x:d)b.append(String.format("%02x",x));return b.toString();}catch(Exception e){return String.valueOf(s.hashCode());}}
 public static void clearCache(Context c){MEMORY.evictAll();File d=new File(c.getCacheDir(),"cubapincha_images");File[] fs=d.listFiles();if(fs!=null)for(File f:fs)f.delete();}
}
