package id.periksa.plugins.usbcamera;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.serenegiant.common.BaseActivity;
import com.serenegiant.usb_libuvccamera.CameraDialog;
import com.serenegiant.usb_libuvccamera.LibUVCCameraUSBMonitor;
import com.serenegiant.usb_libuvccamera.LibUVCCameraUSBMonitor.OnDeviceConnectListener;
import com.serenegiant.usb_libuvccamera.UVCCamera;
import com.serenegiant.usbcameracommon.UVCCameraHandler;
import com.serenegiant.widget.CameraViewInterface;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.UUID;


public class USBCameraActivity extends BaseActivity implements CameraDialog.CameraDialogParent {
    private static final String TAG = "CamActivityDebug";
    /**
     * preview resolution(width)
     * if your camera does not support specific resolution and mode,
     * {@link UVCCamera#setPreviewSize(int, int, int)} throw exception
     */
    private static final int PREVIEW_WIDTH = 640;
    /**
     * preview resolution(height)
     * if your camera does not support specific resolution and mode,
     * {@link UVCCamera#setPreviewSize(int, int, int)} throw exception
     */
    private static final int PREVIEW_HEIGHT = 480;
    /**
     * preview mode
     * if your camera does not support specific resolution and mode,
     * {@link UVCCamera#setPreviewSize(int, int, int)} throw exception
     * 0:YUYV, other:MJPEG
     */
    private static final int PREVIEW_MODE = 1;

    private LibUVCCameraUSBMonitor mUSBMonitor;
    private UVCCameraHandler mCameraHandler;
    private CameraViewInterface mUVCCameraView;
    private ImageButton mBtnCapture;
    private TextView mBtnCancel;

    private Intent intentResult;

    private boolean isCaptureToStorage;

    private ImageButton mBtnRecord;
    private ImageButton mBtnStopRecord;
    private volatile boolean isRecording = false;
    private String lastVideoPath = null;
    private boolean isVideoRecordingMode = false;
    private String videoFilePath = null;
    private boolean isReceiverRegistered = false;

