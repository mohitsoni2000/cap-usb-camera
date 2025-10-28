package id.periksa.plugins.usbcamera;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import java.util.Locale;

import livekit.org.webrtc.VideoSink;
import livekit.org.webrtc.VideoSource;
import livekit.org.webrtc.EglBase;

/**
 * Helper class for integrating USB camera with LiveKit
 *
 * Example usage:
 * <pre>
 * // Create helper with your LiveKit Room and Activity
 * LiveKitUSBCameraHelper helper = new LiveKitUSBCameraHelper(activity);
 *
 * // Get video source for LiveKit
 * VideoSource videoSource = helper.createVideoSource(eglBase);
 *
 * // Start USB camera and connect to LiveKit
 * helper.startUSBCamera();
 *
 * // Publish video track with LiveKit
 * LocalVideoTrack videoTrack = LocalVideoTrack.createVideoTrack(
 *     "usb-camera",
 *     videoSource,
 *     new VideoTrackPublishOptions(),
 *     context
 * );
 * room.localParticipant.publishVideoTrack(videoTrack);
 *
 * // Stop when done
 * helper.stopUSBCamera();
 * </pre>
 */
public class LiveKitUSBCameraHelper {
    private static final String TAG = "LiveKitUSBCameraHelper";
    private static final int REQUEST_CODE_USB_CAMERA = 12345;

    private final Activity activity;
    private VideoSink videoSink;
    private USBCameraVideoCapturer capturer;
    private volatile boolean isStreaming = false;

    public LiveKitUSBCameraHelper(Activity activity) {
        this.activity = activity;
    }

    /**
     * Create a VideoSource for LiveKit
     *
     * Note: This is a placeholder method. Developers should create VideoSource
     * directly using LiveKit SDK's PeerConnectionFactory.
     *
     * @param eglBase EglBase context from LiveKit
     * @return null - VideoSource should be created via LiveKit SDK
     * @deprecated This method is not needed. Create VideoSource via LiveKit SDK directly.
     */
    @Deprecated
    public VideoSource createVideoSource(EglBase eglBase) {
        Log.w(TAG, "createVideoSource() is deprecated. Create VideoSource via LiveKit SDK directly.");
        return null;
    }

    /**
     * Set the VideoSink from LiveKit's VideoSource
     * Call this after creating the VideoSource with LiveKit
     *
     * @param sink VideoSink from videoSource.getCapturerObserver()
     */
    public void setVideoSink(VideoSink sink) {
        this.videoSink = sink;
        Log.d(TAG, "VideoSink set for USB camera");
    }

    /**
     * Start USB camera streaming to LiveKit
     * This will launch the USBCameraStreamActivity in LiveKit mode
     */
    public void startUSBCamera() {
        if (isStreaming) {
            Log.w(TAG, "USB camera already streaming");
            return;
        }

        if (videoSink == null) {
            Log.e(TAG, "VideoSink not set. Call setVideoSink() first.");
            return;
        }

        Intent intent = new Intent(activity, USBCameraStreamActivity.class);
        intent.putExtra("streaming_mode", USBCameraStreamActivity.MODE_LIVEKIT);

        // Set the video sink statically (will be picked up by activity)
        USBCameraStreamActivity.setLiveKitVideoSink(videoSink);

        activity.startActivityForResult(intent, REQUEST_CODE_USB_CAMERA);
        isStreaming = true;

        Log.d(TAG, "Started USB camera in LiveKit mode");
    }

    /**
     * Stop USB camera streaming
     */
    public void stopUSBCamera() {
        if (!isStreaming) {
            return;
        }

        capturer = USBCameraStreamActivity.getLiveKitCapturer();
        if (capturer != null) {
            capturer.stopCapture();
            Log.d(TAG, "USB camera stopped");
        }

        isStreaming = false;
    }

    /**
     * Get the current capturer instance
     */
    public USBCameraVideoCapturer getCapturer() {
        if (capturer == null) {
            capturer = USBCameraStreamActivity.getLiveKitCapturer();
        }
        return capturer;
    }

    /**
     * Check if currently streaming
     */
    public boolean isStreaming() {
        return isStreaming && (capturer != null && capturer.isCapturing());
    }

    /**
     * Get statistics about the stream
     */
    public String getStreamStats() {
        if (capturer == null) {
            return "Not streaming";
        }

        return String.format(
            Locale.US,
            "USB Camera Stats: %dx%d, %d frames captured",
            capturer.getWidth(),
            capturer.getHeight(),
            capturer.getFrameCount()
        );
    }

    /**
     * Handle activity result
     * Call this from your Activity's onActivityResult
     */
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_USB_CAMERA) {
            if (resultCode == Activity.RESULT_OK) {
                Log.d(TAG, "USB camera stream started successfully");
            } else {
                Log.w(TAG, "USB camera stream was cancelled or failed");
                isStreaming = false;
            }
        }
    }
}
