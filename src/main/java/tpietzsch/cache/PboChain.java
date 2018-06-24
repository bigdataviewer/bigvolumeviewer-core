package tpietzsch.cache;

import java.nio.Buffer;
import java.util.ArrayList;
import java.util.List;
import tpietzsch.cache.TextureCache.Tile;

public class PboChain
{
	private final int numBufs; // number of PBOs to create
	private final int bufSize; // size in blocks of each PBO
	private final int blockSize; // size in bytes of each block

	public PboChain( final int numBufs, final int bufSize, final int blockSize )
	{
		this.numBufs = numBufs;
		this.bufSize = bufSize;
		this.blockSize = blockSize;
	}

	public UploadBuffer next()
	{
		// TODO
		return null;
	}

	public void done( UploadBuffer buffer )
	{
		// TODO
	}

	public void flush()
	{
		// TODO
	}


	static class Pbo
	{
		private final int bufSize; // size in blocks of each PBO
		private final int blockSize; // size in bytes of each block
		private final int[] blockDimensions;
		private final TextureCache cache;

		public Pbo( final int bufSize, final int blockSize, final int[] blockDimensions, final TextureCache cache )
		{
			this.bufSize = bufSize;
			this.blockSize = blockSize;
			this.blockDimensions = blockDimensions;
			this.cache = cache;
		}

		Buffer getBuffer()
		{
			// TODO
			return null;
		}

		/**
		 * filled buffers, i.e., those that got handed out.
		 * assumed to be all returned by the time uploadToTexture() is called.
		 */
		private final ArrayList< UploadBuffer > buffers = new ArrayList<>();

		/**
		 * @param fillTiles
		 * 		list of tiles from cache that can be filled.
		 * 		Ordered by (z,y,x) so that contiguous ranges can be inferred.
		 * 		TODO: these are shared between Pbos, ti start index should persist over calls to uploadToTexture for different Pbos
		 */
		void uploadToTexture( List< Tile > fillTiles )
		{
			// TODO: should persist over calls to uploadToTexture for different Pbos
			int ti = 0; // index of next tile

			int bi = 0; // index of next buffer
			while ( bi < buffers.size() )
			{
				final int remainingBlocks = buffers.size() - bi;
				Tile prevTile = fillTiles.get( ti );
				int nb = 1;
				for ( nb = 1; nb < remainingBlocks; ++nb )
				{
					final Tile tile = fillTiles.get( ti + nb );
					if ( tile.z == prevTile.z && tile.y == prevTile.y && tile.x == prevTile.x + 1 )
						prevTile = tile;
					else
						break;
				}

				// upload nb blocks
				// TODO

				// for each (uploadbuffer, tile): map tile to uploadBuffer.getKey, assign uploadBuffer.isComplete
				for ( int i = 0; i < nb; ++i )
				{
					final Tile tile = fillTiles.get( ti + i );
					final UploadBuffer buffer = buffers.get( bi + i );
					cache.assign( tile, buffer.getImageBlockKey(), buffer.getContentState() );
				}

				// increment bi and ti by nb
				bi += nb;
				ti += nb;

				// repeat until bi == buffers.size()
			}
		}
	}
}
