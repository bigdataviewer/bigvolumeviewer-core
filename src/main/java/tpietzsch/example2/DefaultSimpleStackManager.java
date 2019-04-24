package tpietzsch.example2;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.HashMap;

import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

import org.joml.Vector3f;

import tpietzsch.backend.GpuContext;
import tpietzsch.multires.SimpleStack3D;

public class DefaultSimpleStackManager implements SimpleStackManager
{
	private final HashMap< RandomAccessibleInterval< ? >, VolumeTextureU16 > textures;

	public DefaultSimpleStackManager()
	{
		textures = new HashMap<>();
	}

	@Override
	public SimpleVolume getSimpleVolume( final GpuContext context, final SimpleStack3D< ? > stack )
	{
		if ( ! ( stack.getType() instanceof UnsignedShortType ) )
			throw new IllegalArgumentException();

		final RandomAccessibleInterval< ? > image = stack.getImage();
		final VolumeTextureU16 texture = textures.computeIfAbsent( image, rai -> uploadToTexture( context, ( RandomAccessibleInterval< UnsignedShortType > ) rai ) );
		final Vector3f sourceMax = new Vector3f( image.max( 0 ), image.max( 1 ), image.max( 2 ) );
		final Vector3f sourceMin = new Vector3f( image.min( 0 ), image.min( 1 ), image.min( 2 ) );

		return new SimpleVolume( texture, stack.getSourceTransform(), sourceMin, sourceMax );
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

	private static VolumeTextureU16 uploadToTexture( final GpuContext context, final RandomAccessibleInterval< UnsignedShortType > rai )
	{
		final VolumeTextureU16 texture = new VolumeTextureU16();
		texture.init( Intervals.dimensionsAsIntArray( rai ) );

		final int numBytes = ( int ) ( 2 * Intervals.numElements( rai ) );
		final ByteBuffer data = ByteBuffer.allocateDirect( numBytes ); // allocate a bit more than needed...
		data.order( ByteOrder.nativeOrder() );
		copyToBuffer( rai, data );
		texture.upload( context, data );
		return texture;
	}

	private static void copyToBuffer( final RandomAccessibleInterval< UnsignedShortType  > rai, final ByteBuffer buffer )
	{
		// TODO handle specific RAI types more efficiently
		// TODO multithreading
		final Cursor< UnsignedShortType > cursor = Views.flatIterable( rai ).cursor();
		final ShortBuffer sdata = buffer.asShortBuffer();
		int i = 0;
		while ( cursor.hasNext() )
			sdata.put( i++, cursor.next().getShort() );
	}
}
