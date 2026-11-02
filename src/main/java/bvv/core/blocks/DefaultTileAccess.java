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
import java.util.function.Supplier;

import bvv.core.backend.Texture;
import bvv.core.cache.CacheSpec;
import bvv.core.multires.ResolutionLevel3D;
import net.imglib2.Interval;
import net.imglib2.Volatile;
import net.imglib2.blocks.BlockInterval;
import net.imglib2.blocks.PrimitiveBlocks;
import net.imglib2.blocks.TempArray;
import net.imglib2.blocks.VolatileArray;
import net.imglib2.blocks.VolatilePrimitiveBlocks;
import net.imglib2.cache.UncheckedCache;
import net.imglib2.cache.ref.WeakRefLoaderCache;
import net.imglib2.type.NativeType;
import net.imglib2.type.PrimitiveType;
import net.imglib2.type.operators.SetZero;
import net.imglib2.util.Cast;
import net.imglib2.util.CloseableThreadLocal;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

/**
 * Default {@link TileAccess}: one {@code PrimitiveBlocks} copy into a scratch
 * block, then one bulk {@code put} of that block into the tile.
 * <p>
 * Not thread-safe — it reuses one scratch block and one {@code BlockInterval}.
 * Use {@link #threadSafe()}.
 *
 * @param <D>
 * 		what the copier writes into: a primitive array such as {@code short[]}, or
 * 		a {@code VolatileArray} of one for a {@code Volatile} source.
 */
abstract class DefaultTileAccess< D > implements TileAccess
{
	private final CacheSpec cacheSpec;

	/** scratch space for one padded block */
	private final TempArray< D > tempArray;

	/** number of pixels in a padded block */
	private final int numElements;

	private final Upload< D > upload;

	/** min coordinate of the padded block, wrapped by {@link #interval} */
	private final long[] min = new long[ 3 ];

	/** the padded block to copy, re-aimed by writing into {@link #min} */
	private final BlockInterval interval;

	private Supplier< TileAccess > threadSafeSupplier;

	/**
	 * Copies a filled block into the tile portion of an upload buffer, and reports
	 * whether the block was complete. Both halves depend on whether {@code D} is a
	 * plain primitive array or a {@code VolatileArray}, which is why they are one
	 * function rather than two fields.
	 */
	@FunctionalInterface
	interface Upload< D >
	{
		boolean copy( D src, ByteBuffer dest );
	}

	DefaultTileAccess(
			final NativeType< ? > type,
			final Upload< D > upload,
			final CacheSpec cacheSpec )
	{
		this.tempArray = TempArray.forType( type );
		this.upload = upload;
		this.cacheSpec = cacheSpec;
		this.numElements = Util.safeInt( Intervals.numElements( cacheSpec.paddedBlockSize() ) );
		this.interval = BlockInterval.wrap( min, cacheSpec.paddedBlockSize() );
	}

	// creates an independent copy of {@code other}
	DefaultTileAccess( final DefaultTileAccess< D > other )
	{
		this.tempArray = other.tempArray.newInstance();
		this.upload = other.upload;
		this.cacheSpec = other.cacheSpec;
		this.numElements = other.numElements;
		this.interval = BlockInterval.wrap( min, cacheSpec.paddedBlockSize() );
	}

	/**
	 * Copy the block {@code interval} of the source into {@code dest}.
	 */
	abstract void copy( Interval interval, D dest );

	/**
	 * Whether every pixel of the block {@code interval} of the source is valid.
	 */
	abstract boolean allValid( Interval interval, boolean failfast );

	/**
	 * Point {@link #interval} at the padded block for {@code gridPos}.
	 */
	private void setBlock( final int[] gridPos )
	{
		for ( int d = 0; d < 3; ++d )
			min[ d ] = ( long ) gridPos[ d ] * cacheSpec.blockSize()[ d ] - cacheSpec.padOffset()[ d ];
	}

	@Override
	public boolean canLoadCompletely( final int[] gridPos, final boolean failfast )
	{
		setBlock( gridPos );
		return allValid( interval, failfast );
	}

