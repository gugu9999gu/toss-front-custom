import * as T from './three.module.min.js';
import {clamp} from './audio-motion.js';

const TAU=Math.PI*2;
const active=(f,moving)=>!!(f?.live&&moving&&!f.reduced);
const band=(ctx,i)=>clamp((ctx.state.bands?.[i%16]||0)*(ctx.state.gain||1),0,4.2);
const wave=(ctx,i)=>clamp(ctx.wavePoint(i%32),-3,3);

// Palette changes touch preallocated buffers; audio is kept only in these transient scene buffers.
function palette(ctx,geometry,weights){
  const values=new Float32Array(weights.length*3),attribute=new T.BufferAttribute(values,3);
  geometry.setAttribute('color',attribute);
  const a=new T.Color(),b=new T.Color();let key='';
  return ()=>{
    const colors=ctx.state.colors,next=colors.join('|');if(next===key)return;key=next;
    for(let i=0;i<weights.length;i++){
      const t=clamp(weights[i],0,1)*2,k=Math.min(1,Math.floor(t));
      a.set(colors[k]).lerp(b.set(colors[k+1]),t-k);a.toArray(values,i*3);
    }
    attribute.needsUpdate=true;
  };
}
function coloredInstances(ctx,mesh,count){
  const a=new T.Color(),b=new T.Color();let key='';
  return ()=>{
    const colors=ctx.state.colors,next=colors.join('|');if(next===key)return;key=next;
    for(let i=0;i<count;i++){const t=i/Math.max(1,count-1)*2,k=Math.min(1,Math.floor(t));mesh.setColorAt(i,a.set(colors[k]).lerp(b.set(colors[k+1]),t-k));}
    mesh.instanceColor.needsUpdate=true;
  };
}

export function createFluidSphere(ctx){
  const rows=24,columns=64,count=rows*columns,dirs=new Float32Array(count*3),positions=new Float32Array(count*3),radii=new Float32Array(count),weights=new Float32Array(count);
  for(let y=0;y<rows;y++)for(let x=0;x<columns;x++){
    const i=y*columns+x,p=(y+.5)/rows*Math.PI,a=x/columns*TAU;
    dirs.set([Math.sin(p)*Math.cos(a),Math.cos(p),Math.sin(p)*Math.sin(a)],i*3);
    radii[i]=1.6;weights[i]=y/(rows-1);for(let k=0;k<3;k++)positions[i*3+k]=dirs[i*3+k]*1.6;
  }
  const geometry=new T.BufferGeometry();geometry.setAttribute('position',new T.BufferAttribute(positions,3).setUsage(T.DynamicDrawUsage));
  const tint=palette(ctx,geometry,weights);tint();
  const cloud=new T.Points(geometry,new T.PointsMaterial({vertexColors:true,size:.045,transparent:true,opacity:.94,depthWrite:false}));cloud.frustumCulled=false;ctx.root.add(cloud);
  ctx.look(0,.5,7.4,0,0,0);
  return (f,dt,moving)=>{
    tint();if(!active(f,moving))return;
    const k=1-Math.exp(-dt/.1);
    for(let i=0;i<count;i++){
      const target=1.6+wave(ctx,i)*.25+band(ctx,(i>>4)%16)*.15+clamp(f.bass,0,4.2)*.045;
      radii[i]+=(target-radii[i])*k;
      for(let j=0;j<3;j++)positions[i*3+j]=dirs[i*3+j]*radii[i];
    }
    geometry.attributes.position.needsUpdate=true;cloud.rotation.y=f.phase*.045;cloud.rotation.z=Math.sin(f.phase*.09)*.12;
  };
}

