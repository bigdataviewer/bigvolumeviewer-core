package tpietzsch.example2;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.view.Views;
import org.joml.Vector3f;
import tpietzsch.backend.GpuContext;
import tpietzsch.backend.Texture3D;
import tpietzsch.blocks.ByteUtils;
import tpietzsch.cache.TextureCache.Tile;

import static tpietzsch.backend.Texture.InternalFormat.R16;
import static tpietzsch.backend.Texture.InternalFormat.RGBA8UI;

public class VolumeTextureU16 implements Texture3D
{
	private final int[] size = new int[ 3 ];

	private ByteBuffer data;

	/**
	 * Reinitialize.
	 */
	public void init( final int[] size )
	{
		for ( int d = 0; d < 3; d++ )
			this.size[ d ] = size[ d ];

		final int numBytes = 2 * size[ 0 ] * size[ 1 ] * size[ 2 ];
		if ( data == null || data.capacity() < numBytes )
		{
			data = ByteBuffer.allocateDirect( numBytes ); // allocate a bit more than needed...
			data.order( ByteOrder.nativeOrder() );
		}
		ByteUtils.setBytes( ( byte ) 8, ByteUtils.addressOf( data ), numBytes );
	}

	public void setRAI( final RandomAccessibleInterval< UnsignedShortType  > rai )
	{
		final Cursor< UnsignedShortType > cursor = Views.flatIterable( rai ).cursor();
		final ShortBuffer sdata = data.asShortBuffer();
		int i = 0;
		while ( cursor.hasNext() )
			sdata.put( i++, cursor.next().getShort() );
	}

	public void upload( final GpuContext context )
	{
		context.delete( this ); // TODO: is this necessary everytime?
		context.texSubImage3D( this, 0, 0, 0, texWidth(), texHeight(), texDepth(), data );
	}

	@Override
	public InternalFormat texInternalFormat()
	{
		return R16;
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
		return MinFilter.LINEAR;
	}

	@Override
	public MagFilter texMagFilter()
	{
		return MagFilter.LINEAR;
	}

	@Override
	public Wrap texWrap()
	{
		return Wrap.CLAMP_TO_BORDER_ZERO;
	}
}