	@Override
	public boolean loadTile( final int[] gridPos, final ByteBuffer dest )
	{
		setBlock( gridPos );
		final D data = tempArray.get( numElements );
		copy( interval, data );
		return upload.copy( data, dest );
	}

	@Override
	public TileAccess threadSafe()
	{
		if ( threadSafeSupplier == null )
			threadSafeSupplier = CloseableThreadLocal.withInitial( this::independentCopy )::get;
		return new TileAccess()
		{
			@Override
			public boolean canLoadCompletely( final int[] gridPos, final boolean failfast )
			{
				return threadSafeSupplier.get().canLoadCompletely( gridPos, failfast );
			}

			@Override
			public boolean loadTile( final int[] gridPos, final ByteBuffer dest )
			{
				return threadSafeSupplier.get().loadTile( gridPos, dest );
			}

			@Override
			public TileAccess independentCopy()
			{
				return DefaultTileAccess.this.independentCopy().threadSafe();
			}

			@Override
			public TileAccess threadSafe()
			{
				return this;
			}
		};
	}

	/**
	 * {@code TileAccess} for a non-volatile source. Every block is complete.
	 *
	 * @param <D>
	 * 		primitive array type, e.g. {@code short[]}
	 */
	private static class PlainTileAccess< D > extends DefaultTileAccess< D >
	{
		/** the source, extended with zero, as blocks of primitive data */
		private final PrimitiveBlocks< ? > blocks;

		PlainTileAccess(
				final PrimitiveBlocks< ? > blocks,
				final Upload< D > upload,
				final CacheSpec cacheSpec )
		{
			super( blocks.getType(), upload, cacheSpec );
			this.blocks = blocks;
		}

		// creates an independent copy of {@code other}
		private PlainTileAccess( final PlainTileAccess< D > other )
		{
			super( other );
			this.blocks = other.blocks.independentCopy();
		}

		@Override
		void copy( final Interval interval, final D dest )
		{
			blocks.copy( interval, dest );
		}

		@Override
		boolean allValid( final Interval interval, final boolean failfast )
		{
			return true;
		}

		@Override
		public TileAccess independentCopy()
		{
			return new PlainTileAccess<>( this );
		}
	}

	/**
	 * {@code TileAccess} for a {@code Volatile} source.
	 *
	 * @param <P>
	 * 		primitive array type of the data, e.g. {@code short[]}
	 */
	private static class VolatileTileAccess< P > extends DefaultTileAccess< VolatileArray< P > >
	{
		/** the source, extended with zero, as blocks of primitive data */
		private final VolatilePrimitiveBlocks< ? > blocks;

		VolatileTileAccess(
				final VolatilePrimitiveBlocks< ? > blocks,
				final Upload< VolatileArray< P > > upload,
				final CacheSpec cacheSpec )
		{
			super( blocks.getType(), upload, cacheSpec );
			this.blocks = blocks;
		}

		// creates an independent copy of {@code other}
		private VolatileTileAccess( final VolatileTileAccess< P > other )
		{
			super( other );
			this.blocks = other.blocks.independentCopy();
		}

		@Override
		void copy( final Interval interval, final VolatileArray< P > dest )
		{
			blocks.copy( interval, dest );
		}

		/**
		 * {@code UNKNOWN} cannot occur here (no {@code UnaryBlockOperator}
		 * chain). If it ever does, it must count as complete: it means "cannot
		 * tell without doing the work", not "no data".
		 */
		@Override
		boolean allValid( final Interval interval, final boolean failfast )
		{
			return blocks.allValid( interval, failfast ) == VolatilePrimitiveBlocks.AllValid.TRUE;
		}

		@Override
		public TileAccess independentCopy()
		{
			return new VolatileTileAccess<>( this );
		}
	}

