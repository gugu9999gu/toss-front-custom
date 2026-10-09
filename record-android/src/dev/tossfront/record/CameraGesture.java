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
    private final HandGestureCore core=new HandGestureCore();private final HandMotionTracker tracker=new HandMotionTracker();private final Listener listener;
    // Serial ownership keeps the activity and floating panel from opening one camera twice.
    private static HandlerThread captureThread,visionThread;private static Handler captureWorker,visionWorker;private static CameraGesture owner;
    private Handler worker,vision;private volatile boolean modelBusy;private int modelFrames;private CameraDevice camera;private CameraCaptureSession session;private ImageReader reader;private HandLandmarker detector;
    private volatile boolean running,commands;private volatile int generation;private int selection=-1,rotation,processed,retries;private boolean mirror,reported,handsReported,desired;
    private long lastFrame,rateStart,convertCost,inferCost;private boolean gpuRejected;private byte[] yPixels,uPixels,vPixels,rgbaPixels,grayPixels;private ByteBuffer rgba;private String message="카메라 꺼짐";
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
    private final Runnable reconnect=()->{if(desired)configure(true,selection);};
    void configure(boolean enabled,int index){
        if(!enabled){stop();return;}
        if(context.checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){stop();report("카메라 권한이 필요해요");return;}
        List<String> ids=cameras(context);if(ids.isEmpty()){desired=true;report("카메라를 찾는 중");main.removeCallbacks(reconnect);if(++retries<=3)main.postDelayed(reconnect,1500);return;}
        index=index>=0&&index<ids.size()?index:0;
        if(!desired||selection!=index)retries=0;desired=true;
        if(running&&selection==index)return;
        main.removeCallbacks(reconnect);stopPipeline();
        if(owner!=null&&owner!=this)owner.stop();owner=this;
        selection=Math.max(0,Math.min(ids.size()-1,index));String id=ids.get(selection);running=true;final int token=++generation;
        if(captureThread==null){captureThread=new HandlerThread("RecordHandTracking");captureThread.start();captureWorker=new Handler(captureThread.getLooper());}worker=captureWorker;if(visionThread==null){visionThread=new HandlerThread("RecordHandModel");visionThread.start();visionWorker=new Handler(visionThread.getLooper());}vision=visionWorker;report("손 추적을 준비하는 중");
        vision.post(()->open(id,token));
    }
    private boolean current(int token){return running&&generation==token;}
    private void report(String value){message=value;main.post(()->listener.status(value));}
    private void open(String id,int token){
        if(!current(token))return;
        try{
            reported=false;handsReported=false;processed=0;convertCost=inferCost=0;rateStart=SystemClock.elapsedRealtime();
            try{detector=makeDetector(gpuRejected?Delegate.CPU:Delegate.GPU);android.util.Log.i("FrontRecordHands",gpuRejected?"tracking_cpu":"tracking_gpu");}
            catch(Exception|LinkageError unsupported){gpuRejected=true;detector=makeDetector(Delegate.CPU);android.util.Log.i("FrontRecordHands","tracking_cpu_fallback");}
            Handler target=worker;if(current(token)&&target!=null)target.post(()->openCamera(id,token));
        }catch(Exception|LinkageError e){android.util.Log.w("FrontRecordHands","camera_start_unavailable: "+e.getClass().getSimpleName());fail(token,"손 추적을 시작하지 못했어요");}
    }
    private void openCamera(String id,int token){
        if(!current(token))return;
        try{
            modelBusy=false;modelFrames=0;
            CameraCharacteristics k=manager.getCameraCharacteristics(id);Integer angle=k.get(CameraCharacteristics.SENSOR_ORIENTATION),facing=k.get(CameraCharacteristics.LENS_FACING);rotation=angle==null?0:angle;mirror=facing!=null&&facing==CameraCharacteristics.LENS_FACING_FRONT;
            Size[] sizes=k.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.YUV_420_888);
            Size size=Arrays.stream(sizes).filter(s->s.getWidth()>=320&&s.getHeight()>=240).min(Comparator.comparingLong(s->Math.abs((long)s.getWidth()*s.getHeight()-320L*240)+Math.abs(s.getWidth()*3L-s.getHeight()*4L)*1000)).orElse(sizes[0]);
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
    private HandLandmarker makeDetector(Delegate delegate){return HandLandmarker.createFromOptions(context,HandLandmarker.HandLandmarkerOptions.builder().setBaseOptions(BaseOptions.builder().setModelAssetPath("gesture/hand_landmarker.task").setDelegate(delegate).build()).setRunningMode(RunningMode.VIDEO).setNumHands(2).setMinHandDetectionConfidence(.5f).setMinHandPresenceConfidence(.5f).setMinTrackingConfidence(.5f).build());}
    private void capture(int token){
        try{camera.createCaptureSession(Collections.singletonList(reader.getSurface()),new CameraCaptureSession.StateCallback(){
            public void onConfigured(CameraCaptureSession value){if(!current(token)){value.close();return;}session=value;try{CaptureRequest.Builder request=camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);request.addTarget(reader.getSurface());request.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);session.setRepeatingRequest(request.build(),null,worker);report("카메라 켜짐 · 손을 보여 주세요");}catch(CameraAccessException|IllegalStateException e){fail(token,"카메라 촬영을 시작하지 못했어요");}}
            public void onConfigureFailed(CameraCaptureSession value){value.close();fail(token,"선택한 카메라를 사용할 수 없어요");}
        },worker);}catch(CameraAccessException|IllegalStateException e){fail(token,"카메라 연결을 확인해 주세요");}
    }
    private void analyze(ImageReader source,int token){
        Image image=null;
        try{
            image=source.acquireLatestImage();long now=SystemClock.elapsedRealtime();if(image==null||!current(token)||now-lastFrame<50)return;lastFrame=now;
            int width=image.getWidth(),height=image.getHeight(),ow=CameraPixels.width(width,height,rotation),oh=CameraPixels.height(width,height,rotation);Image.Plane[] planes=image.getPlanes();
            yPixels=copy(planes[0].getBuffer(),yPixels);uPixels=copy(planes[1].getBuffer(),uPixels);vPixels=copy(planes[2].getBuffer(),vPixels);
            int bytes=width*height*4;if(rgbaPixels==null||rgbaPixels.length!=bytes){rgbaPixels=new byte[bytes];grayPixels=new byte[width*height];}
            CameraPixels.convert(width,height,yPixels,planes[0].getRowStride(),planes[0].getPixelStride(),uPixels,planes[1].getRowStride(),planes[1].getPixelStride(),vPixels,planes[2].getRowStride(),planes[2].getPixelStride(),rotation,mirror,rgbaPixels);
            for(int i=0;i<grayPixels.length;i++)grayPixels[i]=(byte)(((rgbaPixels[i*4]&255)*77+(rgbaPixels[i*4+1]&255)*150+(rgbaPixels[i*4+2]&255)*29)>>8);
            convertCost+=SystemClock.elapsedRealtime()-now;
            if(!modelBusy){modelBusy=true;byte[] color=rgbaPixels.clone(),gray=grayPixels.clone();vision.post(()->infer(color,gray,ow,oh,now,token));}
            List<HandGestureCore.Hand> hands=tracker.update(grayPixels,ow,oh,now);HandGestureCore.Output output=core.update(hands,now,commands);
            if(++processed==30)main.post(()->{if(current(token))retries=0;});
            if(processed%120==0){android.util.Log.i("FrontRecordHands",String.format(java.util.Locale.ROOT,"tracking_heartbeat flow_fps=%.1f model_fps=%.1f convert_ms=%d infer_ms=%d",120000f/Math.max(1,SystemClock.elapsedRealtime()-rateStart),modelFrames*1000f/Math.max(1,SystemClock.elapsedRealtime()-rateStart),convertCost/120,inferCost/Math.max(1,modelFrames)));rateStart=SystemClock.elapsedRealtime();convertCost=inferCost=0;modelFrames=0;}
            main.post(()->{if(current(token)){message=hands.isEmpty()?"카메라 켜짐 · 손을 보여 주세요":output.pinches>0?"핀치 "+output.pinches+"손 · "+(output.release?"손가락을 떼면 다시 준비":output.ready?"조절 준비":"맞댐 확인 중"):"카메라 켜짐 · 손 "+hands.size()+"개 추적";listener.frame(hands,output);}});
        }catch(Exception|LinkageError e){android.util.Log.w("FrontRecordHands","frame_unavailable: "+e.getClass().getSimpleName());fail(token,"손 추적 오류 · 카메라를 다시 켜 주세요");}
        finally{if(image!=null)image.close();}
    }
    private void infer(byte[] color,byte[] gray,int width,int height,long at,int token){
        if(!current(token))return;MPImage input=null;long began=SystemClock.elapsedRealtime();
        try{
            if(rgba==null||rgba.capacity()!=color.length)rgba=ByteBuffer.allocateDirect(color.length);rgba.clear();rgba.put(color);rgba.rewind();
            // Packet creation copies the direct buffer into native-owned pixels.
            input=new ByteBufferImageBuilder(rgba,width,height,MPImage.IMAGE_FORMAT_RGBA).build();HandLandmarkerResult result=detector.detectForVideo(input,at);ArrayList<HandGestureCore.Hand> hands=new ArrayList<>();
            for(List<NormalizedLandmark> points:result.landmarks()){if(points.size()!=21)continue;float[] xy=new float[42];boolean valid=true;for(int i=0;i<21;i++){float x=points.get(i).x(),y=points.get(i).y();if(!Float.isFinite(x)||!Float.isFinite(y))valid=false;xy[i*2]=HandGestureCore.bound(x,0,1);xy[i*2+1]=HandGestureCore.bound(y,0,1);}if(valid)hands.add(new HandGestureCore.Hand(xy));}
            if(!reported){reported=true;android.util.Log.i("FrontRecordHands","camera_frames_analyzed");}if(!handsReported&&!hands.isEmpty()){handsReported=true;android.util.Log.i("FrontRecordHands","live_hands_tracked");}
            long cost=SystemClock.elapsedRealtime()-began;
            Handler target=worker;if(target!=null)target.post(()->{if(current(token)){tracker.seed(hands,gray,width,height,at);modelFrames++;inferCost+=cost;modelBusy=false;}});
        }catch(Exception|LinkageError e){gpuRejected=true;android.util.Log.w("FrontRecordHands","inference_unavailable: "+e.getClass().getSimpleName());fail(token,"손 분석 오류 · 카메라를 다시 켜 주세요");}
        finally{if(input!=null)input.close();Arrays.fill(color,(byte)0);}
    }
    private static byte[] copy(ByteBuffer source,byte[] buffer){ByteBuffer view=source.duplicate();int size=view.remaining();if(buffer==null||buffer.length!=size)buffer=new byte[size];view.get(buffer);return buffer;}
    private void fail(int token,String value){main.post(()->{if(current(token)){stopPipeline();if(desired&&retries<3){retries++;report("카메라 다시 연결 중 · "+retries+"/3");android.util.Log.i("FrontRecordHands","camera_reconnecting");main.postDelayed(reconnect,1500);}else report(value);}});}
    void stop(){desired=false;retries=0;main.removeCallbacks(reconnect);stopPipeline();if(owner==this)owner=null;}
    private void stopPipeline(){
        if(!running)return;running=false;generation++;Handler previous=worker;
        if(previous!=null)previous.post(()->{core.reset();tracker.clear();if(session!=null){session.close();session=null;}if(camera!=null){camera.close();camera=null;}if(reader!=null){reader.close();reader=null;}for(byte[] data:new byte[][]{yPixels,uPixels,vPixels,rgbaPixels,grayPixels})if(data!=null)Arrays.fill(data,(byte)0);yPixels=uPixels=vPixels=rgbaPixels=grayPixels=null;});
        if(vision!=null)vision.post(()->{if(detector!=null){detector.close();detector=null;}rgba=null;});
        lastFrame=0;report("카메라 꺼짐");
    }
    void close(){stop();worker=null;vision=null;}
}
