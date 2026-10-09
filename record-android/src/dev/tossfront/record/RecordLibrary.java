package dev.tossfront.record;

import android.app.job.*;
import android.content.*;
import org.json.*;
import java.util.*;

/** Only the user's requested IDs/titles/times are stored, in app-private preferences. */
final class RecordLibrary {
    private static RecordLibrary instance;
    final LibraryCore core=new LibraryCore();
    private final android.content.SharedPreferences prefs;
    private long lastWrite,lastPurge;
    int loop;
    static synchronized RecordLibrary get(Context c){if(instance==null)instance=new RecordLibrary(c);return instance;}
    private RecordLibrary(Context c){prefs=c.getApplicationContext().getSharedPreferences("library",0);load(prefs.getString("recent","[]"),core.history);load(prefs.getString("favorites","[]"),core.favorites);loop=Math.max(0,Math.min(2,prefs.getInt("loop_mode",0)));prune();JobScheduler scheduler=(JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);if(scheduler.getPendingJob(301)==null)scheduler.schedule(new JobInfo.Builder(301,new ComponentName(c,HistoryCleanup.class)).setPeriodic(60*60*1000L).setPersisted(true).build());}
    private void load(String json,List<LibraryCore.Entry> list){try{JSONArray a=new JSONArray(json);for(int i=0;i<a.length();i++){JSONObject e=a.getJSONObject(i);String id=e.optString("id");if(LibraryCore.valid(id))list.add(new LibraryCore.Entry(id,e.optString("title"),e.optLong("duration"),e.optLong("played")));}}catch(JSONException ignored){}}
    private String encode(List<LibraryCore.Entry> list){JSONArray a=new JSONArray();for(LibraryCore.Entry e:list){JSONObject v=new JSONObject();try{v.put("id",e.id).put("title",e.title).put("duration",e.duration).put("played",e.played);a.put(v);}catch(JSONException ignored){}}return a.toString();}
    synchronized void prune(){lastPurge=System.currentTimeMillis();if(core.purge(lastPurge))save();}
    synchronized void played(String id,String title,long duration){long now=System.currentTimeMillis();if(now-lastPurge>60*60*1000)prune();LibraryCore.Entry e=LibraryCore.find(core.history,id);if(e!=null&&now-lastWrite<60000)return;core.record(id,title,duration,now);lastWrite=now;save();}
    synchronized List<LibraryCore.Entry> entries(boolean favorite){prune();ArrayList<LibraryCore.Entry> result=new ArrayList<>();for(LibraryCore.Entry e:favorite?core.favorites:core.history)result.add(e.copy());return result;}
    synchronized boolean favorite(String id){return LibraryCore.find(core.favorites,id)!=null;}
    synchronized void toggle(LibraryCore.Entry e){core.toggleFavorite(e);if(core.favorites.isEmpty()&&loop==2)loop=0;save();}
    synchronized void loop(int value){loop=value==2&&core.favorites.isEmpty()?0:value;save();}
    synchronized void clearHistory(){core.history.clear();save();}
    synchronized String neighbor(String id,int direction){return core.neighbor(loop==2?core.favorites:core.history,id,loop==2?direction:-direction,loop==2);}
    private void save(){prefs.edit().putString("recent",encode(core.history)).putString("favorites",encode(core.favorites)).putInt("loop_mode",loop).apply();}
}