    // Callback to capture camera events
    private final UVCCameraHandler.CameraCallback cameraCallback = new UVCCameraHandler.CameraCallback() {
        @Override public void onOpen() {}
        @Override public void onClose() {}
        @Override public void onStartPreview() {}
        @Override public void onStopPreview() {}
        @Override public void onStartRecording() {}

        @Override
        public void onStopRecording(String path) {
            // Assigns the path of the saved video
            lastVideoPath = path;
            videoFilePath = path;
            Log.i(TAG, "onStopRecording(String): Assigned video path: " + path);
        }

        @Override public void onStopRecording() {
            // wait, log in and update MediaStore
            if (lastVideoPath != null) {
                File videoFile = new File(lastVideoPath);
                if (videoFile.exists()) {
                    Log.i(TAG, "Video saved in: " + lastVideoPath + ", size: " + videoFile.length() + " bytes");
                    // Updates MediaStore to ensure visibility
                    sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(videoFile)));
                    Toast.makeText(getApplicationContext(), "Video saved successfully!", Toast.LENGTH_LONG).show();
                } else {
                    Log.e(TAG, "Video file not found: " + lastVideoPath);
                    Toast.makeText(getApplicationContext(), "Error saving video!", Toast.LENGTH_LONG).show();
                }
            } else {
                Log.e(TAG, "Video path is null after recording");
                Toast.makeText(getApplicationContext(), "Error saving video!", Toast.LENGTH_LONG).show();
            }
        }

        @Override public void onError(Exception e) {
            Log.e(TAG, "Camera error occurred", e);
            runOnUiThread(() -> {
                Toast.makeText(getApplicationContext(), "Camera error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }
    };

    // Lifecycle Methods

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usbcamera);

        final View view = findViewById(R.id.camera_view);
        mUVCCameraView = (CameraViewInterface) view;

        mBtnCapture = findViewById(R.id.btn_capture);
        mBtnCapture.setOnClickListener(mOnCaptureClickListener);
        mBtnCancel = findViewById(R.id.btn_cancel);
        mBtnCancel.setOnClickListener(mOnCancelClickListener);
        mBtnRecord = findViewById(R.id.btn_record);
        mBtnRecord.setOnClickListener(mOnRecordClickListener);
        mBtnStopRecord = findViewById(R.id.btn_stop_record);
        mBtnStopRecord.setOnClickListener(mOnStopRecordClickListener);

        // Initialize all invisibles
        mBtnCapture.setVisibility(View.GONE);
        mBtnRecord.setVisibility(View.GONE);
        mBtnStopRecord.setVisibility(View.GONE);

        mUSBMonitor = new LibUVCCameraUSBMonitor(this, mOnDeviceConnectListener);
        mCameraHandler = UVCCameraHandler.createHandler(this, mUVCCameraView,
                1, PREVIEW_WIDTH, PREVIEW_HEIGHT, PREVIEW_MODE);
        mCameraHandler.addCallback(cameraCallback);

        // Fix: Add null check for extras
        Bundle extras = getIntent().getExtras();
        isCaptureToStorage = extras != null && extras.getBoolean("capture_to_storage", false);
        Intent intent = getIntent();
        isVideoRecordingMode = intent.getBooleanExtra("video_recording", false);
        // Controlling the initial visibility of buttons
        if (isVideoRecordingMode) {
            mBtnRecord.setVisibility(View.VISIBLE);
            mBtnStopRecord.setVisibility(View.GONE);
            mBtnCapture.setVisibility(View.GONE);
        } else {
            mBtnCapture.setVisibility(View.VISIBLE);
            mBtnRecord.setVisibility(View.GONE);
            mBtnStopRecord.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mUSBMonitor.register();
        if (mUVCCameraView != null)
            mUVCCameraView.onResume();

        intentResult = new Intent();

        Log.d(TAG, "***** Device count: " + mUSBMonitor.getDeviceCount());

        if (mUSBMonitor.getDeviceCount() == 0) {
            intentResult.putExtra("exit_code", "exit_no_device");
            setResult(RESULT_CANCELED, intentResult);
            finish();
        }
    }

    @Override
    protected void onStop() {
        mCameraHandler.close();
        if (mUVCCameraView != null)
            mUVCCameraView.onPause();
        super.onStop();
    }

    @Override
    public void onDestroy() {
        if (mCameraHandler != null) {
            mCameraHandler.release();
            mCameraHandler = null;
        }
        if (mUSBMonitor != null) {
            mUSBMonitor.destroy();
            mUSBMonitor = null;
        }
        mUVCCameraView = null;
        super.onDestroy();
    }

//    Device Connection Methods

    private final OnDeviceConnectListener mOnDeviceConnectListener = new OnDeviceConnectListener() {
        @Override
        public void onAttach(UsbDevice device) {
            Log.d(TAG, "Device Attached");
            List<UsbDevice> devices = mUSBMonitor.getDeviceList();
            if (devices.size() == 1) {
                UsbDevice item = devices.get(0);
                mUSBMonitor.requestPermission(item);
            } else {
                CameraDialog.showDialog(USBCameraActivity.this);
            }
        }

        @Override
        public void onDettach(UsbDevice device) {
            Log.d(TAG, "Device Detached");
        }

        @Override
        public void onConnect(UsbDevice device, LibUVCCameraUSBMonitor.UsbControlBlock ctrlBlock, boolean createNew) {
            Log.d(TAG, "Connecting to Device");
            showToast("Connecting to Device", Toast.LENGTH_SHORT);
            mCameraHandler.open(ctrlBlock);
            startPreview();
        }

        @Override
        public void onDisconnect(UsbDevice device, LibUVCCameraUSBMonitor.UsbControlBlock ctrlBlock) {
            Log.d(TAG, "Disconnecting device");
            if (mCameraHandler != null) {
                queueEvent(new Runnable() {
                    @Override
                    public void run() {
                        mCameraHandler.close();
                    }
                }, 0);
                exitCancelWithCode("device_disconnected");
            }
        }

        @Override
        public void onCancel(UsbDevice device) {
            Log.d(TAG, "onCancel called");
            exitCancelWithCode("user_canceled");
        }
    };

    @Override
    public LibUVCCameraUSBMonitor getUSBMonitor() {
        return mUSBMonitor;
    }

    @Override
    public void onDialogResult(boolean canceled) {
        Log.d(TAG, "onDialogResult canceled: " + canceled);
        if (canceled) {
            exitCancelWithCode("user_canceled");
        }
    }


    // Utility and other methods

    private void showToast(String msg, int duration) {
        Toast.makeText(this, msg, duration).show();
    }

    private void exitCancelWithCode(String code) {
        intentResult.putExtra("exit_code", code);
        setResult(RESULT_CANCELED, intentResult);
        finish();
    }

    private void startPreview() {
        final SurfaceTexture st = mUVCCameraView.getSurfaceTexture();
        if (st == null) {
            Log.e(TAG, "SurfaceTexture is null, cannot start preview");
            return;
        }
        mCameraHandler.startPreview(new Surface(st));
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Adjusts button visibility when starting preview
                if (isVideoRecordingMode) {
                    mBtnRecord.setVisibility(!isRecording ? View.VISIBLE : View.GONE);
                    mBtnStopRecord.setVisibility(isRecording ? View.VISIBLE : View.GONE);
                    mBtnCapture.setVisibility(View.GONE);
                } else {
                    mBtnCapture.setVisibility(View.VISIBLE);
                    mBtnRecord.setVisibility(View.GONE);
                    mBtnStopRecord.setVisibility(View.GONE);
                }
                mBtnCancel.setVisibility(View.VISIBLE);
            }
        });
    }

    private File saveImgToCache(Bitmap bitmap, String fileName) {
       if (bitmap == null) {
           Log.e(TAG, "Bitmap is null, cannot save to cache");
           return null;
       }

       File dir = new File(getCacheDir(), "USBCamera");
       if (!dir.exists() && !dir.mkdirs()) {
           Log.e(TAG, "Failed to create cache directory");
           return null;
       }

       try {
           if (dir.canWrite()) {
               // Use provided fileName (should include extension)
               File cacheFile = new File(dir, fileName);

               // Use try-with-resources for automatic closure
               try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(cacheFile))) {
                   if (!bitmap.compress(Bitmap.CompressFormat.PNG, 80, bos)) {
                       Log.e(TAG, "Failed to compress bitmap");
                       return null;
                   }
                   bos.flush();
               }
               return cacheFile;
           }
           return null;
       } catch (IOException ex) {
           Log.e(TAG, "Error saving image to cache", ex);
       }
       return null;
    }

    private File captureCameraImage(boolean saveToStorage) {
        TextureView cameraTextureView = (TextureView) mUVCCameraView;
        Bitmap bitmap = cameraTextureView.getBitmap();

        if (bitmap == null) {
            Log.e(TAG, "Failed to capture bitmap from camera view");
            return null;
        }

        // Generates random name for the file with extension
        String fileName = UUID.randomUUID().toString() + ".png";

        File cacheFile = saveImgToCache(bitmap, fileName);

        if (!saveToStorage) {
            return cacheFile;
        }

        final ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/ExternalCamera");

        final ContentResolver resolver = getContentResolver();
        Uri uri = null;

        try {
            final Uri contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            uri = resolver.insert(contentUri, values);

            if (uri == null)
                throw new IOException("Failed to create new MediaStore record.");

            try (final OutputStream stream = resolver.openOutputStream(uri)) {
                if (stream == null)
                    throw new IOException("Failed to open output stream.");

                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                    throw new IOException("Failed to save bitmap.");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error saving image to MediaStore", e);
            if (uri != null) {
                // Don't leave an orphan entry in the MediaStore
                resolver.delete(uri, null, null);
            }
        }
        return cacheFile;
    }

    private final View.OnClickListener mOnCancelClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mCameraHandler.close();
            exitCancelWithCode("user_canceled");
        }
    };

    private final View.OnClickListener mOnCaptureClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mCameraHandler.isOpened()) {
                File imgResult = captureCameraImage(isCaptureToStorage);

                if (imgResult == null) {
                    Toast.makeText(USBCameraActivity.this, "Failed to capture image", Toast.LENGTH_SHORT).show();
                    return;
                }

                Uri fileUri = Uri.fromFile(imgResult);

                mCameraHandler.close();

                intentResult.putExtra("exit_code", "success");
                intentResult.putExtra("img_file", imgResult);
                intentResult.putExtra("img_uri", fileUri);
                setResult(RESULT_OK, intentResult);
                finish();
            }
        }
    };

    private final View.OnClickListener mOnRecordClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mCameraHandler != null && !isRecording) {
                mCameraHandler.startRecording();
                isRecording = true;
                showToast("Recording started", Toast.LENGTH_SHORT);
                mBtnRecord.setVisibility(View.GONE);
                mBtnStopRecord.setVisibility(View.VISIBLE);
            }
        }
    };

    private final View.OnClickListener mOnStopRecordClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mCameraHandler != null && isRecording) {
                mCameraHandler.stopRecording();
                isRecording = false;
                showToast("Recording stopped", Toast.LENGTH_SHORT);
                mBtnRecord.setVisibility(View.VISIBLE);
                mBtnStopRecord.setVisibility(View.GONE);
                // After stopping, send the result to the intent
                if (isVideoRecordingMode) {
                    // Assuming the video path is in videoFilePath
                    intentResult.putExtra("exit_code", "success");
                    intentResult.putExtra("video_uri", videoFilePath != null ? videoFilePath : "");
                    setResult(RESULT_OK, intentResult);
                    finish();
                }
            }
        }
    };

    // Receive broadcast to stop recording externally
    @Override
    protected void onResume() {
        super.onResume();
        if (isVideoRecordingMode && !isReceiverRegistered) {
            try {
                android.content.IntentFilter filter = new android.content.IntentFilter("id.periksa.plugins.usbcamera.STOP_RECORDING");
                // Fix: Check API level for RECEIVER_NOT_EXPORTED
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(stopRecordReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED);
                } else {
                    registerReceiver(stopRecordReceiver, filter);
                }
                isReceiverRegistered = true;
            } catch (Exception e) {
                Log.e(TAG, "Error registering receiver", e);
            }
        }
    }

    @Override
    protected void onPause() {
        if (isVideoRecordingMode && isReceiverRegistered) {
            try {
                unregisterReceiver(stopRecordReceiver);
                isReceiverRegistered = false;
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Receiver was not registered", e);
            }
        }
        super.onPause();
    }

    private final android.content.BroadcastReceiver stopRecordReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            if (isRecording) {
                mBtnStopRecord.performClick();
            }
        }
    };

}