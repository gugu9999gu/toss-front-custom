package dev.tossfront.record;

public final class HandDetectionScheduleTest {
    static int checks;static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        HandDetectionSchedule schedule=new HandDetectionSchedule();check(schedule.search(1000),"The first frame discovers all hands");schedule.result(true,1,1000);
        check(!schedule.search(1100),"A single hand uses its continuous landmark tracker");check(schedule.search(1600),"A second hand is discovered at a bounded interval");schedule.result(true,2,1600);
        check(schedule.search(1700),"Two observed hands keep paired tracking on every model frame");schedule.result(true,1,1700);
        check(schedule.search(2000),"A brief second-hand omission does not switch to single-hand mode");schedule.result(true,1,2000);
        check(!schedule.search(2550),"An expired pair returns to single-hand tracking between searches");check(schedule.search(2600),"Second-hand discovery continues after paired tracking expires");
        schedule.result(true,2,2600);schedule.reset();check(schedule.search(2700),"Camera restart always rediscovers hands");schedule.result(true,1,2700);
        check(schedule.search(500),"Backward clock resets stale scheduling state");
        System.out.println(checks+" adaptive hand detection schedule checks passed");
    }
}
