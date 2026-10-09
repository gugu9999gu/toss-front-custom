package dev.tossfront.record;

import java.util.*;

/** Retention and queue policy, independent of Android and of the web page. */
final class LibraryCore {
    static final long RETENTION=30L*24*60*60*1000;
    static final class Entry {
        final String id; String title; long duration,played;
        Entry(String id,String title,long duration,long played){this.id=id;this.title=title;this.duration=duration;this.played=played;}
        Entry copy(){return new Entry(id,title,duration,played);}
    }
    final ArrayList<Entry> history=new ArrayList<>(),favorites=new ArrayList<>();
    static boolean valid(String id){return id!=null&&id.matches("[A-Za-z0-9_-]{11}");}
    static Entry find(List<Entry> entries,String id){for(Entry e:entries)if(e.id.equals(id))return e;return null;}
    boolean purge(long now){return history.removeIf(e->!valid(e.id)||now-e.played>=RETENTION);}
    void record(String id,String title,long duration,long now){if(!valid(id)||title==null||title.trim().isEmpty())return;purge(now);Entry e=find(history,id);if(e!=null)history.remove(e);history.add(0,new Entry(id,title.substring(0,Math.min(256,title.length())),Math.max(0,duration),now));}
    boolean toggleFavorite(Entry entry){if(entry==null||!valid(entry.id))return false;Entry old=find(favorites,entry.id);if(old!=null){favorites.remove(old);return false;}favorites.add(entry.copy());return true;}
    String neighbor(List<Entry> entries,String current,int direction,boolean wrap){if(entries.isEmpty())return null;int index=-1;for(int i=0;i<entries.size();i++)if(entries.get(i).id.equals(current))index=i;if(index<0)return entries.get(direction>0?0:entries.size()-1).id;int next=index+direction;if(wrap)next=(next+entries.size())%entries.size();return next>=0&&next<entries.size()?entries.get(next).id:null;}
}
