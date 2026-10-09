const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
let checks=0;const check=(condition,label)=>{checks++;assert.ok(condition,label);};
(async()=>{
  const dir=path.resolve(__dirname,'../record-android/assets/visual'),url=s=>'data:text/javascript;base64,'+Buffer.from(s).toString('base64');
  const three=url(fs.readFileSync(path.join(dir,'three.module.min.js'),'utf8')),audio=url(fs.readFileSync(path.join(dir,'audio-motion.js'),'utf8'));
  const T=await import(three),{handPoints,repel,handWave,HandGeometryField}=await import(url(fs.readFileSync(path.join(dir,'hand-field.js'),'utf8').replace("'./three.module.min.js'",JSON.stringify(three)).replace("'./audio-motion.js'",JSON.stringify(audio))));
  check(handPoints({active:false,points:[{x:.3,y:.5}]}).length===0,'disabled tracking has no hand brush');
  check(handPoints({active:true,points:[{x:NaN,y:.5}]}).length===0,'invalid hand coordinates are rejected');
  const points=handPoints({active:true,points:[{x:.2,y:.5,power:1},{x:.8,y:.5,power:1},{x:.5,y:.5,power:1}]});
  check(points.length===2,'at most two hand fields can affect geometry');
  const near=repel(.25,.5,points,1,1);check(near.x>0&&near.y===0,'particles on the right of a hand move away to the right');
  const left=repel(.15,.5,points,1,1);check(left.x<0,'particles on the left move away to the left');
  const far=repel(.5,.05,points,1,1);check(far.x===0&&far.y===0,'far particles stay fixed');
  const exact=repel(.2,.5,points,1,1);check(Number.isFinite(exact.x)&&Number.isFinite(exact.y),'contact has no singularity');
  check(Math.abs(repel(.25,.5,points,1,1).x)>Math.abs(repel(.25,.5,points,0,1).x),'actual audio energy strengthens local particle response');
  check(handWave(0,.2,points)===0,'hand motion cannot invent an audio sample');
  check(handWave(.5,.2,points)>.5&&handWave(.5,.5,points)===.5,'hand shaping changes nearby samples while preserving distant ones');
  const root=new T.Group(),geometry=new T.BufferGeometry(),original=new Float32Array([-1,0,0,0,0,0,1,0,0]);
  geometry.setAttribute('position',new T.BufferAttribute(original.slice(),3));const cloud=new T.Points(geometry,new T.PointsMaterial());root.add(cloud);
  const camera=new T.OrthographicCamera(-2,2,2,-2,.1,10);camera.position.set(0,0,5);camera.lookAt(0,0,0);camera.updateMatrixWorld(true);
  const field=new HandGeometryField(root),layout={width:800,height:800,span:1,focus:.5},pose=JSON.stringify([camera.position.toArray(),camera.quaternion.toArray(),camera.projectionMatrix.toArray()]),rootPose=JSON.stringify([root.position.toArray(),root.rotation.toArray(),root.scale.toArray()]);
  field.apply(root,camera,layout,[points[0]],1);const moved=geometry.attributes.position.array.slice();
  check(Math.abs(moved[0]-original[0])>.01,'actual point-cloud vertices disperse near a hand');
  check(moved[6]===original[6]&&moved[7]===original[7],'far point-cloud vertices retain their audio geometry');
  check(JSON.stringify([camera.position.toArray(),camera.quaternion.toArray(),camera.projectionMatrix.toArray()])===pose,'particle deformation never changes camera position or projection');
  check(JSON.stringify([root.position.toArray(),root.rotation.toArray(),root.scale.toArray()])===rootPose,'particle deformation never translates, rotates, or scales the whole scene');
  field.restore();check(Array.from(geometry.attributes.position.array).every((x,i)=>x===original[i]),'removing the field restores exact unmodified geometry');
  field.apply(root,camera,layout,[points[0]],1);field.restore();field.apply(root,camera,layout,[points[0]],1);
  check(Array.from(geometry.attributes.position.array).every((x,i)=>Math.abs(x-moved[i])<1e-6),'stationary hands cannot accumulate geometric drift');
  field.restore();geometry.attributes.position.array[1]=.1;field.apply(root,camera,layout,[points[0]],1);field.restore();
  check(Math.abs(geometry.attributes.position.array[1]-.1)<1e-6,'field restoration preserves newly updated real audio geometry');
  const group=new T.Group(),instances=new T.InstancedMesh(new T.SphereGeometry(.1),new T.MeshBasicMaterial(),2);instances.userData.handReactiveInstances=true;
  const matrix=new T.Matrix4();instances.setMatrixAt(0,matrix.makeTranslation(-1,0,0));instances.setMatrixAt(1,matrix.makeTranslation(1,0,0));group.add(instances);
  const instanceField=new HandGeometryField(group);instanceField.apply(group,camera,layout,[points[0]],1);
  check(instances.instanceMatrix.array[12]!==-1&&instances.instanceMatrix.array[28]===1,'flower and helix instance centers deform locally');
  instanceField.restore();check(instances.instanceMatrix.array[12]===-1&&instances.instanceMatrix.array[28]===1,'instance matrices restore without modifying their scale');
  const {createRacing}=await import(url(fs.readFileSync(path.join(dir,'game-scenes.js'),'utf8').replace("'./three.module.min.js'",JSON.stringify(three)).replace("'./audio-motion.js'",JSON.stringify(audio))));
  const race=hand=>{
    const root=new T.Group(),views=[],raw=i=>Math.sin((i%32)*.6)*.5;
    const ctx={root,state:{bands:Array(16).fill(.3),gain:1},audioWavePoint:raw,wavePoint:i=>hand?handWave(raw(i),(i%32)/31,points):raw(i),look(...pose){views.push(pose);},
      group(parent,x=0,y=0,z=0){const o=new T.Group();o.position.set(x,y,z);parent.add(o);return o;},
      neutral(parent,g,color,x=0,y=0,z=0){const o=new T.Mesh(g,new T.MeshStandardMaterial({color}));o.position.set(x,y,z);parent.add(o);return o;},
      gradient(parent,g,x=0,y=0,z=0){return this.neutral(parent,g,0xffffff,x,y,z);}};
    const update=createRacing(ctx),f={live:true,energy:1.2,centroid:.35,waveBalance:.2,bass:.6,boost:.4};
    for(let i=0;i<40;i++)update(f,.05,true);
    const road=Array.from(root.children[0].geometry.attributes.position.array);
    root.traverse(o=>{o.geometry?.dispose();o.material?.dispose();});return {views,road};
  };
  const normalRace=race(false),handRace=race(true);
  check(JSON.stringify(normalRace.views)===JSON.stringify(handRace.views),'racing camera eye and target use raw audio even when hands reshape the road');
  check(normalRace.road.some((v,i)=>Math.abs(v-handRace.road[i])>.001),'racing road still reacts locally to hand-shaped audio');
  geometry.dispose();cloud.material.dispose();instances.geometry.dispose();instances.material.dispose();
  console.log(checks+' local waveform, particle and unchanged-camera checks passed');
})().catch(e=>{console.error(e.message);process.exitCode=1;});
