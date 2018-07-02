package tpietzsch.blocks;

import java.lang.reflect.Field;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;

import sun.misc.Unsafe;

@SuppressWarnings( "restriction" )
public class ByteUtils
{
	private static final Unsafe UNSAFE;

	private static final long BUFFER_ADDRESS_OFFSET;
	private static final long BYTE_ARRAY_OFFSET;
	private static final long SHORT_ARRAY_OFFSET;

	static
	{
		try
		{
			final PrivilegedExceptionAction< Unsafe > action = new PrivilegedExceptionAction< Unsafe >()
			{
				@Override
				public Unsafe run() throws Exception
				{
					final Field field = Unsafe.class.getDeclaredField( "theUnsafe" );
					field.setAccessible( true );
					return ( Unsafe ) field.get( null );
				}
			};

			UNSAFE = AccessController.doPrivileged( action );

			final Field bufferAddressField = Buffer.class.getDeclaredField( "address" );
			bufferAddressField.setAccessible(true);
			BUFFER_ADDRESS_OFFSET = UNSAFE.objectFieldOffset( bufferAddressField );

			BYTE_ARRAY_OFFSET = UNSAFE.arrayBaseOffset( byte[].class );
			SHORT_ARRAY_OFFSET = UNSAFE.arrayBaseOffset( short[].class );
		}
		catch ( final Exception ex )
		{
			throw new RuntimeException( ex );
		}
	}

	public static long addressOf( final Buffer buffer )
	{
		return UNSAFE.getLong( buffer, BUFFER_ADDRESS_OFFSET );
	}

	public static void copyShorts( final short[] src, final long dst, final long sox, final long csx )
	{
		UNSAFE.copyMemory( src, SHORT_ARRAY_OFFSET + 2 * sox, null, dst, 2 * csx );
	}

	public static void copyShorts( final long src, final long dst, final long csx )
	{
		UNSAFE.copyMemory( null, src, null, dst, 2 * csx );
	}

	public static void setShorts( final short src, final long dst, final long csx )
	{
		for ( int i = 0; i < csx; ++i )
			UNSAFE.putShort( dst + 2 * i, src );
	}

	public interface Address
	{
		long getAddress();
	}
}
