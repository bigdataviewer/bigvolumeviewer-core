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
package bvv.core.multires;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import bdv.viewer.Interpolation;
import bdv.viewer.Source;
import bvv.core.backend.Texture;
import bvv.core.blocks.TileAccess;
import bvv.core.cache.CacheSpec;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.VoxelDimensions;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.cell.CellImgFactory;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedShortType;

import org.junit.Test;

/**
 * {@link SourceStacks#invalidate} has to reach the multiresolution path.
 * <p>
 * The generation is part of the identity of a stack <em>and of each resolution
 * level</em>. The levels are what {@code TileAccess.Cache} and — through {@code
 * ImageBlockKey} — the {@code TextureCache} are keyed on, so if the generation
 * stops at the stack, an invalidated source keeps being served from blocks that
 * were copied out of the image it held before.
 */
public class SourceStacksTest
{
	private static final CacheSpec CACHE_SPEC =
			new CacheSpec( Texture.InternalFormat.R16, new int[] { 32, 32, 32 } );

	/**
	 * Minimal multiresolution-looking source over a {@code CellImg}.
	 * <p>
	 * It reports two mipmap levels (both the same image) because a small,
	 * single-level, non-volatile source is uploaded whole ({@code SIMPLE}) by
	 * {@code SourceStacks.inferSourceStackType}, and these tests need the
	 * {@code MULTIRESOLUTION} path.
	 */
	private static class TestSource implements Source< UnsignedShortType >
	{
		private final RandomAccessibleInterval< UnsignedShortType > img =
				new CellImgFactory<>( new UnsignedShortType(), 16 ).create( 64, 64, 64 );

		@Override
		public boolean isPresent( final int t )
		{
			return true;
		}

		@Override
		public RandomAccessibleInterval< UnsignedShortType > getSource( final int t, final int level )
		{
			return img;
		}

		@Override
		public RealRandomAccessible< UnsignedShortType > getInterpolatedSource( final int t, final int level, final Interpolation method )
		{
			throw new UnsupportedOperationException();
		}

		@Override
		public void getSourceTransform( final int t, final int level, final AffineTransform3D transform )
		{
			transform.identity();
		}

		@Override
		public UnsignedShortType getType()
		{
			return new UnsignedShortType();
		}

		@Override
		public String getName()
		{
			return "test";
		}

		@Override
		public VoxelDimensions getVoxelDimensions()
		{
			return new FinalVoxelDimensions( "px", 1, 1, 1 );
		}

		@Override
		public int getNumMipmapLevels()
		{
			return 2;
		}
	}

	@SuppressWarnings( "unchecked" )
	private static MultiResolutionStack3D< UnsignedShortType > stack( final Source< UnsignedShortType > source )
	{
		final Stack3D< UnsignedShortType > s = SourceStacks.getStack3D( source, 0 );
		assertTrue( "expected a MULTIRESOLUTION stack, got " + s, s instanceof MultiResolutionStack3D );
		return ( MultiResolutionStack3D< UnsignedShortType > ) s;
	}

	private static ResolutionLevel3D< UnsignedShortType > level0( final Source< UnsignedShortType > source )
	{
		return stack( source ).resolutions().get( 0 );
	}

	@Test
	public void stacksAreEqualWhileTheSourceIsUnchanged()
	{
		final TestSource source = new TestSource();
		assertEquals( stack( source ), stack( source ) );
		assertEquals( level0( source ), level0( source ) );
		assertEquals( level0( source ).hashCode(), level0( source ).hashCode() );
	}

	@Test
	public void invalidateChangesTheStackIdentity()
	{
		final TestSource source = new TestSource();
		final MultiResolutionStack3D< UnsignedShortType > before = stack( source );

		SourceStacks.invalidate( source );

		assertNotEquals( before, stack( source ) );
	}

	/**
	 * The one that actually matters: the resolution <em>level</em>, not just the
	 * stack, because that is what the caches are keyed on.
	 */
	@Test
	public void invalidateChangesTheResolutionLevelIdentity()
	{
		final TestSource source = new TestSource();
		final ResolutionLevel3D< UnsignedShortType > before = level0( source );

		SourceStacks.invalidate( source );

		assertNotEquals( before, level0( source ) );
	}

	/**
	 * What the process-wide cache behind {@link TileAccess#of} buys: two callers
	 * that ask for the same source, level and {@code CacheSpec} get the same
	 * instance, even though each {@code getStack3D} builds fresh — but equal —
	 * {@code ResolutionLevel3D} objects.
	 */
	@Test
	public void tileAccessIsSharedBetweenEqualLevels()
	{
		final TestSource source = new TestSource();

		final ResolutionLevel3D< UnsignedShortType > a = level0( source );
		final ResolutionLevel3D< UnsignedShortType > b = level0( source );
		assertNotSame( "precondition: the levels are distinct objects", a, b );
		assertEquals( "precondition: but equal ones", a, b );

		assertSame( TileAccess.of( a, CACHE_SPEC ), TileAccess.of( b, CACHE_SPEC ) );
	}

	/**
	 * End to end: an invalidated source must not keep being served by the
	 * {@code TileAccess} built over the image it held before.
	 */
	@Test
	public void invalidateInvalidatesTheTileAccessCache()
	{
		final TestSource source = new TestSource();

		final TileAccess before = TileAccess.of( level0( source ), CACHE_SPEC );
		assertSame( "same generation should reuse the TileAccess",
				before, TileAccess.of( level0( source ), CACHE_SPEC ) );

		SourceStacks.invalidate( source );

		assertNotSame( "invalidated source should get a fresh TileAccess",
				before, TileAccess.of( level0( source ), CACHE_SPEC ) );
	}
}
