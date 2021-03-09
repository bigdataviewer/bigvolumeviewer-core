/*-
 * #%L
 * Volume rendering of bdv datasets
 * %%
 * Copyright (C) 2018 - 2021 Tobias Pietzsch
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package tpietzsch.multires;

import net.imglib2.EuclideanSpace;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;

/**
 * One resolution level of a {@link MultiResolutionStack3D}
 * <p>
 * This may be used as part of a cache key, so {@code equals()} and {@code hashCode()} should be overridden such that {@link ResolutionLevel3D}s referring the same image data are equal.
 *
 * @param <T>
 * 		pixel type
 */
public interface ResolutionLevel3D< T > extends EuclideanSpace, Typed< T >
{
	/**
	 * Get the index of this resolution level in the {@link MultiResolutionStack3D}
	 * By default, index {@code 0} is the full resolution, and resolution level at index {@code i>j} has lower resolution (more down-sampled) than index {@code j}.
	 *
	 * @return
	 * 		index of resolution level
	 */
	int getLevel();

	/**
	 * Get per-dimension down-sampling factors with respect to full resolution.
	 *
	 * @return down-sampling factors
	 */
	int[] getR();

	/**
	 * Get per-dimension scale factors with respect to full resolution.
	 * This must always be the inverse of {@link #getR()}, i.e.,
	 * {@code getS()[i] == 1.0 / getR()[i]}.
	 *
	 * @return scale factors
	 */
	double[] getS();

	/**
	 * Get the transformation from image coordinates of this level to image coordinates of level 0 (full resolution).
	 * For {@link DownSamplingScheme#DEFAULT_BLOCK_AVERAGE DEFAULT_BLOCK_AVERAGE} down-sampling this is
	 * <pre>
	 *     rx   0   0  (rx-1)/2
	 *      0  ry   0  (ry-1)/2
	 *      0   0  rz  (rz-1)/2
	 * </pre>
	 *
	 * @return transformation from image coordinates of this level to image coordinates of level 0 (full resolution).
	 */
	AffineTransform3D getLevelTransform();

	/**
	 * Get the image data for this resolution level.
	 *
	 * @return image data for this level.
	 */
	RandomAccessibleInterval< T > getImage();

	@Override
	default int numDimensions()
	{
		return 3;
	}
}
