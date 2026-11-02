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
package bvv.core.blocks;

import static net.imglib2.type.PrimitiveType.BYTE;
import static net.imglib2.type.PrimitiveType.SHORT;

import java.nio.ByteBuffer;

import bvv.core.cache.CacheSpec;
import bvv.core.multires.ResolutionLevel3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.PrimitiveType;
import net.imglib2.util.Fraction;

/**
 * Copies blocks from a {@link ResolutionLevel3D} source into the tile portion of
 * an upload buffer.
 * <p>
 * Instances can be obtained by {@link #of TileAccess.of}. They are cached,
 * shared, and thread-safe.
 */
public interface TileAccess
{
	/**
	 * Whether the tile at {@code gridPos} can be loaded completely, that is,
	 * whether every pixel of it is available. Pixels outside the source image
	 * count as available: they are filled with zero.
	 *
	 * @param failfast
	 * 		if {@code true}, return as soon as missing data is found. If {@code
	 * 		false}, the whole block is inspected, which has the side effect of
	 * 		requesting all the data it reads from.
	 */
	boolean canLoadCompletely( int[] gridPos, boolean failfast );

	/**
	 * Load data for the tile at {@code gridPos} into {@code dest}.
	 * The tile is a padded block according to the {@code CacheSpec}.
	 *
	 * @return {@code true}, if the tile was loaded completely
	 */
	boolean loadTile( int[] gridPos, ByteBuffer dest );

	/**
	 * Returns an instance that can be used independently, e.g. on another thread.
	 */
	TileAccess independentCopy();

	/**
	 * Get a thread-safe version of this {@code TileAccess}.
	 * (Implemented as a wrapper that makes {@link ThreadLocal} copies).
	 */
	TileAccess threadSafe();


	@SuppressWarnings( "rawtypes" )
	static boolean isSupportedType( final Object type )
	{
		// Currently only [Volatile]UnsignedShortType and [Volatile]UnsignedByteType are handled
		if ( type instanceof NativeType )
		{
			final PrimitiveType primitive = ( ( NativeType ) type ).getNativeTypeFactory().getPrimitiveType();
			final Fraction epp = ( ( NativeType ) type ).getEntitiesPerPixel();
			return ( primitive == SHORT || primitive == BYTE ) && epp.getNumerator() == epp.getDenominator();
		}

		return false;
	}

	/**
	 * Get the shared {@code TileAccess} for {@code resolutionLevel3D} and {@code
	 * cacheSpec}, creating it if necessary.
	 * <p>
	 * Instances are shared process-wide and thread-safe, so callers neither need
	 * nor should keep their own. The identity of a {@code ResolutionLevel3D}
	 * includes the source's invalidation generation (see {@code
	 * SourceStacks.invalidate}), so an invalidated source gets a fresh instance
	 * rather than one holding the image it had before.
	 */
	static TileAccess of( final ResolutionLevel3D< ? > resolutionLevel3D, final CacheSpec cacheSpec )
	{
		return DefaultTileAccess.of( resolutionLevel3D, cacheSpec );
	}
}
