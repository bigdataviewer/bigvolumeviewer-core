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

import static tpietzsch.cache.PboChain.PboChainState.FILL;
import static tpietzsch.cache.PboChain.PboChainState.FLUSH;
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

	/** Condition for waiting maintain */
	private final Condition gpu;

	/** Condition for waiting init */
	private final Condition allClean;

	/** texture tiles to fill in current batch */
	private List< Tile > fillTiles;

	/** index of next tile in {@code fillTiles} */
	private int ti;

	public PboChain(
			final int numBufs,
			final int bufSize,
			final int blockSize,
			final int[] blockDimensions,
			final TextureCache cache )
	{
		this.numBufs = numBufs;
		this.bufSize = bufSize;
		this.blockSize = blockSize;

		cleanPbos = new ArrayBlockingQueue<>( numBufs );
		for ( int i = 0; i < numBufs; i++ )
			cleanPbos.add( new Pbo( bufSize, blockSize, blockDimensions, cache ) );
		readyForUploadPbos = new ArrayBlockingQueue<>( numBufs );
		activePbo = cleanPbos.peek();

		lock = new ReentrantLock();
		notEmpty = lock.newCondition();
		gpu = lock.newCondition();
		allClean = lock.newCondition();
	}

	/*
	 * ====================================================
	 * Called by fillers.
	 * ====================================================
	 */

	/**
	 * Take next available UploadBuffer.
	 * (Blocks if necessay until one is available).
	 * When taking the last UploadBuffer of the active Pbo, signal {@code gpu} to activate next Pbo.
	 */
	public UploadBuffer take() throws InterruptedException
	{
		final ReentrantLock lock = this.lock;
		lock.lockInterruptibly();
		try
		{
			if ( chainState != FILL )
				throw new IllegalStateException();

			while ( !activePbo.hasRemainingBuffers() )
				notEmpty.await();

			UploadBuffer buffer = activePbo.takeBuffer();
			if ( !activePbo.hasRemainingBuffers() )
				gpu.signal();

			return buffer;
		}
		finally
		{
			lock.unlock();
		}
	}

	/**
	 * Commit UploadBuffer.
	 * When committing the lase UploadBuffer of a Pbo, signal {@code gpu} to upload the Pbo.
	 *
	 * @param buffer
	 */
	void commit( UploadBuffer buffer )
	{
		final ReentrantLock lock = this.lock;
		lock.lock();
		try
		{
			if ( chainState != FILL )
				throw new IllegalStateException();
		}
		finally
		{
			lock.unlock();
		}

		Pbo pbo = ( ( PboUploadBuffer ) buffer ).pbo;
		pbo.commitBuffer( buffer );
		if ( pbo.isReadyForUpload() )
		{
			lock.lock();
			try
			{
				readyForUploadPbos.add( pbo );
				gpu.signal();
			}
			finally
			{
				lock.unlock();
			}
		}
	}

	/*
	 * ====================================================
	 * Called by TextureCache for upload batch control.
	 * ====================================================
	 */

	enum PboChainState
	{
		FLUSH,
		FILL
	}

	private PboChainState chainState = FLUSH;

	/**
	 * Finalize a batch of cache tile uploads.
	 */
	public void flush()
	{
		final ReentrantLock lock = this.lock;
		lock.lock();
		try
		{
			if ( chainState != FILL )
				throw new IllegalStateException();

			if ( activePbo.flush() )
			{
				// Pbo.flush() returns true if the Pbo becomes immediately ready for upload
				readyForUploadPbos.add( activePbo );
				gpu.signal();
			}

			chainState = FLUSH;
		}
		finally
		{
			lock.unlock();
		}
	}

	/**
	 * @return ready for {@link #init(List)}?
	 */
	public boolean ready()
	{
		final ReentrantLock lock = this.lock;
		lock.lock();
		try
		{
			return chainState == FLUSH && cleanPbos.size() == numBufs;
		}
		finally
		{
			lock.unlock();
		}
	}

	/**
	 * Initialize a new batch of cache tile uploads.
	 * The size of {@code fillTiles} should match exactly the number of times that {@link #take()} and {@link #commit(UploadBuffer)} will be called.
	 *
	 * @param fillTiles
	 * 		cache tiles that are available for filling.
	 */
	public void init( List< Tile > fillTiles ) throws InterruptedException
	{
		final ReentrantLock lock = this.lock;
		lock.lockInterruptibly();
		try
		{
			while ( !ready() )
				allClean.await();

			this.fillTiles = fillTiles;
			this.ti = 0;
			chainState = FILL;
		}
		finally
		{
			lock.unlock();
		}
	}

	/*
	 * ====================================================
	 * Called by gpu maintenance.
	 * ====================================================
	 */

	// runs forever...
	public void maintain( MyGpuContext context ) throws InterruptedException
	{
		while ( true )
		{
			final ReentrantLock lock = this.lock;
			lock.lockInterruptibly();
			try
			{
				while ( ( activePbo.hasRemainingBuffers() || cleanPbos.poll() == null ) // nothing to activate
						&& readyForUploadPbos.poll() == null ) // nothing to upload
					gpu.await();
			}
			finally
			{
				lock.unlock();
			}
			tryActivate( context );
			tryUpload( context );
		}
	}

	/**
	 * Activate next Pbo if necessary and possible.
	 * Signals {@code notEmpty} if Pbo is activated.
	 *
	 * @return whether a Pbo was activated.
	 */
	public boolean tryActivate( MyGpuContext context )
	{
		if ( activePbo.hasRemainingBuffers() )
			return false;

		final Pbo pbo = cleanPbos.poll();
		if ( pbo == null )
			return false;

		pbo.map( context );

		final ReentrantLock lock = this.lock;
		lock.lock();
		try
		{
			activePbo = pbo;
			notEmpty.signalAll();
			return true;
		}
		finally
		{
			lock.unlock();
		}
	}

	/**
	 * Unmap and upload next Pbo if necessary.
	 *
	 * @return whether a Pbo was uploaded.
	 */
	public boolean tryUpload( MyGpuContext context )
	{
		Pbo pbo = readyForUploadPbos.poll();
		if ( pbo == null )
			return false;
		pbo.unmap( context );
		ti = pbo.uploadToTexture( context, fillTiles, ti );

		final ReentrantLock lock = this.lock;
		lock.lock();
		try
		{
			cleanPbos.add( pbo );
			if ( cleanPbos.size() == numBufs )
				allClean.signal();
		}
		finally
		{
			lock.unlock();
		}

		return true;
	}







	/*
	 *
	 */

	public interface MyGpuContext
	{
		// TODO
		// TODO
		// TODO
		// TODO
		// TODO
		// TODO
		// TODO
		// TODO
		// TODO
		// TODO
		// TODO
		// TODO

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

		/**
		 * filled buffers, i.e., those that got handed out.
		 * assumed to be all returned by the time uploadToTexture() is called.
		 */
		private final ArrayList< UploadBuffer > buffers = new ArrayList<>();

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

		boolean hasRemainingBuffers()
		{
			return state == MAPPED && bufSize - nextIndex > 0;
		}

		boolean hasUncommittedBuffers()
		{
			return uncommitted > 0;
		}

		boolean isReadyForUpload()
		{
			return !hasUncommittedBuffers() && !hasRemainingBuffers();
		}

		/**
		 * @return {@code true} if the Pbo becomes {@link #isReadyForUpload() ready for upload} as an immediate result of this {@code flush()}
		 */
		boolean flush()
		{
			final boolean ret = hasRemainingBuffers() && !hasUncommittedBuffers();
			nextIndex = bufSize;
			return ret;
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
			if ( state != MAPPED || hasUncommittedBuffers() )
				throw new IllegalStateException();

			context.unmap( this );
			state = UNMAPPED;
		}

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
		int uploadToTexture( MyGpuContext context, List< Tile > fillTiles, int ti )
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
				context.texSubImage3D( this, cache, x, y, z, w, h, d, pixels_buffer_offset );

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
