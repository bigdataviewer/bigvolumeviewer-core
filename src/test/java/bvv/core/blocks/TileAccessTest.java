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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntPredicate;

import bvv.core.backend.Texture;
import bvv.core.cache.CacheSpec;
import bvv.core.cache.UploadBuffer;
import bvv.core.multires.ResolutionLevel3D;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileShortArray;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.img.cell.CellImg;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.img.list.ListImg;
import net.imglib2.img.list.ListLocalizingCursor;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.NativeTypeFactory;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.volatiles.VolatileUnsignedShortType;
import net.imglib2.util.Cast;
import net.imglib2.util.Fraction;
import net.imglib2.util.IntervalIndexer;

import org.junit.Test;

/**
 * What {@link TileAccess} has to do, pinned down against a reference computed
 * pixel by pixel from the source image:
 * <ul>
 * <li>{@code loadTile} fills the padded block, out-of-bounds pixels and pixels
 *     in invalid cells with {@code 0}, and reports whether every cell it read
 *     was valid.</li>
 * <li>{@code canLoadCompletely} gives that same answer without copying.</li>
 * </ul>
 */
public class TileAccessTest
{
	private static final long[] DIMENSIONS = { 13, 11, 7 };

	private static final int[] CELL_DIMENSIONS = { 4, 5, 3 };

	private static final int[] BLOCK_SIZE = { 4, 4, 4 };

	/** Padded block size is {@code {6,6,6}}, pad offset {@code {1,1,1}}. */
	private static final CacheSpec CACHE_SPEC = new CacheSpec( Texture.InternalFormat.R16, BLOCK_SIZE );

	/**
	 * Straddling the min border; interior, crossing cell borders; straddling
	 * the max border; entirely outside past max; entirely outside before min;
	 * covering the whole image and then some.
	 */
	private static final int[][] GRID_POS = {
			{ 0, 0, 0 },
			{ 1, 1, 1 },
			{ 3, 2, 1 },
			{ 5, 5, 5 },
			{ -2, -2, -2 },
			{ -1, -1, -1 } };

	/** Which cells (by flattened grid index) hold valid data. */
	private static final IntPredicate[] PATTERNS = {
			i -> true,
			i -> false,
			i -> i % 2 == 0,
			i -> i % 7 != 0,
			i -> i != 13 };

	/** Offset of the tile within the upload buffer, in bytes. Deliberately not 0. */
	private static final int BUFFER_OFFSET = 64;

	@Test
	public void loadTileMatchesTheReference()
	{
		forEachCase( ( access, cellValid, gridPos, msg ) -> {
			final int[] size = CACHE_SPEC.paddedBlockSize();
			final int length = size[ 0 ] * size[ 1 ] * size[ 2 ];

			final ByteBuffer bytes = ByteBuffer.allocateDirect( BUFFER_OFFSET + 2 * length );
			final UploadBuffer buffer = new UploadBuffer( bytes, BUFFER_OFFSET, 2 * length );
			final boolean complete = access.loadTile( gridPos, buffer.buffer() );

			final short[] actual = new short[ length ];
			final ShortBuffer shorts = bytes.order( ByteOrder.nativeOrder() ).asShortBuffer();
			shorts.position( BUFFER_OFFSET / 2 );
			shorts.get( actual );

			assertArrayEquals( msg, reference( cellValid, gridPos ), actual );
			assertEquals( msg + " (complete)", expectedComplete( cellValid, gridPos ), complete );
		} );
	}

	@Test
	public void canLoadCompletelyMatchesLoadTile()
	{
		forEachCase( ( access, cellValid, gridPos, msg ) -> {
			final boolean expected = expectedComplete( cellValid, gridPos );
			assertEquals( msg + " (failfast)", expected, access.canLoadCompletely( gridPos, true ) );
			assertEquals( msg + " (visit all)", expected, access.canLoadCompletely( gridPos, false ) );
		} );
	}

