package tpietzsch.example2;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import org.joml.Vector3f;
import tpietzsch.backend.GpuContext;
import tpietzsch.backend.Texture3D;
import tpietzsch.multires.SimpleStack3D;

public class DefaultSimpleStackManager implements SimpleStackManager
{
	private final HashMap< SimpleStack3D< ? >, VolumeTextureU16 > texturesU16;
	private final HashMap< SimpleStack3D< ? >, VolumeTextureU8 > texturesU8;
	private final HashMap< SimpleStack3D< ? >, VolumeTextureRGBA8 > texturesRGBA8;

	private final HashMap< Texture3D, Integer > timestamps;
	private int currentTimestamp;

	public DefaultSimpleStackManager()
	{
		texturesU16 = new HashMap<>();
		texturesU8 = new HashMap<>();
		texturesRGBA8 = new HashMap<>();
		timestamps = new HashMap<>();
		currentTimestamp = 0;
	}

	@Override
	public synchronized SimpleVolume getSimpleVolume( final GpuContext context, final SimpleStack3D< ? > stack )
	{
		final RandomAccessibleInterval< ? > image = stack.getImage();
		final Object type = stack.getType();

		final Texture3D texture;
		final Vector3f sourceMax;
		final Vector3f sourceMin;

		if ( type instanceof UnsignedShortType )
		{
			texture = texturesU16.computeIfAbsent( stack, s -> uploadToTextureU16( context, ( RandomAccessibleInterval< UnsignedShortType > ) image ) );
			sourceMax = new Vector3f( image.max( 0 ), image.max( 1 ), image.max( 2 ) );
			sourceMin = new Vector3f( image.min( 0 ), image.min( 1 ), image.min( 2 ) );
		}
		else if ( type instanceof UnsignedByteType )
		{
			texture = texturesU8.computeIfAbsent( stack, s -> uploadToTextureU8( context, ( RandomAccessibleInterval< UnsignedByteType > ) image ) );
			sourceMax = new Vector3f( image.max( 0 ), image.max( 1 ), image.max( 2 ) );
			sourceMin = new Vector3f( image.min( 0 ), image.min( 1 ), image.min( 2 ) );
		}
		else if ( type instanceof ARGBType )
		{
			texture = texturesRGBA8.computeIfAbsent( stack, s -> uploadToTextureRGBA8( context, ( RandomAccessibleInterval< ARGBType > ) image ) );
			sourceMax = new Vector3f( image.max( 0 ), image.max( 1 ), image.max( 2 ) );
			sourceMin = new Vector3f( image.min( 0 ), image.min( 1 ), image.min( 2 ) );
		}
		else
			throw new IllegalArgumentException();

		timestamps.put( texture, currentTimestamp );

		return new SimpleVolume( texture, stack.getSourceTransform(), sourceMin, sourceMax );
	}

	/**
	 * Free allocated resources associated to all stacks that have not been
	 * {@link #getSimpleVolume(GpuContext,SimpleStack3D) requested} since the
	 * last call to {@link #freeUnusedSimpleVolumes(GpuContext)}.
	 */
	@Override
	public synchronized void freeUnusedSimpleVolumes( final GpuContext context )
	{
		final Iterator< Map.Entry< Texture3D, Integer > > it = timestamps.entrySet().iterator();

		texturesU16.entrySet().removeIf( entry -> timestamps.get( entry.getValue() ) < currentTimestamp );
		texturesU8.entrySet().removeIf( entry -> timestamps.get( entry.getValue() ) < currentTimestamp );
		texturesRGBA8.entrySet().removeIf( entry -> timestamps.get( entry.getValue() ) < currentTimestamp );
		while ( it.hasNext() )
		{
			final Map.Entry< Texture3D, Integer > entry = it.next();
			if ( entry.getValue() < currentTimestamp )
			{
				context.delete( entry.getKey() );
				it.remove();
			}
		}
		++currentTimestamp;
	}