export function createRibbonWave(ctx){
  const count=96,strips=[];
  for(let lane=0;lane<5;lane++){
    const positions=new Float32Array(count*2*3),weights=new Float32Array(count*2),indices=[];
    for(let i=0;i<count;i++){
      const u=i/(count-1),x=(u-.5)*7.4;
      for(let side=0;side<2;side++){const p=(i*2+side)*3;positions[p]=x;positions[p+1]=(lane-2)*.42+(side-.5)*.11;positions[p+2]=Math.sin(u*TAU)*.45+(lane-2)*.36;weights[i*2+side]=u;}
      if(i<count-1){const a=i*2;indices.push(a,a+1,a+2,a+1,a+3,a+2);}
    }
    const g=new T.BufferGeometry();g.setAttribute('position',new T.BufferAttribute(positions,3).setUsage(T.DynamicDrawUsage));g.setIndex(indices);
    const tint=palette(ctx,g,weights);tint();
    const m=new T.Mesh(g,new T.MeshBasicMaterial({vertexColors:true,side:T.DoubleSide}));m.frustumCulled=false;ctx.root.add(m);strips.push({positions,g,tint,lane});
  }
  ctx.look(0,1.7,8,0,0,0);
  return (f,dt,moving)=>{
    const ok=active(f,moving),k=1-Math.exp(-dt/.09);
    for(const s of strips){
      s.tint();if(!ok)continue;
      for(let i=0;i<count;i++){
        const u=i/(count-1),sample=Math.floor(u*31),bend=wave(ctx,sample+s.lane*3)*.48,depth=band(ctx,sample>>1)*.1;
        for(let side=0;side<2;side++){
          const p=(i*2+side)*3,ty=(s.lane-2)*.42+bend+(side-.5)*(.1+depth);
          const tz=Math.sin(u*TAU+s.lane*.45+f.phase*.04)*(.45+depth)+(s.lane-2)*.36;
          s.positions[p+1]+=(ty-s.positions[p+1])*k;s.positions[p+2]+=(tz-s.positions[p+2])*k;
        }
      }
      s.g.attributes.position.needsUpdate=true;
    }
  };
}

export class SpectrumTrail {
  constructor(rows,columns){this.rows=rows;this.columns=columns;this.values=new Float32Array(rows*columns);this.elapsed=0;}
  tick(bands,waves,gain,dt,live){
    if(!live){this.elapsed=0;return false;}
    this.elapsed+=clamp(dt,0,.1);if(this.elapsed<.05)return false;this.elapsed%=.05;
    const v=this.values,n=this.columns;v.copyWithin(n,0,v.length-n);
    for(let i=0;i<n;i++){
      const bin=Math.min(15,Math.floor(i/n*16)),wi=Math.min(31,Math.floor(i/n*32));
      v[i]=clamp(clamp(bands?.[bin],0,1)*clamp(gain,.25,4.2)*.55+clamp(waves?.[wi],-3,3)*.23,-.65,2.5);
    }
    return true;
  }
}
export function createWaveTerrain(ctx){
  const columns=40,rows=28,trail=new SpectrumTrail(rows,columns),g=new T.PlaneGeometry(7.4,5.6,columns-1,rows-1);g.rotateX(-Math.PI/2);
  const p=g.attributes.position.array,weights=new Float32Array(rows*columns),samples=new Float32Array(32);
  for(let i=0;i<weights.length;i++)weights[i]=(i%columns)/(columns-1);
  const tint=palette(ctx,g,weights);tint();g.attributes.position.setUsage(T.DynamicDrawUsage);
  const grid=new T.Mesh(g,new T.MeshBasicMaterial({vertexColors:true,wireframe:true}));grid.frustumCulled=false;ctx.root.add(grid);
  ctx.look(0,3.4,7.3,0,.1,-.4);
  return (f,dt,moving)=>{
    tint();const ok=active(f,moving);
    for(let i=0;i<32;i++)samples[i]=wave(ctx,i);
    if(!trail.tick(ctx.state.bands,samples,ctx.state.gain,dt,ok))return;
    for(let row=0;row<rows;row++)for(let col=0;col<columns;col++){
      const i=row*columns+col;p[i*3+1]=trail.values[i]*Math.pow(1-row/rows,.55);
    }
    g.attributes.position.needsUpdate=true;
  };
}

