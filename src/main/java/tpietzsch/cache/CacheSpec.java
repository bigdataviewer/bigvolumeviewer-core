package tpietzsch.cache;

import tpietzsch.backend.Texture.InternalFormat;

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
