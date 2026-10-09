const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const source=fs.readFileSync(path.join(__dirname,'../youtube-web-android/src/local/tossfront/youtubeweb/WebProbe.java'),'utf8');
const js=JSON.parse(source.match(/static final String SEEK_JS=("(?:\\.|[^"\\])*");/)[1]);
let checks=0;const check=(v,label)=>{checks++;assert.ok(v,label);};
function seek(seconds,{player=null,video={duration:180,currentTime:12,dispatchEvent(){}},ad=false}={}){return JSON.parse(new Function('document','window','Event','return '+js.replace('SECONDS',String(seconds)))({querySelector:s=>s==='video'?video:player},{__frontRecordAd:ad},class Event{constructor(type){this.type=type;}}));}
const direct={duration:180,currentTime:12,dispatchEvent(){}};check(seek(65,{video:direct}).ok&&direct.currentTime===65,'HTML fallback seeks the actual media element');
let request=false;const player={seekTo:()=>{request=true;}};const media={duration:180,currentTime:12,dispatchEvent(){}};const result=seek(65,{player,video:media});check(result.method==='html'&&media.currentTime===65&&!request,'Use the media timeline even when the private player API has a different live-stream origin');
const range={duration:180,currentTime:20,seekable:{length:1,start:()=>10,end:()=>100},dispatchEvent(){}};
check(seek(150,{video:range}).clamped&&Math.abs(range.currentTime-99.9)<.001,'Unavailable DVR positions clamp to the seekable end');
check(seek(0,{video:range}).target===10&&range.currentTime===10,'DVR seek cannot precede the available start');
check(seek(80,{video:range}).target===80,'An available target is preserved');
check(!seek(30,{video:null}).ok,'No player cannot report a successful seek');
const untouched={duration:180,currentTime:12,dispatchEvent(){}};check(!seek(30,{video:untouched,ad:true}).ok&&untouched.currentTime===12,'Ads cannot be scrubbed by the record app');
const blocked={duration:180,dispatchEvent(){},set currentTime(t){throw Error('unavailable');}};check(!seek(30,{video:blocked}).ok,'A media seek failure is reported without pretending playback moved');
const infinite={duration:Infinity,currentTime:40,seekable:{length:1,start:()=>5,end:()=>120},dispatchEvent(){}};check(seek(60,{video:infinite}).target===60,'Seekable live media does not require a finite duration');
check(seek(-100,{video:direct}).target===0,'Negative targets are bounded');
console.log(checks+' player seek and DVR range checks passed');