export function createSpectrumFlower(ctx){
  const count=64,geometry=new T.ConeGeometry(.1,.85,5),material=new T.MeshStandardMaterial({color:0xffffff,roughness:.58,metalness:.18});
  const petals=new T.InstancedMesh(geometry,material,count);petals.frustumCulled=false;ctx.root.add(petals);
  const tint=coloredInstances(ctx,petals,count);tint();
  const core=ctx.gradient(ctx.root,new T.IcosahedronGeometry(.28,1));
  const dirs=Array.from({length:count},(_,i)=>{const y=1-2*(i+.5)/count,r=Math.sqrt(1-y*y),a=i*2.399963;return new T.Vector3(Math.cos(a)*r,y,Math.sin(a)*r);});
  const matrix=new T.Matrix4(),rotation=new T.Quaternion(),position=new T.Vector3(),scale=new T.Vector3(),up=new T.Vector3(0,1,0),heights=new Float32Array(count).fill(.48);
  ctx.look(0,.5,7.8,0,0,0);
  const draw=()=>{
    for(let i=0;i<count;i++){const d=dirs[i],h=heights[i];position.copy(d).multiplyScalar(1.02+h*.3);rotation.setFromUnitVectors(up,d);scale.set(1,h,1);matrix.compose(position,rotation,scale);petals.setMatrixAt(i,matrix);}
    petals.instanceMatrix.needsUpdate=true;
  };draw();
  return (f,dt,moving)=>{
    tint();if(!active(f,moving))return;const k=1-Math.exp(-dt/.08);
    for(let i=0;i<count;i++)heights[i]+=(.48+band(ctx,i%16)*.5+Math.abs(wave(ctx,i))*.15-heights[i])*k;
    draw();petals.rotation.y=f.phase*.04;core.scale.setScalar(1+clamp(f.boost,0,1.5)*.32);
  };
}

export function createDoubleHelix(ctx){
  const count=64,points=count*2,nodes=new T.InstancedMesh(new T.SphereGeometry(.065,6,4),new T.MeshStandardMaterial({color:0xffffff,roughness:.62}),points);nodes.frustumCulled=false;ctx.root.add(nodes);
  const tintNodes=coloredInstances(ctx,nodes,points);tintNodes();
  const positions=new Float32Array(points*3),weights=new Float32Array(points);
  for(let i=0;i<count;i++)weights[i*2]=weights[i*2+1]=i/(count-1);
  const g=new T.BufferGeometry();g.setAttribute('position',new T.BufferAttribute(positions,3).setUsage(T.DynamicDrawUsage));
  const tintLines=palette(ctx,g,weights);tintLines();const rungs=new T.LineSegments(g,new T.LineBasicMaterial({vertexColors:true}));rungs.frustumCulled=false;ctx.root.add(rungs);
  const matrix=new T.Matrix4(),quaternion=new T.Quaternion(),position=new T.Vector3(),scale=new T.Vector3(1,1,1);
  const shape=(phase,live)=>{
    for(let i=0;i<count;i++){
      const u=i/(count-1),r=1+(live?band(ctx,i%16)*.11:0),a=u*TAU*2+phase*.045+(live?wave(ctx,i)*.18:0),y=(u-.5)*4.5;
      for(let side=0;side<2;side++){
        const index=i*2+side,sign=side?1:-1;position.set(Math.cos(a)*r*sign,y,Math.sin(a)*r*sign);
        position.toArray(positions,index*3);matrix.compose(position,quaternion,scale);nodes.setMatrixAt(index,matrix);
      }
    }
    nodes.instanceMatrix.needsUpdate=true;g.attributes.position.needsUpdate=true;
  };shape(0,false);ctx.look(0,.4,8.2,0,0,0);
  return (f,dt,moving)=>{tintNodes();tintLines();if(active(f,moving))shape(f.phase,true);};
}
