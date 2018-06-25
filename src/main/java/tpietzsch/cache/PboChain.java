package tpietzsch.cache;

import java.nio.Buffer;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import tpietzsch.cache.TextureCache.Tile;

import static tpietzsch.cache.PboChain.PboState.CLEAN;
import static tpietzsch.cache.PboChain.PboState.MAPPED;
import static tpietzsch.cache.PboChain.PboState.UNMAPPED;

public class PboChain
{
	private final int numBufs; // number of PBOs to create
	private final int bufSize; // size in blocks of each PBO
	private final int blockSize; // size in bytes of each block

	private final BlockingQueue< Pbo > cleanPbos;
	private final BlockingQueue< Pbo > readyForUploadPbos;
	private Pbo activePbo;


	/** Main lock guarding all access */
	private final ReentrantLock lock;

	/** Condition for waiting takes */
	private final Condition notEmpty;

	/** TODO */
	private final Condition activeExhausted;


	/** texture tiles to fill in current batch */
	private List< Tile > fillTiles;

	/** index of next tile in {@code fillTiles} */
	private int ti;


	public PboChain( final int numBufs, final int bufSize, final int blockSize )
	{
		this.numBufs = numBufs;
		this.bufSize = bufSize;
		this.blockSize = blockSize;

		// TODO: create PBOs, set up initial state
		cleanPbos = new ArrayBlockingQueue<>( numBufs );
		readyForUploadPbos = new ArrayBlockingQueue<>( numBufs );
		activePbo = null;

		lock = new ReentrantLock();
		notEmpty = lock.newCondition();
		activeExhausted = lock.newCondition();
	}

	/*
	 * Called by fillers.
	 */

	public UploadBuffer take() throws InterruptedException
	{
		final ReentrantLock lock = this.lock;
		lock.lockInterruptibly();
		try
		{
			while ( activePbo.numBuffersRemaining() == 0 )
				notEmpty.await();

			UploadBuffer buffer = activePbo.takeBuffer();
			if ( activePbo.numBuffersRemaining() == 0 )
				activeExhausted.signal();

			return buffer;
		}
		finally
		{
			lock.unlock();
		}
	}

	void commit( UploadBuffer buffer )
	{
		( ( PboUploadBuffer ) buffer ).pbo.commitBuffer( buffer );
	}

	/*
	 * Called by TextureCache for upload batch control.
	 */

	public void flush()
	{
		// TODO
	}

	public boolean ready()
	{
		// TODO
		return false;
	}

	public void init( List< Tile > fillTiles )
	{
		this.fillTiles = fillTiles;
		this.ti = 0;
	}

	/*
	 * Called by gpu maintenance.
	 */

	public void tryActivate( MyGpuContext context ) throws InterruptedException
	{
		final ReentrantLock lock = this.lock;
		lock.lockInterruptibly();
		try
		{
			if ( activePbo.numBuffersRemaining() == 0 )
			{
				Pbo pbo = cleanPbos.poll();
				if ( pbo != null )
				{
					activePbo = pbo;
					activePbo.map( context );
					notEmpty.signalAll();
				}
			}
		}
		finally
		{
			lock.unlock();
		}
	}

	public void tryUpload( MyGpuContext context ) throws InterruptedException
	{
		final ReentrantLock lock = this.lock;
		lock.lockInterruptibly();
		try
		{
			final Pbo pbo = readyForUploadPbos.poll();
			if ( pbo != null )
			{
				pbo.uploadToTexture( List< Tile > fillTiles, int ti );
			}
		}
		finally
		{
			lock.unlock();
		}

	}



	/*
	 *
	 */

	public interface MyGpuContext
	{
		// TODO details...
		Buffer map( Pbo pbo );

		// TODO details...
		void unmap( Pbo pbo );

		// TODO details...
		void texSubImage3D( Pbo pbo, TextureCache texture, int xoffset, int yoffset, int zoffset, int width, int height, int depth, long pixels_buffer_offset );
	}





	enum PboState
	{
		CLEAN,
		MAPPED,
		UNMAPPED
	};

	static class PboUploadBuffer extends UploadBuffer
	{
		final Pbo pbo;

		public PboUploadBuffer( final Buffer buffer, final int offset, final Pbo pbo )
		{
			super( buffer, offset );
			this.pbo = pbo;
		}
	}

	public static class Pbo
	{
		private final int bufSize; // size in blocks of this PBO
		private final int blockSize; // size in bytes of each block
		private final int[] blockDimensions;
		private final TextureCache cache;

		private PboState state;
		private Buffer buffer;
		private int nextIndex;
		private int uncommitted;

		Pbo( final int bufSize, final int blockSize, final int[] blockDimensions, final TextureCache cache )
		{
			this.bufSize = bufSize;
			this.blockSize = blockSize;
			this.blockDimensions = blockDimensions;
			this.cache = cache;

			state = CLEAN;
			buffer = null;
			nextIndex = 0;
			uncommitted = 0;
		}

		// for GpuContext to initialized Pbo to correct size
		public int getSizeInBytes()
		{
			return bufSize * blockSize;
		}

		UploadBuffer takeBuffer()
		{
			if ( state != MAPPED )
				throw new IllegalStateException();

			if ( nextIndex >= bufSize )
				throw new NoSuchElementException();

			final UploadBuffer b = new PboUploadBuffer( buffer, nextIndex * blockSize, this );
			++uncommitted;
			++nextIndex;
			return b;
		}

		void commitBuffer( UploadBuffer buffer )
		{
			buffers.add( buffer );
			--uncommitted;
		}

		int numBuffersRemaining()
		{
			return state == MAPPED ? bufSize - nextIndex : 0;
		}

		boolean hasUncommittedBlocks()
		{
			return uncommitted > 0;
		}

		void map( MyGpuContext context )
		{
			if ( state != CLEAN )
				throw new IllegalStateException();

			buffer = context.map( this );
			state = MAPPED;
			nextIndex = 0;
			uncommitted = 0;
		}

		void unmap( MyGpuContext context )
		{
			if ( state != MAPPED || hasUncommittedBlocks() )
				throw new IllegalStateException();

			context.unmap( this );
			state = UNMAPPED;
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
		 * 		(these are shared between Pbos, ti start index persists over calls to uploadToTexture for different Pbos).
		 * @param ti
		 * 		index of next tile in {@code fillTiles}.
		 * @return
		 * 		index of next tile in {@code fillTiles} (after uploading committed UploadBuffers).
		 */
		int uploadToTexture( MyGpuContext context, TextureCache texture, List< Tile > fillTiles, int ti )
		{
			if ( state != UNMAPPED )
				throw new IllegalStateException();

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
				final Tile tile0 = fillTiles.get( ti );
				final int x = blockDimensions[ 0 ] * tile0.x;
				final int y = blockDimensions[ 1 ] * tile0.y;
				final int z = blockDimensions[ 2 ] * tile0.z;
				final int w = blockDimensions[ 0 ] * nb;
				final int h = blockDimensions[ 1 ];
				final int d = blockDimensions[ 2 ];
				final long pixels_buffer_offset = buffers.get( bi ).getOffset();
				context.texSubImage3D( this, texture, x, y, z, w, h, d, pixels_buffer_offset );

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
			}	// repeat until bi == buffers.size()

			buffers.clear();
			state = CLEAN;

			return ti;
		}
	}
}
