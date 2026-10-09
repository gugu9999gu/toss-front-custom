package dev.tossfront.record;

public final class GestureVolumeTest {
    public static void main(String[] args){
        int[][] cases={{-1,1,0,-1},{-2,0,0,-1},{-1,8,7,0},{1,0,1,1},{2,5,7,1},{1,15,15,0},{0,0,5,0}};
        for(int[] c:cases)if(GestureVolume.playback(c[0],c[1],c[2])!=c[3])throw new AssertionError("Unexpected gesture volume playback intent");
        System.out.println(cases.length+" gesture volume playback checks passed");
    }
}
