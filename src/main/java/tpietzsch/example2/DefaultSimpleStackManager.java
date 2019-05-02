package tpietzsch.example2;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.HashMap;

import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

import org.joml.Vector3f;

import tpietzsch.backend.GpuContext;
import tpietzsch.multires.SimpleStack3D;

public class DefaultSimpleStackManager implements SimpleStackManager
{
	private final HashMap< RandomAccessibleInterval< ? >, VolumeTextureU16 > texturesU16;

	private final HashMap< RandomAccessibleInterval< ? >, VolumeTextureU8 > texturesU8;

	public DefaultSimpleStackManager()
	{
		texturesU8 = new HashMap<>();
		texturesU16 = new HashMap<>();
	}

	@Override
	public SimpleVolume getSimpleVolume( final GpuContext context, final SimpleStack3D< ? > stack )
	{
		if ( stack.getType() instanceof UnsignedShortType )
		{
			final RandomAccessibleInterval< ? > image = stack.getImage();
			final VolumeTextureU16 texture = texturesU16.computeIfAbsent( image, rai -> uploadToTextureU16( context, ( RandomAccessibleInterval< UnsignedShortType > ) rai ) );
			final Vector3f sourceMax = new Vector3f( image.max( 0 ), image.max( 1 ), image.max( 2 ) );
			final Vector3f sourceMin = new Vector3f( image.min( 0 ), image.min( 1 ), image.min( 2 ) );

			return new SimpleVolume( texture, stack.getSourceTransform(), sourceMin, sourceMax );
		}
		else if ( stack.getType() instanceof UnsignedByteType )
		{
			final RandomAccessibleInterval< ? > image = stack.getImage();
			final VolumeTextureU8 texture = texturesU8.computeIfAbsent( image, rai -> uploadToTextureU8( context, ( RandomAccessibleInterval< UnsignedByteType > ) rai ) );
			final Vector3f sourceMax = new Vector3f( image.max( 0 ), image.max( 1 ), image.max( 2 ) );
			final Vector3f sourceMin = new Vector3f( image.min( 0 ), image.min( 1 ), image.min( 2 ) );

			return new SimpleVolume( texture, stack.getSourceTransform(), sourceMin, sourceMax );
		}
		else
			throw new IllegalArgumentException();
	}

	@Override
	public void freeUnusedSimpleVolumes( final GpuContext context )
	{
		// TODO
		System.out.println( "DefaultSimpleStackManager.freeUnusedSimpleVolumes" );
		System.out.println( "TODO" );
	}

	@Override
	public void freeSimpleVolumes( final GpuContext context )
	{
		// TODO
		System.out.println( "DefaultSimpleStackManager.freeSimpleVolumes" );
		System.out.println( "TODO" );
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

	private static void copyToBufferU16( final RandomAccessibleInterval< UnsignedShortType  > rai, final ByteBuffer buffer )
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

	private static void copyToBufferU8( final RandomAccessibleInterval< UnsignedByteType  > rai, final ByteBuffer buffer )
	{
		// TODO handle specific RAI types more efficiently
		// TODO multithreading
		final Cursor< UnsignedByteType > cursor = Views.flatIterable( rai ).cursor();
		int i = 0;
		while ( cursor.hasNext() )
			buffer.put( i++, cursor.next().getByte() );
	}
}
