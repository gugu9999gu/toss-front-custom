package dev.tossfront.record;

/** Local repulsion fields deform audio geometry, never a camera or the whole scene. */
final class GestureInfluence {
    final float[] xs=new float[2],ys=new float[2],powers=new float[2];
    int count;boolean active;float dx,dy;
    void update(HandGestureCore.Output o){
        int next=Math.min(2,o.hands);
        for(int i=0;i<next;i++){
            float x=.14f+.72f*o.xs[i],y=.1f+.8f*o.ys[i];
            if(i>=count){xs[i]=x;ys[i]=y;powers[i]=o.powers[i];}
            else{xs[i]+=(x-xs[i])*.5f;ys[i]+=(y-ys[i])*.5f;powers[i]+=(o.powers[i]-powers[i])*.5f;}
        }
        count=next;active=count>0;
    }
    void clear(){count=0;active=false;dx=dy=0;}
    void displace(float x,float y,float energy,float aspect){
        dx=dy=0;if(!active)return;aspect=Math.max(.4f,Math.min(3,aspect));
        for(int i=0;i<count;i++){
            float ax=(x-xs[i])*aspect,ay=y-ys[i],d2=ax*ax+ay*ay,r=.28f;
            if(d2>=r*r)continue;
            float weight=1-d2/(r*r),distance=(float)Math.sqrt(d2);
            if(distance<.0001f){ax=.006f;ay=-.006f;distance=.009f;}
            float force=weight*weight*(.035f+Math.min(1,Math.max(0,energy))*.08f)*powers[i]/(distance+.025f);
            dx+=ax*force/aspect;dy+=ay*force;
        }
    }
}
