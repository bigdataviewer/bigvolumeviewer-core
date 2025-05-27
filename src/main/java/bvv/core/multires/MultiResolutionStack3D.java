/*-
 * #%L
 * Volume rendering of bdv datasets
 * %%
 * Copyright (C) 2018 - 2025 Tobias Pietzsch
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
package bvv.core.multires;

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
		return DownSamplingScheme.DEFAULT_BLOCK_AVERAGE;
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
