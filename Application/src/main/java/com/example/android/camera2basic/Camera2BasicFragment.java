/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.camera2basic;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v13.app.FragmentCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class Camera2BasicFragment extends Fragment implements View.OnClickListener, FragmentCompat.OnRequestPermissionsResultCallback
{

    // just for debugging
    public static boolean DEBUG = false;

    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final String FRAGMENT_DIALOG = "dialog";

    static
    {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    /**
     * Tag for the {@link Log}.
     */
    private static final String TAG = "Camera2BasicFragment";




    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener()
    {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height)
        {
            Dlog.i("");
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height)
        {
            Dlog.i("");
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture)
        {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture)
        {
        }

    };

    private static long timeprev;
    private AutoFitTextureView mTextureView;

    // 1. setUpCameraOutputs() 에 생기는 변수들이다.
    private CameraCharacteristics mCameraCharacteristics;
    private String mCameraId;
    private Size mPreviewSize;
    private boolean mFlashSupported;
    private int mSensorOrientation;
    private ImageReader mImageReader;

    // 2. mCameraId 으로  다음을 생성.
    private CameraDevice mCameraDevice;

    // 3. createCameraPreviewSession()에서 생성되는 것이다.
    private CameraCaptureSession mPreviewCaptureSession;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    // mPreviewRequestBuilder.build()의 출력이 mPreviewRequest 이다.
    // 목적은 처음에 시작했던 것 처럼 되돌리기 위한 것이다.
    private CaptureRequest mPreviewRequest;

    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAITING_LOCK0 = 1;
    private static final int STATE_WAITING_PRECAPTURE = 2;
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;
    private static final int STATE_PICTURE_TAKEN = 4;
    private static final int STATE_WAITING_LOCK1 = 5;
    private static final int STATE_WAITING_PRELOCK = 6 ;

    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    private File mFile;
    private File mFile2;

    private static int width = 0 ;
    private static int height = 0 ;
    private static int pixelStride = 0;
    private static int rowStride = 0;
    private static int rowPadding = 0;

    private static byte[] ImageByteARGB0 ;
    private static byte[] ImageByteARGB1 ;

    private static int mState = STATE_PREVIEW;
    private static int picture0_taken = 0 ;


    private final CameraDevice.StateCallback mDeviceStateCallback = new CameraDevice.StateCallback()
    {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice)
        {
            Dlog.i("CameraDevice_StateCallback_opened");
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice)
        {
            Dlog.i("CameraDevice_StateCallback_disconnected");
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error)
        {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            Activity activity = getActivity();
            if (null != activity)
            {
                activity.finish();
            }
        }

    };

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    /**
     * An {@link ImageReader} that handles still image capture.
     */




    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener()
    {

        @Override
        public void onImageAvailable(ImageReader reader)
        {
            Dlog.i("");
            int format = reader.getImageFormat() ;
            if ( format == ImageFormat.JPEG)
                Dlog.i("format Jpeg");
            else if (format == PixelFormat.RGBA_8888)
                Dlog.i("pixel RGBA 8888");

            if (picture0_taken == 1 )
                mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage(), mFile2, format ));
            else
                mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage(), mFile, format ));

        }

    };





    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    /**
     * Whether the current camera device supports Flash or not.
     */


    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
     */
    private CameraCaptureSession.CaptureCallback mPreviewCaptureCallback = new CameraCaptureSession.CaptureCallback()
    {

        private void process(CaptureResult result)
        {
            switch (mState)
            {
                case STATE_PREVIEW:
                {
                    Dlog.i("STATE_PREVIEW");
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }
                case STATE_WAITING_PRELOCK:
                {
                    Dlog.i("STATE_WAITING_PRELOCK");
                    if (result.get(CaptureResult.CONTROL_AF_MODE) == CaptureResult.CONTROL_AF_MODE_AUTO)
                    {
                        Dlog.i("CONTROL_AF_MODE_AUTO");
                        try
                        {
                            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
                            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);


                            mPreviewCaptureSession.capture(mPreviewRequestBuilder.build(), null, null);

                            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
                            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);

                            mPreviewCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mPreviewCaptureCallback, mBackgroundHandler);
                            mState = STATE_WAITING_LOCK1;

                        }
                        catch (CameraAccessException e)
                        {
                            e.printStackTrace();
                        }
                    }
                    break;
                }
                case STATE_WAITING_LOCK0:
                case STATE_WAITING_LOCK1:
                {
                    if(mState == STATE_WAITING_LOCK0)
                        Dlog.i("STATE_WAITING_LOCK0");
                    else
                        Dlog.i("STATE_WAITING_LOCK1");

                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null)
                    {
                        // image capture 완료하기 전에  또 다른 event가 들어 오는 것을 막기 위해 미리 상태를 바꾼다.
                        mState = STATE_PREVIEW;
                        captureStillPicture();
                    }
                    else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState || CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState)
                    {
                        // image capture 완료하기 전에  또 다른 event가 들어 오는 것을 막기 위해 미리 상태를 바꾼다.
                        mState = STATE_PREVIEW;

                        Dlog.i("STATE_FOCUSED_LOCKED");
                        // CONTROL_AE_STATE can be null on some devices
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED)
                        {
                            captureStillPicture();
                        }
                        else
                        {
                            runPrecaptureSequence();
                        }
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE:
                {
                    Dlog.i("case STATE_WAITING_PRECAPTURE");
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE || aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED)
                    {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE:
                {
                    Dlog.i("case STATE_WAITING_NON_PRECAPTURE");
                    // CONTROL_AE_STATE can be null on some devices
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE)
                    {
                        captureStillPicture();
                    }
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult)
        {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result)
        {
            process(result);
        }

    };

    private CameraCaptureSession.CaptureCallback mCaptureStillImageCallback1 = new CameraCaptureSession.CaptureCallback()
    {
        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult)
        {
            Dlog.i("LENS_FOCUS_DISTANCE_1 : " + partialResult.get(CaptureResult.LENS_FOCUS_DISTANCE));
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result)
        {
            Dlog.i("Ending--StillPicture_1");
            Dlog.i("LENS_FOCUS_DISTANCE_1 : " + result.get(CaptureResult.LENS_FOCUS_DISTANCE));
            long now = System.currentTimeMillis();
            Dlog.i("elapsedTime : " + (now - timeprev));
            showToast("elapsedTime : " + (now - timeprev));
            //showToast("Saved2: " + mFile2);
            Log.d(TAG, mFile2.toString());
            unlockFocus();
        }

    };

    /**
     * Shows a {@link Toast} on the UI thread.
     *
     * @param text The message to show
     */
    private void showToast(final String text)
    {
        final Activity activity = getActivity();
        if (activity != null)
        {
            activity.runOnUiThread(new Runnable()
            {
                @Override
                public void run()
                {
                    Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that
     * is at least as large as the respective texture view size, and that is at most as large as the
     * respective max size, and whose aspect ratio matches with the specified value. If such size
     * doesn't exist, choose the largest one that is at most as large as the respective max size,
     * and whose aspect ratio matches with the specified value.
     *
     * @param choices           The list of sizes that the camera supports for the intended output
     *                          class
     * @param textureViewWidth  The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth          The maximum width that can be chosen
     * @param maxHeight         The maximum height that can be chosen
     * @param aspectRatio       The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth, int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio)
    {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices)
        {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight && option.getHeight() == option.getWidth() * h / w)
            {
                if (option.getWidth() >= textureViewWidth && option.getHeight() >= textureViewHeight)
                {
                    bigEnough.add(option);
                }
                else
                {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0)
        {
            return Collections.min(bigEnough, new CompareSizesByArea());
        }
        else if (notBigEnough.size() > 0)
        {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        }
        else
        {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    public static Camera2BasicFragment newInstance()
    {
        return new Camera2BasicFragment();
    }

    /**
     * get Debug Mode
     *
     * @param context
     * @return
     */
    private boolean isDebuggable(Context context)
    {
        boolean debuggable = false;

        PackageManager pm = context.getPackageManager();
        try
        {
            ApplicationInfo appinfo = pm.getApplicationInfo(context.getPackageName(), 0);
            debuggable = (0 != (appinfo.flags & ApplicationInfo.FLAG_DEBUGGABLE));
        }
        catch (PackageManager.NameNotFoundException e)
        {
            /* debuggable variable will remain false */
        }

        return debuggable;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        this.DEBUG = isDebuggable(getContext());

        return inflater.inflate(R.layout.fragment_camera2_basic, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState)
    {
        view.findViewById(R.id.picture).setOnClickListener(this);
        view.findViewById(R.id.info).setOnClickListener(this);
        Dlog.i("");
        mTextureView = (AutoFitTextureView) view.findViewById(R.id.texture);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);
        Dlog.i("");

    }

    @Override
    public void onResume()
    {
        super.onResume();
        Dlog.i("");
        startBackgroundThread();

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (mTextureView.isAvailable())
        {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        }
        else
        {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause()
    {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    private void requestCameraPermission()
    {
        if (FragmentCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA))
        {
            new ConfirmationDialog().show(getChildFragmentManager(), FRAGMENT_DIALOG);
        }
        else
        {
            FragmentCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        if (requestCode == REQUEST_CAMERA_PERMISSION)
        {
            if (grantResults.length != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED)
            {
                ErrorDialog.newInstance(getString(R.string.request_permission)).show(getChildFragmentManager(), FRAGMENT_DIALOG);
            }
        }
        else
        {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    private void setUpCameraOutputs(int width, int height)
    {
        Dlog.i("");
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try
        {
            for (String cameraId : manager.getCameraIdList())
            {
                mCameraCharacteristics = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                Integer facing = mCameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT)
                {
                    continue;
                }


                StreamConfigurationMap map = mCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null)
                {
                    continue;
                }

                // For still image captures, we use the largest available size.
                Size largest = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea());


                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
                int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
                //noinspection ConstantConditions
                mSensorOrientation = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                boolean swappedDimensions = false;
                switch (displayRotation)
                {
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        if (mSensorOrientation == 90 || mSensorOrientation == 270)
                        {
                            swappedDimensions = true;
                        }
                        break;
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if (mSensorOrientation == 0 || mSensorOrientation == 180)
                        {
                            swappedDimensions = true;
                        }
                        break;
                    default:
                        Log.e(TAG, "Display rotation is invalid: " + displayRotation);
                }

                Point displaySize = new Point();
                activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
                int rotatedPreviewWidth = width;
                int rotatedPreviewHeight = height;
                int maxPreviewWidth = displaySize.x;
                int maxPreviewHeight = displaySize.y;

                if (swappedDimensions)
                {
                    rotatedPreviewWidth = height;
                    rotatedPreviewHeight = width;
                    maxPreviewWidth = displaySize.y;
                    maxPreviewHeight = displaySize.x;
                }

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH)
                {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT)
                {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth, maxPreviewHeight, largest);

                // G4 : PixelFormat 은 지원 안된다.  ImageFormat=JPEG, YUV_420_888
                // G3 : PixelFormat = RGBA_8888     ImageFormat= JPEG, YUV_420_888

                int supportdImageFormat = ImageFormat.YUV_420_888 ;
                mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), supportdImageFormat, /*maxImages*/2);
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);

                if (supportdImageFormat == ImageFormat.YUV_420_888 )
                {
                    mFile = new File(getActivity().getExternalFilesDir(null), "pic.yuv");
                    mFile2 = new File(getActivity().getExternalFilesDir(null), "pic2.yuv");
                }
                else if ( supportdImageFormat == ImageFormat.JPEG )
                {
                    mFile = new File(getActivity().getExternalFilesDir(null), "pic.jpg");
                    mFile2 = new File(getActivity().getExternalFilesDir(null), "pic2.jpg");
                }


                // We fit the aspect ratio of TextureView to the size of preview we picked.
                int orientation = getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE)
                {
                    mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                }
                else
                {
                    mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
                }

                // Check if the flash is supported.
                Boolean available = mCameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                mFlashSupported = available == null ? false : available;

                mCameraId = cameraId;
                return;
            }
        }
        catch (CameraAccessException e)
        {
            e.printStackTrace();
        }
        catch (NullPointerException e)
        {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            ErrorDialog.newInstance(getString(R.string.camera_error)).show(getChildFragmentManager(), FRAGMENT_DIALOG);
        }
    }

    /**
     * Opens the camera specified by {@link Camera2BasicFragment#mCameraId}.
     */
    private void openCamera(int width, int height)
    {
        Dlog.i("");
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
        {
            requestCameraPermission();
            return;
        }
        setUpCameraOutputs(width, height);
        configureTransform(width, height);
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try
        {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS))
            {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(mCameraId, mDeviceStateCallback, mBackgroundHandler);
        }
        catch (CameraAccessException e)
        {
            e.printStackTrace();
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera()
    {
        Dlog.i("");
        try
        {
            mCameraOpenCloseLock.acquire();
            if (null != mPreviewCaptureSession)
            {
                mPreviewCaptureSession.close();
                mPreviewCaptureSession = null;
            }
            if (null != mCameraDevice)
            {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mImageReader)
            {
                mImageReader.close();
                mImageReader = null;
            }
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        }
        finally
        {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread()
    {
        Dlog.i("");
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread()
    {
        Dlog.i("");
        mBackgroundThread.quitSafely();
        try
        {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    private void createCameraPreviewSession()
    {
        Dlog.i("");
        try
        {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);


            // Here, we create a CameraCaptureSession for camera preview.
            //여기서 두개의 surface을 capture surface으로 등록을 한다.
            // surface : preview 에서 보여주기 위한 것.
            // mImageReader.getSurface() : capture 중. stil image을 가져 올 때.
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()), new CameraCaptureSession.StateCallback()
            {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession)
                {
                    Dlog.i("PreviewCaptureSession_configured");
                    // The camera is already closed
                    if (null == mCameraDevice)
                    {
                        return;
                    }

                    // When the session is ready, we start displaying the preview.
                    mPreviewCaptureSession = cameraCaptureSession;
                    try
                    {
                        // Auto focus should be continuous for camera preview.
                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        //mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
                        // Flash is automatically enabled when necessary.
                        setAutoFlash(mPreviewRequestBuilder);

                        // Finally, we start displaying the camera preview.
                        mPreviewRequest = mPreviewRequestBuilder.build();
                        mPreviewCaptureSession.setRepeatingRequest(mPreviewRequest, mPreviewCaptureCallback, mBackgroundHandler);
                    }
                    catch (CameraAccessException e)
                    {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession)
                {
                    showToast("Failed");
                }
            }, null);
        }
        catch (CameraAccessException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight)
    {
        Activity activity = getActivity();
        if (null == mTextureView || null == mPreviewSize || null == activity)
        {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation)
        {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max((float) viewHeight / mPreviewSize.getHeight(), (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        else if (Surface.ROTATION_180 == rotation)
        {
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    /**
     * Initiate a still image capture.
     */
    private void takePicture()
    {
        Dlog.i("");
        lockFocus0();
    }


    private void lockFocus0()
    {
        try
        {
            picture0_taken = 0 ;
            // This is how to tell the camera to lock focus.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            // Tell #mPreviewCaptureCallback to wait for the lock.
            mState = STATE_WAITING_LOCK0;
            mPreviewCaptureSession.capture(mPreviewRequestBuilder.build(), mPreviewCaptureCallback, mBackgroundHandler);
        }
        catch (CameraAccessException e)
        {
            e.printStackTrace();
        }
    }

    private void lockFocus1()
    {
        try
        {
            // This is how to tell the camera to lock focus.
            Dlog.i("");

            // refer to https://stackoverflow.com/questions/42127464/how-to-lock-focus-in-camera2-api-android

            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);

            // 아래 부분을 해야,  먼 거리 초점을 맞춘다. 만일하지 않으면 원복했을 때, 원거리 초점이 돼어 버린다.
            Rect newRect = new Rect(0, 0, mPreviewSize.getWidth(), mPreviewSize.getHeight());
            MeteringRectangle[] focusArea = new MeteringRectangle[1];
            focusArea[0] = new MeteringRectangle(newRect, 500);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, focusArea);
            mPreviewRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, 0.0f);

            //mState = STATE_WAITING_PRELOCK;
            mPreviewCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), null, null);


            //----------- process 부분에서 할 것을 여기서 해 버린다.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
            mPreviewCaptureSession.capture(mPreviewRequestBuilder.build(), null, null);

            //-----------
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
            mState = STATE_WAITING_LOCK1;
            mPreviewCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mPreviewCaptureCallback, mBackgroundHandler);

        }
        catch (CameraAccessException e)
        {
            e.printStackTrace();
        }
    }



    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in {@link #mPreviewCaptureCallback} from {@link #lockFocus()}.
     */
    private void runPrecaptureSequence()
    {
        Dlog.i("");
        try
        {
            // This is how to tell the camera to trigger.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #mPreviewCaptureCallback to wait for the precapture sequence to be set.
            mState = STATE_WAITING_PRECAPTURE;
            mPreviewCaptureSession.capture(mPreviewRequestBuilder.build(), mPreviewCaptureCallback, mBackgroundHandler);
        }
        catch (CameraAccessException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * {@link #mPreviewCaptureCallback} from both {@link #lockFocus()}.
     */
    private void captureStillPicture()
    {
        try
        {
            final Activity activity = getActivity();
            if (null == activity || null == mCameraDevice)
            {
                return;
            }
            Dlog.i("");
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            setAutoFlash(captureBuilder);

            // Orientation
            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));


            CameraCaptureSession.CaptureCallback mCaptureStillImageCallback0 = new CameraCaptureSession.CaptureCallback()
            {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result)
                {
                    if(picture0_taken == 0)
                    {
                        picture0_taken = 1 ;
                        Dlog.i("picture0_taken");
                        long now = System.currentTimeMillis();
                        showToast("elapsedTime : " + (now - timeprev));
                        lockFocus1();
                    }
                    else
                    {
                        Dlog.i("picture1_taken");
                        picture0_taken = 0 ;
                        long now = System.currentTimeMillis();
                        showToast("elapsedTime : " + (now - timeprev));
                        unlockFocus();
                    }

                }
            };


            mPreviewCaptureSession.stopRepeating();
            mPreviewCaptureSession.capture(captureBuilder.build(), mCaptureStillImageCallback0, null);
        }
        catch (CameraAccessException e)
        {
            e.printStackTrace();
        }
    }


    /**
     * Retrieves the JPEG orientation from the specified screen rotation.
     *
     * @param rotation The screen rotation.
     * @return The JPEG orientation (one of 0, 90, 270, and 360)
     */
    private int getOrientation(int rotation)
    {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private void unlockFocus()
    {
        try
        {
            // Reset the auto-focus trigger
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            setAutoFlash(mPreviewRequestBuilder);
            mPreviewCaptureSession.capture(mPreviewRequestBuilder.build(), mPreviewCaptureCallback, mBackgroundHandler);
            // After this, the camera will go back to the normal state of preview.
            mState = STATE_PREVIEW;
            mPreviewCaptureSession.setRepeatingRequest(mPreviewRequest, mPreviewCaptureCallback, mBackgroundHandler);
        }
        catch (CameraAccessException e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(View view)
    {
        Dlog.i("");
        timeprev = System.currentTimeMillis();
        switch (view.getId())
        {
            case R.id.picture:
            {
                takePicture();
                break;
            }
            case R.id.info:
            {
                Activity activity = getActivity();
                if (null != activity)
                {
                    new AlertDialog.Builder(activity).setMessage(R.string.intro_message).setPositiveButton(android.R.string.ok, null).show();
                }
                break;
            }
        }
    }

    private void setAutoFlash(CaptureRequest.Builder requestBuilder)
    {
        if (mFlashSupported)
        {
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        }
    }

    /**
     * Saves a JPEG {@link Image} into the specified {@link File}.
     */
    private static class ImageSaver implements Runnable
    {
        private final Image mImage;
        private final File mFile;
        private final int mImageFormat;

        public ImageSaver(Image image, File file, int imageformat)
        {
            mImage = image;
            mFile = file;
            mImageFormat = imageformat ;
        }

        @Override
        public void run()
        {

            if(mImageFormat == PixelFormat.RGBA_8888 )
            {
                Image.Plane[] planes = mImage.getPlanes();
                ByteBuffer buffer = planes[0].getBuffer();
                if (buffer == null)
                {
                    return;
                }
                width = mImage.getWidth();
                height = mImage.getHeight();
                pixelStride = planes[0].getPixelStride();
                rowStride = planes[0].getRowStride();
                rowPadding = rowStride - pixelStride * width;

                if(picture0_taken == 0 )
                {
                    ImageByteARGB0 = new byte[buffer.remaining()];
                    buffer.get(ImageByteARGB0);
                }
                else
                {
                    ImageByteARGB1 = new byte[buffer.remaining()];
                    buffer.get(ImageByteARGB1);
                }


                FileOutputStream fos = null;
                Bitmap bitmap = null;

                try
                {
//                    int offset = 0;
//                    bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
//
//                    for (int i = 0; i < height; ++i)
//                    {
//                        for (int j = 0; j < width; ++j)
//                        {
//                            int pixel = 0;
//                            pixel |= (buffer.get(offset) & 0xff) << 16;     // R
//                            pixel |= (buffer.get(offset + 1) & 0xff) << 8;  // G
//                            pixel |= (buffer.get(offset + 2) & 0xff);       // B
//                            pixel |= (buffer.get(offset + 3) & 0xff) << 24; // A
//                            bitmap.setPixel(j, i, pixel);
//                            offset += pixelStride;
//                        }
//                        offset += rowPadding;
//                    }
//                    fos = new FileOutputStream(mFile);
//                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                    
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
                finally
                {
                    mImage.close();
                    if (null != fos)
                    {
                        try
                        {
                            fos.close();
                        }
                        catch (IOException e)
                        {
                            e.printStackTrace();
                        }
                    }
                }
            }
            else if (mImageFormat == ImageFormat.JPEG || mImageFormat == ImageFormat.YUV_420_888)
            {
                ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                FileOutputStream output = null;
                try
                {
                    output = new FileOutputStream(mFile);
                    output.write(bytes);
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
                finally
                {
                    mImage.close();
                    if (null != output)
                    {
                        try
                        {
                            output.close();
                        }
                        catch (IOException e)
                        {
                            e.printStackTrace();
                        }
                    }
                }
            }
            else
            {
                mImage.close();
            }

        }

    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size>
    {

        @Override
        public int compare(Size lhs, Size rhs)
        {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    /**
     * Shows an error message dialog.
     */
    public static class ErrorDialog extends DialogFragment
    {

        private static final String ARG_MESSAGE = "message";

        public static ErrorDialog newInstance(String message)
        {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState)
        {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity).setMessage(getArguments().getString(ARG_MESSAGE)).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialogInterface, int i)
                {
                    activity.finish();
                }
            }).create();
        }

    }

    /**
     * Shows OK/Cancel confirmation dialog about camera permission.
     */
    public static class ConfirmationDialog extends DialogFragment
    {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState)
        {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity()).setMessage(R.string.request_permission).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    FragmentCompat.requestPermissions(parent, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                }
            }).setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)
                {
                    Activity activity = parent.getActivity();
                    if (activity != null)
                    {
                        activity.finish();
                    }
                }
            }).create();
        }
    }

}
