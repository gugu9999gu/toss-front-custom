package dev.tossfront.record;
import java.util.*;
public final class CameraPixelsTest {
    static int checks;static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    static int[] gray(byte[] out){int[] result=new int[out.length/4];for(int i=0;i<result.length;i++)result[i]=out[i*4]&255;return result;}
    static byte[] convert(int rotation,boolean mirror){byte[] out=new byte[16];CameraPixels.convert(2,2,new byte[]{16,81,(byte)145,(byte)235},2,1,new byte[]{(byte)128},1,1,new byte[]{(byte)128},1,1,rotation,mirror,out);return out;}
    public static void main(String[] args){
        check(Arrays.equals(gray(convert(0,false)),new int[]{0,76,150,255}),"Limited-range luminance becomes full-range RGBA");
        check(Arrays.equals(gray(convert(90,false)),new int[]{150,0,255,76}),"Portrait clockwise rotation preserves pixel positions");
        check(Arrays.equals(gray(convert(90,true)),new int[]{0,150,76,255}),"Mirrored portrait matches a front-facing display");
        check(Arrays.equals(gray(convert(180,false)),new int[]{255,150,76,0}),"Upside-down sensors are supported");
        check(Arrays.equals(gray(convert(270,false)),new int[]{76,255,0,150}),"Counterclockwise sensors are supported");
        byte[] padded=new byte[16];CameraPixels.convert(2,2,new byte[]{16,0,81,0,0,0,(byte)145,0,(byte)235},6,2,new byte[]{(byte)128,0},2,2,new byte[]{(byte)128,0},2,2,0,false,padded);
        check(Arrays.equals(gray(padded),new int[]{0,76,150,255}),"Padded rows and interleaved plane strides are honored");
        byte[] red=new byte[4];CameraPixels.convert(1,1,new byte[]{81},1,1,new byte[]{90},1,1,new byte[]{(byte)240},1,1,0,false,red);
        check((red[0]&255)>=250&&(red[1]&255)<3&&(red[2]&255)<3&&(red[3]&255)==255,"Chroma channels and alpha reach the inference model correctly");
        check(CameraPixels.width(320,240,90)==240&&CameraPixels.height(320,240,90)==320,"Model dimensions match the oriented image");
        byte[] source=new byte[40];int[] values={16,81,145,235};
        for(int row=0;row<4;row++)for(int col=0;col<4;col++)source[row*10+col*2]=(byte)values[row/2*2+col/2];
        byte[] neutral=new byte[8];Arrays.fill(neutral,(byte)128);byte[] small=new byte[16];
        CameraPixels.convert(4,4,source,10,2,neutral,4,2,neutral,4,2,0,false,2,small);
        check(Arrays.equals(gray(small),new int[]{0,76,150,255}),"Fast sampling keeps every full-camera quadrant with padded planes");
        CameraPixels.convert(4,4,source,10,2,neutral,4,2,neutral,4,2,90,true,2,small);
        check(Arrays.equals(gray(small),gray(convert(90,true))),"Fast mode preserves front-facing rotation and mirror coordinates");
        byte[] mean=new byte[4];CameraPixels.convert(2,2,new byte[]{16,81,(byte)145,(byte)235},2,1,new byte[]{(byte)128},1,1,new byte[]{(byte)128},1,1,0,false,2,mean);
        check((mean[0]&255)==120&&(mean[3]&255)==255,"Fast sampling averages a 2x2 block rather than discarding three pixels");
        byte[] luma=new byte[16];Arrays.fill(luma,(byte)81);CameraPixels.convert(4,4,luma,4,1,new byte[]{90,(byte)128,(byte)128,(byte)128},2,1,new byte[]{(byte)240,(byte)128,(byte)128,(byte)128},2,1,0,false,2,small);
        check((small[0]&255)>=250&&(small[1]&255)<3&&(small[4]&255)==76,"Fast mode samples the matching full-frame chroma blocks");
        boolean odd=false;try{CameraPixels.convert(3,2,new byte[6],3,1,new byte[2],2,1,new byte[2],2,1,0,false,2,mean);}catch(IllegalArgumentException expected){odd=true;}
        check(odd,"Invalid sampling sizes fail before indexing a plane");
        boolean shortBuffer=false;try{CameraPixels.convert(2,2,new byte[4],2,1,new byte[1],1,1,new byte[1],1,1,0,false,2,new byte[3]);}catch(IllegalArgumentException expected){shortBuffer=true;}
        check(shortBuffer,"Short output buffers are rejected before writing pixels");
        System.out.println(checks+" camera color, stride and orientation checks passed");
    }
}