	static TileAccess create( final ResolutionLevel3D< ? > resolutionLevel3D, final CacheSpec cacheSpec )
	{
		final Object type = resolutionLevel3D.getType();
		if ( TileAccess.isSupportedType( type ) )
		{
			final boolean volatil = type instanceof Volatile;
			final PrimitiveType primitive = ( ( NativeType< ? > ) type ).getNativeTypeFactory().getPrimitiveType();

			if ( primitive == SHORT && cacheSpec.format() == Texture.InternalFormat.R16 )
			{
				if ( volatil )
					return new VolatileTileAccess<>( volatileBlocksOf( Cast.unchecked( resolutionLevel3D ) ), ( VolatileArray< short[] > src, ByteBuffer dst ) -> {
						dst.asShortBuffer().put( src.data() );
						return src.allValid();
					}, cacheSpec );
				else
					return new PlainTileAccess<>( blocksOf( Cast.unchecked( resolutionLevel3D ) ), ( short[] src, ByteBuffer dst ) -> {
						dst.asShortBuffer().put( src );
						return true;
					}, cacheSpec );
			}
			else if ( primitive == BYTE && cacheSpec.format() == Texture.InternalFormat.R8 )
			{
				if ( volatil )
					return new VolatileTileAccess<>( volatileBlocksOf( Cast.unchecked( resolutionLevel3D ) ), ( VolatileArray< byte[] > src, ByteBuffer dst ) -> {
						dst.put( src.data() );
						return src.allValid();
					}, cacheSpec );
				else
					return new PlainTileAccess<>( blocksOf( Cast.unchecked( resolutionLevel3D ) ), ( byte[] src, ByteBuffer dst ) -> {
						dst.put( src );
						return true;
					}, cacheSpec );
			}
		}

		throw new UnsupportedOperationException( "pixel and/or image type not supported (yet)." );
	}

	/**
	 * Blocks of the (non-volatile) source image, extended with zero so that out-of-bounds padding works.
	 */
	private static < T extends NativeType< T > & SetZero > PrimitiveBlocks< T > blocksOf( final ResolutionLevel3D< T > resolutionLevel3D )
	{
		return PrimitiveBlocks.of( Views.extendZero( resolutionLevel3D.getImage() ) );
	}

	/**
	 * Blocks of the volatile source image, extended with zero so that out-of-bounds padding works.
	 */
	private static < T extends Volatile< ? > & NativeType< T > & SetZero > VolatilePrimitiveBlocks< T > volatileBlocksOf( final ResolutionLevel3D< T > resolutionLevel3D )
	{
		return VolatilePrimitiveBlocks.of( Views.extendZero( resolutionLevel3D.getImage() ) );
	}

	/**
	 * The process-wide cache behind {@link TileAccess#of}.
	 * <p>
	 * Values are held weakly, so it never keeps instances alive and never leaks —
	 * but it also never promises reuse. See the note in CLAUDE.md.
	 */
	private static class Cache
	{
		private static class Key
		{
			final ResolutionLevel3D< ? > resolutionLevel3D;
			final CacheSpec cacheSpec;
			private final int hashcode;

			Key( final ResolutionLevel3D< ? > resolutionLevel3D, final CacheSpec cacheSpec )
			{
				this.resolutionLevel3D = resolutionLevel3D;
				this.cacheSpec = cacheSpec;
				int value = resolutionLevel3D.hashCode();
				value = 31 * value + cacheSpec.hashCode();
				this.hashcode = value;
			}

			@Override
			public boolean equals( final Object o )
			{
				if ( !( o instanceof Cache.Key ) )
					return false;

				final Cache.Key key = ( Cache.Key ) o;

				if ( !resolutionLevel3D.equals( key.resolutionLevel3D ) )
					return false;
				return cacheSpec.equals( key.cacheSpec );
			}

			@Override
			public int hashCode()
			{
				return hashcode;
			}
		}

		private final UncheckedCache< Cache.Key, TileAccess > accesses =
				new WeakRefLoaderCache< Cache.Key, TileAccess >()
						.withLoader( key -> DefaultTileAccess.create( key.resolutionLevel3D, key.cacheSpec ).threadSafe() )
						.unchecked();

		TileAccess get( final ResolutionLevel3D< ? > resolutionLevel3D, final CacheSpec cacheSpec )
		{
			return accesses.get( new Cache.Key( resolutionLevel3D, cacheSpec ) );
		}
	}

	private static final Cache CACHE = new Cache();

	static TileAccess of( final ResolutionLevel3D< ? > resolutionLevel3D, final CacheSpec cacheSpec )
	{
		return CACHE.get( resolutionLevel3D, cacheSpec );
	}
}
