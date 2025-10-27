# LiveKit Integration Guide for USB Camera Plugin

This guide explains how to integrate the USB camera plugin with LiveKit for live streaming applications.

## Overview

The plugin now supports real-time frame streaming from USB cameras, making it possible to use external USB cameras with LiveKit for video conferencing and live streaming applications.

## Features

- Real-time frame streaming from USB cameras
- Configurable resolution and frame rate
- Event-based frame delivery
- Compatible with LiveKit's video track API
- Low-latency streaming support

## Installation

```bash
npm install @periksa/cap-usb-camera
npx cap sync
```

Ensure you've added the maven repository to your `android/build.gradle`:

```gradle
allprojects {
  repositories {
      google()
      jcenter()
      maven { url 'https://raw.github.com/saki4510t/libcommon/master/repository/' }
  }
}
```

## Basic Usage

### 1. Import the Plugin

```typescript
import { UsbCamera } from '@periksa/cap-usb-camera';
```

### 2. Start Streaming

```typescript
// Start streaming from USB camera
const streamResult = await UsbCamera.startStream({
  width: 640,
  height: 480,
  frameRate: 30
});

if (streamResult.streaming) {
  console.log('Streaming started successfully');
  console.log(`Resolution: ${streamResult.width}x${streamResult.height}`);
}
```

### 3. Listen for Frames

```typescript
// Add listener for incoming frames
await UsbCamera.addListener('frame', (frameData) => {
  console.log('Received frame:', {
    width: frameData.width,
    height: frameData.height,
    format: frameData.format,
    timestamp: frameData.timestamp,
    dataSize: frameData.frameData.length
  });

  // Process frame for LiveKit (see LiveKit integration below)
  processFrameForLiveKit(frameData);
});
```

### 4. Stop Streaming

```typescript
// Stop streaming when done
await UsbCamera.stopStream();
await UsbCamera.removeAllListeners('frame');
```

## LiveKit Integration

### Option 1: Using Custom Video Track (Web)

```typescript
import { UsbCamera } from '@periksa/cap-usb-camera';
import { Room, RoomEvent, VideoPresets } from 'livekit-client';

class USBCameraVideoSource {
  private canvas: HTMLCanvasElement;
  private ctx: CanvasRenderingContext2D;
  private mediaStream: MediaStream;

  constructor(width: number = 640, height: number = 480) {
    // Create offscreen canvas for frame processing
    this.canvas = document.createElement('canvas');
    this.canvas.width = width;
    this.canvas.height = height;
    this.ctx = this.canvas.getContext('2d')!;

    // Get MediaStream from canvas
    this.mediaStream = this.canvas.captureStream(30);
  }

  async start() {
    // Start USB camera streaming
    await UsbCamera.startStream({ width: 640, height: 480, frameRate: 30 });

    // Listen for frames
    await UsbCamera.addListener('frame', (frameData) => {
      this.processFrame(frameData);
    });
  }

  processFrame(frameData: any) {
    // Convert base64 YUV frame to RGB and draw on canvas
    const frameBytes = atob(frameData.frameData);
    const imageData = this.yuv420ToRGB(
      frameBytes,
      frameData.width,
      frameData.height
    );

    this.ctx.putImageData(imageData, 0, 0);
  }

  yuv420ToRGB(yuvData: string, width: number, height: number): ImageData {
    // YUV to RGB conversion
    const rgbData = new Uint8ClampedArray(width * height * 4);

    // Conversion logic here (simplified)
    // ... YUV420SP to RGB conversion ...

    return new ImageData(rgbData, width, height);
  }

  getMediaStream(): MediaStream {
    return this.mediaStream;
  }

  async stop() {
    await UsbCamera.stopStream();
    await UsbCamera.removeAllListeners('frame');
  }
}

// Usage with LiveKit
async function startLiveKitStream() {
  const room = new Room();

  await room.connect('wss://your-livekit-server.com', 'your-token');

  // Create USB camera video source
  const usbCameraSource = new USBCameraVideoSource(640, 480);
  await usbCameraSource.start();

  // Publish to LiveKit
  const videoTrack = await room.localParticipant.publishTrack(
    usbCameraSource.getMediaStream().getVideoTracks()[0],
    {
      name: 'usb-camera',
      videoPresets: VideoPresets.h720
    }
  );

  console.log('USB camera published to LiveKit');
}
```

### Option 2: Using Native Android SDK (Recommended)

For better performance, use LiveKit's native Android SDK directly:

```kotlin
// Create custom video source for USB camera
class USBCameraVideoSource : VideoSource {
    private var videoCapturer: VideoCapturer? = null

    override fun start() {
        // Register frame receiver
        val filter = IntentFilter("id.periksa.plugins.usbcamera.FRAME_AVAILABLE")
        context.registerReceiver(frameReceiver, filter)
    }

    private val frameReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val frameData = intent.getByteArrayExtra("frame_data")
            val width = intent.getIntExtra("width", 640)
            val height = intent.getIntExtra("height", 480)

            // Convert to I420 format and feed to LiveKit
            val i420Buffer = convertYUV420ToI420(frameData, width, height)
            val videoFrame = VideoFrame(i420Buffer, 0, System.nanoTime())

            videoCapturer?.onFrame(videoFrame)
        }
    }
}
```

## Performance Considerations

### Frame Rate

- Default: 30 FPS
- Recommended for video calls: 15-30 FPS
- For high-quality streaming: 30 FPS

### Resolution

- 640x480: Good for video calls, low bandwidth
- 1280x720: HD quality, moderate bandwidth
- 1920x1080: Full HD, high bandwidth

### Bandwidth Usage

| Resolution | Frame Rate | Approximate Bandwidth |
|-----------|------------|----------------------|
| 640x480   | 30 FPS     | 500-800 Kbps        |
| 1280x720  | 30 FPS     | 1-2 Mbps            |
| 1920x1080 | 30 FPS     | 2-4 Mbps            |

## Example: Complete LiveKit Integration

```typescript
import { UsbCamera, UsbCameraFrameData } from '@periksa/cap-usb-camera';
import { Room, Track } from 'livekit-client';

class LiveKitUSBCameraStream {
  private room: Room;
  private isStreaming: boolean = false;
  private videoSource: USBCameraVideoSource;

  constructor(private serverUrl: string, private token: string) {
    this.room = new Room({
      adaptiveStream: true,
      dynacast: true,
    });
  }

  async connect() {
    await this.room.connect(this.serverUrl, this.token);
    console.log('Connected to LiveKit room');
  }

  async startUSBCameraStream() {
    // Initialize video source
    this.videoSource = new USBCameraVideoSource(640, 480);
    await this.videoSource.start();

    // Get media stream and publish
    const mediaStream = this.videoSource.getMediaStream();
    const videoTrack = mediaStream.getVideoTracks()[0];

    await this.room.localParticipant.publishTrack(videoTrack, {
      name: 'usb-camera',
      source: Track.Source.Camera,
    });

    this.isStreaming = true;
    console.log('USB camera stream published to LiveKit');
  }

  async stopUSBCameraStream() {
    if (this.videoSource) {
      await this.videoSource.stop();
    }

    this.isStreaming = false;
    console.log('USB camera stream stopped');
  }

  async disconnect() {
    await this.stopUSBCameraStream();
    await this.room.disconnect();
  }
}

// Usage
const liveKitStream = new LiveKitUSBCameraStream(
  'wss://your-livekit-server.com',
  'your-token'
);

await liveKitStream.connect();
await liveKitStream.startUSBCameraStream();

// ... stream is active ...

await liveKitStream.disconnect();
```

## Troubleshooting

### No Frames Received

1. Check USB camera connection
2. Verify permissions are granted
3. Check USB device is supported (UVC compatible)

### Low Frame Rate

1. Reduce resolution
2. Check device performance
3. Verify network bandwidth for LiveKit

### High Latency

1. Use lower resolution
2. Optimize frame processing
3. Check LiveKit server location

## API Reference

### `startStream(options?: UsbCameraStreamOptions): Promise<UsbCameraStreamResult>`

Starts streaming frames from the USB camera.

**Options:**
- `width`: Frame width (default: 640)
- `height`: Frame height (default: 480)
- `frameRate`: Target frame rate (default: 30)

**Returns:** Stream status and configuration

### `stopStream(): Promise<{status: string, exit_code: string}>`

Stops the frame streaming.

### `addListener(eventName: 'frame', callback: (data: UsbCameraFrameData) => void)`

Adds a listener for frame events.

**Frame Data:**
- `frameData`: Base64 encoded YUV420SP frame
- `width`: Frame width
- `height`: Frame height
- `format`: Frame format (YUV420SP)
- `timestamp`: Capture timestamp

## Additional Resources

- [LiveKit Documentation](https://docs.livekit.io/)
- [UVCCamera Library](https://github.com/saki4510t/UVCCamera)
- [WebRTC Frame Processing](https://webrtc.org/)

## License

MIT
