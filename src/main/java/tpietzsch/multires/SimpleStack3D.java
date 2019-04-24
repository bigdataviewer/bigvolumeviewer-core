package tpietzsch.multires;

import net.imglib2.RandomAccessibleInterval;

/**
 * A simple 3D stack (not multi-resolution)
 * <p>
 * This may be used as part of a cache key, so {@code equals()} and
 * {@code hashCode()} should be overridden such that {@link SimpleStack3D}s
 * referring the same image data are equal.
 *
 * @param <T>
 *            pixel type
 */
public interface SimpleStack3D< T > extends Stack3D< T >
{
	/**
	 * Get the image data.
	 *
	 * @return the image.
	 */
	RandomAccessibleInterval< T > getImage();
}
