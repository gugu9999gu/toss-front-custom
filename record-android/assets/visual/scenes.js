import * as T from './three.module.min.js';
import {AudioMotion,clamp} from './audio-motion.js';
import {createRacing,createFighting} from './game-scenes.js';
import {createDance,createOrchestra} from './stage-scenes.js';
import {SceneFilter} from './filters.js';
import {AudioCamera} from './camera-motion.js';
import {HandGeometryField,handPoints,handWave} from './hand-field.js';
import {createFluidSphere,createRibbonWave,createWaveTerrain,createSpectrumFlower,createDoubleHelix} from './wave-scenes.js';

// Procedural low-poly scenes; no textures, remote models, recordings, or title/ID input.
const canvas=document.querySelector('#scene'),fallback=document.querySelector('#fallback');
let renderer,webgl=false,lost=false;let filter=new SceneFilter(1,1);
try{renderer=new T.WebGLRenderer({canvas,alpha:false,antialias:true,powerPreference:'low-power'});renderer.setPixelRatio(1);renderer.outputColorSpace=T.SRGBColorSpace;webgl=true;}catch(e){fallback.style.display='flex';}
const scene=new T.Scene(),camera=new T.PerspectiveCamera(45,1,.1,180);
const hemi=new T.HemisphereLight(0xdcedff,0x444054,1.8);scene.add(hemi);
const key=new T.DirectionalLight(0xffffff,2.3);key.position.set(4,8,5);scene.add(key);
const rim=new T.DirectionalLight(0x77baff,1.2);rim.position.set(-5,3,-6);scene.add(rim);
let root=new T.Group();scene.add(root);
let state={mode:0,filter:0,cameraMotion:true,playing:false,signal:false,reduced:false,gain:1,colors:['#6fc2be','#798dff','#f08ac8'],background:'#000000',bands:[],rms:0};
let selected=-1,paintKey='',backgroundKey='',framingKey='',parts=[],grounds=[],updateScene=()=>{},last=0,frames=0,raf=0,lastData=0;
const waveSmooth=new Float32Array(32),audioWaveSmooth=new Float32Array(32);
const wavePoint=i=>waveSmooth[i%32];
const audioWavePoint=i=>audioWaveSmooth[i%32];
const motion=new AudioMotion(),colorA=new T.Color(),colorB=new T.Color();
let lowOnsets=0,highOnsets=0;
const audioCamera=new AudioCamera(),baseEye=new T.Vector3(),baseTarget=new T.Vector3(),offset=new T.Vector3(),target=new T.Vector3(),lastEye=new T.Vector3();
let cameraMoves=0;let handField=new HandGeometryField(root);

