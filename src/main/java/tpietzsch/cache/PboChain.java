package tpietzsch.cache;

import java.nio.Buffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import net.imglib2.util.Intervals;
import tpietzsch.backend.GpuContext;
import tpietzsch.backend.StagingBuffer;
import tpietzsch.cache.TextureCache.StagedTasks;
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

	private final Queue< Pbo > cleanPbos;
	private final Queue< Pbo > readyForUploadPbos;
	private Pbo activePbo;


	/** Main lock guarding all access */
	private final ReentrantLock lock;

	/** Condition for waiting takes */
	private final Condition notEmpty;

	/** Condition for waiting maintain */
	private final Condition gpu;

	/** Condition for waiting init */
	private final Condition allClean;


	/** tile fill tasks in current batch */
	private List< TileFillTask > tileFillTasks;

	/** texture tiles that can be reused in current batch */
	private List< Tile > reusableTiles;

	/** index of next task in {@code fillTileTasks} */
	private AtomicInteger ti = new AtomicInteger();

	/** index of next tile in {@code reusableTiles} */
	private int rti;

	/**
	 *
	 * @param numBufs number of PBOs to create
	 * @param bufSize size in blocks of each PBO
	 * @param cache texture to upload to
	 */
	public PboChain(
			final int numBufs,
			final int bufSize,
			final TextureCache cache )
	{
		this( numBufs, bufSize,
				cache.spec().format().getBytesPerElement() * ( int ) Intervals.numElements( cache.spec().paddedBlockSize() ),
				cache.spec().paddedBlockSize(),
				cache );
	}

	/**
	 *
	 * @param numBufs number of PBOs to create
	 * @param bufSize size in blocks of each PBO
	 * @param blockSize size in bytes of each block
	 * @param blockDimensions dimensions of each block
	 * @param cache texture to upload to
	 */
	// TODO: remove? (inline into other constructor)
	PboChain(
			final int numBufs,
			final int bufSize,
			final int blockSize,
			final int[] blockDimensions,
			final TextureCache cache )
	{
		this.numBufs = numBufs;
		this.bufSize = bufSize;
		this.blockSize = blockSize;

		cleanPbos = new ArrayDeque<>( numBufs );
		for ( int i = 0; i < numBufs; i++ )
			cleanPbos.add( new Pbo( bufSize, blockSize, blockDimensions, cache ) );
		readyForUploadPbos = new ArrayDeque<>( numBufs );
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

	TileFillTask nextTask()
	{
		final int i = ti.getAndIncrement();

		if ( i >= tileFillTasks.size() )
			throw new NoSuchElementException();

		return tileFillTasks.get( i );
	}

	/**
	 * Take next available UploadBuffer. (Blocks if necessary until one is
	 * available). When taking the last UploadBuffer of the active Pbo,
	 * {@code gpu.signal()} to activate next Pbo.
	 *
	 * @return new buffer to be filled and {@link #commit(PboUploadBuffer)
	 *         committed}.
	 * @throws InterruptedException
	 * @throws NoSuchElementException
	 *             if there are no more upload buffers to process for the
	 *             current batch of tasks.
	 * @throws IllegalStateException
	 *             if there is no current batch of tasks.
	 */
	PboUploadBuffer take( final TileFillTask task ) throws InterruptedException, NoSuchElementException, IllegalStateException
	{
		final ReentrantLock lock = this.lock;
		lock.lockInterruptibly();
		try
		{
			if ( chainState != FILL )
				throw new IllegalStateException();

			while ( !activePbo.hasRemainingBuffers() )
				notEmpty.await();

			if ( task.getTile() == null )
			{
				if ( rti >= reusableTiles.size() )
					throw new NoSuchElementException();

				task.setTile( reusableTiles.get( rti++ ) );
			}

			final PboUploadBuffer buffer = activePbo.takeBuffer();
			buffer.setTask( task );
			if ( !activePbo.hasRemainingBuffers() )
			{
//				System.out.println( "take() last buffer --> gpu.signal() to trigger activate" );
				gpu.signal();
			}

			return buffer;
		}
		finally
		{
			lock.unlock();
		}
	}

	/**
	 * Commit {@code buffer}. When committing the last UploadBuffer of a Pbo,
	 * {@code gpu.signal()} to upload the Pbo.
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

			final Pbo pbo = buffer.pbo;
			pbo.commitBuffer();

			if ( pbo.isReadyForUpload() )
			{
				readyForUploadPbos.add( pbo );
//				System.out.println( "commit() makes pbo.isReadyForUpload -> gpu.signal() to trigger upload" );
				gpu.signal();
			}
		}
		finally
		{
			lock.unlock();
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

			chainState = FLUSH;

//			System.out.println( "flush()" );
//			System.out.println( "  activePbo.nextIndex = " + activePbo.nextIndex + " / " + activePbo.bufSize + "  uncommitted = " + activePbo.uncommitted );

			if ( activePbo.flush() )
			{
				// Pbo.flush() returns true if the Pbo becomes immediately ready for upload
				readyForUploadPbos.add( activePbo );
//				System.out.println( "flush() make activePbo immediately ready -> gpu.signal() for" );
			}
			gpu.signal();
		}
		finally
		{
			lock.unlock();
		}
	}

	/**
	 * @return ready for {@link #init(StagedTasks)}?
	 */
	public boolean ready()
	{
		final ReentrantLock lock = this.lock;
		lock.lock();
		try
		{
//			System.out.println();
//			System.out.println( "PboChain.ready() = " + ( chainState == FLUSH && cleanPbos.size() == numBufs ) );
//			System.out.println( "  chainState = " + chainState );
//			System.out.println( "  cleanPbos.size = " + cleanPbos.size() + " / " + numBufs );
//			System.out.println( "  readyForUploadPbos.size = " + readyForUploadPbos.size() + " / " + numBufs );
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
	public void init( final StagedTasks stagedTasks ) throws InterruptedException
	{
		final ReentrantLock lock = this.lock;
		lock.lockInterruptibly();
		try
		{
			while ( !ready() )
				allClean.await();

			this.tileFillTasks = stagedTasks.tasks;
			this.reusableTiles = stagedTasks.reusableTiles;
			this.ti.set( 0 );
			this.rti = 0;
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

	/**
	 * Maps and unmaps Pbos and uploads to texture. Runs until flush() is
	 * completed
	 */
	public void maintain( final GpuContext context ) throws InterruptedException
	{
		while ( !ready() )
		{
			final ReentrantLock lock = this.lock;
			lock.lockInterruptibly();
			try
			{
				while ( ( chainState != FLUSH || cleanPbos.size() != numBufs ) // not done (==!ready())
						&& ( chainState == FLUSH || activePbo.hasRemainingBuffers() || cleanPbos.peek() == null ) // nothing to activate
						&& readyForUploadPbos.peek() == null ) // nothing to upload
				{
//					System.out.println("gpu.await();");
//					System.out.println( "  chainState = " + chainState );
//					System.out.println( "  cleanPbos.size = " + cleanPbos.size() + " / " + numBufs );
//					System.out.println( "  readyForUploadPbos.size = " + readyForUploadPbos.size() + " / " + numBufs );
//					System.out.println( "  activePbo.nextIndex = " + activePbo.nextIndex + " / " + activePbo.bufSize + "  uncommitted = " + activePbo.uncommitted );
					gpu.await();
				}
//				System.out.println("go");
			}
			finally
			{
				lock.unlock();
			}
			tryActivate( context );
			tryUpload( context );
//			final boolean a = tryActivate( context );
//			System.out.println( "a = " + a );
//			final boolean u = tryUpload( context );
//			System.out.println( "u = " + u );
		}
//		System.out.println( "done" );
//		System.out.println( "=====================" );
	}

	/**
	 * Activate next Pbo if necessary and possible. Signals {@code notEmpty} if
	 * Pbo is activated.
	 *
	 * @return whether a Pbo was activated.
	 */
	public boolean tryActivate( final GpuContext context )
	{
		Pbo pbo = null;

		final ReentrantLock lock = this.lock;
		lock.lock();
		try
		{
			if ( chainState == FLUSH )
				return false;

			if ( activePbo.hasRemainingBuffers() )
				return false;

			pbo = cleanPbos.poll();
		}
		finally
		{
			lock.unlock();
		}

		if ( pbo == null )
			return false;
		pbo.map( context );

		lock.lock();
		try
		{
			if ( chainState == FLUSH )
			{
				// oops, that map() was unnecessary...
				if ( pbo.flush() )
					readyForUploadPbos.add( pbo );
				gpu.signal();
			}
			else
			{
				activePbo = pbo;
				notEmpty.signalAll();
			}
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
		Pbo pbo = null;

		final ReentrantLock lock = this.lock;
		lock.lock();
		try
		{
			pbo = readyForUploadPbos.poll();
		}
		finally
		{
			lock.unlock();
		}

		if ( pbo == null )
			return false;
		pbo.unmap( context );
		pbo.uploadToTexture( context );

		lock.lock();
		try
		{
			cleanPbos.add( pbo );
			if ( cleanPbos.size() == numBufs )
				allClean.signalAll();
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
		TileFillTask task;

		final Pbo pbo;

		public PboUploadBuffer( final Buffer buffer, final int offset, final Pbo pbo )
		{
			super( buffer, offset );
			this.pbo = pbo;
		}

		public void setTask( final TileFillTask task )
		{
			this.task = task;
		}
	}

	static class Pbo implements StagingBuffer
	{
		private final int bufSize; // size in blocks of this PBO
		private final int blockSize; // size in bytes of each block
		private final int[] blockDimensions;
		private final TextureCache cache;

		/**
		 * Committed buffers, ready for upload. All buffers that were taken out,
		 * are assumed to be committed by the time uploadToTexture() is called.
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

		// for GpuContext to initialize StagingBuffer to correct size
		@Override
		public int getSizeInBytes()
		{
			return bufSize * blockSize;
		}

		PboUploadBuffer takeBuffer()
		{
			if ( state != MAPPED )
				throw new IllegalStateException();

			if ( nextIndex >= bufSize )
				throw new NoSuchElementException();

			final PboUploadBuffer b = new PboUploadBuffer( buffer, nextIndex * blockSize, this );
			buffers.add( b );
			++uncommitted;
			++nextIndex;
			return b;
		}

		void commitBuffer()
		{
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
		 * Tiles of buffers list might have contiguous ranges that will be
		 * recognized and uploaded in batches.
		 */
		void uploadToTexture( final GpuContext context )
		{
			if ( state != UNMAPPED )
				throw new IllegalStateException();

			final int restoreId = context.bindStagingBuffer( this );

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
				if ( x != 0 || y != 0 || z != 0 )
				/*
				 * Workaround for weird bug, where texSubImage3D starting at 0,0,0 is mangled.
				 * The above if() makes sure, that this block (the oob block) is uploaded in an individual call.
				 * That still doesn't work correctly, but at least no "real" block is mangled in the process...
				 */
				{
					for ( ; nb < remainingBlocks; ++nb )
					{
						final Tile tile = buffers.get( bi + nb ).task.getTile();
						if ( tile.z == prevTile.z + 1 && tile.y == prevTile.y && tile.x == prevTile.x )
							prevTile = tile;
						else
							break;
					}
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

			context.bindStagingBufferId( restoreId );

			buffers.clear();
			state = CLEAN;
		}
	}
}
