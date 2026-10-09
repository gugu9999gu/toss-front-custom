package dev.tossfront.record;

/** Gain stays linear below the display boundary, without clipping every band at 1. */
final class ResponseMath {
    static int percent(int value){return Math.max(25,Math.min(420,value));}
    static float signed(float value,float gain){float x=value*gain;return Math.max(-3,Math.min(3,x));}
    static float band(float value,float gain){return Math.max(0,Math.min(3,value*gain));}
    static float display(float value){return value<=1?value:1+(float)Math.tanh(value-1)*.65f;}
    static float rms(float[] wave){double sum=0;for(float value:wave)sum+=value*value;return wave.length==0?0:(float)Math.sqrt(sum/wave.length);}
}
