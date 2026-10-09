// Initial animal and dancer rigs by Claude Opus 5.5; concert staging and beat accents refined by this project.
import * as T from './three.module.min.js';
import {clamp} from './audio-motion.js';
const E=(o,x,y,z,k)=>{const r=o.rotation;r.x+=(x-r.x)*k;r.y+=(y-r.y)*k;r.z+=(z-r.z)*k;};
const cyl=(a,b,h,n=8)=>new T.CylinderGeometry(a,b,h,n);
const sph=(r,w=10,h=8)=>new T.SphereGeometry(r,w,h);
const box=(x,y,z)=>new T.BoxGeometry(x,y,z);
function band(ctx,a,b){const s=ctx.state,B=s.bands||[],g=s.gain||1;let v=0;for(let i=a;i<=b;i++)v+=B[i]||0;return clamp(v/(b-a+1)*g,0,3);}
function wv(ctx,i){return clamp(ctx.wavePoint(i)||0,-3,3);}
function live(f,m){return f&&f.live&&m?1:0;}
function seg(ctx,p,len,r,x,y,z,col){const j=ctx.group(p,x,y,z),g=cyl(r,r*.8,len);if(col==null)ctx.gradient(j,g,0,-len/2,0);else ctx.neutral(j,g,col,0,-len/2,0);return j;}

export function createDance(ctx){
 const R=ctx.root,root=ctx.group(R,0,0,0),hip=ctx.group(root,0,1,0);
 ctx.gradient(hip,sph(.15),0,0,0).scale.set(1.2,.8,.9);
 const spine=ctx.group(hip,0,.05,0);ctx.gradient(spine,cyl(.17,.11,.6),0,.33,0);
 const neck=ctx.group(spine,0,.68,0);ctx.gradient(neck,new T.IcosahedronGeometry(.15,0),0,.17,0);
 const halo=ctx.gradient(neck,new T.TorusGeometry(.26,.014,4,24),0,.2,0);halo.rotation.x=1.2;
 const arms=[],legs=[];
 for(const s of[-1,1]){
  const sh=seg(ctx,spine,.34,.05,s*.21,.6,0),el=seg(ctx,sh,.32,.04,0,-.34,0),hand=ctx.gradient(el,sph(.06),0,-.34,0);arms.push({s,sh,el,hand});
  const th=seg(ctx,hip,.46,.07,s*.11,-.04,0),kn=seg(ctx,th,.44,.055,0,-.46,0),ft=ctx.gradient(kn,sph(.07),0,-.46,.05);ft.scale.set(.8,.5,1.6);legs.push({s,th,kn});
 }
 const N=14,trails=arms.map(()=>{const t=[];for(let i=0;i<N;i++)t.push(ctx.gradient(R,sph(.055*(1-i/N)+.012,6,4),0,1,0));return t;});
 const A=32,arc=[];
 for(let i=0;i<A;i++){const g=-1.25+2.5*i/(A-1);arc.push(ctx.gradient(R,sph(.07,6,4),Math.sin(g)*3.6,1.2,-Math.cos(g)*2.4-.6));}
 const P=18,sp=[];
 for(let i=0;i<P;i++){const m=ctx.neutral(R,new T.OctahedronGeometry(.06,0),0xfff2d0);m.visible=false;sp.push({m,v:new T.Vector3(),l:0});}
 let pi=0,prev=0;const tmp=new T.Vector3();
 ctx.look(0,1.35,7.6,0,1.1,0);
 return(f,dt,moving)=>{
  if(!f)return;
  const a=live(f,moving),k=1-Math.exp(-dt*9),q=1-Math.exp(-dt*4);
  const lo=band(ctx,0,2),mid=band(ctx,3,7),hi=band(ctx,8,15),b=clamp(f.bass/4.2,0,1);
  hip.position.y+=(1-b*.14*a-hip.position.y)*k;
  E(hip,0,f.balance*.5*a,wv(ctx,1)*.04*a,q);
  E(spine,(-b*.35+wv(ctx,5)*.06)*a,wv(ctx,7)*.12*a,f.waveBalance*.32*a,k);
  E(neck,((f.centroid-.5)*.5+wv(ctx,9)*.05)*a,-f.waveBalance*.3*a,wv(ctx,11)*.1*a,k);
  for(const r of arms){
   const s=r.s,on=s<0?f.onsetLeft:f.onsetRight,w=wv(ctx,s<0?6:22);
   E(r.sh,(-w*.4-mid*.25)*a,0,s*(.18+(on*.8+mid*.25+lo*.1)*a),k);
   E(r.el,-(.15+(hi*.45+f.highVocal/4.2*.6)*a),0,s*wv(ctx,s<0?12:18)*.15*a,k);
  }
  for(const r of legs){const w=wv(ctx,r.s<0?2:26);E(r.th,(-w*.22-b*.3)*a,0,r.s*(.04+lo*.06*a),k);E(r.kn,(b*.55+Math.abs(w)*.12)*a,0,0,k);}
  halo.rotation.z+=dt*f.energy*1.4*a;
  arms.forEach((r,j)=>{
   const t=trails[j];r.hand.getWorldPosition(tmp);R.worldToLocal(tmp);t[0].position.lerp(tmp,.8);
   for(let i=1;i<N;i++){const p=t[i].position;p.lerp(t[i-1].position,clamp(dt*14,0,1));p.y+=wv(ctx,i*2+j)*.012*a;}
  });
  for(let i=0;i<A;i++){
   const m=arc[i],ty=1.2+wv(ctx,i)*.38*a,sc=1+band(ctx,i>>1,i>>1)*.6*a;
   m.position.y+=(ty-m.position.y)*k;m.scale.setScalar(m.scale.x+(sc-m.scale.x)*k);
  }
  const ons=a*(f.onsetLeft+f.onsetRight);
  if(ons>.7&&prev<=.7){
   for(let n=0;n<3;n++){
    const s=sp[pi++%P];arms[n%2].hand.getWorldPosition(s.m.position);R.worldToLocal(s.m.position);
    s.v.set((Math.random()-.5)*3,Math.random()*2+.5,(Math.random()-.5)*1.5).multiplyScalar(.6+f.boost);s.l=1;s.m.visible=true;
   }
  }
  prev=ons;
  for(const s of sp){
   if(!a){s.l=0;s.m.visible=false;continue;}
   if(s.l<=0)continue;
   s.l-=dt*1.4;s.m.position.addScaledVector(s.v,dt);s.v.y-=dt*1.2;s.m.scale.setScalar(Math.max(s.l,0)*1.6+.01);s.m.rotation.y+=dt*4;
   if(s.l<=0)s.m.visible=false;
  }
 };
}

