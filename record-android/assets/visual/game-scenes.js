// Initial rigs by Claude Opus 5.5 at the user's request; contact, defense and beat accents refined by this project.
import * as T from './three.module.min.js';
import {clamp} from './audio-motion.js';
const PI=Math.PI;
const ease=(v,t,k,dt)=>v+(t-v)*(1-Math.exp(-k*dt));
function env(){return {a:0,t:0,d:.4,v:0,arm:true};}
function stepEnv(e,on,f,dt,ok){
  if(!ok){e.a=0;e.v=0;e.arm=true;return 0;}
  if(on<=.01)e.arm=true;
  if(e.arm&&on>.08){e.arm=false;e.a=clamp(on,0,1.5);e.t=0;e.d=clamp(f.interval||.4,.12,1.6);}
  if(e.a>0){e.t+=dt;const u=e.t/e.d;if(u>=1){e.a=0;e.v=0;}else e.v=e.a*(u<.22?u/.22:(1-u)/.78);}
  return e.v;
}
function armT(o,a,d,c,r,wb){
  o.pitch=Math.min(1.7,a*(.55+.9*c+.3*d));
  o.yaw=a*r*(.4+.6*d);
  o.ext=Math.min(1.95,a*(1.25-.7*r));
  o.hand=a>0?wb:0;
}
function kickT(o,a,w,c){o.hip=Math.min(1.6,a*w*(.9+.7*c));o.knee=a*w*.7*(1-c);}
export function fighterTargets(f,leftAttack,rightAttack,out={}){
  const q=f||{},live=!!q.live;
  const la=out.leftArm||(out.leftArm={}),ra=out.rightArm||(out.rightArm={});
  const lk=out.leftKick||(out.leftKick={}),rk=out.rightKick||(out.rightKick={});
  const a=live?clamp(+leftAttack||0,0,1.5):0,b=live?clamp(+rightAttack||0,0,1.5):0;
  const lo=(q.bass||0)+(q.lowVocal||0),hi=(q.high||0)+(q.highVocal||0),tot=lo+hi+1e-3;
  const dl=lo/tot,dh=hi/tot,c=clamp(q.centroid||0,0,1),r=clamp(q.roughness||0,0,1),wb=clamp(q.waveBalance||0,-1,1);
  armT(la,a,dl,c,r,wb);armT(ra,b,dh,c,r,-wb);
  kickT(lk,a,clamp((q.bass||0)/4.2,0,1),c);kickT(rk,b,clamp((q.high||0)/4.2,0,1),c);
  out.leftAdvance=a*(.3+.5*dl);out.rightAdvance=b*(.3+.5*dh);
  return out;
}
export function createRacing(ctx){
  const R=64,C=6,W=5.2,L=1.05,root=ctx.root;
  let bend=0,dist=0,drift=0,sy=0,sv=0,boost=0,camX=0;
  const rg=new T.PlaneGeometry(1,1,C,R-1),pa=rg.attributes.position,P=pa.array,hs=new Float32Array(R);
  const cx=j=>-bend*j*j*.0035;
  const shape=()=>{
    for(let j=0;j<R;j++){const x0=cx(j),z=4-j*L,bk=bend*Math.min(j,20)*.03;
      for(let k=0;k<=C;k++){const i=(j*(C+1)+k)*3,u=k/C-.5;P[i]=x0+u*W;P[i+1]=hs[j]-u*bk;P[i+2]=z;}}
    pa.needsUpdate=true;rg.computeVertexNormals();
  };
  shape();
  const road=ctx.gradient(root,rg);road.material.side=T.DoubleSide;
  const car=ctx.group(root,0,.6,0);
  ctx.gradient(car,new T.SphereGeometry(.5,18,10)).scale.set(.78,.3,2.1);
  ctx.neutral(car,new T.SphereGeometry(.3,12,8),0x18202c,0,.14,-.3).scale.set(.95,.65,1.7);
  const pod=new T.CylinderGeometry(.15,.2,1.7,10);pod.rotateX(PI/2);
  const fg=new T.ConeGeometry(.12,1,10);fg.translate(0,.5,0);fg.rotateX(PI/2);
  const fl=[];
  for(const s of [-1,1]){
    ctx.gradient(car,pod,s*.66,-.06,.15);
    const m=ctx.gradient(car,fg,s*.66,-.06,1);
    m.material=m.material.clone();m.material.transparent=true;m.material.opacity=.8;m.material.depthWrite=false;
    if(m.material.emissive)m.material.emissive.setRGB(.7,.7,.7);
    fl.push(m);
  }
  ctx.gradient(car,new T.BoxGeometry(1.8,.035,.32),0,.3,.95);
  const pg=new T.CylinderGeometry(.04,.07,1,6);pg.translate(0,.5,0);
  const pm=new T.MeshStandardMaterial({color:0xcfe0ff,emissive:0x2a3a66,roughness:.4});
  const N=24,py=new T.InstancedMesh(pg,pm,N);root.add(py);
  const M=new T.Matrix4(),Q=new T.Quaternion(),V=new T.Vector3(),S=new T.Vector3(1,1,1),be=env();
  return (f,dt,moving)=>{
    dt=Math.min(dt||0,.1);
    const ok=!!(f&&f.live&&moving),q=f||{},st=ctx.state,b16=st.bands||[],g=st.gain||1;
    let asym=0;
    if(ok)for(let i=0;i<8;i++)asym+=ctx.wavePoint(i)-ctx.wavePoint(i+8);
    const e=ok?clamp(q.energy||0,0,3):0;
    const bv=stepEnv(be,ok?Math.max(q.onsetLeft||0,q.onsetRight||0):0,q,dt,ok);
    boost=ease(boost,ok?Math.max(bv,(q.boost||0)*.35):0,14,dt);
    const dT=ok?clamp(((q.centroid||0)-.5)*1.2+(q.waveBalance||0)*.4+asym*.04,-.65,.65)*Math.min(1,e):0;
    drift=ease(drift,dT,4,dt);bend=ease(bend,drift,1.5,dt);
    if(ok)dist+=(e*5+bv*9)*dt;
    for(let j=0;j<R;j++)hs[j]=ok?ctx.wavePoint(j)*.09*Math.min(1,j/10):ease(hs[j],0,3,dt);
    shape();
    const sT=ok?-clamp((q.bass||0)/4.2,0,1)*.16:0;
    sv+=((sT-sy)*70-sv*10)*dt;sy+=sv*dt;
    car.position.set(drift*.7,.6+sy+hs[4],0);
    car.rotation.set(-bv*.06+sv*.08,drift*.9,-drift*.45);
    for(const m of fl){m.visible=boost>.02;m.scale.set(1,1,.05+boost*1.6);}
    for(let k=0;k<N;k++){
      const z=-58+((Math.floor(k/2)*5+dist)%60),j=clamp(Math.round((4-z)/L),0,R-1),sd=k%2?1:-1;
      V.set(cx(j)+sd*(W*.5+.5),hs[j],z);S.set(1,clamp(.5+(b16[k%16]||0)*g*.25,.3,3),1);
      M.compose(V,Q,S);py.setMatrixAt(k,M);
    }
    py.instanceMatrix.needsUpdate=true;
    camX=ease(camX,drift*.8,3,dt);
    ctx.look(camX,1.45,6.6,drift*.5,.7,-6);
  };
}
export function createFighting(ctx){
  const root=ctx.root;
  const G={l:new T.CapsuleGeometry(.065,.2,2,6),t:new T.CapsuleGeometry(.08,.28,2,6),b:new T.CapsuleGeometry(.17,.3,3,8),h:new T.SphereGeometry(.13,12,8),p:new T.SphereGeometry(.15,10,6),f:new T.IcosahedronGeometry(.085,0)};
  ctx.neutral(root,new T.CircleGeometry(7,32),0x161a24).rotation.x=-PI/2;
  const rig=sd=>{
    const r=ctx.group(root,sd*1.05,0,0);r.rotation.y=-sd*PI/2;
    const hip=ctx.group(r,0,.95,0);ctx.gradient(hip,G.p);
    const sp=ctx.group(hip,0,.08,0);ctx.gradient(sp,G.b,0,.28,0);ctx.neutral(sp,G.h,0xe6d0bf,0,.68,0);
    const arms=[-1,1].map(o=>{
      const sh=ctx.group(sp,o*.25,.46,0);ctx.gradient(sh,G.l,0,-.16,0);
      const el=ctx.group(sh,0,-.32,0);ctx.gradient(el,G.l,0,-.16,0);
      ctx.neutral(el,G.f,sd<0?0xff8a53:0x69cfff,0,-.34,0).scale.set(1.25,1.35,1.5);
      return {sh,el,side:o};
    });
    const legs=[-1,1].map(o=>{
      const hp=ctx.group(hip,o*.11,-.04,0);ctx.gradient(hp,G.t,0,-.22,0);
      const kn=ctx.group(hp,0,-.44,0);ctx.gradient(kn,G.t,0,-.22,0);
      return {hp,kn};
    });
    return {r,hip,sp,arms,legs,sd,s:{p:0,y:0,e:0,h:0,kh:0,kk:0,ad:0,rc:0}};
  };
  const pose=(R,A,K,adv,rec,w,dt)=>{
    const s=R.s;
    s.p=ease(s.p,A.pitch,18,dt);s.y=ease(s.y,A.yaw,18,dt);s.e=ease(s.e,A.ext,18,dt);s.h=ease(s.h,A.hand,18,dt);
    s.kh=ease(s.kh,K.hip,14,dt);s.kk=ease(s.kk,K.knee,14,dt);s.ad=ease(s.ad,adv,10,dt);s.rc=ease(s.rc,rec,12,dt);
    const lead=clamp(.5+.5*s.h,0,1);
    R.arms.forEach((m,i)=>{
      const k=i?lead:1-lead,o=m.side;
      m.sh.rotation.set(-.5-s.p*k-s.rc*.32,-o*s.y*k,o*(.12+s.rc*.12));
      m.el.rotation.x=-2.05+s.e*k-s.rc*.28;
    });
    R.legs[0].hp.rotation.x=.25-s.kh;R.legs[0].kn.rotation.x=.25+s.kk;
    R.legs[1].hp.rotation.x=-.25+s.kh*.25;R.legs[1].kn.rotation.x=.25;
    R.r.position.x=R.sd*(1.05-s.ad*.55+s.rc*.25);
    R.hip.position.y=.95+w*.015-s.kh*.05;
    R.sp.rotation.set(s.p*.2+s.ad*.25-s.rc*.5,s.y*.5,s.rc*R.sd*.1);
  };
  const F=[rig(-1),rig(1)],E=[env(),env()],tg={},off={live:false};
  const glow=new T.Mesh(new T.RingGeometry(.07,.14,20),new T.MeshBasicMaterial({color:0xffe0a0,transparent:true,opacity:0,depthWrite:false,side:T.DoubleSide}));glow.position.set(0,1.25,.35);root.add(glow);
  const sparks=Array.from({length:12},(_,i)=>{const m=new T.Mesh(new T.OctahedronGeometry(.035,0),new T.MeshBasicMaterial({color:i%2?0xffddb4:0xff8a53}));m.visible=false;root.add(m);return m;});
  let contact=0,previousA=0,previousB=0,impactSide=0;

  return (f,dt,moving)=>{
    dt=Math.min(dt||0,.1);
    const ok=!!(f&&f.live&&moving),q=f||{};
    const a=stepEnv(E[0],ok?q.onsetLeft||0:0,q,dt,ok),b=stepEnv(E[1],ok?q.onsetRight||0:0,q,dt,ok);
    fighterTargets(ok?q:off,a,b,tg);
    pose(F[0],tg.leftArm,tg.leftKick,tg.leftAdvance,b,ok?ctx.wavePoint(3):0,dt);
    pose(F[1],tg.rightArm,tg.rightKick,tg.rightAdvance,a,ok?ctx.wavePoint(11):0,dt);
    if(ok&&((a>.32&&previousA<=.32)||(b>.32&&previousB<=.32))){contact=Math.min(1.5,Math.max(a,b));impactSide=a>b?1:-1;glow.position.set(impactSide*.22,1.05+(q.centroid||0)*.5,.3);}
    previousA=a;previousB=b;contact=ok?contact*Math.exp(-dt/0.14):0;
    glow.material.opacity=Math.min(.85,contact);glow.scale.setScalar(1+(1-Math.min(1,contact))*3);
    for(let i=0;i<sparks.length;i++){const m=sparks[i];m.visible=contact>.035;const angle=i*PI*2/sparks.length,r=(1-Math.min(1,contact))*.6;m.position.set(glow.position.x+Math.cos(angle)*r,glow.position.y+Math.sin(angle)*r,.35+i*.012);m.scale.setScalar(contact);}
    const hit=ok?Math.max(a,b):0;ctx.look(impactSide*hit*.08,1.4+hit*.08,7.4-hit*.28,0,1.05+hit*.025,0);
  };
}
