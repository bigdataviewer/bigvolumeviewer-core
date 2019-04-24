package tpietzsch.multires;

import net.imglib2.EuclideanSpace;
import net.imglib2.realtransform.AffineTransform3D;

/**
 * A 3D stack (simple or multi-resolution)
 * <p>
 * This may be used as part of a cache key, so {@code equals()} and
 * {@code hashCode()} should be overridden such that {@link Stack3D}s referring
 * the same image data are equal.
 *
 * @param <T>
 *            pixel type
 */
public interface Stack3D< T > extends EuclideanSpace, Typed< T >
{
	/**
	 * Get the transformation from image coordinates to world coordinates.
	 *
	 * @return transformation from image coordinates to world coordinates.
	 */
	AffineTransform3D getSourceTransform();

	@Override
	default int numDimensions()
	{
		return 3;
	}
}
