// Offline tests exercise the exact probe shipped in the web app.
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const source=fs.readFileSync(path.join(__dirname,'../youtube-web-android/src/local/tossfront/youtubeweb/WebProbe.java'),'utf8');
const literal=source.match(/static final String STATE_JS=("(?:\\.|[^"\\])*");/)[1];
const script=JSON.parse(literal);
let assertions=0;function equal(actual,expected,message){assertions++;assert.equal(actual,expected,message);}
function element({visible=true,disabled=false,opacity='1',hidden=false}={}){return {disabled,style:{display:'block',visibility:hidden?'hidden':'visible',opacity},getClientRects:()=>visible?[{}]:[],getAttribute:()=>null,click(){this.clicks=(this.clicks||0)+1;}};}
function state({url='https://m.youtube.com/watch?v=sampleID001',video={},metadata=null,heading=null,artist=null,title='Fallback title - YouTube',skip=null,ad=null,next=null,previous=null,auto=true,window={}}={}){
  const player=video===null?null:{duration:180,currentTime:12,paused:false,ended:false,readyState:4,playbackRate:1,error:null,...video};
  const document={title,querySelector:s=>s==='video'?player:s.includes('h1')?heading:artist,querySelectorAll:s=>s.includes('.ytp-ad-skip-button')?(skip?[skip]:[]):s.includes('.ad-showing')?(ad?[ad]:[]):s.includes('.ytp-next-button')?(next?[next]:[]):s.includes('.ytp-prev-button')?(previous?[previous]:[]):[]};
  const probe=new Function('document','navigator','location','getComputedStyle','window','return '+script.replace('AUTO_SKIP',String(auto)));
  return JSON.parse(probe(document,{mediaSession:{metadata}},{href:url},e=>e.style,window));
}
equal(state({url:'https://m.youtube.com/',metadata:{title:'Stale previous video'}}).active,false,'No active player on home');
equal(state({video:null}).active,false,'A URL alone is not playback');
const playing=state({metadata:{title:'Native media title',artist:'Channel'},heading:{textContent:'DOM title'}});
equal(playing.title,'Native media title');equal(playing.artist,'Channel');equal(playing.position,12);equal(playing.paused,false);
equal(state({heading:{textContent:'DOM title'}}).title,'DOM title');equal(state().title,'Fallback title');
equal(state({video:{duration:Infinity,currentTime:NaN}}).duration,0);equal(state({video:{duration:Infinity,currentTime:NaN}}).position,0);
equal(state({video:{paused:true}}).paused,true);equal(state({video:{error:{code:4}}}).error,true);
equal(state({url:'https://m.youtube.com/shorts/abcdefghijk'}).id,'abcdefghijk');equal(state({url:'https://m.youtube.com/results?search_query=music'}).active,false);
const skip=element(),shared={};equal(state({skip,window:shared}).ad,true,'Official visible skip implies ad');equal(skip.clicks,1,'Enabled skip clicked');
state({skip,window:shared});equal(skip.clicks,1,'Click throttled');
const off=element();state({skip:off,auto:false});equal(off.clicks,undefined,'Setting off never clicks');
const disabled=element({disabled:true});equal(state({skip:disabled,ad:element()}).ad,true,'Countdown ad remains identified');equal(disabled.clicks,undefined,'Disabled skip never clicked');
const hidden=element({visible:false});equal(state({skip:hidden}).ad,false);equal(hidden.clicks,undefined);
const transparent=element({opacity:'0'});state({skip:transparent});equal(transparent.clicks,undefined);
equal(state({ad:element()}).ad,true,'Unskippable ad is only reported');equal(state().ad,false,'Normal content not labeled ad');
equal(state({next:element()}).next,true);equal(state({next:element({disabled:true})}).next,false);
equal(state({previous:element()}).previous,true);
let endedCallback;const endWindow={};state({window:endWindow,video:{addEventListener:(name,callback)=>{endedCallback=callback;}}});endedCallback();equal(state({window:endWindow}).endedId,'sampleID001','Content ended event preserves ID');equal(state({window:endWindow}).endedAt>0,true,'Event has deduplication timestamp');
let adEnded;const adWindow={};state({window:adWindow,ad:element(),video:{addEventListener:(name,callback)=>{adEnded=callback;}}});adEnded();equal(state({window:adWindow}).endedId,'','Ad completion never becomes a content end event');
const unmuteScript=JSON.parse(source.match(/static String unmute\(\)\{return ("(?:\\.|[^"\\])*");\}/)[1]);
function unmute(button,video){return new Function('document','getComputedStyle','return '+unmuteScript)({querySelectorAll:()=>button?[button]:[],querySelector:()=>video},e=>e.style);}
const sound=element();equal(unmute(sound,{muted:true,volume:0}),true);equal(sound.clicks,1,'Explicit playback clicks an actual unmute control');
const hiddenSound=element({visible:false});unmute(hiddenSound,{muted:true,volume:0});equal(hiddenSound.clicks,undefined);
const disabledSound=element({disabled:true});unmute(disabledSound,{muted:true,volume:0});equal(disabledSound.clicks,undefined);
const quiet={muted:true,volume:.4};unmute(null,quiet);equal(quiet.volume,.4,'An existing nonzero HTML volume is preserved');
const silent={muted:true,volume:0};unmute(null,silent);equal(silent.volume,1,'A zero HTML volume can be unmuted for requested playback');
const transparentSound=element({opacity:'0'});unmute(transparentSound,{muted:true,volume:0});equal(transparentSound.clicks,undefined);
console.log('Web metadata/official ad and sound controls: '+assertions+' assertions passed');
