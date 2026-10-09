// Audio feature and onset design by Claude Opus 5.5; reviewed and bounded for this app.
export const clamp=(n,a,b)=>Math.max(a,Math.min(b,Number.isFinite(n)?n:0));
const KEYS=['energy','bass','mid','high','lowVocal','highVocal','centroid','roughness','waveBalance','crest'];
const SM=['energy','bass','mid','high','lowVocal','highVocal'];
function mean(bands,a,b,g){let t=0;for(let i=a;i<b;i++)t+=clamp(bands[i],0,1);return t/(b-a)*g;}
function zero(o){for(let i=0;i<KEYS.length;i++)o[KEYS[i]]=0;o.gain=0;o.live=false;return o;}
export function features(input,out){
  out=out||{};const inp=input||{},wave=inp.wave||[],bands=inp.bands||[];
  let sum=0,abs=0,peak=0,zc=0,prev=0;
  for(let i=0;i<wave.length;i++){const w=clamp(wave[i],-1,1),m=Math.abs(w);sum+=w;abs+=m;if(m>peak)peak=m;if(i>0&&w*prev<0&&Math.abs(w-prev)>.02)zc++;prev=w;}
  const silent=wave.length?abs===0:!(clamp(inp.rms,0,1)>0);
  if(!inp.playing||!inp.signal||silent)return zero(out);
  const gain=clamp(inp.gain,.25,4.2),rms=clamp(inp.rms,0,1);
  let w=0,c=0;for(let i=0;i<16;i++){const v=clamp(bands[i],0,1);w+=v;c+=v*i;}
  out.energy=clamp(rms*gain*2.4,0,3);out.bass=mean(bands,0,5,gain);out.mid=mean(bands,5,11,gain);out.high=mean(bands,11,16,gain);
  out.lowVocal=clamp(inp.lowVocal,0,1)*gain;out.highVocal=clamp(inp.highVocal,0,1)*gain;
  out.centroid=w>0?c/w/15:0;out.roughness=wave.length>1?zc/(wave.length-1):0;
  out.waveBalance=abs>0?sum/abs:0;out.crest=rms>0?clamp(Math.max(peak,clamp(inp.peak,0,1))/rms,0,8):0;
  out.gain=gain;out.live=true;return out;
}
export class AudioMotion{
  constructor(){this.prev=new Float32Array(16);this.f={};this.o={};this.s={balance:0};this.phase=0;this.reset();}
  reset(){this.primed=false;this.lastGain=0;this.mFlux=0;this.since=0;this.iv=0;this.count=0;this.attack=0;this.pl=0;this.ph=0;this.prev.fill(0);for(let i=0;i<SM.length;i++)this.s[SM[i]]=0;this.s.balance=0;}
  tick(input,dt){
    dt=clamp(dt,0,.1);const f=features(input,this.f),o=this.o,s=this.s,red=!!(input&&input.reduced);
    if(!f.live){if(this.primed)this.reset();zero(o);o.balance=0;o.flux=0;o.flow=0;o.onsetLeft=0;o.onsetRight=0;o.beat=false;o.boost=0;o.tempo=0;o.interval=0;o.reduced=red;o.phase=this.phase;return o;}
    const bands=input.bands||[];let lo=0,hi=0;
    for(let i=0;i<16;i++){const v=clamp(bands[i],0,1)*f.gain,d=v-this.prev[i];this.prev[i]=v;if(d>0){if(i<6)lo+=d;else if(i>9)hi+=d;else{lo+=d*.4;hi+=d*.6;}}}
    const dl=f.lowVocal-this.pl,dh=f.highVocal-this.ph;this.pl=f.lowVocal;this.ph=f.highVocal;
    if(dl>0)lo+=dl*1.5;if(dh>0)hi+=dh*1.5;
    if(!this.primed||this.lastGain!==f.gain){this.primed=true;this.lastGain=f.gain;lo=0;hi=0;}
    const flux=lo+hi,thr=this.mFlux*1.5+.08;let onL=0,onR=0,beat=false;
    this.since+=dt;this.attack*=Math.exp(-dt/.22);
    if(flux>thr&&this.since>.09){
      const st=clamp((flux-thr)*1.8,0,1.5)*(red?.2:1),r=lo/flux;
      if(r>=.38)onL=st*Math.min(1,r*1.6);if(r<=.62)onR=st*Math.min(1,(1-r)*1.6);beat=true;
      if(this.count>0&&this.since<1.6)this.iv=this.iv?this.iv+(clamp(this.since,.12,1.6)-this.iv)*.3:clamp(this.since,.12,1.6);
      this.count++;this.since=0;this.attack=Math.max(this.attack,st);
    }
    this.mFlux+=(flux-this.mFlux)*(1-Math.exp(-dt/.45));
    for(let i=0;i<SM.length;i++){const k=SM[i],v=f[k],b=1-Math.exp(-dt/(v>s[k]?.06:.18));s[k]+=(v-s[k])*b;o[k]=s[k];}
    const tb=(f.lowVocal-f.highVocal)/(f.lowVocal+f.highVocal+1e-4);s.balance+=(tb-s.balance)*(1-Math.exp(-dt/.15));
    if(!red&&s.energy>0)this.phase+=dt*s.energy*2.2;
    o.centroid=f.centroid;o.roughness=f.roughness;o.waveBalance=f.waveBalance;o.crest=f.crest;o.balance=s.balance;
    o.flux=flux;o.flow=this.mFlux;o.onsetLeft=onL;o.onsetRight=onR;o.beat=beat;o.boost=this.attack;
    o.interval=this.count>1?this.iv:0;o.tempo=o.interval?60/o.interval:0;o.live=true;o.reduced=red;o.phase=this.phase;return o;
  }
}
