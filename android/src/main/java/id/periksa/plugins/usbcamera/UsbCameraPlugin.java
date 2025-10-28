package id.periksa.plugins.usbcamera;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;

import androidx.activity.result.ActivityResult;

import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@CapacitorPlugin(name = "UsbCamera", permissions = {
        @Permission(strings = {Manifest.permission.CAMERA}, alias = UsbCameraPlugin.PERM_CAMERA),
        @Permission(strings = {Manifest.permission.READ_EXTERNAL_STORAGE}, alias = UsbCameraPlugin.PERM_READ_EXT_STORAGE),
        @Permission(strings = {Manifest.permission.WRITE_EXTERNAL_STORAGE}, alias = UsbCameraPlugin.PERM_WRITE_EXT_STORAGE)
})
public class UsbCameraPlugin extends Plugin {

    static final String PERM_CAMERA = "camera";
    static final String PERM_READ_EXT_STORAGE = "r_ext";
    static final String PERM_WRITE_EXT_STORAGE = "w_ext";

    private static final String TAG = "PluginBridgeDebug";
    private static final String[] REQUIRED_PERMISSION_ALIASES = new String[]{
            PERM_CAMERA, PERM_READ_EXT_STORAGE, PERM_WRITE_EXT_STORAGE
    };

    private final List<String> mMissPermissions = new ArrayList<>();
    private BroadcastReceiver frameReceiver;
    private volatile boolean isStreamingActive = false;
    private boolean isFrameReceiverRegistered = false;

    @Override
    protected void handleOnStart() {
        super.handleOnStart();
    }

    @Override
    protected void handleOnStop() {
        super.handleOnStop();
        if (frameReceiver != null && isFrameReceiverRegistered) {
            try {
                getContext().unregisterReceiver(frameReceiver);
                isFrameReceiverRegistered = false;
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Frame receiver was not registered", e);
            }
            frameReceiver = null;
        }
    }

    @PluginMethod
    public void getPhoto(PluginCall call) {
        if (checkAndRequestPermissions(call)) {
            showIntent(call);
        }
    }

    private boolean checkAndRequestPermissions(PluginCall call) {
        mMissPermissions.clear();
        for (String permission : REQUIRED_PERMISSION_ALIASES) {
            boolean permissionGranted = getPermissionState(permission) == PermissionState.GRANTED;
            if (!permissionGranted) {
                mMissPermissions.add(permission);
            }
        }

        if (!mMissPermissions.isEmpty()) {
            requestPermissionForAliases(
                    mMissPermissions.toArray(new String[mMissPermissions.size()]),
                    call,
                    "appPermissionCallback"
            );
            return false;
        }
        return true;
    }

    @PermissionCallback
    private void appPermissionCallback(PluginCall call) {
        if (getPermissionState(PERM_CAMERA) != PermissionState.GRANTED) {
            call.reject("User denied required permissions");
            return;
        } else if (getPermissionState(PERM_WRITE_EXT_STORAGE) != PermissionState.GRANTED) {
            call.reject("User denied required permissions");
            return;
        }
        showIntent(call);
    }

    private void showIntent(PluginCall call) {
        JSObject configObject = call.getData();
        boolean saveToStorage = configObject.getBoolean("saveToStorage", false);

        Intent camIntent = new Intent(getActivity(), USBCameraActivity.class);
        camIntent.putExtra("capture_to_storage", saveToStorage);
        startActivityForResult(call, camIntent, "imageResult");
    }

    private String loadImageFromUri(Uri photoUri, int compressionRatio) {
        Bitmap bitmap = null;
        try {
            if (Build.VERSION.SDK_INT > 27) {
                // on newer versions of Android, use the new decodeBitmap method
                ImageDecoder.Source source = ImageDecoder.createSource(getContext().getContentResolver(), photoUri);
                bitmap = ImageDecoder.decodeBitmap(source);
            } else {
                // support older versions of Android by using getBitmap
                bitmap = MediaStore.Images.Media.getBitmap(getContext().getContentResolver(), photoUri);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 640, 480, true);

        // Convert Bitmap to Base64 string.
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, compressionRatio, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        String encoded = Base64.encodeToString(byteArray, Base64.NO_PADDING | Base64.NO_WRAP);

        return encoded;
    }

    @ActivityCallback
    private void imageResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        Bundle bundle = result.getData().getExtras();
        if (bundle != null) {
            String exitCode = (String) bundle.get("exit_code");
            int resultCode = result.getResultCode();

            String resultCodeDesc = (resultCode == -1) ? "OK" : "CANCELED";

            String b64Result = "";
            Uri photoUri = (Uri) bundle.get("img_uri");

            // Result Code is OK.
            if (resultCode == -1) {
                if (photoUri != null) {
                    b64Result = loadImageFromUri(photoUri, 85);
                    b64Result = "data:image/jpeg;base64," + b64Result;
                }
            }

            JSObject plResult = new JSObject();
            plResult.put("status_code", resultCode);
            plResult.put("status_code_s", resultCodeDesc);
            plResult.put("exit_code", exitCode);

            JSObject dataResult = new JSObject();
            if (b64Result.length() > 0) {
                dataResult.put("dataURL", b64Result);
                dataResult.put("fileURI", photoUri);
                plResult.put("data", dataResult);
            }

            call.resolve(plResult);
        }
    }