	@Override
	public void freeSimpleVolumes( final GpuContext context )
	{
		texturesU16.clear();
		texturesU8.clear();
		texturesRGBA8.clear();
		timestamps.keySet().forEach( context::delete );
		timestamps.clear();
	}

	private static VolumeTextureU16 uploadToTextureU16( final GpuContext context, final RandomAccessibleInterval< UnsignedShortType > rai )
	{
		final VolumeTextureU16 texture = new VolumeTextureU16();
		texture.init( Intervals.dimensionsAsIntArray( rai ) );

		final int numBytes = ( int ) ( 2 * Intervals.numElements( rai ) );
		final ByteBuffer data = ByteBuffer.allocateDirect( numBytes ); // allocate a bit more than needed...
		data.order( ByteOrder.nativeOrder() );
		copyToBufferU16( rai, data );
		texture.upload( context, data );
		return texture;
	}

	private static void copyToBufferU16( final RandomAccessibleInterval< UnsignedShortType > rai, final ByteBuffer buffer )
	{
		// TODO handle specific RAI types more efficiently
		// TODO multithreading
		final Cursor< UnsignedShortType > cursor = Views.flatIterable( rai ).cursor();
		final ShortBuffer sdata = buffer.asShortBuffer();
		int i = 0;
		while ( cursor.hasNext() )
			sdata.put( i++, cursor.next().getShort() );
	}

	private static VolumeTextureU8 uploadToTextureU8( final GpuContext context, final RandomAccessibleInterval< UnsignedByteType > rai )
	{
		final VolumeTextureU8 texture = new VolumeTextureU8();
		texture.init( Intervals.dimensionsAsIntArray( rai ) );

		final int numBytes = ( int ) ( Intervals.numElements( rai ) );
		final ByteBuffer data = ByteBuffer.allocateDirect( numBytes ); // allocate a bit more than needed...
		data.order( ByteOrder.nativeOrder() );
		copyToBufferU8( rai, data );
		texture.upload( context, data );
		return texture;
	}

	private static void copyToBufferU8( final RandomAccessibleInterval< UnsignedByteType > rai, final ByteBuffer buffer )
	{
		// TODO handle specific RAI types more efficiently
		// TODO multithreading
		final Cursor< UnsignedByteType > cursor = Views.flatIterable( rai ).cursor();
		int i = 0;
		while ( cursor.hasNext() )
			buffer.put( i++, cursor.next().getByte() );
	}

	private static VolumeTextureRGBA8 uploadToTextureRGBA8( final GpuContext context, final RandomAccessibleInterval< ARGBType > rai )
	{
		final VolumeTextureRGBA8 texture = new VolumeTextureRGBA8();
		texture.init( Intervals.dimensionsAsIntArray( rai ) );

		final int numBytes = ( int ) ( 4 * Intervals.numElements( rai ) );
		final ByteBuffer data = ByteBuffer.allocateDirect( numBytes ); // allocate a bit more than needed...
		data.order( ByteOrder.nativeOrder() );
		copyToBufferRGBA8( rai, data );
		texture.upload( context, data );
		return texture;
	}

	private static void copyToBufferRGBA8( final RandomAccessibleInterval< ARGBType > rai, final ByteBuffer buffer )
	{
		// TODO handle specific RAI types more efficiently
		// TODO multithreading
		final Cursor< ARGBType > cursor = Views.flatIterable( rai ).cursor();
		final IntBuffer sdata = buffer.asIntBuffer();
		int i = 0;
		while ( cursor.hasNext() )
			sdata.put( i++, toRGBA( cursor.next().get() ) );
	}

	private static int toRGBA( final int argb )
	{
		final int a = ( argb >> 24 ) & 0xff;
		final int r = ( argb >> 16 ) & 0xff;
		final int g = ( argb >> 8 ) & 0xff;
		final int b = argb & 0xff;
		return ( a << 24 ) | ( b << 16 ) | ( g << 8 ) | r;
	}
}
