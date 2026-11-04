/*-
 * #%L
 * Volume rendering of bdv datasets
 * %%
 * Copyright (C) 2018 - 2026 Tobias Pietzsch
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
package bvv.core.render;

import static net.imglib2.blocks.PrimitiveBlocks.OnFallback.ACCEPT;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.HashMap;

import java.util.Iterator;
import java.util.Map;
import java.util.function.IntFunction;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.blocks.PrimitiveBlocks;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Intervals;

import org.joml.Vector3f;

import bvv.core.backend.GpuContext;
import bvv.core.backend.Texture3D;
import bvv.core.multires.SimpleStack3D;

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
		if ( type instanceof UnsignedShortType )
			texture = texturesU16.computeIfAbsent( stack, s -> uploadToTextureU16( context, ( RandomAccessibleInterval< UnsignedShortType > ) image ) );
		else if ( type instanceof UnsignedByteType )
			texture = texturesU8.computeIfAbsent( stack, s -> uploadToTextureU8( context, ( RandomAccessibleInterval< UnsignedByteType > ) image ) );
		else if ( type instanceof ARGBType )
			texture = texturesRGBA8.computeIfAbsent( stack, s -> uploadToTextureRGBA8( context, ( RandomAccessibleInterval< ARGBType > ) image ) );
		else
			throw new IllegalArgumentException();

		timestamps.put( texture, currentTimestamp );

		return new SimpleVolume( texture, stack.getSourceTransform(), image );
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

	private static void copyToBufferU16( final RandomAccessibleInterval< UnsignedShortType  > rai, final ByteBuffer buffer )
	{
		final ShortBuffer sdata = buffer.asShortBuffer();
		copySlabs( rai, short[]::new, ( slab, length ) -> sdata.put( slab, 0, length ) );
	}

	/**
	 * Approximate number of pixels copied per slab in {@link #copySlabs}.
	 */
	private static final int SLAB_ELEMENTS = 1 << 20;

	/**
	 * Receives the slabs copied by {@link #copySlabs}, in order.
	 *
	 * @param <A>
	 * 		primitive array type
	 */
	private interface SlabSink< A >
	{
		/**
		 * Consume the first {@code length} elements of {@code slab}.
		 */
		void put( A slab, int length );
	}

	/**
	 * Copy the 3D {@code rai} in flat iteration order, as slabs of whole
	 * z-planes of about {@link #SLAB_ELEMENTS} pixels each, into {@code sink}.
	 * One slab array is allocated with {@code newArray} and reused.
	 */
	private static < T extends NativeType< T >, A > void copySlabs(
			final RandomAccessibleInterval< T > rai,
			final IntFunction< A > newArray,
			final SlabSink< A > sink )
	{
		// ACCEPT: fall-back to LoopBuilder is fine
		final PrimitiveBlocks< T > blocks = PrimitiveBlocks.of( rai, ACCEPT );
		final int w = ( int ) rai.dimension( 0 );
		final int h = ( int ) rai.dimension( 1 );
		final int d = ( int ) rai.dimension( 2 );
		final int planeSize = w * h;

		final int planesPerSlab = Math.max( 1, Math.min( d, SLAB_ELEMENTS / planeSize ) );
		final A slab = newArray.apply( planesPerSlab * planeSize );
		final long[] srcPos = rai.minAsLongArray();
		final int[] size = { w, h, 0 };
		for ( int z = 0; z < d; z += planesPerSlab )
		{
			size[ 2 ] = Math.min( planesPerSlab, d - z );
			srcPos[ 2 ] = rai.min( 2 ) + z;
			blocks.copy( srcPos, slab, size );
			sink.put( slab, size[ 2 ] * planeSize );
		}
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
		final ByteBuffer bdata = buffer.duplicate();
		copySlabs( rai, byte[]::new, ( slab, length ) -> bdata.put( slab, 0, length ) );
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
		final IntBuffer idata = buffer.asIntBuffer();
		copySlabs( rai, int[]::new, ( slab, length ) -> {
			for ( int i = 0; i < length; i++ )
				slab[ i ] = toRGBA( slab[ i ] );
			idata.put( slab, 0, length );
		} );
	}

	private static int toRGBA( final int argb )
	{
		return ( argb & 0xff00ff00 ) | ( ( argb >> 16 ) & 0xff ) | ( ( argb & 0xff ) << 16 );
	}
}
