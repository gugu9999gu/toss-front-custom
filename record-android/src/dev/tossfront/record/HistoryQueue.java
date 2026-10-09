package dev.tossfront.record;

import java.util.*;

/** In-memory chronological navigation, unaffected by a replay moving its history entry to the top. */
final class HistoryQueue {
    private final ArrayList<String> ids=new ArrayList<>();
    void clear(){ids.clear();}
    String neighbor(List<LibraryCore.Entry> recent,String current,int direction){
        if(!LibraryCore.valid(current)||direction==0)return null;
        Set<String> valid=new HashSet<>();for(LibraryCore.Entry e:recent)if(LibraryCore.valid(e.id))valid.add(e.id);
        ids.removeIf(id->!id.equals(current)&&!valid.contains(id));
        if(!ids.contains(current)){
            ids.clear();for(int i=recent.size()-1;i>=0;i--){String id=recent.get(i).id;if(LibraryCore.valid(id)&&!ids.contains(id))ids.add(id);}
            if(!ids.contains(current))ids.add(current);
        }
        int next=ids.indexOf(current)+(direction>0?1:-1);
        return next>=0&&next<ids.size()?ids.get(next):null;
    }
}
