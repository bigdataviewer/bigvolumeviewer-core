package tpietzsch.multires;

import net.imglib2.*;
import net.imglib2.realtransform.*;

import java.nio.*;

public class BufferedSimpleStack3D implements SimpleStack3D {
    final ByteBuffer backingBuffer;
    final Object type;
    final int[] dimensions;

    public BufferedSimpleStack3D(ByteBuffer buffer, Object type, int[] dimensions) {
        this.backingBuffer = buffer;
        this.type = type;
        this.dimensions = dimensions;
    }

    /**
     * Get the image data.
     *
     * @return the image.
     */
    @Override
    public RandomAccessibleInterval getImage() {
        return null;
    }

    public ByteBuffer getBuffer() {
        return backingBuffer.duplicate();
    }

    /**
     * Get the transformation from image coordinates to world coordinates.
     *
     * @return transformation from image coordinates to world coordinates.
     */
    @Override
    public AffineTransform3D getSourceTransform() {
        return null;
    }

    @Override
    public int numDimensions() {
        return 3;
    }

    @Override
    public Object getType() {
        return type;
    }

    public int[] getDimensions() {
        return dimensions;
    }
}
