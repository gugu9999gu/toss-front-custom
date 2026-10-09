package dev.tossfront.record;

public final class RecordNavigationTest {
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    public static void main(String[] args){
        LibraryCore library=new LibraryCore();HistoryQueue queue=new HistoryQueue();long now=1700000000000L;
        String old="abcdefghijk",current="lmnopqrstuv",later="zyxwvutsrqp";
        library.record(old,"Old",90000,now-1000);library.record(current,"Current",120000,now);
        check(old.equals(queue.neighbor(library.history,current,-1)),"Previous follows chronological history");
        check(queue.neighbor(library.history,current,1)==null,"Latest history item has no invented next");
        library.record(old,"Replayed",90000,now+1000);
        check(current.equals(queue.neighbor(library.history,old,1)),"Replaying history cannot destroy the forward path");
        check(queue.neighbor(library.history,old,-1)==null,"History start does not wrap");
        check(old.equals(queue.neighbor(library.history,current,-1)),"Repeated back and forward uses the same sequence");
        library.record(later,"Manual selection",90000,now+2000);queue.clear();
        check(old.equals(queue.neighbor(library.history,later,-1)),"Manual playback rebuilds chronology from current history");
        check(queue.neighbor(library.history,"invalid",-1)==null,"Invalid current ID cannot navigate");
        check(queue.neighbor(library.history,later,0)==null,"Direction zero is rejected");
        library.history.removeIf(e->e.id.equals(old));
        check(current.equals(queue.neighbor(library.history,later,-1)),"Expired history entries are removed from a navigation snapshot");
        library.history.clear();queue.clear();
        check(queue.neighbor(library.history,current,-1)==null,"Single current item cannot fabricate a previous item");
        check(queue.neighbor(library.history,current,1)==null,"Single current item cannot fabricate a next item");
        System.out.println(checks+" chronological navigation checks passed");
    }
}
