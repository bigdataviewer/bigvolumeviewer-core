/*-
 * #%L
 * Volume rendering of bdv datasets
 * %%
 * Copyright (C) 2018 - 2024 Tobias Pietzsch
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package bvv.core.blocks;

import java.lang.reflect.Field;
import java.nio.Buffer;
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

	public static void setShorts( final short src, final long dst, final long csx )
	{
		for ( int i = 0; i < csx; ++i )
			UNSAFE.putShort( dst + 2 * i, src );
	}

	public static void setBytes( final byte src, final long dst, final long csx )
	{
		UNSAFE.setMemory( dst, csx, src );
	}

	public interface Address
	{
		long getAddress();
	}
}
