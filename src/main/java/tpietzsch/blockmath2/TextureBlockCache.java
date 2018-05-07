package tpietzsch.blockmath2;

import com.jogamp.opengl.GL3;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import tpietzsch.day10.BlockKey;
import tpietzsch.day10.LRUBlockCache;
import tpietzsch.day10.LRUBlockCache.TextureBlock;
import tpietzsch.day8.BlockTextureUtils;

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
	private static final int bytesPerVoxel = 2; // always UnsignedShortType for now

	private final CacheTexture cacheTexture;

	private final LRUBlockCache< K > lruBlockCache;

	private final ThreadLocal< ByteBuffer > tlBuffer;

	private final BiConsumer< K, ByteBuffer > blockLoader;

	public TextureBlockCache(
			final int[] blockSize,
			final int maxMemoryInMB,
			final BiConsumer< K, ByteBuffer > blockLoader )
	{
		this.blockLoader = blockLoader;
		final int[] gridSize = LRUBlockCache.findSuitableGridSize( blockSize, bytesPerVoxel, maxMemoryInMB );
		// block (0,0,0) is reserved for out-of-bounds values
		final int numReservedBlocks = 1;
		lruBlockCache = new LRUBlockCache<>( blockSize, gridSize, numReservedBlocks );
		cacheTexture = new CacheTexture( blockSize, gridSize );
		tlBuffer = ThreadLocal.withInitial( () ->
		{
			final ByteBuffer buffer = BlockTextureUtils.allocateBlockBuffer( getBlockSize() );
			buffer.order( ByteOrder.LITTLE_ENDIAN );
			return buffer;
		} );
	}

	public void bindTextures( GL3 gl, int textureUnit )
	{
		cacheTexture.bindTextures( gl, textureUnit );
	}

	public TextureBlock get( GL3 gl, K key )
	{
		TextureBlock block = null;
		boolean needsLoading = false;

		synchronized ( this )
		{
			initOob( gl );
			block = lruBlockCache.get( key );
			if ( block == null )
			{
				block = lruBlockCache.add( key );
				needsLoading = true;
			}
		}

		if ( needsLoading )
		{
			final ByteBuffer buffer = tlBuffer.get();
			blockLoader.accept( key, buffer );
			cacheTexture.putBlockData( gl, block, buffer );
		}

		return block;
	}

	private boolean oobInitialized = false;

	private void initOob( GL3 gl )
	{
		if ( oobInitialized )
			return;
		oobInitialized = true;

		final ByteBuffer buffer = tlBuffer.get();
		final ShortBuffer sbuffer = buffer.asShortBuffer();
		final int cap = sbuffer.capacity();
		for ( int i = 0; i < cap; i++ )
			sbuffer.put( i, ( short ) 0x0fff );
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