	/**
	 * The buffer outside the tile must not be touched.
	 */
	@Test
	public void loadTileStaysWithinItsOffset()
	{
		final int[] size = CACHE_SPEC.paddedBlockSize();
		final int length = size[ 0 ] * size[ 1 ] * size[ 2 ];
		final int trailing = 32;

		final ByteBuffer bytes = ByteBuffer.allocateDirect( BUFFER_OFFSET + 2 * length + trailing );
		for ( int i = 0; i < bytes.capacity(); ++i )
			bytes.put( i, ( byte ) 0x5a );

		final TileAccess access = tileAccess( i -> true );
		access.loadTile( new int[] { 1, 1, 1 }, new UploadBuffer( bytes, BUFFER_OFFSET, 2 * length ).buffer() );

		for ( int i = 0; i < BUFFER_OFFSET; ++i )
			assertEquals( "byte " + i + " before the tile", ( byte ) 0x5a, bytes.get( i ) );
		for ( int i = BUFFER_OFFSET + 2 * length; i < bytes.capacity(); ++i )
			assertEquals( "byte " + i + " after the tile", ( byte ) 0x5a, bytes.get( i ) );
	}

	/**
	 * {@code TileAccess} is shared between the filler threads, so it has to be
	 * thread-safe. Every thread must get the same answers it would get alone.
	 */
	@Test
	public void isThreadSafe() throws Exception
	{
		final IntPredicate cellValid = PATTERNS[ 2 ];
		final TileAccess access = tileAccess( cellValid ).threadSafe();

		final int[] size = CACHE_SPEC.paddedBlockSize();
		final int length = size[ 0 ] * size[ 1 ] * size[ 2 ];
		final int numThreads = 4;
		final int iterations = 200;

		final ExecutorService exec = Executors.newFixedThreadPool( numThreads );
		try
		{
			final List< Future< String > > futures = new ArrayList<>();
			for ( int t = 0; t < numThreads; ++t )
			{
				futures.add( exec.submit( () -> {
					// one destination per thread: we are testing TileAccess, not ByteBuffer
					final ByteBuffer bytes = ByteBuffer.allocateDirect( 2 * length );
					final short[] actual = new short[ length ];
					for ( int i = 0; i < iterations; ++i )
					{
						for ( final int[] gridPos : GRID_POS )
						{
							final UploadBuffer buffer = new UploadBuffer( bytes, 0, 2 * length );
							final boolean complete = access.loadTile( gridPos, buffer.buffer() );

							bytes.order( ByteOrder.nativeOrder() ).asShortBuffer().get( actual );
							if ( !Arrays.equals( reference( cellValid, gridPos ), actual ) )
								return "wrong data for " + Arrays.toString( gridPos );
							if ( complete != expectedComplete( cellValid, gridPos ) )
								return "wrong completeness for " + Arrays.toString( gridPos );
							if ( complete != access.canLoadCompletely( gridPos, true ) )
								return "canLoadCompletely disagrees for " + Arrays.toString( gridPos );
						}
					}
					return null;
				} ) );
			}
			for ( final Future< String > f : futures )
				assertNull( f.get( 60, TimeUnit.SECONDS ) );
		}
		finally
		{
			exec.shutdownNow();
		}
	}

	/**
	 * Sources are no longer restricted to {@code AbstractCellImg}: anything
	 * {@code PrimitiveBlocks.of} understands works. A non-volatile {@code
	 * ArrayImg} is always complete.
	 */
	@Test
	public void supportsNonCellImgSources()
	{
		final ArrayImg< UnsignedShortType, ? > img = ArrayImgs.unsignedShorts( DIMENSIONS );
		final Cursor< UnsignedShortType > c = img.localizingCursor();
		while ( c.hasNext() )
		{
			c.fwd();
			c.get().set( value( c.getIntPosition( 0 ), c.getIntPosition( 1 ), c.getIntPosition( 2 ) ) & 0xffff );
		}

		final TileAccess access = DefaultTileAccess.create( new Level<>( img, img.getType() ), CACHE_SPEC );

		final int[] size = CACHE_SPEC.paddedBlockSize();
		final int length = size[ 0 ] * size[ 1 ] * size[ 2 ];
		for ( final int[] gridPos : GRID_POS )
		{
			final ByteBuffer bytes = ByteBuffer.allocateDirect( 2 * length );
			final boolean complete = access.loadTile( gridPos, new UploadBuffer( bytes, 0, 2 * length ).buffer() );

			final short[] actual = new short[ length ];
			bytes.order( ByteOrder.nativeOrder() ).asShortBuffer().get( actual );

			final String msg = "ArrayImg source, gridPos " + Arrays.toString( gridPos );
			assertArrayEquals( msg, reference( i -> true, gridPos ), actual );
			assertTrue( msg + " (complete)", complete );
			assertTrue( msg + " (canLoadCompletely)", access.canLoadCompletely( gridPos, true ) );
		}
	}

