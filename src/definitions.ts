export interface UsbCameraPhotoOptions {
  /** Let app save captured photo to the device storage. */
  saveToStorage?: boolean;
}

export interface UsbCameraResult {
  /** Status Code from Intent ResultCode. */
  status_code: number;
  /** Description string of the status code number. */
  status_code_s: string;
  /** Description of exit or cancel reason. */
  exit_code: string;
  /**
   * Result data payload, contains image in base64 DataURL,
   * and Android filesystem URI to the file.
   * */
  data?: {
    dataURL?: string,
    fileURI?: string,
  };
}

export interface UsbCameraStreamOptions {
  /** Frame rate for streaming (frames per second). Default: 30 */
  frameRate?: number;
  /** Width of the stream. Default: 640 */
  width?: number;
  /** Height of the stream. Default: 480 */
  height?: number;
}

export interface UsbCameraStreamResult {
  /** Status code from streaming activity */
  status_code: number;
  /** Exit code description */
  exit_code: string;
  /** Whether streaming is active */
  streaming: boolean;
  /** Stream width */
  width?: number;
  /** Stream height */
  height?: number;
}

export interface UsbCameraFrameData {
  /** Base64 encoded frame data in YUV420SP format */
  frameData: string;
  /** Frame width */
  width: number;
  /** Frame height */
  height: number;
  /** Frame format (e.g., 'YUV420SP') */
  format: string;
  /** Timestamp when frame was captured */
  timestamp: number;
}

export type UsbCameraPluginEvents = 'frame';

export interface UsbCameraPlugin {
  /**
   * Open native activity and get photo from usb camera device attached to the phone.
   * If there is no usb device connected, will return canceled exit code.
   * @returns {Promise<UsbCameraResult>} Image and result status.
   * */
  getPhoto(config?: UsbCameraPhotoOptions): Promise<UsbCameraResult>;

  /**
   * Start streaming camera frames for LiveKit integration.
   * Frames will be emitted via the 'frame' event.
   * @param {UsbCameraStreamOptions} options - Streaming configuration
   * @returns {Promise<UsbCameraStreamResult>} Streaming status
   */
  startStream(options?: UsbCameraStreamOptions): Promise<UsbCameraStreamResult>;

  /**
   * Stop streaming camera frames.
   * @returns {Promise<{status: string, exit_code: string}>} Stop status
   */
  stopStream(): Promise<{status: string, exit_code: string}>;

  /**
   * Start streaming in LiveKit mode for native integration.
   * This mode directly pushes I420 frames to LiveKit without broadcasting.
   *
   * Note: This method is primarily for native Android development.
   * For JavaScript/Web integration, use startStream() with the canvas approach.
   *
   * @param {UsbCameraStreamOptions} options - Streaming configuration
   * @returns {Promise<UsbCameraStreamResult>} Streaming status
   */
  startLiveKitStream(options?: UsbCameraStreamOptions): Promise<UsbCameraStreamResult>;

  /**
   * Add a listener for camera frame events.
   * @param {UsbCameraPluginEvents} eventName - Name of the event to listen to
   * @param {(data: UsbCameraFrameData) => void} listenerFunc - Callback function to handle frames
   */
  addListener(
    eventName: UsbCameraPluginEvents,
    listenerFunc: (data: UsbCameraFrameData) => void
  ): Promise<any>;

  /**
   * Remove all listeners for an event.
   * @param {UsbCameraPluginEvents} eventName - Name of the event
   */
  removeAllListeners(eventName?: UsbCameraPluginEvents): Promise<void>;
}
