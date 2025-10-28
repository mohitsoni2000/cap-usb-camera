package id.periksa.plugins.usbcamera;

import android.util.Log;

import com.serenegiant.usb_libuvccamera.IFrameCallback;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

import livekit.org.webrtc.VideoFrame;
import livekit.org.webrtc.VideoSink;
import livekit.org.webrtc.JavaI420Buffer;

/**
 * Custom video capturer for LiveKit that captures frames from USB camera
 * and converts them from YUV420SP (NV21) to I420 format
 */
public class USBCameraVideoCapturer implements IFrameCallback {
    private static final String TAG = "USBCameraVideoCapturer";

    private final int width;
    private final int height;
    private volatile boolean isCapturing = false;
    private volatile VideoSink videoSink;
    private final AtomicLong frameCount = new AtomicLong(0);

    public USBCameraVideoCapturer(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /**
     * Set the video sink that will receive frames
     */
    public void setVideoSink(VideoSink sink) {
        this.videoSink = sink;
    }

    /**
     * Start capturing frames
     */
    public void startCapture() {
        isCapturing = true;
        frameCount.set(0);
        Log.d(TAG, "Started capturing USB camera frames for LiveKit");
    }

    /**
     * Stop capturing frames
     */
    public void stopCapture() {
        isCapturing = false;
        Log.d(TAG, "Stopped capturing USB camera frames");
    }

    /**
     * IFrameCallback implementation - called for each USB camera frame
     */
    @Override
    public void onFrame(ByteBuffer frame) {
        if (!isCapturing || videoSink == null || frame == null) {
            return;
        }

        try {
            // Save and restore ByteBuffer position (important for reuse)
            int position = frame.position();

            // Convert ByteBuffer to byte array
            byte[] frameData = new byte[frame.remaining()];
            frame.get(frameData);
            frame.position(position); // Reset position for reuse

            // Convert YUV420SP (NV21) to I420 format
            YUVConverter.I420Data i420Data = YUVConverter.convertYUV420SPToI420(
                frameData, width, height
            );

            // Create I420Buffer for LiveKit
            JavaI420Buffer i420Buffer = JavaI420Buffer.wrap(
                width,
                height,
                i420Data.yPlane,
                i420Data.strideY,
                i420Data.uPlane,
                i420Data.strideU,
                i420Data.vPlane,
                i420Data.strideV,
                null // No release callback needed for direct buffers
            );

            // Create VideoFrame with timestamp
            long timestampNs = System.nanoTime();
            VideoFrame videoFrame = new VideoFrame(i420Buffer, 0, timestampNs);

            // Push frame to LiveKit
            videoSink.onFrame(videoFrame);

            // Release resources
            videoFrame.release();
            i420Data.release();

            long count = frameCount.incrementAndGet();
            if (count % 30 == 0) {
                Log.d(TAG, "Pushed " + count + " frames to LiveKit");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error processing frame for LiveKit: " + e.getMessage(), e);
        }
    }

    /**
     * Get the current frame count
     */
    public long getFrameCount() {
        return frameCount.get();
    }

    /**
     * Check if currently capturing
     */
    public boolean isCapturing() {
        return isCapturing;
    }

    /**
     * Get the configured width
     */
    public int getWidth() {
        return width;
    }

    /**
     * Get the configured height
     */
    public int getHeight() {
        return height;
    }
}
