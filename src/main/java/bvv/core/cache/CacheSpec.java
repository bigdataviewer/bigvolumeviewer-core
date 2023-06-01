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
package bvv.core.cache;

import bvv.core.backend.Texture.InternalFormat;

public class CacheSpec
{
	private final int[] blockSize;

	private final int[] paddedBlockSize;

	private final int[] padOffset;

	private final InternalFormat format;

	public CacheSpec( final InternalFormat format, final int blockSizeX, final int blockSizeY, final int blockSizeZ )
	{
		this( format, new int[] { blockSizeX, blockSizeY, blockSizeZ } );
	}

	public CacheSpec( final InternalFormat format, final int[] blockSize )
	{
		this( format, blockSize,
				new int[] { blockSize[ 0 ] + 2, blockSize[ 1 ] + 2, blockSize[ 2 ] + 2 },
				new int[] { 1, 1, 1 } );
	}

	public CacheSpec( final InternalFormat format, final int[] blockSize, final int[] paddedBlockSize, final int[] padOffset )
	{
		assert blockSize.length == 3;
		assert paddedBlockSize.length == 3;
		assert padOffset.length == 3;

		this.format = format;
		this.blockSize = blockSize;
		this.paddedBlockSize = paddedBlockSize;
		this.padOffset = padOffset;
	}

	/**
	 * Get the size of a block without interpolation padding.
	 * This is the size of blocks into which the source image has to be cut.
	 *
	 * @return the size of a block without interpolation padding.
	 */
	public int[] blockSize()
	{
		return blockSize;
	}

	/**
	 * Get the size of a padded block.
	 * This is the tile size of the {@link TextureCache}.
	 * <p>
	 * This is typically {@link #blockSize()}{@code + (2,2,2)}.
	 *
	 * @return the size of a padded block.
	 */
	public int[] paddedBlockSize()
	{
		return paddedBlockSize;
	}

	/**
	 * Get the offset in a padded block, where the (actual, unpadded) block data starts.
	 * <p>
	 * This is typically {@code (1,1,1)}.
	 *
	 * @return block start within padded block.
	 */
	public int[] padOffset()
	{
		return padOffset;
	}

	public InternalFormat format()
	{
		return format;
	}
}
