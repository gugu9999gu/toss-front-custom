// Offline contract checks for the actual WebView metadata probe. No YouTube/network access.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const source = fs.readFileSync(path.join(__dirname, '../youtube-web-android/src/local/tossfront/youtubeweb/YouTubeWebActivity.java'), 'utf8');
const literal = source.match(/private static final String STATE_JS=("(?:\\.|[^"\\])*");/)[1];
const probe = new Function('document', 'navigator', 'location', 'return ' + JSON.parse(literal));
function state({url='https://m.youtube.com/watch?v=sampleID001', video={}, metadata=null, heading=null, artist=null, title='Fallback title - YouTube'}={}) {
  const element = video === null ? null : {duration:180,currentTime:12,paused:false,ended:false,readyState:4,playbackRate:1,error:null,...video};
  const document = { title, querySelector: selector => selector==='video' ? element : selector.includes('h1') ? heading : artist };
  return JSON.parse(probe(document, {mediaSession:{metadata}}, {href:url}));
}
assert.equal(state({url:'https://m.youtube.com/',metadata:{title:'Stale previous video'}}).active,false,'Home page must not manufacture an active player');
assert.equal(state({video:null}).active,false,'A watch URL alone is not playback');
const playing = state({metadata:{title:'Native media title',artist:'Channel'},heading:{textContent:'DOM title'}});
assert.equal(playing.title,'Native media title');assert.equal(playing.artist,'Channel');assert.equal(playing.position,12);assert.equal(playing.paused,false);
assert.equal(state({heading:{textContent:'DOM title'}}).title,'DOM title');
assert.equal(state().title,'Fallback title');
assert.equal(state({video:{duration:Infinity,currentTime:NaN}}).duration,0,'Live streams must not generate an infinite seek range');
assert.equal(state({video:{duration:Infinity,currentTime:NaN}}).position,0);
assert.equal(state({video:{paused:true}}).paused,true);
assert.equal(state({video:{error:{code:4}}}).error,true);
assert.equal(state({url:'https://m.youtube.com/shorts/abcdefghijk'}).id,'abcdefghijk');
assert.equal(state({url:'https://m.youtube.com/results?search_query=music'}).active,false,'Search results are not an active watch session');
console.log('FrontRecord metadata contract: 14 assertions passed');
