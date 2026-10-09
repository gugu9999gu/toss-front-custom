package dev.tossfront.record;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

/** User-selected visual design; never contains playback history or metadata. */
final class Appearance {
    static final String[] MODES={"레코드","선 파형","스펙트럼","입체 링","점 구체","오로라","궤도","레이싱","격투","댄스","오케스트라","입자 은하","워프 터널","유체 구체","입체 리본","파형 지형","스펙트럼 꽃","이중 나선"};
    static final String[] VISUAL_FILTERS={"원본","디더링","픽셀","픽셀 + 디더링"};
    static final String[] VISUAL_PALETTES={"단색","오로라","선셋","바다","캔디","직접 설정"};
    private static final int[][] VISUAL_COLORS={{0xff6fc2be,0xff6fc2be,0xff6fc2be},{0xff60edcd,0xff667cff,0xffe77ad0},{0xffffcf75,0xffff745e,0xff9854df},{0xff91f5d0,0xff38cadd,0xff4f69ed},{0xffff9ada,0xffb49aff,0xff79d9ff}};
    static final String[] THEMES={"크림","안개","먹","심야","게임보이"};
    static final String[] ACCENTS={"갈색","테라코타","올리브","청록","남색"};
    private static final String[][] PALETTES={
        {"#F4EDE1","#FBF7F0","#2A211B","#6F6257","#D9CCBC"},
        {"#EEF1F3","#F8FAFB","#1C2329","#5B6670","#D3DAE0"},
        {"#16140F","#211E18","#EDE7DD","#A39A8E","#3A352D"},
        {"#000000","#111111","#F2F2F2","#9A9A9A","#2A2A2A"},
        {"#9BBC0F","#A8C932","#0F380F","#306230","#8BAC0F"}
    };
    private static final String[][] POINTS={{"#8A5A3B","#A8472F","#56632F","#2C6E73","#3B5487"},{"#C89A72","#E58A6E","#A9B86E","#6FC2BE","#93A8DB"}};
    private final SharedPreferences prefs;
    int mode,theme,point,gain,area,sensitivity,visualPalette,visualFilter,handCameraIndex;
    final int[] customColors={0xff6fc2be,0xff798dff,0xfff08ac8};
    boolean immersive,reduced,showTitle,showProgress,showPlay,showSkip,showLoop,autoSkip,showVolume,cameraMotion;
    boolean handCamera,handVisual,handVolume,handClap,handNext,handTrace,handTrail,handFast;
    Appearance(Context context){prefs=context.getSharedPreferences("appearance",0);reload();}
    void reload(){mode=bounded(prefs.getInt("mode",0),MODES.length);theme=bounded(prefs.getInt("theme",0),THEMES.length);point=bounded(prefs.getInt("point",0),5);gain=bounded(prefs.getInt("gain",1),3);sensitivity=ResponseMath.percent(prefs.getInt("sensitivity",gain==0?70:gain==2?140:100));visualPalette=bounded(prefs.getInt("visual_palette",0),VISUAL_PALETTES.length);visualFilter=bounded(prefs.getInt("visual_filter",0),VISUAL_FILTERS.length);for(int i=0;i<3;i++)customColors[i]=prefs.getInt("visual_color_"+i,i==0?pointColor(point):customColors[i])|0xff000000;area=Math.max(30,Math.min(100,prefs.getInt("area",100)));immersive=prefs.getBoolean("immersive",true);reduced=prefs.getBoolean("reduced",false);showTitle=prefs.getBoolean("title",true);showProgress=prefs.getBoolean("progress",true);showPlay=prefs.getBoolean("play",true);showSkip=prefs.getBoolean("skip",true);showLoop=prefs.getBoolean("loop",true);autoSkip=prefs.getBoolean("auto_skip",true);showVolume=prefs.getBoolean("volume",true);cameraMotion=prefs.getBoolean("camera_motion",true);handCamera=prefs.getBoolean("hand_camera",false);handCameraIndex=Math.max(0,prefs.getInt("hand_camera_index",0));handVisual=prefs.getBoolean("hand_visual",true);handVolume=prefs.getBoolean("hand_volume",true);handClap=prefs.getBoolean("hand_clap",true);handNext=prefs.getBoolean("hand_next",true);handTrace=prefs.getBoolean("hand_trace",true);handTrail=prefs.getBoolean("hand_trail",true);handFast=prefs.getBoolean("hand_fast",true);}
    private int bounded(int value,int max){return Math.max(0,Math.min(max-1,value));}
    void save(){SharedPreferences.Editor e=prefs.edit().putInt("prefs_v",8).putInt("mode",mode).putInt("theme",theme).putInt("point",point).putInt("gain",gain).putInt("sensitivity",sensitivity).putInt("visual_palette",visualPalette).putInt("visual_filter",visualFilter).putInt("area",area).putBoolean("immersive",immersive).putBoolean("reduced",reduced).putBoolean("title",showTitle).putBoolean("progress",showProgress).putBoolean("play",showPlay).putBoolean("skip",showSkip).putBoolean("loop",showLoop).putBoolean("auto_skip",autoSkip).putBoolean("volume",showVolume).putBoolean("camera_motion",cameraMotion).putBoolean("hand_camera",handCamera).putInt("hand_camera_index",handCameraIndex).putBoolean("hand_visual",handVisual).putBoolean("hand_volume",handVolume).putBoolean("hand_clap",handClap).putBoolean("hand_next",handNext).putBoolean("hand_trace",handTrace).putBoolean("hand_trail",handTrail).putBoolean("hand_fast",handFast);for(int i=0;i<3;i++)e.putInt("visual_color_"+i,customColors[i]);e.apply();}
    int bg(){return Color.parseColor(PALETTES[theme][0]);}
    int surface(){return Color.parseColor(PALETTES[theme][1]);}
    int text(){return Color.parseColor(PALETTES[theme][2]);}
    int muted(){return Color.parseColor(PALETTES[theme][3]);}
    int track(){return Color.parseColor(PALETTES[theme][4]);}
    int accent(){return theme==4?0xff0f380f:pointColor(point);}
    int pointColor(int index){return Color.parseColor(POINTS[dark()?1:0][index]);}
    boolean dark(){return theme>=2&&theme!=4;}
    boolean motion(){return !reduced&&ValueAnimator.areAnimatorsEnabled();}
    float gain(){return sensitivity/100f;}
    int[] visualColors(){if(theme==4)return new int[]{0xff0f380f,0xff306230,0xff8bac0f};return visualPalette==0?new int[]{customColors[0],customColors[0],customColors[0]}:visualPalette==5?customColors.clone():VISUAL_COLORS[visualPalette].clone();}
    void reset(){mode=theme=point=visualPalette=visualFilter=0;gain=1;sensitivity=100;customColors[0]=0xff6fc2be;customColors[1]=0xff798dff;customColors[2]=0xfff08ac8;area=100;immersive=showTitle=showProgress=showPlay=showSkip=showLoop=showVolume=autoSkip=cameraMotion=true;reduced=handCamera=false;handCameraIndex=0;handVisual=handVolume=handClap=handNext=handTrace=handTrail=handFast=true;save();}
}
