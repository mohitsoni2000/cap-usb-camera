package id.periksa.plugins.usbcamera;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

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
    private VideoSource videoSource;
    private VideoSink videoSink;
    private USBCameraVideoCapturer capturer;
    private boolean isStreaming = false;

    public LiveKitUSBCameraHelper(Activity activity) {
        this.activity = activity;
    }

    /**
     * Create a VideoSource for LiveKit
     *
     * @param eglBase EglBase context from LiveKit
     * @return VideoSource that can be used with LiveKit
     */
    public VideoSource createVideoSource(EglBase eglBase) {
        if (videoSource != null) {
            Log.w(TAG, "VideoSource already created");
            return videoSource;
        }

        // Create video source using LiveKit's factory
        // Note: You need to get the PeerConnectionFactory from your LiveKit Room
        // This is a simplified version - adjust based on your LiveKit setup
        Log.d(TAG, "Creating VideoSource for LiveKit");

        // The VideoSource will be created by LiveKit's factory
        // This is just a helper to manage the USB camera integration
        return null; // Return null here - developers should create VideoSource via LiveKit SDK
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
