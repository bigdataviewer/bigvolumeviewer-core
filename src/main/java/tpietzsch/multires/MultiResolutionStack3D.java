package tpietzsch.multires;

import java.util.List;
import net.imglib2.EuclideanSpace;
import net.imglib2.realtransform.AffineTransform3D;

import static tpietzsch.multires.DownSamplingScheme.DEFAULT_BLOCK_AVERAGE;

/**
 * A 3D stack with multiple resolution levels.
 * <p>
 * This may be used as part of a cache key, so {@code equals()} and {@code hashCode()} should be overridden such that {@link MultiResolutionStack3D}s referring the same image data are equal.
 *
 * @param <T>
 * 		pixel type
 */
public interface MultiResolutionStack3D< T > extends EuclideanSpace, Typed< T >
{
	/**
	 * Get the transformation from image coordinates (of level 0) to world coordinates.
	 *
	 * @return transformation from image coordinates (of level 0) to world coordinates.
	 */
	AffineTransform3D getSourceTransform();

	default DownSamplingScheme getDownSamplingScheme()
	{
		return DEFAULT_BLOCK_AVERAGE;
	}

	/**
	 * Returns the list of all resolution levels.
	 * By default, at index {@code 0} is the full resolution, and resolution level at index {@code i>j} has lower resolution (more down-sampled) than index {@code j}.
	 *
	 * @return list of all resolution levels.
	 */
	List< ? extends ResolutionLevel3D< T > > resolutions();

	@Override
	default int numDimensions()
	{
		return 3;
	}
}
