package tpietzsch.example2;

import java.nio.ByteBuffer;
import net.imglib2.util.IntervalIndexer;
import tpietzsch.backend.GpuContext;
import tpietzsch.backend.Texture3D;
import tpietzsch.blocks.ByteUtils;
import tpietzsch.cache.TextureCache.Tile;

import static tpietzsch.backend.Texture.InternalFormat.RGBA8UI;

public class LookupTextureARGB implements Texture3D
{
	int[] getSize()
	{
		return size;
	}

	int[] getOffset()
	{
		return offset;
	}

	/**
	 * Size of the lut texture.
	 */
	private final int[] size = new int[ 3 ];

	/**
	 * Offset into lut texture.
	 * Source grid coordinate {@code x} corresponds to lut coordinate {@code x - offset}.
	 */
	private final int[] offset = new int[ 3 ];

	// border around lut texture (points to oob blocks)
	private final int[] pad = { 1, 1, 1 };

	private int baseLevel;

	/**
	 * Contains the block lut data.
	 * It is modified voxel-wise with {@link #putTile(int[], Tile, int)},
	 * then uploaded to the GPU with {@link #upload(GpuContext)}.
	 * <p>
	 * Each voxel (4 bytes, "RGBA") represents a tile in the cache:
	 * X,Y,Z grid coordinates, and
	 * resolution relative to {@code baseLevel} (1 means {@code baseLevel}).
	 */
	private ByteBuffer data;

	/**
	 * Reinitialize the lut data.
	 *
	 * @param rmin min source grid coordinate that needs to be represented.
	 * @param rmin max source grid coordinate that needs to be represented.
	 */
	public void init( final int[] rmin, final int[] rmax, final int baseLevel )
	{
		this.baseLevel = baseLevel;

		size[ 0 ] = rmax[ 0 ] - rmin[ 0 ] + 1 + 2 * pad[ 0 ];
		size[ 1 ] = rmax[ 1 ] - rmin[ 1 ] + 1 + 2 * pad[ 1 ];
		size[ 2 ] = rmax[ 2 ] - rmin[ 2 ] + 1 + 2 * pad[ 2 ];

		offset[ 0 ] = rmin[ 0 ] - pad[ 0 ];
		offset[ 1 ] = rmin[ 1 ] - pad[ 1 ];
		offset[ 2 ] = rmin[ 2 ] - pad[ 2 ];

		final int numBytes = 4 * size[ 0 ] * size[ 1 ] * size[ 2 ];
		if ( data == null || data.capacity() < numBytes )
			data = ByteBuffer.allocateDirect( 3 * numBytes / 2 ); // allocate a bit more than needed...
		ByteUtils.setBytes( ( byte ) 0, ByteUtils.addressOf( data ), numBytes );
	}

	/**
	 * @param g0 source grid coordinate at which to put the tile
	 * @param tile cache tile to put into lut
	 * @param level resolution level of the tile
	 */
	public void putTile( final int[] g0, final Tile tile, final int level )
	{
		final int i = IntervalIndexer.positionWithOffsetToIndex( g0, size, offset );
		data.put( i * 4, ( byte ) tile.x() );
		data.put( i * 4 + 1, ( byte ) tile.y() );
		data.put( i * 4 + 2, ( byte ) tile.z() );
		data.put( i * 4 + 3, ( byte ) ( level - baseLevel + 1 ) );
	}

	public void upload( final GpuContext context )
	{
		context.delete( this );
		context.texSubImage3D( this, 0, 0, 0, texWidth(), texHeight(), texDepth(), data );
	}

	@Override
	public InternalFormat texInternalFormat()
	{
		return RGBA8UI;
	}

	@Override
	public int texWidth()
	{
		return size[ 0 ];
	}

	@Override
	public int texHeight()
	{
		return size[ 1 ];
	}

	@Override
	public int texDepth()
	{
		return size[ 2 ];
	}

	@Override
	public MinFilter texMinFilter()
	{
		return MinFilter.NEAREST;
	}

	@Override
	public MagFilter texMagFilter()
	{
		return MagFilter.NEAREST;
	}

	@Override
	public Wrap texWrap()
	{
		return Wrap.CLAMP_TO_EDGE;
	}
}
