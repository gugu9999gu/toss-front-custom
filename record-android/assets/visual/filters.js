// Initial filters authored by Claude Opus 5.5; the project added the Game Boy pass.
import * as T from './three.module.min.js';

const MAX_SIZE = 8192;
const CELL = 6;
const size = (v) => {
  const n = Math.floor(Number(v));
  return Number.isFinite(n) ? Math.min(MAX_SIZE, Math.max(1, n)) : 1;
};

const VS = `varying vec2 vUv;
void main() {
  vUv = uv;
  gl_Position = vec4(position.xy, 0.0, 1.0);
}`;

const FS = `uniform sampler2D tSrc;
uniform vec2 uRatio;
uniform float uDither;
uniform float uRetro;
varying vec2 vUv;
float b2(vec2 p) { return mod(2.0 * p.x + 3.0 * p.y, 4.0); }
void main() {
  gl_FragColor = vec4(texture2D(tSrc, vUv).rgb, 1.0);
  #include <tonemapping_fragment>
  #include <colorspace_fragment>
  if (uDither > 0.5) {
    vec2 p = floor(gl_FragCoord.xy * uRatio);
    float t = (4.0 * b2(mod(p, 2.0)) + b2(mod(floor(p * 0.5), 2.0)) + 0.5) / 16.0;
    vec3 c = clamp(gl_FragColor.rgb, 0.0, 1.0);
    gl_FragColor.rgb = min(floor(c * 5.0 + t), 5.0) / 5.0;
  }
  if (uRetro > 0.5) {
    vec2 p=floor(gl_FragCoord.xy*uRatio);
    float threshold=(4.0*b2(mod(p,2.0))+b2(mod(floor(p*.5),2.0))+.5)/16.0;
    float l=dot(gl_FragColor.rgb,vec3(.2126,.7152,.0722));
    float level=clamp(floor(l*3.0+threshold),0.0,3.0);
    vec3 c=level<.5?vec3(.0588,.2196,.0588):level<1.5?vec3(.1882,.3843,.1882):level<2.5?vec3(.5451,.6745,.0588):vec3(.6078,.7373,.0588);
    gl_FragColor.rgb=c;
  }
}`;

export class SceneFilter {
  constructor(width, height) {
    this.w = size(width);
    this.h = size(height);
    this.mode = 0;
    this.retro = false;
    this.disposed = false;
    this.viewport = new T.Vector4();
    this.target = new T.WebGLRenderTarget(this.w, this.h, {
      type: T.UnsignedByteType,
      format: T.RGBAFormat,
      depthBuffer: true,
      stencilBuffer: false,
      minFilter: T.NearestFilter,
      magFilter: T.NearestFilter,
      generateMipmaps: false
    });
    this.material = new T.ShaderMaterial({
      uniforms: {
        tSrc: { value: this.target.texture },
        uRatio: { value: new T.Vector2(1, 1) },
        uDither: { value: 0 },
        uRetro: { value: 0 }
      },
      vertexShader: VS,
      fragmentShader: FS,
      depthTest: false,
      depthWrite: false,
      toneMapped: true
    });
    this.geometry = new T.PlaneGeometry(2, 2);
    this.quad = new T.Mesh(this.geometry, this.material);
    this.quad.frustumCulled = false;
    this.quadScene = new T.Scene();
    this.quadScene.add(this.quad);
    this.camera = new T.OrthographicCamera(-1, 1, 1, -1, 0, 1);
  }

  _live() {
    if (this.disposed) throw new Error('SceneFilter disposed');
  }

  _sync() {
    const pixel = this.mode >= 2 || this.retro;
    const tw = pixel ? Math.max(1, Math.round(this.w / CELL)) : this.w;
    const th = pixel ? Math.max(1, Math.round(this.h / CELL)) : this.h;
    if (this.target.width !== tw || this.target.height !== th) this.target.setSize(tw, th);
    const u = this.material.uniforms;
    u.uRatio.value.set(tw / this.w, th / this.h);
    u.uDither.value = this.mode === 1 || this.mode === 3 ? 1 : 0;
    u.uRetro.value = this.retro ? 1 : 0;
  }

  setMode(mode) {
    this._live();
    const m = Number(mode);
    if (!Number.isInteger(m) || m < 0 || m > 3) throw new RangeError('SceneFilter mode must be 0-3');
    if (m === this.mode) return;
    this.mode = m;
    if (m === 0) this.target.dispose();
    else this._sync();
  }
  setRetro(value){this._live();if(this.retro===!!value)return;this.retro=!!value;this._sync();}

  resize(width, height) {
    this._live();
    this.w = size(width);
    this.h = size(height);
    if (this.mode !== 0 || this.retro) this._sync();
  }

  render(renderer, scene, camera) {
    this._live();
    if (!renderer || !scene || !camera) throw new TypeError('SceneFilter.render needs renderer, scene, camera');
    if (this.mode === 0 && !this.retro) {
      renderer.render(scene, camera);
      return;
    }
    const prev = renderer.getRenderTarget();
    renderer.getViewport(this.viewport);
    try {
      renderer.setRenderTarget(this.target);
      renderer.render(scene, camera);
      renderer.setRenderTarget(prev);
      renderer.setViewport(this.viewport);
      renderer.render(this.quadScene, this.camera);
    } finally {
      renderer.setRenderTarget(prev);
      renderer.setViewport(this.viewport);
    }
  }

  dispose() {
    if (this.disposed) return;
    this.disposed = true;
    this.quadScene.remove(this.quad);
    this.target.dispose();
    this.geometry.dispose();
    this.material.dispose();
  }
}