    // LiveKit Streaming Methods

    @PluginMethod
    public void startStream(PluginCall call) {
        if (checkAndRequestPermissions(call)) {
            startStreamingIntent(call);
        }
    }

    @PluginMethod
    public void stopStream(PluginCall call) {
        isStreamingActive = false;
        if (frameReceiver != null && isFrameReceiverRegistered) {
            try {
                getContext().unregisterReceiver(frameReceiver);
                isFrameReceiverRegistered = false;
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Frame receiver was not registered", e);
            }
            frameReceiver = null;
        }

        JSObject result = new JSObject();
        result.put("status", "stopped");
        result.put("exit_code", "stream_stopped");
        call.resolve(result);
    }

    private void startStreamingIntent(PluginCall call) {
        // Register broadcast receiver for frame data
        if (frameReceiver == null) {
            frameReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (!isStreamingActive) return;

                    byte[] frameData = intent.getByteArrayExtra("frame_data");
                    int width = intent.getIntExtra("width", 640);
                    int height = intent.getIntExtra("height", 480);
                    String format = intent.getStringExtra("format");

                    if (frameData != null) {
                        // Convert frame to base64 for sending to JavaScript
                        String base64Frame = Base64.encodeToString(frameData, Base64.NO_WRAP);

                        JSObject frameObject = new JSObject();
                        frameObject.put("frameData", base64Frame);
                        frameObject.put("width", width);
                        frameObject.put("height", height);
                        frameObject.put("format", format);
                        frameObject.put("timestamp", System.currentTimeMillis());

                        // Emit event to JavaScript
                        notifyListeners("frame", frameObject);
                    }
                }
            };

            IntentFilter filter = new IntentFilter("id.periksa.plugins.usbcamera.FRAME_AVAILABLE");

            // Fix: Check API level for RECEIVER_NOT_EXPORTED
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    getContext().registerReceiver(frameReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    getContext().registerReceiver(frameReceiver, filter);
                }
                isFrameReceiverRegistered = true;
                isStreamingActive = true; // Set only after successful registration
            } catch (Exception e) {
                Log.e(TAG, "Error registering frame receiver", e);
                isStreamingActive = false;
                call.reject("Failed to register frame receiver: " + e.getMessage());
                return;
            }
        } else {
            isStreamingActive = true;
        }

        Intent streamIntent = new Intent(getActivity(), USBCameraStreamActivity.class);
        streamIntent.putExtra("streaming_mode", USBCameraStreamActivity.MODE_BROADCAST);
        startActivityForResult(call, streamIntent, "streamResult");
    }

    /**
     * Start USB camera streaming in LiveKit mode
     * This mode is optimized for native LiveKit integration
     *
     * Note: This method is intended for native Android development.
     * JavaScript/TypeScript developers should use the standard startStream() method.
     */
    @PluginMethod
    public void startLiveKitStream(PluginCall call) {
        if (!checkAndRequestPermissions(call)) {
            return;
        }

        Intent streamIntent = new Intent(getActivity(), USBCameraStreamActivity.class);
        streamIntent.putExtra("streaming_mode", USBCameraStreamActivity.MODE_LIVEKIT);
        startActivityForResult(call, streamIntent, "streamResult");

        Log.d(TAG, "Started LiveKit streaming mode");
    }

    /**
     * Get LiveKit capturer instance
     * This method allows native code to access the video capturer for LiveKit integration
     *
     * @return USBCameraVideoCapturer instance or null if not available
     */
    public static USBCameraVideoCapturer getLiveKitCapturer() {
        return USBCameraStreamActivity.getLiveKitCapturer();
    }

    @ActivityCallback
    private void streamResult(PluginCall call, ActivityResult result) {
        if (call == null) return;

        Bundle bundle = result.getData() != null ? result.getData().getExtras() : null;
        if (bundle != null) {
            String exitCode = bundle.getString("exit_code", "unknown");
            int width = bundle.getInt("width", 640);
            int height = bundle.getInt("height", 480);

            JSObject plResult = new JSObject();
            plResult.put("status_code", result.getResultCode());
            plResult.put("exit_code", exitCode);
            plResult.put("width", width);
            plResult.put("height", height);

            if ("streaming_started".equals(exitCode)) {
                plResult.put("streaming", true);
                call.resolve(plResult);
            } else {
                isStreamingActive = false;
                plResult.put("streaming", false);
                call.resolve(plResult);
            }
        } else {
            isStreamingActive = false;
            JSObject errorResult = new JSObject();
            errorResult.put("status_code", result.getResultCode());
            errorResult.put("exit_code", "error");
            errorResult.put("streaming", false);
            call.resolve(errorResult);
        }
    }
}
