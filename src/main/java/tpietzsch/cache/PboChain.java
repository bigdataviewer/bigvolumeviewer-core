package tpietzsch.cache;

import java.nio.Buffer;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import tpietzsch.backend.GpuContext;
import tpietzsch.cache.TextureCache.Tile;
import tpietzsch.cache.TextureCache.TileFillTask;

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
	private List< TileFillTask > tileFillTasks;

	/** index of next task in {@code fillTileTasks} */
	private int ti;

	/**
	 *
	 * @param numBufs number of PBOs to create
	 * @param bufSize size in blocks of each PBO
	 * @param blockSize size in bytes of each block
	 * @param blockDimensions dimensions of each block
	 * @param cache texture to upload to
	 */
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
	 * Take next available UploadBuffer. (Blocks if necessay until one is
	 * available). When taking the last UploadBuffer of the active Pbo, signal
	 * {@code gpu} to activate next Pbo.
	 *
	 * @return new buffer to be filled and {@link #commit(PboUploadBuffer)
	 *         commited}.
	 * @throws InterruptedException
	 * @throws NoSuchElementException if there are no more upload buffers to process for the current batch of tasks.
	 * @throws IllegalStateException if there is no current batch of tasks.
	 */
	PboUploadBuffer take() throws InterruptedException, NoSuchElementException, IllegalStateException
	{
		final ReentrantLock lock = this.lock;
		lock.lockInterruptibly();
		try
		{
			if ( chainState != FILL )
				throw new IllegalStateException();

			if ( ti >= tileFillTasks.size() )
				throw new NoSuchElementException();

			while ( !activePbo.hasRemainingBuffers() )
				notEmpty.await();

			final PboUploadBuffer buffer = activePbo.takeBuffer( tileFillTasks.get( ti++ ) );
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
	 * Commit {@code buffer}. When committing the last UploadBuffer of a Pbo,
	 * signal {@code gpu} to upload the Pbo.
	 *
	 * @param buffer
	 *            buffer to commit
	 */
	void commit( final PboUploadBuffer buffer )
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

		final Pbo pbo = buffer.pbo;
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
	 */
	public void init( final List< TileFillTask > tileFillTasks ) throws InterruptedException
	{
		final ReentrantLock lock = this.lock;
		lock.lockInterruptibly();
		try
		{
			while ( !ready() )
				allClean.await();

			this.tileFillTasks = tileFillTasks;
			this.ti = 0;
			chainState = FILL;
		}
		finally
		{
			lock.unlock();
		}
	}

	/**
	 * TODO: move to separate class (?)
	 * Run all fill tasks and flush.
	 */
	public void processAll( ExecutorService es ) throws InterruptedException, ExecutionException
	{
		final ArrayList< Callable< Void > > tasks = new ArrayList<>();
		for ( TileFillTask tileFillTask : tileFillTasks )
			tasks.add( () -> {
				final PboUploadBuffer buf = take();
				buf.task.fill( buf );
				commit( buf );
				return null;
			} );
		final List< Future< Void > > futures = es.invokeAll( tasks );
		for ( final Future< Void > f : futures )
			f.get();
		flush();
	}


	/*
	 * ====================================================
	 * Called by gpu maintenance.
	 * ====================================================
	 */

	// runs forever...
	public void maintain( final GpuContext context ) throws InterruptedException
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
	 * Activate next Pbo if necessary and possible. Signals {@code notEmpty} if
	 * Pbo is activated.
	 *
	 * @return whether a Pbo was activated.
	 */
	public boolean tryActivate( final GpuContext context )
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
	public boolean tryUpload( final GpuContext context )
	{
		final Pbo pbo = readyForUploadPbos.poll();
		if ( pbo == null )
			return false;
		pbo.unmap( context );
		pbo.uploadToTexture( context );

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
	 * ====================================================
	 * inner classes representing one PBO.
	 * ====================================================
	 */

	enum PboState
	{
		CLEAN,
		MAPPED,
		UNMAPPED
	};

	static class PboUploadBuffer extends UploadBuffer
	{
		final TileFillTask task;

		final Pbo pbo;

		public PboUploadBuffer( final Buffer buffer, final int offset, final TileFillTask task, final Pbo pbo )
		{
			super( buffer, offset );
			this.task = task;
			this.pbo = pbo;
		}
	}

	static class Pbo implements tpietzsch.backend.Pbo
	{
		private final int bufSize; // size in blocks of this PBO
		private final int blockSize; // size in bytes of each block
		private final int[] blockDimensions;
		private final TextureCache cache;

		/**
		 * Committed buffers, ready for upload.
		 * All buffers that were taken out, are assumed to be committed by the time uploadToTexture() is called.
		 */
		private final ArrayList< PboUploadBuffer > buffers = new ArrayList<>();

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
		@Override
		public int getSizeInBytes()
		{
			return bufSize * blockSize;
		}

		PboUploadBuffer takeBuffer( TileFillTask task )
		{
			if ( state != MAPPED )
				throw new IllegalStateException();

			if ( nextIndex >= bufSize )
				throw new NoSuchElementException();

			final PboUploadBuffer b = new PboUploadBuffer( buffer, nextIndex * blockSize, task, this );
			++uncommitted;
			++nextIndex;
			return b;
		}

		void commitBuffer( final PboUploadBuffer buffer )
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
		 * @return {@code true} if the Pbo becomes {@link #isReadyForUpload()
		 *         ready for upload} as an immediate result of this
		 *         {@code flush()}
		 */
		boolean flush()
		{
			final boolean ret = hasRemainingBuffers() && !hasUncommittedBuffers();
			nextIndex = bufSize;
			return ret;
		}

		void map( final GpuContext context )
		{
			if ( state != CLEAN )
				throw new IllegalStateException();

			buffer = context.map( this );
			state = MAPPED;
			nextIndex = 0;
			uncommitted = 0;
		}

		void unmap( final GpuContext context )
		{
			if ( state != MAPPED || hasUncommittedBuffers() )
				throw new IllegalStateException();

			context.unmap( this );
			state = UNMAPPED;
		}

		/**
		 * Tiles of buffers list might have contiguous ranges that will be recognized and uploaded in batches.
		 */
		void uploadToTexture( final GpuContext context )
		{
			if ( state != UNMAPPED )
				throw new IllegalStateException();

			int bi = 0; // index of next buffer
			while ( bi < buffers.size() )
			{
				final PboUploadBuffer buf0 = buffers.get( bi );
				Tile prevTile = buf0.task.getTile();
				final int x = blockDimensions[ 0 ] * prevTile.x;
				final int y = blockDimensions[ 1 ] * prevTile.y;
				final int z = blockDimensions[ 2 ] * prevTile.z;
				final long pixels_buffer_offset = buf0.getOffset();

				final int remainingBlocks = buffers.size() - bi;
				int nb = 1;
				for ( ; nb < remainingBlocks; ++nb )
				{
					final Tile tile = buffers.get( bi + nb ).task.getTile();
					if ( tile.z == prevTile.z + 1 && tile.y == prevTile.y && tile.x == prevTile.x )
						prevTile = tile;
					else
						break;
				}

				// upload nb blocks
				final int w = blockDimensions[ 0 ];
				final int h = blockDimensions[ 1 ];
				final int d = blockDimensions[ 2 ] * nb;
				context.texSubImage3D( this, cache, x, y, z, w, h, d, pixels_buffer_offset );

				// for each (uploadbuffer, tile): map tile to uploadBuffer.getKey, assign uploadBuffer.isComplete
				for ( int i = 0; i < nb; ++i )
				{
					final PboUploadBuffer buffer = buffers.get( bi + i );
					cache.assign( buffer.task.getTile(), buffer.task.getKey(), buffer.getContentState() );
				}

				bi += nb;
			}	// repeat until bi == buffers.size()

			buffers.clear();
			state = CLEAN;
		}
	}
}
