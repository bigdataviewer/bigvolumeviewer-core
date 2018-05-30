package tpietzsch.blockmath5;

import com.jogamp.opengl.GL3;
import java.nio.Buffer;
import net.imglib2.util.Intervals;
import net.imglib2.util.StopWatch;
import tpietzsch.blockmath3.LRUBlockCache;
import tpietzsch.blockmath3.TextureBlock;
import tpietzsch.blockmath5.CacheTexture.UploadBuffer;
import tpietzsch.blocks.ByteUtils;

import static tpietzsch.blocks.ByteUtils.addressOf;

/**
 * A LRU cache associating keys to blocks. A key identifies a
 * chunk of image data and a block identifies coordinates in a texture where the
 * chunk is stored on the GPU.
 * <p>
 * When a key is added to the cache, a block with unused texture
 * coordinates is created . If the cache is already full, least recently used
 * key is removed and the associated block is re-used for the newly added key.
 * <p>
 * The block data is loaded through TODO
 *
 * @param <K>
 *            key type
 *
 * @author Tobias Pietzsch
 */
public class TextureBlockCache< K >
{
	public interface BlockLoader< K >
	{
		boolean loadBlock( final K key, final UploadBuffer buffer );

		boolean canLoadBlock( final K key );
	}

	private static final int bytesPerVoxel = 2; // always UnsignedShortType for now

	private final CacheTexture cacheTexture;

	private final LRUBlockCache< K > lruBlockCache;

	private final BlockLoader< K > blockLoader;

	public TextureBlockCache(
			final int[] blockSize,
			final int maxMemoryInMB,
			final BlockLoader< K > blockLoader )
	{
		this.blockLoader = blockLoader;
		final int[] gridSize = LRUBlockCache.findSuitableGridSize( blockSize, bytesPerVoxel, maxMemoryInMB );
		// block (0,0,0) is reserved for out-of-bounds values
		final int numReservedBlocks = 1;
		lruBlockCache = new LRUBlockCache<>( blockSize, gridSize, numReservedBlocks );
		cacheTexture = new CacheTexture( blockSize, gridSize );
	}

	public void bindTextures( final GL3 gl, final int textureUnit )
	{
		cacheTexture.bindTextures( gl, textureUnit );
	}

	public void flush( final GL3 gl )
	{
		cacheTexture.flush( gl );
	}

	int numUploaded = 0;
	StopWatch copyWatch = new StopWatch();
	StopWatch uploadWatch = new StopWatch();
	long copyStart = copyWatch.nanoTime();
	long uploadStart = uploadWatch.nanoTime();

	public void resetStats()
	{
		numUploaded = 0;
		copyStart = copyWatch.nanoTime();
		uploadStart = uploadWatch.nanoTime();
	}

	public long currentUploadMillis()
	{
		final double tcopy = ( copyWatch.nanoTime() - copyStart ) / 1_000_000.0;
		final double tupload = ( uploadWatch.nanoTime() - uploadStart ) / 1_000_000.0;
		return ( long ) ( tcopy + tupload );
	}

	public void printStats()
	{
		if ( numUploaded > 0 )
		{
			final double tcopy2 = ( copyWatch.nanoTime() - copyStart ) / ( 1_000_000.0 );
			final double tupload2 = ( uploadWatch.nanoTime() - uploadStart ) / ( 1_000_000.0 );
			System.out.println( "tcopy = " + tcopy2 + ",  tupload = " + tupload2 );
			final double tcopy = ( copyWatch.nanoTime() - copyStart ) / ( numUploaded * 1_000_000.0 );
			final double tupload = ( uploadWatch.nanoTime() - uploadStart ) / ( numUploaded * 1_000_000.0 );
			System.out.println( "numUploaded = " + numUploaded + ",  tcopy/block = " + tcopy + ",  tupload/block = " + tupload );
		}
		resetStats();
	}

	public TextureBlock getIfPresentOrCompletable( final GL3 gl, final K key )
	{
		TextureBlock block = null;

		synchronized ( this )
		{
			initOob( gl );
			block = lruBlockCache.get( key );
			if ( block == null )
			{
				if ( !blockLoader.canLoadBlock( key ) )
					return null;
				block = lruBlockCache.add( key );
			}
		}

		if ( block.needsLoading() )
		{
			synchronized ( block )
			{
				if ( block.needsLoading() )
				{
					copyWatch.start();
					final UploadBuffer buffer = cacheTexture.getNextBuffer( gl );
					final boolean complete = blockLoader.loadBlock( key, buffer );
					copyWatch.stop();
					uploadWatch.start();
					cacheTexture.putBlockData( gl, block, buffer );
					uploadWatch.stop();
					block.setNeedsLoading( !complete );
					++numUploaded;
				}
			}
		}

		return block;
	}

	public TextureBlock get( final GL3 gl, final K key )
	{
		TextureBlock block = null;

		synchronized ( this )
		{
			initOob( gl );
			block = lruBlockCache.get( key );
			if ( block == null )
			{
				block = lruBlockCache.add( key );
			}
		}

		if ( block.needsLoading() )
		{
			copyWatch.start();
			final UploadBuffer buffer = cacheTexture.getNextBuffer( gl );
			final boolean complete = blockLoader.loadBlock( key, buffer );
			copyWatch.stop();
			uploadWatch.start();
			cacheTexture.putBlockData( gl, block, buffer );
			uploadWatch.stop();
			block.setNeedsLoading( !complete );
			++numUploaded;
		}

		return block;
	}

	private boolean oobInitialized = false;

	private void initOob( final GL3 gl )
	{
		if ( oobInitialized )
			return;
		oobInitialized = true;

		final UploadBuffer buffer = cacheTexture.getNextBuffer( gl );
		ByteUtils.setShorts( ( short ) 0x0fff, buffer.getAddress(), ( int ) Intervals.numElements( getBlockSize() ) );
		final TextureBlock oobBlock = new TextureBlock( new int[] { 0, 0, 0 }, new int[] { 0, 0, 0 } );
		cacheTexture.putBlockData( gl, oobBlock, buffer );
	}

	private int[] getBlockSize()
	{
		return cacheTexture.getBlockSize();
	}

	public int[] getCacheTextureSize()
	{
		return cacheTexture.getSize();
	}
}