function player(ctx,x,z){
 const g=ctx.group(ctx.root,x,0,z);g.rotation.y=Math.atan2(-x,7-z);
 const b=ctx.group(g,0,0,0);
 for(const s of[-1,1])ctx.neutral(b,cyl(.06,.05,.5),0x1c1e26,s*.09,.25,0);
 ctx.gradient(b,cyl(.19,.27,.62),0,.78,0);
 ctx.neutral(b,box(.13,.3,.03),0xf4f1ea,0,.9,.2);
 ctx.neutral(b,new T.ConeGeometry(.05,.1,4),0x111111,0,1.03,.22).rotation.z=1.57;
 const head=ctx.group(b,0,1.17,0),arm=[];
 for(const s of[-1,1]){
  const sh=seg(ctx,b,.28,.05,s*.24,1.02,0),el=seg(ctx,sh,.26,.04,0,-.28,0),hand=ctx.group(el,0,-.27,0);
  ctx.neutral(hand,sph(.045,6,5),0xf0e6d8);arm.push({sh,el,hand});
 }
 return{g,b,head,L:arm[0],Rt:arm[1]};
}

export function createOrchestra(ctx){
 const ow=player(ctx,0,.9),oh=ow.head;
 ctx.neutral(oh,sph(.2),0x7a5a3c,0,.1,0).scale.set(1.1,.95,.9);
 for(const s of[-1,1]){
  ctx.neutral(oh,new T.ConeGeometry(.05,.16,5),0x5a3f28,s*.13,.3,0).rotation.z=-s*.4;
  ctx.neutral(oh,cyl(.07,.07,.03,10),0xffcf4a,s*.08,.13,.17).rotation.x=1.57;
 }
 ctx.neutral(oh,new T.ConeGeometry(.03,.08,4),0xe08a20,0,.06,.2).rotation.x=1.9;
 ctx.neutral(ow.Rt.hand,cyl(.008,.012,.4,5),0xfafafa,0,-.18,0);
 const fr=player(ctx,-2.5,.1),fh=fr.head;
 ctx.neutral(fh,sph(.2),0x58a845,0,.06,0).scale.set(1.35,.7,1.05);
 for(const s of[-1,1])ctx.neutral(fh,sph(.07,8,6),0x7cc95c,s*.12,.17,.06);
 const vio=ctx.group(fr.b,-.12,1.05,.2);vio.rotation.set(-.3,0,.9);
 ctx.neutral(vio,sph(.1,8,6),0x9a4f1e,0,0,0).scale.set(.75,1.25,.35);
 ctx.neutral(vio,cyl(.015,.015,.26,5),0x2a160a,0,.24,0);
 ctx.neutral(fr.Rt.hand,cyl(.006,.006,.6,4),0xd8c8a0,0,0,0).rotation.z=1.57;
 const de=player(ctx,-1.15,-1),dh=de.head;
 ctx.neutral(dh,sph(.15),0xa36c3a,0,.06,0).scale.set(.9,1,1.1);
 ctx.neutral(dh,new T.ConeGeometry(.08,.2,8),0xb98552,0,.02,.18).rotation.x=1.57;
 for(const s of[-1,1]){
  const an=ctx.group(dh,s*.08,.18,0);an.rotation.z=-s*.35;
  ctx.neutral(an,cyl(.012,.018,.3,5),0xe8dcc0,0,.15,0);
  ctx.neutral(an,cyl(.01,.014,.14,5),0xe8dcc0,s*.05,.2,0).rotation.z=-s*.8;
 }
 const ce=ctx.group(de.b,0,.55,.32);ce.rotation.x=-.15;
 ctx.neutral(ce,sph(.2,10,8),0x7a3a14,0,0,0).scale.set(.85,1.4,.4);
 ctx.neutral(ce,cyl(.018,.018,.6,5),0x221208,0,.5,0);
 ctx.neutral(de.Rt.hand,cyl(.007,.007,.7,4),0xd8c8a0,0,0,0).rotation.z=1.57;
 const li=player(ctx,1.15,-1),lh=li.head;
 ctx.neutral(lh,new T.TorusGeometry(.17,.08,6,14),0xa8561c,0,.06,-.03);
 ctx.neutral(lh,sph(.16),0xe0a640,0,.06,0);
 ctx.neutral(lh,sph(.06,8,6),0xf2d29a,0,.01,.14).scale.set(1.3,.8,1);
 const tp=ctx.group(lh,0,-.02,.2);
 ctx.neutral(tp,cyl(.022,.022,.4,6),0xf0c040,0,0,.2).rotation.x=1.57;
 ctx.neutral(tp,new T.ConeGeometry(.09,.14,10),0xf0c040,0,0,.43).rotation.x=-1.57;
 const vals=[];
 for(let i=0;i<3;i++)vals.push(ctx.neutral(tp,cyl(.012,.012,.06,5),0xd8a830,-.03+i*.03,.04,.15));
 const pe=player(ctx,2.5,.1),ph=pe.head;
 ctx.neutral(ph,sph(.16),0x15161c,0,.05,0).scale.set(1,1.1,1);
 ctx.neutral(ph,sph(.11,8,6),0xf6f6f2,0,.03,.08).scale.set(1,1.1,.7);
 ctx.neutral(ph,new T.ConeGeometry(.035,.12,6),0xf2b21c,0,.02,.2).rotation.x=1.57;
 const pn=ctx.group(pe.g,0,0,.55);
 ctx.neutral(pn,box(.9,.12,.32),0x101014,0,.72,0);
 ctx.neutral(pn,box(.8,.03,.14),0xf8f8f4,0,.8,-.1);
 const keys=[];
 for(let j=0;j<5;j++)keys.push(ctx.neutral(pn,box(.05,.02,.08),0x111111,-.32+j*.16,.82,-.12));
 const floor=ctx.neutral(ctx.root,new T.CircleGeometry(6.8,48),0x1b1c24,0,-.045,-.8);floor.rotation.x=-Math.PI/2;
 const ensemble=[ow,fr,de,li,pe],back=[];
 for(let i=0;i<4;i++){
  const animal=player(ctx,(i-1.5)*1.75,-2.5);ctx.neutral(animal.head,sph(.17),i%2?0xa47c5a:0x737986,0,.07,0);
  for(const side of[-1,1])ctx.neutral(animal.head,new T.ConeGeometry(.06,.12,5),0x535964,side*.12,.22,0).rotation.z=-side*.3;
  const body=ctx.neutral(animal.b,sph(.13),0x85512f,0,.95,.27);body.scale.set(.7,1.5,.4);
  ctx.neutral(animal.Rt.hand,cyl(.007,.007,.65,4),0xe3c9a5,0,0,0).rotation.z=1.57;back.push(animal);ensemble.push(animal);
 }
 const stands=[];
 for(const musician of ensemble.slice(1)){
  const x=musician.g.position.x,z=musician.g.position.z+.55,stand=ctx.group(ctx.root,x+.3,0,z);
  ctx.neutral(stand,cyl(.012,.015,.75,5),0x4b4d53,0,.37,0);const paper=ctx.neutral(stand,box(.32,.2,.022),0xded8c3,0,.85,0);paper.rotation.x=-.25;stands.push(stand);
 }
 const lights=[];
 for(let i=0;i<4;i++){
  const beam=ctx.neutral(ctx.root,new T.ConeGeometry(.9,5,12,1,true),i%2?0xaac8ff:0xffd99d,(i-1.5)*1.7,3,-1.6);
  beam.rotation.z=(i-1.5)*.09;beam.material.dispose();beam.material=new T.MeshBasicMaterial({color:i%2?0xaac8ff:0xffd99d,transparent:true,opacity:.025,depthWrite:false,side:T.DoubleSide});lights.push(beam);
 }
 ctx.look(0,2.3,10.1,0,1,-.8);
 return(f,dt,moving)=>{
  if(!f)return;
  const a=live(f,moving),k=1-Math.exp(-dt*14),w=i=>wv(ctx,i)*a,pulse=clamp(f.boost||0,0,1.5)*a;
  const lo=band(ctx,0,2)*a,mid=band(ctx,5,9)*a,hi=band(ctx,10,15)*a,en=clamp(f.energy/3,0,1)*a;
  E(ow.Rt.sh,-.9-w(3)*.5-en*.55-pulse*.3,0,.2+f.waveBalance*.35*a,k);E(ow.Rt.el,-.4-w(9)*.65-pulse*.22,0,w(14)*.2,k);
  E(ow.L.sh,-.5-en*.7,0,-.3-lo*.15,k);E(ow.L.el,-.6+w(20)*.3,0,0,k);
  E(ow.head,(f.centroid-.5)*.4*a,f.balance*.4*a,w(6)*.05,k);E(ow.b,-en*.12,0,f.waveBalance*.05*a,k);
  E(fr.L.sh,-1.2,0,-.3,k);E(fr.L.el,-.6+w(5)*.08,0,0,k);
  E(fr.Rt.sh,-.9-hi*.12,0,-.45-w(12)*.52,k);E(fr.Rt.el,-1.1+w(13)*.5,0,0,k);
  E(fr.head,.1,0,.35+hi*.06,k);E(fr.b,0,0,w(10)*.04,k);
  E(de.L.sh,-.55,0,.25,k);E(de.L.el,-1.1,0,w(4)*.12,k);
  E(de.Rt.sh,-.6,0,-.35-w(2)*.58-lo*.14,k);E(de.Rt.el,-.9+w(1)*.3,0,0,k);
  E(de.head,lo*.08,0,-.15,k);E(de.b,-lo*.04,0,w(0)*.03,k);
  E(li.L.sh,-1.25,0,.3,k);E(li.L.el,-1.25,0,0,k);E(li.Rt.sh,-1.25-mid*.05,0,-.3,k);E(li.Rt.el,-1.25,0,0,k);
  E(li.head,-mid*.12-w(8)*.04,0,0,k);E(li.b,-mid*.04,0,w(11)*.03,k);
  for(let i=0;i<3;i++){const v=vals[i];v.position.y+=(.04-band(ctx,6+i*3,8+i*3)*.025*a-v.position.y)*k;}
  E(pe.L.sh,-1.05-Math.max(0,w(3))*.15,0,.2+w(6)*.1,k);E(pe.L.el,-.35,0,0,k);
  E(pe.Rt.sh,-1.05-Math.max(0,w(19))*.15,0,-.2+w(22)*.1,k);E(pe.Rt.el,-.35,0,0,k);
  E(pe.b,-lo*.05,0,w(15)*.04,k);
  for(let j=0;j<5;j++){const m=keys[j];m.position.y+=(.82-band(ctx,j*3,j*3+2)*.012*a-m.position.y)*k;}
  for(let i=0;i<back.length;i++){const m=back[i],energy=band(ctx,i*3,i*3+3)*a;E(m.L.sh,-1.05,0,-.2,k);E(m.L.el,-.7,0,0,k);E(m.Rt.sh,-.85-energy*.1,0,-.4-w(i*5+2)*.5,k);E(m.Rt.el,-1+w(i*6+4)*.5,0,0,k);E(m.b,-energy*.025,0,w(i*4)*.06,k);}
  for(let i=0;i<lights.length;i++){const m=lights[i];m.material.opacity=.025+a*(.012+band(ctx,i*4,i*4+3)*.018+pulse*.025);m.rotation.z=(i-1.5)*.09+w(i*7)*.035;}
  const focus=clamp((f.high-f.bass)/4.2,-1,1)*a;ctx.look(focus*.2,2.3+pulse*.1,10.1-pulse*.22,0,1,-.8);

 };
}
