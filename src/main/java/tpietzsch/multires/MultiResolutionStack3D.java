package tpietzsch.multires;

import static tpietzsch.multires.DownSamplingScheme.DEFAULT_BLOCK_AVERAGE;

import java.util.List;

/**
 * A 3D stack with multiple resolution levels.
 * <p>
 * This may be used as part of a cache key, so {@code equals()} and
 * {@code hashCode()} should be overridden such that
 * {@link MultiResolutionStack3D}s referring the same image data are equal.
 *
 * @param <T>
 *            pixel type
 */
public interface MultiResolutionStack3D< T > extends Stack3D< T >
{
	default DownSamplingScheme getDownSamplingScheme()
	{
		return DEFAULT_BLOCK_AVERAGE;
	}

	/**
	 * Returns the list of all resolution levels. By default, at index {@code 0}
	 * is the full resolution, and resolution level at index {@code i>j} has
	 * lower resolution (more down-sampled) than index {@code j}.
	 *
	 * @return list of all resolution levels.
	 */
	List< ? extends ResolutionLevel3D< T > > resolutions();
}
