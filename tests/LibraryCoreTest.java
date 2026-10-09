package dev.tossfront.record;
public final class LibraryCoreTest {
    private static int assertions;
    private static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){LibraryCore l=new LibraryCore();long now=1700000000000L;
        l.record("abcdefghijk","First",90000,now-LibraryCore.RETENTION+1);l.record("lmnopqrstuv","Old",12000,now-LibraryCore.RETENTION);
        LibraryCore.Entry favorite=LibraryCore.find(l.history,"lmnopqrstuv");l.toggleFavorite(favorite);l.purge(now);
        check(l.history.size()==1,"30 day boundary expires history");check(l.favorites.size()==1,"Favorites survive retention");
        l.record("abcdefghijk","Updated",-1,now);check(l.history.size()==1,"Distinct history entry per video");check(l.history.get(0).duration==0,"Duration bounded");check(l.history.get(0).title.equals("Updated"),"Current title updated");
        l.record("bad?url","Bad",10,now);check(l.history.size()==1,"Invalid IDs rejected");l.toggleFavorite(l.history.get(0));check(l.neighbor(l.favorites,"abcdefghijk",1,true).equals("lmnopqrstuv"),"Favorite queue wraps");check(l.neighbor(l.favorites,"lmnopqrstuv",-1,true).equals("abcdefghijk"),"Reverse queue wraps");
        check(l.neighbor(l.history,"abcdefghijk",1,false)==null,"History does not fabricate next item");l.toggleFavorite(l.history.get(0));check(l.favorites.size()==1,"Toggle removes favorite only");check(l.history.size()==1,"Favorite toggle preserves history");
        PlaybackGate g=new PlaybackGate();check(!g.update("abcdefghijk",true,false,true,10),"Ad start does not transition");check(!g.update("abcdefghijk",false,true,true,20),"Ad end does not transition");check(!g.recordable(3000),"Ad not recordable");
        g.update("abcdefghijk",true,false,false,100);check(!g.recordable(2000),"Wait for actual content");check(g.recordable(2700),"Actual content is recordable");check(!g.update("abcdefghijk",false,false,false,3000),"Pause is not completion");check(!g.update("abcdefghijk",false,true,false,3100),"An end while paused never advances");g.update("abcdefghijk",true,false,false,3200);check(g.update("abcdefghijk",false,true,false,4000),"Content end transitions once");check(!g.update("abcdefghijk",false,true,false,4800),"Repeated ended callbacks ignored");g.update("abcdefghijk",true,false,false,6000);check(g.update("abcdefghijk",false,true,false,7000),"Next real play cycle can complete");g.reset();check(!g.update("abcdefghijk",false,true,false,8000),"Navigation reset rejects stale end");g.update("abcdefghijk",true,false,false,9000);check(!g.update("lmnopqrstuv",false,true,false,10000),"New unplayed ID does not advance");
        System.out.println("History/queue contracts: "+assertions+" assertions passed");
    }
}
