import {clamp} from './audio-motion.js';

// Audio phase is integrated by AudioMotion. Wall-clock time never drives a camera tour.
export class AudioCamera {
  constructor(){this.pose={orbit:0,truck:0,lift:0,dolly:1,roll:0,focusX:0,focusY:0};}
  reset(){Object.assign(this.pose,{orbit:0,truck:0,lift:0,dolly:1,roll:0,focusX:0,focusY:0});return this.pose;}
  tick(f,dt,enabled,mode){
    if(!enabled||f?.reduced)return this.reset();
    if(!f?.live)return this.pose;
    const p=this.pose,e=clamp(f.energy,0,3),amp=Math.min(1,e/.7);
    const phase=clamp(f.phase,0,1e9),balance=clamp(f.balance,-1,1),wave=clamp(f.waveBalance,-1,1);
    const bass=clamp(f.bass,0,4.2)/4.2,hit=clamp(f.boost,0,1.5)/1.5;
    const left=clamp(f.onsetLeft,0,1.5),right=clamp(f.onsetRight,0,1.5);
    let orbit=0,truck=0,lift=0,dolly=1,roll=0,focusX=0,focusY=0;
    if(mode===0){ // Chase camera: bank, suspension lift and an onset-driven push forward.
      orbit=wave*.11*amp;truck=balance*.22*amp;lift=bass*.12;
      dolly=1-hit*.15-bass*.035;roll=-wave*.055*amp;
    }else if(mode===1){ // Duel: arc around both fighters and bias framing toward the current strike.
      orbit=(Math.sin(phase*.07)*.25+wave*.07)*amp;
      truck=balance*.18*amp;lift=bass*.12;dolly=1-hit*.12;
      focusX=(right-left)*.22;roll=wave*hit*.035;
    }else if(mode===2){
      orbit=Math.sin(phase*.08)*.42*amp;truck=wave*.16*amp;
      lift=(clamp(f.high,0,4.2)/4.2)*.24;dolly=1-bass*.09-hit*.05;
      roll=wave*.045*amp;focusY=bass*.08;
    }else if(mode===3){ // Keep every musician inside the open ensemble framing.
      orbit=(Math.sin(phase*.055)*.23+balance*.035)*amp;
      truck=balance*.14*amp;lift=bass*.14;dolly=1-hit*.06;
      focusX=balance*.15*amp;
    }else{
      orbit=Math.sin(phase*.065)*.28*amp;lift=bass*.14;
      dolly=1-hit*.07;roll=wave*.02*amp;
    }
    const k=1-Math.exp(-clamp(dt,0,.1)/.22);
    p.orbit+=(orbit-p.orbit)*k;p.truck+=(truck-p.truck)*k;p.lift+=(lift-p.lift)*k;
    p.dolly+=(dolly-p.dolly)*k;p.roll+=(roll-p.roll)*k;p.focusX+=(focusX-p.focusX)*k;p.focusY+=(focusY-p.focusY)*k;
    return p;
  }
}
