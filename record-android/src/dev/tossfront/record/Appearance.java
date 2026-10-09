package dev.tossfront.record;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

/** User-selected visual design; never contains playback history or metadata. */
final class Appearance {
    static final String[] MODES={"레코드","선 파형","스펙트럼","입체 링","점 구체","오로라","궤도"};
    static final String[] THEMES={"크림","안개","먹","심야"};
    static final String[] ACCENTS={"갈색","테라코타","올리브","청록","남색"};
    private static final String[][] PALETTES={
        {"#F4EDE1","#FBF7F0","#2A211B","#6F6257","#D9CCBC"},
        {"#EEF1F3","#F8FAFB","#1C2329","#5B6670","#D3DAE0"},
        {"#16140F","#211E18","#EDE7DD","#A39A8E","#3A352D"},
        {"#000000","#111111","#F2F2F2","#9A9A9A","#2A2A2A"}
    };
    private static final String[][] POINTS={{"#8A5A3B","#A8472F","#56632F","#2C6E73","#3B5487"},{"#C89A72","#E58A6E","#A9B86E","#6FC2BE","#93A8DB"}};
    private final SharedPreferences prefs;
    int mode,theme,point,gain,area;
    boolean immersive,reduced,showTitle,showProgress,showPlay,showSkip,showLoop,autoSkip;
    Appearance(Context context){prefs=context.getSharedPreferences("appearance",0);reload();}
    void reload(){mode=bounded(prefs.getInt("mode",0),MODES.length);theme=bounded(prefs.getInt("theme",0),4);point=bounded(prefs.getInt("point",0),5);gain=bounded(prefs.getInt("gain",1),3);area=Math.max(30,Math.min(100,prefs.getInt("area",100)));immersive=prefs.getBoolean("immersive",true);reduced=prefs.getBoolean("reduced",false);showTitle=prefs.getBoolean("title",true);showProgress=prefs.getBoolean("progress",true);showPlay=prefs.getBoolean("play",true);showSkip=prefs.getBoolean("skip",true);showLoop=prefs.getBoolean("loop",true);autoSkip=prefs.getBoolean("auto_skip",true);}
    private int bounded(int value,int max){return Math.max(0,Math.min(max-1,value));}
    void save(){prefs.edit().putInt("prefs_v",3).putInt("mode",mode).putInt("theme",theme).putInt("point",point).putInt("gain",gain).putInt("area",area).putBoolean("immersive",immersive).putBoolean("reduced",reduced).putBoolean("title",showTitle).putBoolean("progress",showProgress).putBoolean("play",showPlay).putBoolean("skip",showSkip).putBoolean("loop",showLoop).putBoolean("auto_skip",autoSkip).apply();}
    int bg(){return Color.parseColor(PALETTES[theme][0]);}
    int surface(){return Color.parseColor(PALETTES[theme][1]);}
    int text(){return Color.parseColor(PALETTES[theme][2]);}
    int muted(){return Color.parseColor(PALETTES[theme][3]);}
    int track(){return Color.parseColor(PALETTES[theme][4]);}
    int accent(){return pointColor(point);}
    int pointColor(int index){return Color.parseColor(POINTS[dark()?1:0][index]);}
    boolean dark(){return theme>=2;}
    boolean motion(){return !reduced&&ValueAnimator.areAnimatorsEnabled();}
    float gain(){return gain==0?.7f:gain==2?1.4f:1;}
    void reset(){mode=theme=point=0;gain=1;area=100;immersive=showTitle=showProgress=showPlay=showSkip=showLoop=autoSkip=true;reduced=false;save();}
}