	private interface Case
	{
		void check( TileAccess access, IntPredicate cellValid, int[] gridPos, String msg );
	}

	private static void forEachCase( final Case c )
	{
		for ( int p = 0; p < PATTERNS.length; ++p )
		{
			final IntPredicate cellValid = PATTERNS[ p ];
			final TileAccess access = tileAccess( cellValid );
			for ( final int[] gridPos : GRID_POS )
				c.check( access, cellValid, gridPos, "pattern " + p + ", gridPos " + Arrays.toString( gridPos ) );
		}
	}

	private static TileAccess tileAccess( final IntPredicate cellValid )
	{
		final CellImg< VolatileUnsignedShortType, VolatileShortArray > img = createVolatileCellImg( cellValid );
		return DefaultTileAccess.create( new Level<>( img, img.getType() ), CACHE_SPEC );
	}

	/**
	 * Min coordinate of the padded block for {@code gridPos}, as {@link
	 * TileAccess} computes it.
	 */
	private static int[] blockMin( final int[] gridPos )
	{
		final int[] min = new int[ 3 ];
		for ( int d = 0; d < 3; ++d )
			min[ d ] = gridPos[ d ] * CACHE_SPEC.blockSize()[ d ] - CACHE_SPEC.padOffset()[ d ];
		return min;
	}

	/** The value stored at {@code (x,y,z)}, never {@code 0}. */
	private static short value( final int x, final int y, final int z )
	{
		return ( short ) ( 1 + ( ( x * 31 + y * 37 + z * 41 ) % 60000 ) );
	}

	private static int cellIndex( final int x, final int y, final int z )
	{
		final long[] gridDimensions = new long[ 3 ];
		new CellGrid( DIMENSIONS, CELL_DIMENSIONS ).gridDimensions( gridDimensions );
		return ( int ) IntervalIndexer.positionToIndex(
				new long[] { x / CELL_DIMENSIONS[ 0 ], y / CELL_DIMENSIONS[ 1 ], z / CELL_DIMENSIONS[ 2 ] },
				gridDimensions );
	}

	private static boolean inside( final int x, final int y, final int z )
	{
		return x >= 0 && y >= 0 && z >= 0
				&& x < DIMENSIONS[ 0 ] && y < DIMENSIONS[ 1 ] && z < DIMENSIONS[ 2 ];
	}

	/** Per-pixel expectation: out-of-bounds and invalid both read as {@code 0}. */
	private static short[] reference( final IntPredicate cellValid, final int[] gridPos )
	{
		final int[] min = blockMin( gridPos );
		final int[] size = CACHE_SPEC.paddedBlockSize();
		final short[] expected = new short[ size[ 0 ] * size[ 1 ] * size[ 2 ] ];
		int i = 0;
		for ( int z = 0; z < size[ 2 ]; ++z )
			for ( int y = 0; y < size[ 1 ]; ++y )
				for ( int x = 0; x < size[ 0 ]; ++x )
				{
					final int gx = min[ 0 ] + x;
					final int gy = min[ 1 ] + y;
					final int gz = min[ 2 ] + z;
					expected[ i++ ] = inside( gx, gy, gz ) && cellValid.test( cellIndex( gx, gy, gz ) )
							? value( gx, gy, gz )
							: 0;
				}
		return expected;
	}

	/** Whether every cell the padded block reads from is valid. */
	private static boolean expectedComplete( final IntPredicate cellValid, final int[] gridPos )
	{
		final int[] min = blockMin( gridPos );
		final int[] size = CACHE_SPEC.paddedBlockSize();
		for ( int z = 0; z < size[ 2 ]; ++z )
			for ( int y = 0; y < size[ 1 ]; ++y )
				for ( int x = 0; x < size[ 0 ]; ++x )
				{
					final int gx = min[ 0 ] + x;
					final int gy = min[ 1 ] + y;
					final int gz = min[ 2 ] + z;
					if ( inside( gx, gy, gz ) && !cellValid.test( cellIndex( gx, gy, gz ) ) )
						return false;
				}
		return true;
	}

