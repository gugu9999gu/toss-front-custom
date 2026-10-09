import * as T from './three.module.min.js';
import {clamp} from './audio-motion.js';

/** Screen-space hand brushes affect nearby geometry; no root or camera transform. */
export function handPoints(gesture){
  if(!gesture?.active||!Array.isArray(gesture.points))return [];
  return gesture.points.slice(0,2).filter(p=>Number.isFinite(p.x)&&Number.isFinite(p.y)).map(p=>({x:clamp(p.x,0,1),y:clamp(p.y,0,1),power:clamp(p.power??1,.5,1.5)}));
}
export function repel(x,y,points,energy=0,aspect=1,result={x:0,y:0}){
  result.x=result.y=0;aspect=clamp(aspect,.4,3);
  for(const p of points){
    let dx=(x-p.x)*aspect,dy=y-p.y;const d2=dx*dx+dy*dy,r=.28;
    if(d2>=r*r)continue;
    const weight=1-d2/(r*r);let d=Math.sqrt(d2);
    if(d<.0001){dx=.006;dy=-.006;d=.009;}
    const force=weight*weight*(.035+clamp(energy,0,1)*.08)*p.power/(d+.025);
    result.x+=dx*force/aspect;result.y+=dy*force;
  }
  return result;
}
export function handWave(value,x,points){
  let gain=1;for(const p of points){const d=Math.abs(x-p.x)/.28;if(d<1)gain+=(1-d)*(1-d)*p.power*.7;}
  return clamp(value*gain,-3,3);
}
export class HandGeometryField {
  constructor(root){
    this.items=[];root.traverse(o=>{const a=o.userData?.handReactiveInstances?o.instanceMatrix:o.geometry?.getAttribute('position');if(a&&(o.isPoints||o.userData?.handReactive||o.userData?.handReactiveInstances))this.items.push({object:o,attribute:a,base:a.array.slice(),instances:!!o.userData?.handReactiveInstances});});
    this.point=new T.Vector3();this.inverse=new T.Matrix4();this.force={x:0,y:0};this.changed=false;
  }
  restore(){if(!this.changed)return;for(const item of this.items){item.attribute.array.set(item.base);item.attribute.needsUpdate=true;}this.changed=false;}
  apply(root,camera,layout,points,energy){
    if(!points.length)return;root.updateMatrixWorld(true);camera.updateMatrixWorld(true);
    const span=clamp(layout.span??.4,.08,1),focus=clamp(layout.focus??.3,.05,.95),top=focus-span*.5;
    const aspect=clamp((layout.width||800)/Math.max(1,(layout.height||1280)*span),.4,3);
    for(const item of this.items){
      const {object,attribute,base,instances}=item;const data=attribute.array;base.set(data);this.inverse.copy(object.matrixWorld).invert();
      for(let i=0;i<attribute.count;i++){
        const at=instances?i*16+12:i*3;this.point.fromArray(base,at).applyMatrix4(object.matrixWorld).project(camera);
        if(this.point.z< -1||this.point.z>1)continue;
        const x=(this.point.x+1)*.5,y=((1-this.point.y)*.5-top)/span;
        repel(x,y,points,energy,aspect,this.force);if(!this.force.x&&!this.force.y)continue;
        this.point.x+=this.force.x*2;this.point.y-=this.force.y*span*2;
        this.point.unproject(camera).applyMatrix4(this.inverse).toArray(data,at);
      }
      attribute.needsUpdate=true;
    }
    this.changed=true;
  }
}
