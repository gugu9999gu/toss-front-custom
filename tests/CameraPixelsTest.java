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
        System.out.println(checks+" camera color, stride and orientation checks passed");
    }
}
