package id.periksa.plugins.usbcamera;

import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.serenegiant.common.BaseActivity;
import com.serenegiant.usb_libuvccamera.CameraDialog;
import com.serenegiant.usb_libuvccamera.IFrameCallback;
import com.serenegiant.usb_libuvccamera.LibUVCCameraUSBMonitor;
import com.serenegiant.usb_libuvccamera.LibUVCCameraUSBMonitor.OnDeviceConnectListener;
import com.serenegiant.usb_libuvccamera.UVCCamera;
import com.serenegiant.usbcameracommon.UVCCameraHandler;
import com.serenegiant.widget.CameraViewInterface;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * Activity for streaming USB camera frames for LiveKit integration
 */
public class USBCameraStreamActivity extends BaseActivity implements CameraDialog.CameraDialogParent {
    private static final String TAG = "USBCameraStream";

    private static final int PREVIEW_WIDTH = 640;
    private static final int PREVIEW_HEIGHT = 480;
    private static final int PREVIEW_MODE = 1; // MJPEG mode

    private LibUVCCameraUSBMonitor mUSBMonitor;
    private UVCCameraHandler mCameraHandler;
    private CameraViewInterface mUVCCameraView;
    private TextView mBtnCancel;

    private Intent intentResult;
    private volatile boolean isStreaming = false;

    // Frame callback for streaming
    private final IFrameCallback mFrameCallback = new IFrameCallback() {
        @Override
        public void onFrame(ByteBuffer frame) {
            if (!isStreaming || frame == null) return;

            try {
                // Fix: Save and restore ByteBuffer position
                int position = frame.position();
                byte[] frameData = new byte[frame.remaining()];
                frame.get(frameData);
                frame.position(position); // Reset position for reuse

                // Send frame data back to plugin via broadcast
                Intent frameIntent = new Intent("id.periksa.plugins.usbcamera.FRAME_AVAILABLE");
                frameIntent.putExtra("frame_data", frameData);
                frameIntent.putExtra("width", PREVIEW_WIDTH);
                frameIntent.putExtra("height", PREVIEW_HEIGHT);
                frameIntent.putExtra("format", "YUV420SP");

                // Note: This broadcast should ideally use local broadcast or permissions
                // for production apps to prevent interception
                sendBroadcast(frameIntent);

            } catch (Exception e) {
                Log.e(TAG, "Error processing frame: " + e.getMessage(), e);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_usbcamera_stream);

        final View view = findViewById(R.id.camera_stream_view);
        mUVCCameraView = (CameraViewInterface) view;

        mBtnCancel = findViewById(R.id.btn_cancel_stream);
        mBtnCancel.setOnClickListener(v -> {
            stopStreaming();
            exitWithCode("user_canceled");
        });

        mUSBMonitor = new LibUVCCameraUSBMonitor(this, mOnDeviceConnectListener);
        mCameraHandler = UVCCameraHandler.createHandler(this, mUVCCameraView,
                1, PREVIEW_WIDTH, PREVIEW_HEIGHT, PREVIEW_MODE);

        intentResult = new Intent();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mUSBMonitor.register();
        if (mUVCCameraView != null)
            mUVCCameraView.onResume();

        Log.d(TAG, "Device count: " + mUSBMonitor.getDeviceCount());

        if (mUSBMonitor.getDeviceCount() == 0) {
            intentResult.putExtra("exit_code", "exit_no_device");
            setResult(RESULT_CANCELED, intentResult);
            finish();
        }
    }

    @Override
    protected void onStop() {
        stopStreaming();
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

    private final OnDeviceConnectListener mOnDeviceConnectListener = new OnDeviceConnectListener() {
        @Override
        public void onAttach(UsbDevice device) {
            Log.d(TAG, "Device Attached");
            List<UsbDevice> devices = mUSBMonitor.getDeviceList();
            if (devices.size() == 1) {
                mUSBMonitor.requestPermission(devices.get(0));
            } else {
                CameraDialog.showDialog(USBCameraStreamActivity.this);
            }
        }

        @Override
        public void onDettach(UsbDevice device) {
            Log.d(TAG, "Device Detached");
            stopStreaming();
        }

        @Override
        public void onConnect(UsbDevice device, LibUVCCameraUSBMonitor.UsbControlBlock ctrlBlock, boolean createNew) {
            Log.d(TAG, "Connecting to Device");
            showToast("Connecting to Device", Toast.LENGTH_SHORT);
            mCameraHandler.open(ctrlBlock);
            startPreviewAndStreaming();
        }

        @Override
        public void onDisconnect(UsbDevice device, LibUVCCameraUSBMonitor.UsbControlBlock ctrlBlock) {
            Log.d(TAG, "Disconnecting device");
            stopStreaming();
            if (mCameraHandler != null) {
                mCameraHandler.close();
            }
            exitWithCode("device_disconnected");
        }

        @Override
        public void onCancel(UsbDevice device) {
            Log.d(TAG, "onCancel called");
            exitWithCode("user_canceled");
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
            exitWithCode("user_canceled");
        }
    }

    private void showToast(String msg, int duration) {
        Toast.makeText(this, msg, duration).show();
    }

    private void exitWithCode(String code) {
        intentResult.putExtra("exit_code", code);
        setResult(RESULT_CANCELED, intentResult);
        finish();
    }

    private void startPreviewAndStreaming() {
        final SurfaceTexture st = mUVCCameraView.getSurfaceTexture();
        if (st == null) {
            Log.e(TAG, "SurfaceTexture is null, cannot start preview");
            exitWithCode("error_no_surface");
            return;
        }

        try {
            mCameraHandler.startPreview(new Surface(st));

            // Register frame callback for streaming
            // Note: Using YUV420SP format for compatibility with most cameras
            mCameraHandler.setFrameCallback(mFrameCallback, UVCCamera.PIXEL_FORMAT_YUV420SP);
        } catch (Exception e) {
            Log.e(TAG, "Error starting preview and streaming", e);
            exitWithCode("error_start_failed");
            return;
        }

        runOnUiThread(() -> {
            mBtnCancel.setVisibility(View.VISIBLE);
            isStreaming = true;
            showToast("Streaming started", Toast.LENGTH_SHORT);

            // Notify that streaming has started
            intentResult.putExtra("exit_code", "streaming_started");
            intentResult.putExtra("width", PREVIEW_WIDTH);
            intentResult.putExtra("height", PREVIEW_HEIGHT);
            setResult(RESULT_OK, intentResult);
        });
    }

    private void stopStreaming() {
        if (isStreaming) {
            isStreaming = false;
            if (mCameraHandler != null) {
                mCameraHandler.setFrameCallback(null, 0);
            }
            Log.d(TAG, "Streaming stopped");
        }
    }
}
