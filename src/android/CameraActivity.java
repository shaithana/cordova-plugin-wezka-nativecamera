/*
	    Copyright 2014 Giovanni Di Gregorio.

		Licensed under the Apache License, Version 2.0 (the "License");
		you may not use this file except in compliance with the License.
		You may obtain a copy of the License at

		http://www.apache.org/licenses/LICENSE-2.0

		Unless required by applicable law or agreed to in writing, software
		distributed under the License is distributed on an "AS IS" BASIS,
		WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
		See the License for the specific language governing permissions and
   		limitations under the License.   			
 */

package com.wezka.nativecamera;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.PictureCallback;
import android.widget.TextView;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class CameraActivity extends Activity implements SensorEventListener {

    private static final String TAG = "CameraActivity";

    private SurfaceView preview;
    private SurfaceHolder previewHolder = null;
    private Camera camera = null;
    private boolean inPreview = false;
    private boolean cameraConfigured = false;
    private int led = 0;
    private int cam = 0;
    private boolean pressed = false;
    private int degrees = 0;
    private boolean isFlash = false;
    private boolean isFrontCamera = false;
    SensorManager sm;
    WindowManager mWindowManager;

    public int mOrientationDeg;
    private static final int _DATA_X = 0;
    private static final int _DATA_Y = 1;
    private static final int _DATA_Z = 2;
    int ORIENTATION_UNKNOWN = -1;

    private int screenWidth;
    private int screenHeight;

    private float viewfinderHalfPx;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //setContentView(R.layout.activity_main);
        setContentView(getResources().getIdentifier("nativecameraplugin", "layout", getPackageName()));

        preview = (SurfaceView) findViewById(getResources().getIdentifier("preview", "id", getPackageName()));
        final Button flipCamera = (Button) findViewById(getResources().getIdentifier("flipCamera", "id", getPackageName()));
        final Button flashButton = (Button) findViewById(getResources().getIdentifier("flashButton", "id", getPackageName()));
        final Button captureButton = (Button) findViewById(getResources().getIdentifier("captureButton", "id", getPackageName()));
        final ImageView viewfinder = (ImageView) findViewById(getResources().getIdentifier("viewfinder", "id", getPackageName()));
        final RelativeLayout focusButton = (RelativeLayout) findViewById(getResources().getIdentifier("viewfinderArea", "id", getPackageName()));
        final int imgFlashNo = getResources().getIdentifier("@drawable/btn_flash_no", null, getPackageName());
        final int imgFlashAuto = getResources().getIdentifier("@drawable/btn_flash_auto", null, getPackageName());
        final int imgFlashOn = getResources().getIdentifier("@drawable/btn_flash_on", null, getPackageName());
        viewfinderHalfPx = pxFromDp(72)/2;

        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        if(sm.getSensorList(Sensor.TYPE_ACCELEROMETER).size()!=0){
            Sensor s = sm.getSensorList(Sensor.TYPE_ACCELEROMETER).get(0);
            sm.registerListener(this,s, SensorManager.SENSOR_DELAY_NORMAL);
        }

        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        if(getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)){
            flashButton.setVisibility(View.VISIBLE);
            isFlash = true;
        } else {
            flashButton.setVisibility(View.INVISIBLE);
            isFlash = false;
        }

        if(Camera.getNumberOfCameras() > 1){
            flipCamera.setVisibility(View.VISIBLE);
            isFrontCamera = true;
        } else {
            flipCamera.setVisibility(View.INVISIBLE);
            isFrontCamera = false;
        }

        Display display = getWindowManager().getDefaultDisplay();
        // Necessary to use deprecated methods for Android 2.x support
        screenWidth = display.getWidth();
        screenHeight = display.getHeight();


        focusButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                float x = 0;
                float y = 0;
                Rect focusRect;
                y = (((event.getX() * 2000) / screenWidth) - 1000) *-1;
                x = (((event.getY() * 2000) / screenHeight) - 1000);

                if((int)x-100 > -1000 && (int)x+100 < 1000 && (int)y-100 > -1000 && (int)y+100 < 1000) {
                    focusRect = new Rect((int)x-100, (int)y-100, (int)x+100, (int)y+100);
                } else {
                    focusRect = new Rect(-100, -100, 100, 100);
                }

                if (camera == null)
                    return true;

                Parameters parameters = camera.getParameters();

                if (parameters.getMaxNumFocusAreas() > 0) {

                    if(event.getX() - viewfinderHalfPx < 0) {
                        viewfinder.setX(0);
                    } else if(event.getX() + viewfinderHalfPx > screenWidth) {
                        viewfinder.setX(screenWidth - viewfinderHalfPx*2);
                    } else {
                        viewfinder.setX(event.getX() - viewfinderHalfPx);
                    }

                    if(event.getY() - viewfinderHalfPx < 0) {
                        viewfinder.setY(0);
                    } else if(event.getY() + viewfinderHalfPx > screenHeight - pxFromDp(125)) {
                        viewfinder.setY((screenHeight - pxFromDp(125)) - viewfinderHalfPx*2);
                    } else {
                        viewfinder.setY(event.getY() - viewfinderHalfPx);
                    }

                    List<Camera.Area> focusArea = new ArrayList<Camera.Area>();
                    focusArea.add(new Camera.Area(focusRect, 750));
                    parameters.setFocusAreas(focusArea);
                    if(parameters.getMaxNumMeteringAreas() > 0) {
                        parameters.setMeteringAreas(focusArea);
                    }

                    camera.setParameters(parameters);
                }
                return true;
            }
        });


        if(isFrontCamera) {
            flipCamera.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (cam == 0) {
                        cam = 1;
                        led = 0;
                        viewfinder.setVisibility(View.INVISIBLE);
                        if (isFlash) flashButton.setVisibility(View.INVISIBLE);
                        if (isFlash) flashButton.setBackgroundResource(imgFlashNo);
                    } else {
                        cam = 0;
                        led = 0;
                        viewfinder.setVisibility(View.VISIBLE);
                        viewfinder.setX(screenWidth / 2 - viewfinderHalfPx);
                        viewfinder.setY(screenHeight / 2 - viewfinderHalfPx*3);
                        if (isFlash) flashButton.setVisibility(View.VISIBLE);
                        if (isFlash) flashButton.setBackgroundResource(imgFlashNo);
                    }
                    cameraConfigured = false;
                    restartPreview(cam);
                }
            });
        }

        if(isFlash) {
            flashButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Parameters p = camera.getParameters();
                    if (led == 0) {
                        p.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
                        flashButton.setBackgroundResource(imgFlashAuto);
                        led = 1;
                    } else if (led == 1) {
                        p.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                        flashButton.setBackgroundResource(imgFlashOn);
                        led = 2;
                    } else if (led == 2) {
                        p.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                        flashButton.setBackgroundResource(imgFlashNo);
                        led = 0;
                    }
                    camera.setParameters(p);
                    camera.startPreview();
                }
            });
        }

        captureButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (pressed || camera == null)
                    return;
                
                Parameters p = camera.getParameters();
                p.setRotation(degrees);
                camera.setParameters(p);
                pressed = true;
                // Auto-focus first, catching rare autofocus error
                try {
                    camera.autoFocus(new AutoFocusCallback() {
                        public void onAutoFocus(boolean success, Camera camera) {
                            // Catch take picture error
                            try {
                                camera.takePicture(null, null, mPicture);
                            } catch (RuntimeException ex) {
                                // takePicture crash. Ignore.
                                Toast.makeText(getApplicationContext(), 
                                    "Error taking picture", Toast.LENGTH_SHORT).show();
                                Log.e(TAG, "Auto-focus crash");
                            }
                        }
                    });
                } catch (RuntimeException ex) {
                    // Auto focus crash. Ignore.
                    Toast.makeText(getApplicationContext(), 
                        "Error focusing", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Auto-focus crash");
                }            
            }
        });

    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN){
            Parameters p = camera.getParameters();
            p.setRotation(degrees);
            camera.setParameters(p);
            if (pressed || camera == null)
                return false;
            pressed = true;
            // Auto-focus first, catching rare autofocus error
            try {
                camera.autoFocus(new AutoFocusCallback() {
                    public void onAutoFocus(boolean success, Camera camera) {
                        // Catch take picture error
                        try {
                            camera.takePicture(null, null, mPicture);
                        } catch (RuntimeException ex) {
                            // takePicture crash. Ignore.
                            Toast.makeText(getApplicationContext(), 
                                "Error taking picture", Toast.LENGTH_SHORT).show();
                            Log.e(TAG, "Auto-focus crash");
                        }
                    }
                });
            } catch (RuntimeException ex) {
                // Auto focus crash. Ignore.
                Toast.makeText(getApplicationContext(), 
                    "Error focusing", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Auto-focus crash");
            }            
            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    private PictureCallback mPicture = new PictureCallback() {
        public void onPictureTaken(byte[] data, Camera camera) {
            Uri fileUri = (Uri) getIntent().getExtras().get(
                    MediaStore.EXTRA_OUTPUT);
            File pictureFile = new File(fileUri.getPath());
            try {
                FileOutputStream fos = new FileOutputStream(pictureFile);
                fos.write(data);
                fos.close();
            } catch (FileNotFoundException e) {
                Log.d(TAG, "File not found: " + e.getMessage());
            } catch (IOException e) {
                Log.d(TAG, "Error accessing file: " + e.getMessage());
            }
            setResult(RESULT_OK);
            pressed = false;
            finish();
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        previewHolder = preview.getHolder();
        previewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        previewHolder.addCallback(surfaceCallback);
        if (Camera.getNumberOfCameras() >= 1) {
            camera = Camera.open(cam);
        }

        // Initialize preview if surface still exists
        if (preview.getHeight() > 0) {
            initPreview(preview.getHeight());
            startPreview();
        }
    }

    private float pxFromDp(float dp) {
        return dp * CameraActivity.this.getResources().getDisplayMetrics().density;
    }

    void restartPreview(int isFront) {
        if (inPreview) {
            camera.stopPreview();
        }
        camera.release();
        camera = Camera.open(isFront);
        initPreview(preview.getHeight());
        startPreview();
    }

    @Override
    public void onPause() {
        if (inPreview) {
            camera.stopPreview();
        }
        camera.release();
        camera = null;
        inPreview = false;
        super.onPause();
    }

    private Camera.Size getBestPreviewSize(int height,
                                           Camera.Parameters parameters) {

        final double ASPECT_TOLERANCE = 0.1;
        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;
        // Try to find an size match aspect ratio and size
        for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - height) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - height) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - height);
            }
        }
        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
                if (Math.abs(size.height - height) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - height);
                }
            }
        }
        return optimalSize;
    }

    private void initPreview(int height) {
        if (camera != null && previewHolder.getSurface() != null) {
            try {
                camera.setPreviewDisplay(previewHolder);
            }
            catch (Throwable t) {
                Log.e("PreviewDemo-surfaceCallback",
                        "Exception in setPreviewDisplay()", t);
            }

            if (!cameraConfigured) {
                Camera.Parameters parameters = camera.getParameters();
                Camera.Size size = getBestPreviewSize(height, parameters);
                Camera.Size pictureSize = getSmallestPictureSize(parameters);
                if (size != null && pictureSize != null) {
                    parameters.setPreviewSize(size.width, size.height);
                    parameters.setPictureSize(pictureSize.width, pictureSize.height);

                    parameters.setPictureFormat(ImageFormat.JPEG);
                    // For Android 2.3.4 quirk
                    if (parameters.getSupportedFocusModes() != null) {
                        if (parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                        } else if (parameters.getSupportedFocusModes().contains(android.hardware.Camera.Parameters.FOCUS_MODE_AUTO)) {
                            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                        }
                    }
                    if (parameters.getSupportedSceneModes() != null) {
                        if (parameters.getSupportedSceneModes().contains(Camera.Parameters.SCENE_MODE_AUTO)) {
                            parameters.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);
                        }
                    }
                    if (parameters.getSupportedWhiteBalance() != null) {
                        if (parameters.getSupportedWhiteBalance().contains(Camera.Parameters.WHITE_BALANCE_AUTO)) {
                            parameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
                        }
                    }
                    cameraConfigured=true;
                    camera.setParameters(parameters);
                }
            }
        }
    }

    private Camera.Size getSmallestPictureSize(Camera.Parameters parameters) {
        Camera.Size result=null;
        for (Camera.Size size : parameters.getSupportedPictureSizes()) {
            if (result == null) {
                result=size;
            }
            else {
                int resultArea=result.width * result.height;
                int newArea=size.width * size.height;

                if (newArea > resultArea) {
                    result=size;
                }
            }
        }
        return(result);
    }

    private void startPreview() {
        if (cameraConfigured && camera != null) {
            camera.setDisplayOrientation(90);
            camera.startPreview();
            inPreview = true;
        }
    }

    SurfaceHolder.Callback surfaceCallback = new SurfaceHolder.Callback() {
        public void surfaceCreated(SurfaceHolder holder) {
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int width,
                                   int height) {
            if (camera != null) {
                camera.setDisplayOrientation(90);
            }
            initPreview(preview.getHeight());
            startPreview();
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
            if (camera != null) {
                camera.stopPreview();
                camera.release();
                camera = null;
            }
        }
    };

    @Override
    protected void onDestroy() {
        // Stop listening to sensor
        sm.unregisterListener(this);
        super.onDestroy();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float[] values = event.values;
            int orientation = ORIENTATION_UNKNOWN;
            float X = -values[_DATA_X];
            float Y = -values[_DATA_Y];
            float Z = -values[_DATA_Z];
            float magnitude = X*X + Y*Y;
            // Don't trust the angle if the magnitude is small compared to the y value
            if (magnitude * 4 >= Z*Z) {
                float OneEightyOverPi = 57.29577957855f;
                float angle = (float)Math.atan2(-Y, X) * OneEightyOverPi;
                orientation = 90 - Math.round(angle);
                // normalize to 0 - 359 range
                while (orientation >= 360) {
                    orientation -= 360;
                }
                while (orientation < 0) {
                    orientation += 360;
                }
            }
            //^^ thanks to google for that code
            //now we must figure out which orientation based on the degrees
            if (orientation != mOrientationDeg) {
                mOrientationDeg = orientation;
                //figure out actual orientation
                if(orientation == -1){//basically flat
                    if(cam == 1) {
                        degrees = 270;
                    } else {
                        degrees = 90;
                    }
                }
                else if(orientation <= 45 || orientation > 315){//round to 0
                    if(cam == 1) {
                        degrees = 270;
                    } else {
                        degrees = 90;
                    }
                }
                else if(orientation > 45 && orientation <= 135){//round to 90
                    degrees = 180;
//                    RotateAnimation a = new RotateAnimation(0, 90, 34, 34);
                }
                else if(orientation > 135 && orientation <= 225){//round to 180
                    if(cam == 1) {
                        degrees = 90;
                    } else {
                        degrees = 270;
                    }
                }
                else if(orientation > 225 && orientation <= 315){//round to 270
                    degrees = 0;
                }
            }

        }

    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // TODO Auto-generated method stub
    }

}




