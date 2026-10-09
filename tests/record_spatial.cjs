const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),{spawnSync}=require('node:child_process');
let checks=0;const check=(condition,label)=>{checks++;assert.ok(condition,label);};
(async()=>{
  const directory=path.resolve(__dirname,'../record-android/assets/visual');
  const url=s=>'data:text/javascript;base64,'+Buffer.from(s).toString('base64');
  const threeURL=url(fs.readFileSync(path.join(directory,'three.module.min.js'),'utf8'));
  const motionURL=url(fs.readFileSync(path.join(directory,'audio-motion.js'),'utf8'));
  const load=file=>import(url(fs.readFileSync(path.join(directory,file),'utf8').replace("'./three.module.min.js'",JSON.stringify(threeURL)).replace("'./audio-motion.js'",JSON.stringify(motionURL))));
  const T=await import(threeURL),{AudioCamera}=await load('camera-motion.js'),waves=await load('wave-scenes.js');
  const f={live:true,reduced:false,energy:1.4,phase:4,bass:2.5,high:1.2,boost:1.4,waveBalance:.4,balance:.5,onsetLeft:1,onsetRight:0};
  for(let mode=0;mode<4;mode++){
    const camera=new AudioCamera();for(let i=0;i<40;i++)camera.tick({...f,phase:4+i*.08},.05,true,mode);
    const pose={...camera.pose};
    check(Math.abs(pose.orbit)>.005&&pose.dolly<1,'audio changes camera orbit and distance in mode '+mode);
    for(let i=0;i<20;i++)camera.tick({...f,live:false,phase:1000+i},.1,true,mode);
    check(JSON.stringify(camera.pose)===JSON.stringify(pose),'signal loss cannot advance the camera in mode '+mode);
    const disabled=camera.tick(f,.05,false,mode);
    check(disabled.orbit===0&&disabled.roll===0&&disabled.dolly===1,'camera setting restores neutral framing');
  }
  const reduced=new AudioCamera();reduced.tick(f,.05,true,2);reduced.tick({...f,reduced:true},.05,true,2);
  check(reduced.pose.orbit===0&&reduced.pose.dolly===1,'reduced motion disables camera transforms');
  const extreme=new AudioCamera();for(let i=0;i<100;i++)extreme.tick({...f,energy:1e20,boost:1e20,phase:Infinity,bass:Infinity,waveBalance:1e20},.05,true,0);
  check(Object.values(extreme.pose).every(Number.isFinite)&&extreme.pose.dolly>=.8&&Math.abs(extreme.pose.roll)<.06,'camera input is bounded and finite');
  const trail=new waves.SpectrumTrail(4,4),bands=Array(16).fill(.4),wave=Array(32).fill(.1);
  check(!trail.tick(bands,wave,1,.01,false)&&trail.values.every(x=>x===0),'paused terrain cannot accumulate a synthetic history');
  for(let i=0;i<5;i++)trail.tick(bands,wave,1,.01,true);
  const first=trail.values.slice(0,4);check(first.every(x=>x>0)&&trail.values.slice(4).every(x=>x===0),'terrain history samples actual audio at a bounded rate');
  trail.tick(Array(16).fill(.9),wave,4.2,.05,true);
  check(trail.values.slice(4,8).every((x,i)=>x===first[i]),'terrain moves the previous spectrum into its depth history');
  const held=trail.values.slice();trail.tick(bands,wave,1,.1,false);
  check(trail.values.every((x,i)=>x===held[i]),'paused terrain preserves its final audio shape');
  const state={bands:Array(16).fill(.1),gain:1.4,colors:['#6fc2be','#798dff','#f08ac8']};let samples=Array(32).fill(0);
  const snapshot=root=>{const numbers=[];root.traverse(o=>{for(const a of [o.geometry?.attributes.position,o.instanceMatrix])if(a)numbers.push(...a.array);});return numbers;};
  const colors=root=>{const numbers=[];root.traverse(o=>{for(const a of [o.geometry?.attributes.color,o.instanceColor])if(a)numbers.push(...a.array);});return numbers;};
  for(const name of ['createFluidSphere','createRibbonWave','createWaveTerrain','createSpectrumFlower','createDoubleHelix']){
    const root=new T.Group(),ctx={root,state,wavePoint:i=>samples[i%32]||0,look(){},gradient(parent,g){const m=new T.Mesh(g,new T.MeshStandardMaterial());parent.add(m);return m;}};
    state.bands=Array(16).fill(.1);state.colors=['#6fc2be','#798dff','#f08ac8'];samples=Array(32).fill(0);
    const update=waves[name](ctx),initial=snapshot(root);samples=Array.from({length:32},(_,i)=>Math.sin(i*.6)*.8);state.bands=Array(16).fill(.7);
    for(let i=0;i<12;i++)update({...f,phase:i*.1},.05,true);
    const playing=snapshot(root);check(playing.every(Number.isFinite)&&playing.some((x,i)=>Math.abs(x-initial[i])>.01),name+' changes actual 3D geometry from audio');
    for(let i=0;i<20;i++)update({...f,live:false,phase:999},.05,false);
    check(JSON.stringify(snapshot(root))===JSON.stringify(playing),name+' cannot animate from time alone while paused');
    const oldColors=colors(root);state.colors=['#ff3300','#ffee00','#2200ff'];update({...f,live:false},.05,false);
    check(colors(root).some((x,i)=>Math.abs(x-oldColors[i])>.01),name+' supports changing three-stop colors while paused');
    const geoms=new Set(),mats=new Set();root.traverse(o=>{if(o.geometry)geoms.add(o.geometry);if(o.material)mats.add(o.material);});geoms.forEach(g=>g.dispose());mats.forEach(m=>m.dispose());
  }
  for(const file of ['camera-motion.js','wave-scenes.js','scenes.js']){
    const result=spawnSync(process.execPath,['--input-type=module','--check'],{input:fs.readFileSync(path.join(directory,file),'utf8'),encoding:'utf8'});assert.equal(result.status,0,result.stderr);checks++;
  }
  console.log(checks+' camera, spatial waveform and module checks passed');
})().catch(error=>{console.error(error.message);process.exitCode=1;});