	private static class Level< T extends NativeType< T > > implements ResolutionLevel3D< T >
	{
		private final RandomAccessibleInterval< T > img;

		private final T type;

		Level( final RandomAccessibleInterval< T > img, final T type )
		{
			this.img = img;
			this.type = type;
		}

		@Override
		public int getLevel()
		{
			return 0;
		}

		@Override
		public int[] getR()
		{
			return new int[] { 1, 1, 1 };
		}

		@Override
		public double[] getS()
		{
			return new double[] { 1, 1, 1 };
		}

		@Override
		public AffineTransform3D getLevelTransform()
		{
			return new AffineTransform3D();
		}

		@Override
		public RandomAccessibleInterval< T > getImage()
		{
			return img;
		}

		@Override
		public T getType()
		{
			return type;
		}
	}

	/**
	 * A {@code CellImg} with {@code VolatileShortArray} cells, valid exactly
	 * where {@code cellValid} says so. Valid cells are filled with {@link
	 * #value}, invalid ones are left zeroed.
	 * <p>
	 * That models what bdv actually hands out: {@code CreateInvalidVolatileCell}
	 * gets the data for an invalid cell from {@code DefaultEmptyArrayCreator},
	 * which returns one shared, freshly allocated (and therefore zeroed) array
	 * for all of them. The copier reads an invalid cell rather than filling it,
	 * so what a tile shows for missing data is exactly that array's content.
	 * <p>
	 * {@code CellImgFactory} always creates non-volatile accesses, so the cells
	 * are built by hand.
	 */
	private static CellImg< VolatileUnsignedShortType, VolatileShortArray > createVolatileCellImg( final IntPredicate cellValid )
	{
		final VolatileUnsignedShortType type = new VolatileUnsignedShortType();
		final NativeTypeFactory< VolatileUnsignedShortType, ? super VolatileShortArray > typeFactory =
				Cast.unchecked( type.getNativeTypeFactory() );
		final CellImgFactory< VolatileUnsignedShortType > factory = new CellImgFactory<>( type, CELL_DIMENSIONS );

		final Fraction entitiesPerPixel = type.getEntitiesPerPixel();
		final CellGrid grid = new CellGrid( DIMENSIONS, CELL_DIMENSIONS );
		final long[] gridDimensions = new long[ 3 ];
		grid.gridDimensions( gridDimensions );

		final Cell< VolatileShortArray > cellType = new Cell<>( new int[] { 1 }, new long[] { 1 }, null );
		final ListImg< Cell< VolatileShortArray > > cells = new ListImg<>( gridDimensions, cellType );

		final long[] cellGridPosition = new long[ 3 ];
		final ListLocalizingCursor< Cell< VolatileShortArray > > cellCursor = cells.localizingCursor();
		int index = 0;
		while ( cellCursor.hasNext() )
		{
			cellCursor.fwd();
			cellCursor.localize( cellGridPosition );
			final long[] cellMin = new long[ 3 ];
			final CellGrid.CellDimensionsAndSteps dimsAndSteps = grid.getCellDimensions( cellGridPosition, cellMin );
			final int[] cellDims = dimsAndSteps.dimensions();
			final boolean valid = cellValid.test( index );
			final short[] data = new short[ ( int ) entitiesPerPixel.mulCeil( dimsAndSteps.numPixels() ) ];
			if ( valid )
			{
				int i = 0;
				for ( int z = 0; z < cellDims[ 2 ]; ++z )
					for ( int y = 0; y < cellDims[ 1 ]; ++y )
						for ( int x = 0; x < cellDims[ 0 ]; ++x )
							data[ i++ ] = value(
									( int ) cellMin[ 0 ] + x,
									( int ) cellMin[ 1 ] + y,
									( int ) cellMin[ 2 ] + z );
			}
			cellCursor.set( new Cell<>( dimsAndSteps, cellMin, new VolatileShortArray( data, valid ) ) );
			++index;
		}

		final CellImg< VolatileUnsignedShortType, VolatileShortArray > img =
				new CellImg<>( factory, grid, cells, entitiesPerPixel );
		img.setLinkedType( typeFactory.createLinkedType( img ) );
		return img;
	}
}
