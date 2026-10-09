package dev.tossfront.record;

/** Only bounded hand-derived display parameters live here; no camera frame survives inference. */
final class GestureInfluence {
    float x=.5f,y=.5f,strength=1,spread=1;boolean active;
    void update(HandGestureCore.Output o){active=o.hands>0;float k=.3f;x+=(o.x-x)*k;y+=(o.y-y)*k;strength+=((active?o.strength:1)-strength)*k;spread+=((active?o.spread:1)-spread)*k;}
    void clear(){active=false;x=y=.5f;strength=spread=1;}
}