function material(color){return new T.MeshStandardMaterial({color,roughness:.72,metalness:.12,flatShading:true});}
function neutral(parent,geometry,color,x=0,y=0,z=0){const mesh=new T.Mesh(geometry,material(color));mesh.position.set(x,y,z);parent.add(mesh);return mesh;}
function gradient(parent,geometry,x=0,y=0,z=0){const m=material(0xffffff);m.vertexColors=true;const mesh=new T.Mesh(geometry,m);mesh.position.set(x,y,z);parent.add(mesh);parts.push(mesh);tint(mesh);return mesh;}
function tint(mesh){const g=mesh.geometry,p=g.getAttribute('position');g.computeBoundingBox();const box=g.boundingBox,span=Math.max(.01,box.max.y-box.min.y),colors=new Float32Array(p.count*3),stops=state.colors;
  for(let i=0;i<p.count;i++){const t=clamp((p.getY(i)-box.min.y)/span,0,1)*2,k=Math.min(1,Math.floor(t));colorA.set(stops[k]);colorB.set(stops[k+1]);colorA.lerp(colorB,t-k);colors[i*3]=colorA.r;colors[i*3+1]=colorA.g;colors[i*3+2]=colorA.b;}
  g.setAttribute('color',new T.BufferAttribute(colors,3));mesh.material.needsUpdate=true;
}
const box=(w,h,d)=>new T.BoxGeometry(w,h,d),ball=r=>new T.SphereGeometry(r,12,8),cylinder=(r,h)=>new T.CylinderGeometry(r,r,h,10);
function group(parent,x=0,y=0,z=0){const g=new T.Group();g.position.set(x,y,z);parent.add(g);return g;}
function dispose(){const geoms=new Set(),mats=new Set();root.traverse(o=>{if(o.geometry)geoms.add(o.geometry);if(o.material)(Array.isArray(o.material)?o.material:[o.material]).forEach(m=>mats.add(m));});scene.remove(root);geoms.forEach(g=>g.dispose());mats.forEach(m=>m.dispose());root=new T.Group();scene.add(root);parts=[];grounds=[];}
function look(x,y,z,tx=0,ty=1,tz=0){baseEye.set(x,y,z);baseTarget.set(tx,ty,tz);camera.position.copy(baseEye);camera.lookAt(baseTarget);}
function moveCamera(f,dt){
  if(selected===4||selected===5)return;
  const p=audioCamera.tick(f,dt,state.cameraMotion!==false,selected);
  offset.copy(baseEye).sub(baseTarget);
  const x=offset.x*Math.cos(p.orbit)+offset.z*Math.sin(p.orbit),z=offset.z*Math.cos(p.orbit)-offset.x*Math.sin(p.orbit);
  camera.position.set(baseTarget.x+x*p.dolly+p.truck,baseTarget.y+offset.y*p.dolly+p.lift,baseTarget.z+z*p.dolly);
  camera.up.set(Math.sin(p.roll),Math.cos(p.roll),0);target.copy(baseTarget);target.x+=p.focusX;target.y+=p.focusY;camera.lookAt(target);
  if(f.live&&!f.reduced&&state.cameraMotion!==false&&camera.position.distanceToSquared(lastEye)>1e-8)cameraMoves++;
}

function galaxy(){
  look(0,1.2,7.8,0,0,0);const count=1700,positions=new Float32Array(count*3),colors=new Float32Array(count*3);
  for(let i=0;i<count;i++){const r=Math.sqrt((i+.5)/count)*3.0,a=i*2.399963+r*1.5;positions[i*3]=Math.cos(a)*r;positions[i*3+1]=Math.sin(i*1.7)*.3*(1-r/3);positions[i*3+2]=Math.sin(a)*r;const t=i/count*2,k=Math.min(1,Math.floor(t));colorA.set(state.colors[k]).lerp(colorB.set(state.colors[k+1]),t-k);colors.set([colorA.r,colorA.g,colorA.b],i*3);}
  const geometry=new T.BufferGeometry();geometry.setAttribute('position',new T.BufferAttribute(positions,3));geometry.setAttribute('color',new T.BufferAttribute(colors,3));const cloud=new T.Points(geometry,new T.PointsMaterial({size:.045,vertexColors:true,transparent:true,opacity:.95,depthWrite:false}));root.add(cloud);const core=gradient(root,ball(.27));
  return (f,dt,moving)=>{const e=Math.min(2.4,f.energy);cloud.rotation.y=moving?f.phase*.13:cloud.rotation.y;cloud.rotation.z=moving?Math.sin(f.phase*.15)*.15:cloud.rotation.z;cloud.scale.setScalar(1+e*.23);core.scale.setScalar(.75+e*.5+f.boost*.25);};
}
function tunnel(){
  look(0,0,5,0,0,-15);const rings=[];for(let i=0;i<24;i++){const ring=gradient(root,new T.TorusGeometry(2.0,.035,6,40),0,0,-i*3);ring.userData.handReactive=true;rings.push(ring);}let flow=0;
  return (f,dt,moving)=>{flow+=(moving?f.energy*12+f.boost*13:0)*dt;rings.forEach((ring,i)=>{ring.position.z=4-((i*3-flow)%72+72)%72;ring.rotation.z=moving?f.phase*.1+i*.18:ring.rotation.z;ring.scale.setScalar(1+(state.bands[i%16]||0)*state.gain*.18);});camera.position.x=moving?audioWavePoint(0)*f.energy*.12:0;camera.position.y=moving?audioWavePoint(16)*f.energy*.08:0;camera.lookAt(0,0,-12);};
}
function build(mode){dispose();selected=mode;audioCamera.reset();camera.up.set(0,1,0);camera.fov=45;const context={root,camera,get state(){return state;},wavePoint,audioWavePoint,gradient,neutral,group,look};const factory=[createRacing,createFighting,createDance,createOrchestra,galaxy,tunnel,createFluidSphere,createRibbonWave,createWaveTerrain,createSpectrumFlower,createDoubleHelix][mode];updateScene=factory(context);handField=new HandGeometryField(root);paintKey='';framingKey='';}
function resize(){if(!renderer)return;const w=Math.max(1,canvas.clientWidth),h=Math.max(1,canvas.clientHeight);if(canvas.width!==w||canvas.height!==h)renderer.setSize(w,h,false);const focus=clamp(state.focus??.3,.05,.95),span=clamp(state.span??.4,.08,1),next=[w,h,focus,span,camera.fov].join('|');if(next!==framingKey){framingKey=next;camera.zoom=span;camera.setViewOffset(w,h,0,(.5-focus)*h,w,h);}}

