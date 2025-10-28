package id.periksa.plugins.usbcamera;

import java.nio.ByteBuffer;

/**
 * Utility class for converting YUV420SP (NV21) to I420 format for LiveKit
 *
 * YUV420SP (NV21): 2-plane format with Y plane followed by interleaved VU plane
 * I420: 3-plane format with separate Y, U, V planes
 */
public class YUVConverter {

    /**
     * Convert YUV420SP (NV21) to I420 format
     *
     * @param nv21Data Source YUV420SP data
     * @param width Frame width
     * @param height Frame height
     * @return I420Data object containing separate Y, U, V planes
     */
    public static I420Data convertYUV420SPToI420(byte[] nv21Data, int width, int height) {
        if (nv21Data == null || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid input parameters");
        }

        int ySize = width * height;
        int uvSize = ySize / 4;

        // Validate input data size
        int expectedSize = width * height * 3 / 2; // Y plane + UV plane
        if (nv21Data.length < expectedSize) {
            throw new IllegalArgumentException(
                "Input data too small. Expected at least " + expectedSize +
                " bytes but got " + nv21Data.length
            );
        }

        // Allocate direct ByteBuffers for LiveKit (required for native code)
        ByteBuffer yPlane = ByteBuffer.allocateDirect(ySize);
        ByteBuffer uPlane = ByteBuffer.allocateDirect(uvSize);
        ByteBuffer vPlane = ByteBuffer.allocateDirect(uvSize);

        // Copy Y plane (identical in both formats)
        yPlane.put(nv21Data, 0, ySize);
        yPlane.rewind();

        // De-interleave UV plane
        // NV21 format: ...VUVUVU... (interleaved)
        // I420 format: ...UUU... ...VVV... (separate)
        int uvStart = ySize;
        for (int i = 0; i < uvSize; i++) {
            int vuIndex = uvStart + (i * 2);
            vPlane.put(nv21Data[vuIndex]);     // V component
            uPlane.put(nv21Data[vuIndex + 1]); // U component
        }

        uPlane.rewind();
        vPlane.rewind();

        return new I420Data(yPlane, uPlane, vPlane, width, height);
    }

    /**
     * Container class for I420 frame data
     */
    public static class I420Data {
        public final ByteBuffer yPlane;
        public final ByteBuffer uPlane;
        public final ByteBuffer vPlane;
        public final int width;
        public final int height;
        public final int strideY;
        public final int strideU;
        public final int strideV;
        public final int chromaWidth;
        public final int chromaHeight;

        public I420Data(ByteBuffer yPlane, ByteBuffer uPlane, ByteBuffer vPlane, int width, int height) {
            this.yPlane = yPlane;
            this.uPlane = uPlane;
            this.vPlane = vPlane;
            this.width = width;
            this.height = height;

            // Calculate strides and chroma dimensions per I420 spec
            this.strideY = width;
            this.strideU = (width + 1) / 2;
            this.strideV = (width + 1) / 2;
            this.chromaWidth = (width + 1) / 2;
            this.chromaHeight = (height + 1) / 2;
        }

        /**
         * Release native ByteBuffer resources
         */
        public void release() {
            // Direct ByteBuffers are managed by native code
            // No explicit cleanup needed, but we can clear references
            yPlane.clear();
            uPlane.clear();
            vPlane.clear();
        }
    }
}
