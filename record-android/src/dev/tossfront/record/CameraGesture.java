package dev.tossfront.record;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.*;
import android.media.*;
import android.os.*;
import android.util.Size;
import com.google.mediapipe.framework.image.*;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.handlandmarker.*;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.nio.ByteBuffer;
import java.util.*;

/** On-device Camera2 + MediaPipe. Images are never saved, logged, previewed, or sent to a server. */
final class CameraGesture {
    interface Listener {void frame(List<HandGestureCore.Hand> hands,HandGestureCore.Output output);void status(String value);}
    private final Context context;private final CameraManager manager;private final Handler main=new Handler(Looper.getMainLooper());
    private final HandDetectionSchedule schedule=new HandDetectionSchedule();
    private final HandGestureCore core=new HandGestureCore();private final HandMotionTracker tracker=new HandMotionTracker();private final Listener listener;
    // Serial ownership keeps the activity and floating panel from opening one camera twice.
    private static HandlerThread captureThread,visionThread;private static Handler captureWorker,visionWorker;private static CameraGesture owner;
    private Handler worker,vision;private volatile boolean modelBusy;private int modelFrames,oneHandModels,twoHandModels,singleFrames,emptyFrames,pairedFrames,pairedPinches;private CameraDevice camera;private CameraCaptureSession session;private ImageReader reader;private HandLandmarker detector,singleDetector;
    private volatile boolean running,commands;private volatile int generation;private int selection=-1,rotation,processed,retries;private boolean mirror,reported,handsReported,desired,fast=true;
    private long lastFrame,rateStart,convertCost,inferCost,flowCost;private boolean gpuRejected,liteRejected,usingLite;private byte[] yPixels,uPixels,vPixels,rgbaPixels;private ByteBuffer rgba;private String message="카메라 꺼짐";
    private static final class Delivery {
        final List<HandGestureCore.Hand> hands;final HandGestureCore.Output output;final int token;
        Delivery(List<HandGestureCore.Hand> hands,HandGestureCore.Output output,int token){this.hands=hands;this.output=output;this.token=token;}
    }
    private Delivery pending;private final ArrayDeque<Delivery> events=new ArrayDeque<>();private boolean deliveryPosted;
    private void deliver(List<HandGestureCore.Hand> hands,HandGestureCore.Output output,int token){
        synchronized(this){
            // Coalesce visual-only frames; never overwrite a volume/playback/navigation event.
            Delivery frame=new Delivery(hands,output,token);
            if(output.volumeDelta!=0||output.playback!=0||output.navigation!=0){events.addLast(frame);pending=null;}else pending=frame;
            if(deliveryPosted)return;deliveryPosted=true;
        }
        main.post(()->{ArrayList<Delivery> frames;synchronized(this){frames=new ArrayList<>(events);events.clear();if(pending!=null)frames.add(pending);pending=null;deliveryPosted=false;}for(Delivery frame:frames)send(frame);});
    }
    private void send(Delivery frame){
        if(!current(frame.token))return;HandGestureCore.Output output=frame.output;List<HandGestureCore.Hand> hands=frame.hands;
        message=output.edge!=0?(output.edge<0?"왼쪽 끝 · 손가락을 떼면 이전 곡":"오른쪽 끝 · 손가락을 떼면 다음 곡"):output.recovering?"손 추적 확인 중 · 자세 유지":hands.isEmpty()?"카메라 켜짐 · 손을 보여 주세요":output.pinches>0?"핀치 "+output.pinches+"손 · "+(output.release?"손가락을 떼면 다시 준비":output.ready?"조절 준비":"맞댐 확인 중"):"카메라 켜짐 · 손 "+hands.size()+"개 추적";listener.frame(hands,output);
    }
    CameraGesture(Context c,Listener listener){context=c.getApplicationContext();this.listener=listener;manager=(CameraManager)c.getSystemService(Context.CAMERA_SERVICE);}
    static List<String> cameras(Context c){
        ArrayList<String> physical=new ArrayList<>(),logical=new ArrayList<>(),fallback=new ArrayList<>();
        try{CameraManager m=(CameraManager)c.getSystemService(Context.CAMERA_SERVICE);for(String id:m.getCameraIdList()){
            CameraCharacteristics k=m.getCameraCharacteristics(id);android.hardware.camera2.params.StreamConfigurationMap map=k.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if(map==null||map.getOutputSizes(ImageFormat.YUV_420_888)==null)continue;
            fallback.add(id);Integer facing=k.get(CameraCharacteristics.LENS_FACING);if(facing==null||facing!=CameraCharacteristics.LENS_FACING_FRONT)continue;
            int[] caps=k.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);boolean multi=false;
            if(caps!=null)for(int cap:caps)if(cap==CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)multi=true;
            (multi?logical:physical).add(id);
        }}catch(CameraAccessException|RuntimeException ignored){}
        return !physical.isEmpty()?physical:!logical.isEmpty()?logical:fallback;
    }
    String status(){return message;}
    boolean running(){return running;}
    void commands(boolean value){commands=value;}
    private final Runnable reconnect=()->{if(desired)configure(true,selection,fast);};
    void configure(boolean enabled,int index,boolean fast){
        if(!enabled){stop();return;}
        if(context.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){stop();report("카메라 권한이 필요해요");return;}
        List<String> ids=cameras(context);if(ids.isEmpty()){desired=true;report("카메라를 찾는 중");main.removeCallbacks(reconnect);if(++retries<=3)main.postDelayed(reconnect,1500);return;}
        index=index>=0&&index<ids.size()?index:0;
        if(!desired||selection!=index||this.fast!=fast)retries=0;desired=true;
        if(running&&selection==index&&this.fast==fast)return;
        main.removeCallbacks(reconnect);stopPipeline();
        if(owner!=null&&owner!=this)owner.stop();owner=this;
        this.fast=fast;selection=Math.max(0,Math.min(ids.size()-1,index));String id=ids.get(selection);running=true;final int token=++generation;
        if(captureThread==null){captureThread=new HandlerThread("RecordHandTracking");captureThread.start();captureWorker=new Handler(captureThread.getLooper());}worker=captureWorker;if(visionThread==null){visionThread=new HandlerThread("RecordHandModel");visionThread.start();visionWorker=new Handler(visionThread.getLooper());}vision=visionWorker;report("손 추적을 준비하는 중");
        vision.post(()->open(id,token));
    }
    private boolean current(int token){return running&&generation==token;}
    private void report(String value){message=value;main.post(()->listener.status(value));}
    private void open(String id,int token){
        if(!current(token))return;
        try{
            reported=false;handsReported=false;processed=0;convertCost=inferCost=flowCost=0;rateStart=SystemClock.elapsedRealtime();
            try{makeDetectors(gpuRejected?Delegate.CPU:Delegate.GPU);schedule.reset();android.util.Log.i("FrontRecordHands",gpuRejected?"tracking_cpu":"tracking_gpu");}
            catch(Exception|LinkageError unsupported){gpuRejected=true;closeDetectors();makeDetectors(Delegate.CPU);schedule.reset();android.util.Log.i("FrontRecordHands","tracking_cpu_fallback");}
            Handler target=worker;if(current(token)&&target!=null)target.post(()->openCamera(id,token));
        }catch(Exception|LinkageError e){android.util.Log.w("FrontRecordHands","camera_start_unavailable: "+e.getClass().getSimpleName());fail(token,"손 추적을 시작하지 못했어요");}
    }
    private void openCamera(String id,int token){
        if(!current(token))return;
        try{
            modelBusy=false;modelFrames=oneHandModels=twoHandModels=singleFrames=emptyFrames=pairedFrames=pairedPinches=0;
            CameraCharacteristics k=manager.getCameraCharacteristics(id);Integer angle=k.get(CameraCharacteristics.SENSOR_ORIENTATION),facing=k.get(CameraCharacteristics.LENS_FACING);rotation=angle==null?0:angle;mirror=facing!=null&&facing==CameraCharacteristics.LENS_FACING_FRONT;
            Size[] sizes=k.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.YUV_420_888);
            Comparator<Size> preference=Comparator.comparingLong(s->Math.abs((long)s.getWidth()*s.getHeight()-640L*480)+Math.abs(s.getWidth()*3L-s.getHeight()*4L)*1000);
            Size size=Arrays.stream(sizes).filter(s->s.getWidth()>=640&&s.getHeight()>=480).min(preference).orElseGet(()->Arrays.stream(sizes).min(preference).get());
            android.util.Log.i("FrontRecordHands","tracking_input width="+size.getWidth()+" height="+size.getHeight());
            if(!current(token))return;
            reader=ImageReader.newInstance(size.getWidth(),size.getHeight(),ImageFormat.YUV_420_888,2);
            reader.setOnImageAvailableListener(r->analyze(r,token),worker);
            if(!current(token))return;
            manager.openCamera(id,new CameraDevice.StateCallback(){
                public void onOpened(CameraDevice device){if(!current(token)){device.close();return;}camera=device;capture(token);}
                public void onDisconnected(CameraDevice device){device.close();fail(token,"카메라 연결이 끊겼어요");}
                public void onError(CameraDevice device,int error){device.close();fail(token,"카메라를 사용할 수 없어요 · 다른 카메라를 선택해 주세요");}
            },worker);
        }catch(Exception|LinkageError e){android.util.Log.w("FrontRecordHands","camera_start_unavailable: "+e.getClass().getSimpleName());fail(token,"손 추적을 시작하지 못했어요 · 카메라 권한을 확인해 주세요");}
    }
    private void makeDetectors(Delegate delegate){
        usingLite=fast&&!liteRejected;
        try{detector=makeDetector(delegate,2);singleDetector=makeDetector(delegate,1);}
        catch(Exception|LinkageError unsupported){
            if(!usingLite)throw unsupported;
            liteRejected=true;usingLite=false;closeDetectors();
            android.util.Log.i("FrontRecordHands","tracking_lite_unavailable_standard_fallback");
            detector=makeDetector(delegate,2);singleDetector=makeDetector(delegate,1);
        }
        android.util.Log.i("FrontRecordHands",usingLite?"tracking_model_lite":"tracking_model_standard");
    }
    private HandLandmarker makeDetector(Delegate delegate,int hands){String model=usingLite?"gesture/hand_landmarker_lite.task":"gesture/hand_landmarker.task";return HandLandmarker.createFromOptions(context,HandLandmarker.HandLandmarkerOptions.builder().setBaseOptions(BaseOptions.builder().setModelAssetPath(model).setDelegate(delegate).build()).setRunningMode(RunningMode.VIDEO).setNumHands(hands).setMinHandDetectionConfidence(.4f).setMinHandPresenceConfidence(.45f).setMinTrackingConfidence(.35f).build());}
    private void capture(int token){
        try{camera.createCaptureSession(Collections.singletonList(reader.getSurface()),new CameraCaptureSession.StateCallback(){
            public void onConfigured(CameraCaptureSession value){if(!current(token)){value.close();return;}session=value;try{CaptureRequest.Builder request=camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);request.addTarget(reader.getSurface());request.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);android.util.Range<Integer>[] ranges=manager.getCameraCharacteristics(camera.getId()).get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);if(ranges!=null){android.util.Range<Integer> range=Arrays.stream(ranges).filter(f->f.getUpper()<=30).max(Comparator.comparingInt((android.util.Range<Integer> f)->f.getUpper()*100+f.getLower())).orElse(null);if(range!=null)request.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,range);}session.setRepeatingRequest(request.build(),null,worker);report("카메라 켜짐 · 손을 보여 주세요");}catch(CameraAccessException|IllegalStateException e){fail(token,"카메라 촬영을 시작하지 못했어요");}}
            public void onConfigureFailed(CameraCaptureSession value){value.close();fail(token,"선택한 카메라를 사용할 수 없어요");}
        },worker);}catch(CameraAccessException|IllegalStateException e){fail(token,"카메라 연결을 확인해 주세요");}
    }
    private void analyze(ImageReader source,int token){
        Image image=null;
        try{
            image=source.acquireLatestImage();long now=SystemClock.elapsedRealtime();if(image==null||!current(token)||now-lastFrame<30)return;lastFrame=now;
            int width=image.getWidth(),height=image.getHeight(),step=fast&&width%2==0&&height%2==0?2:1,ow=CameraPixels.width(width/step,height/step,rotation),oh=CameraPixels.height(width/step,height/step,rotation);Image.Plane[] planes=image.getPlanes();
            if(!modelBusy){
                modelBusy=true;yPixels=copy(planes[0].getBuffer(),yPixels);uPixels=copy(planes[1].getBuffer(),uPixels);vPixels=copy(planes[2].getBuffer(),vPixels);
                int bytes=ow*oh*4;if(rgbaPixels==null||rgbaPixels.length!=bytes)rgbaPixels=new byte[bytes];
                CameraPixels.convert(width,height,yPixels,planes[0].getRowStride(),planes[0].getPixelStride(),uPixels,planes[1].getRowStride(),planes[1].getPixelStride(),vPixels,planes[2].getRowStride(),planes[2].getPixelStride(),rotation,mirror,step,rgbaPixels);
                byte[] color=rgbaPixels.clone();vision.post(()->infer(color,ow,oh,now,token));
            }
            long converted=SystemClock.elapsedRealtime();convertCost+=converted-now;
            List<HandGestureCore.Hand> hands=tracker.update(now);HandGestureCore.Output output=core.update(hands,now,commands);flowCost+=SystemClock.elapsedRealtime()-converted;
            if(hands.isEmpty())emptyFrames++;else if(hands.size()==1)singleFrames++;if(hands.size()==2)pairedFrames++;if(output.pinches==2)pairedPinches++;
            if(++processed==30)main.post(()->{if(current(token))retries=0;});
            if(processed%120==0){android.util.Log.i("FrontRecordHands",String.format(java.util.Locale.ROOT,"tracking_heartbeat tracking_fps=%.1f model_fps=%.1f convert_ms=%d infer_ms=%d association_ms=%d one_hand_models=%d two_hand_models=%d one_hand_frames=%d empty_frames=%d two_hand_frames=%d two_pinch_frames=%d",120000f/Math.max(1,SystemClock.elapsedRealtime()-rateStart),modelFrames*1000f/Math.max(1,SystemClock.elapsedRealtime()-rateStart),convertCost/120,inferCost/Math.max(1,modelFrames),flowCost/120,oneHandModels,twoHandModels,singleFrames,emptyFrames,pairedFrames,pairedPinches));rateStart=SystemClock.elapsedRealtime();convertCost=inferCost=flowCost=0;modelFrames=oneHandModels=twoHandModels=singleFrames=emptyFrames=pairedFrames=pairedPinches=0;}
            deliver(hands,output,token);
        }catch(Exception|LinkageError e){android.util.Log.w("FrontRecordHands","frame_unavailable: "+e.getClass().getSimpleName());fail(token,"손 추적 오류 · 카메라를 다시 켜 주세요");}
        finally{if(image!=null)image.close();}
    }
    private void infer(byte[] color,int width,int height,long at,int token){
        if(!current(token))return;MPImage input=null;long began=SystemClock.elapsedRealtime();
        try{
            if(rgba==null||rgba.capacity()!=color.length)rgba=ByteBuffer.allocateDirect(color.length);rgba.clear();rgba.put(color);rgba.rewind();
            // Packet creation copies the direct buffer into native-owned pixels.
            input=new ByteBufferImageBuilder(rgba,width,height,MPImage.IMAGE_FORMAT_RGBA).build();boolean search=schedule.search(at);HandLandmarkerResult result=(search?detector:singleDetector).detectForVideo(input,at);schedule.result(search,result.landmarks().size(),at);ArrayList<HandGestureCore.Hand> hands=new ArrayList<>();
            for(List<NormalizedLandmark> points:result.landmarks()){if(points.size()!=21)continue;float[] xy=new float[42];boolean valid=true;for(int i=0;i<21;i++){float x=points.get(i).x(),y=points.get(i).y();if(!Float.isFinite(x)||!Float.isFinite(y))valid=false;xy[i*2]=HandGestureCore.bound(x,0,1);xy[i*2+1]=HandGestureCore.bound(y,0,1);}if(valid)hands.add(new HandGestureCore.Hand(xy,at));}
            if(!reported){reported=true;android.util.Log.i("FrontRecordHands","camera_frames_analyzed");}if(!handsReported&&!hands.isEmpty()){handsReported=true;android.util.Log.i("FrontRecordHands","live_hands_tracked");}
            long cost=SystemClock.elapsedRealtime()-began;
            Handler target=worker;if(target!=null)target.post(()->{if(current(token)){tracker.acceptModel(hands,at,SystemClock.elapsedRealtime());modelFrames++;if(hands.size()==1)oneHandModels++;if(hands.size()==2)twoHandModels++;inferCost+=cost;modelBusy=false;}});
        }catch(Exception|LinkageError e){if(usingLite)liteRejected=true;else gpuRejected=true;android.util.Log.w("FrontRecordHands",usingLite?"tracking_lite_inference_unavailable_standard_fallback":"inference_unavailable: "+e.getClass().getSimpleName());fail(token,"손 분석 오류 · 카메라를 다시 켜 주세요");}
        finally{if(input!=null)input.close();Arrays.fill(color,(byte)0);}
    }
    private static byte[] copy(ByteBuffer source,byte[] buffer){ByteBuffer view=source.duplicate();int size=view.remaining();if(buffer==null||buffer.length!=size)buffer=new byte[size];view.get(buffer);return buffer;}
    private void fail(int token,String value){main.post(()->{if(current(token)){stopPipeline();if(desired&&retries<3){retries++;report("카메라 다시 연결 중 · "+retries+"/3");android.util.Log.i("FrontRecordHands","camera_reconnecting");main.postDelayed(reconnect,1500);}else report(value);}});}
    void stop(){desired=false;retries=0;main.removeCallbacks(reconnect);stopPipeline();if(owner==this)owner=null;}
    private void stopPipeline(){
        if(!running)return;running=false;generation++;Handler previous=worker;
        if(previous!=null)previous.post(()->{core.reset();tracker.clear();if(session!=null){session.close();session=null;}if(camera!=null){camera.close();camera=null;}if(reader!=null){reader.close();reader=null;}for(byte[] data:new byte[][]{yPixels,uPixels,vPixels,rgbaPixels})if(data!=null)Arrays.fill(data,(byte)0);yPixels=uPixels=vPixels=rgbaPixels=null;});
        if(vision!=null)vision.post(()->{closeDetectors();schedule.reset();rgba=null;});
        lastFrame=0;report("카메라 꺼짐");
    }
    private void closeDetectors(){if(singleDetector!=null){singleDetector.close();singleDetector=null;}if(detector!=null){detector.close();detector=null;}}
    void close(){stop();worker=null;vision=null;}
}