function paint(){if(!webgl||lost)return;resize();filter.setMode(state.filter);filter.setRetro(state.retro);filter.resize(canvas.width,canvas.height);filter.render(renderer,scene,camera);frames++;}
function step(now){raf=0;const limit=state.reduced?83:33;if(last&&now-last<limit){raf=requestAnimationFrame(step);return;}const dt=last?Math.min(.06,(now-last)/1000):.016;last=now;const live=state.playing&&state.signal&&performance.now()-lastData<1000,input={...state,playing:live};const brushes=handPoints(state.gesture);for(let i=0;i<32;i++){const raw=live?clamp((state.wave?.[i]||0)*state.gain,-3,3):0,k=1-Math.exp(-dt/.08);audioWaveSmooth[i]+=(raw-audioWaveSmooth[i])*k;const target=live?handWave(raw,i/31,brushes):0;waveSmooth[i]+=(target-waveSmooth[i])*k;}const f=motion.tick(input,dt);if(f.onsetLeft>0)lowOnsets++;if(f.onsetRight>0)highOnsets++;lastEye.copy(camera.position);handField.restore();updateScene(f,dt,live&&!state.reduced);moveCamera(f,dt);resize();handField.apply(root,camera,{width:canvas.clientWidth,height:canvas.clientHeight,span:state.span,focus:state.focus},brushes,live?f.energy:0);paint();if(live)raf=requestAnimationFrame(step);}
function request(){if(!raf&&webgl&&!lost)raf=requestAnimationFrame(step);}
window.FrontScene={update(next){state={...state,...next};state.mode=Math.floor(clamp(state.mode,0,10));state.filter=Math.floor(clamp(state.filter,0,3));state.gain=clamp(state.gain,.25,4.2);if(!Array.isArray(state.colors)||state.colors.length!==3||state.colors.some(c=>!/^#[0-9a-f]{6}$/i.test(c)))state.colors=['#6fc2be','#798dff','#f08ac8'];lastData=performance.now();
    if(state.mode!==selected)build(state.mode);const colors=state.colors.join('|');if(colors!==paintKey){parts.forEach(tint);paintKey=colors;if(selected===4){build(4);paintKey=colors;}}
    if(/^#[0-9a-f]{6}$/i.test(state.background)&&backgroundKey!==state.background){backgroundKey=state.background;scene.background=new T.Color(state.background);scene.fog=new T.Fog(state.background,12,85);}
    request();return {webgl:webgl&&!lost,mode:selected,filter:state.filter,frames,active:state.playing&&state.signal,lowOnsets,highOnsets,cameraMoves,view:[camera.position.x,camera.position.y,camera.position.z]};},dispose(){if(raf)cancelAnimationFrame(raf);raf=0;dispose();filter.dispose();if(renderer){renderer.dispose();renderer.forceContextLoss();}webgl=false;}};
canvas.addEventListener('webglcontextlost',e=>{e.preventDefault();lost=true;if(raf)cancelAnimationFrame(raf);raf=0;});canvas.addEventListener('webglcontextrestored',()=>{lost=false;filter.dispose();filter=new SceneFilter(canvas.width,canvas.height);build(state.mode);request();});
window.addEventListener('resize',request);window.addEventListener('pagehide',()=>window.FrontScene.dispose());
build(0);paint();
