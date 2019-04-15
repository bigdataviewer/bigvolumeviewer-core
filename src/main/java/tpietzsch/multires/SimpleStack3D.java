package tpietzsch.multires;

import java.util.List;
import net.imglib2.EuclideanSpace;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;

import static tpietzsch.multires.DownSamplingScheme.DEFAULT_BLOCK_AVERAGE;

/**
 * A simple 3D stack (not multi-resolution)
 * <p>
 * This may be used as part of a cache key, so {@code equals()} and {@code hashCode()} should be overridden such that {@link SimpleStack3D}s referring the same image data are equal.
 *
 * @param <T>
 * 		pixel type
 */
public interface SimpleStack3D< T > extends EuclideanSpace, Typed< T >
{
	/**
	 * Get the transformation from image coordinates to world coordinates.
	 *
	 * @return transformation from image coordinates to world coordinates.
	 */
	AffineTransform3D getSourceTransform();

	/**
	 * Get the image data.
	 *
	 * @return the image.
	 */
	RandomAccessibleInterval< T > getImage();

	@Override
	default int numDimensions()
	{
		return 3;
	}
}
