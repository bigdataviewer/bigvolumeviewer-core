package tpietzsch.day8;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

public class BlockTextureUtils
{
	public static ByteBuffer imgToBuffer( RandomAccessibleInterval< UnsignedShortType > img )
	{
		assert img.numDimensions() == 3;

		final int bytesPerPixel = 2;
		int size = ( int ) Intervals.numElements( img ) * bytesPerPixel;
		final ByteBuffer buffer = ByteBuffer.allocateDirect( size );
		return imgToBuffer( img, buffer );
	}

	public static ByteBuffer imgToBuffer( RandomAccessibleInterval< UnsignedShortType > img, ByteBuffer buffer )
	{
		assert img.numDimensions() == 3;

		buffer.order( ByteOrder.LITTLE_ENDIAN );
		final ShortBuffer sbuffer = buffer.asShortBuffer();
		Cursor< UnsignedShortType > c = Views.flatIterable( img ).cursor();
		int i = 0;
		while ( c.hasNext() )
			sbuffer.put( i++, c.next().getShort() );
		return buffer;
	}

	public static ByteBuffer allocateBlockBuffer( final int[] paddedBlockSize )
	{
		final int bytesPerPixel = 2;
		final int size = ( int ) Intervals.numElements( paddedBlockSize ) * bytesPerPixel;
		return ByteBuffer.allocateDirect( size );
	}
}
