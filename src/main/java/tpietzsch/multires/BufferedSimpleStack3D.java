package tpietzsch.multires;

import java.nio.ByteBuffer;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;

/**
 * A {@link SimpleStack3D} implementation for use in scenery.
 * This holds no {@link RandomAccessibleInterval}, but instead
 * <ul>
 * <li>a {@link ByteBuffer} with the image data,</li>
 * <li>an {@code int[]} array with the image dimensions</li>
 * </ul>
 *
 * @param <T>
 *            pixel type
 */
// TODO move to scenery package
public class BufferedSimpleStack3D< T > implements SimpleStack3D< T >
{
	private final ByteBuffer backingBuffer;

	private final T type;

	private final int[] dimensions;

	private final AffineTransform3D sourceTransform;

	public BufferedSimpleStack3D( ByteBuffer buffer, T type, int[] dimensions, final AffineTransform3D sourceTransform )
	{
		this.backingBuffer = buffer;
		this.type = type;
		this.dimensions = dimensions;
		this.sourceTransform = sourceTransform.copy();
	}

	/**
	 * Get the image data. Returns {@code} null, because
	 *
	 * @return {@code null}
	 */
	@Override
	public RandomAccessibleInterval getImage()
	{
		return null;
	}

	public ByteBuffer getBuffer()
	{
		return backingBuffer.duplicate();
	}

	/**
	 * Get the transformation from image coordinates to world coordinates.
	 *
	 * @return transformation from image coordinates to world coordinates.
	 */
	@Override
	public AffineTransform3D getSourceTransform()
	{
		return sourceTransform;
	}

	@Override
	public T getType()
	{
		return type;
	}

	public int[] getDimensions()
	{
		return dimensions;
	}
}
