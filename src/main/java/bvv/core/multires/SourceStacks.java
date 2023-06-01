/*-
 * #%L
 * Volume rendering of bdv datasets
 * %%
 * Copyright (C) 2018 - 2021 Tobias Pietzsch
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

import bdv.util.volatiles.VolatileView;
import bdv.viewer.Source;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.cell.AbstractCellImg;
import net.imglib2.realtransform.AffineTransform3D;
import bvv.core.blocks.TileAccess;

import static bvv.core.multires.SourceStacks.SourceStackType.MULTIRESOLUTION;
import static bvv.core.multires.SourceStacks.SourceStackType.SIMPLE;
import static bvv.core.multires.SourceStacks.SourceStackType.UNDEFINED;

public class SourceStacks
{
	public enum SourceStackType
	{
		SIMPLE,
		MULTIRESOLUTION,
		UNDEFINED
	}

	private static final Map< Source< ? >, SourceStackType > sourceStackTypes = new WeakHashMap<>();

	private static final Map< Source< ? >, AtomicInteger > sourceGenerations = new WeakHashMap<>();

	public static void setSourceStackType( Source< ? > source, SourceStackType stack )
	{
		sourceStackTypes.put( source, stack );
	}

	public static SourceStackType getSourceStackType( Source< ? > source )
	{
		return sourceStackTypes.getOrDefault( source, UNDEFINED );
	}

	public static void invalidate( final Source< ? > source )
	{
		synchronized ( source )
		{
			sourceGenerations.computeIfAbsent( source, s -> new AtomicInteger() ).getAndIncrement();
		}
	}

	public static < T > Stack3D< T > getStack3D( final Source< T > source, final int timepoint )
	{
		if ( !source.isPresent( timepoint ) )
			return null;

		SourceStackType stackType = getSourceStackType( source );
		if ( stackType == UNDEFINED )
		{
			stackType = inferSourceStackType( source, timepoint );
			setSourceStackType( source, stackType );
		}

		if ( stackType == SIMPLE )
		{
			final int generation;
			synchronized ( source )
			{
				generation = sourceGenerations.computeIfAbsent( source, s -> new AtomicInteger() ).get();
			}
			return new SimpleStack3DImp<>( source, timepoint, generation );
		}
		else if ( stackType == MULTIRESOLUTION )
			return new MultiResolutionStack3DImp<>( source, timepoint );
		else
			return null;
	}

	/*
	 * Decide whether to use a cached multiresolution stack or a simple stack.
	 * Depends on properties of source and what is currently implemented in BVV...
	 */
	private static SourceStackType inferSourceStackType( Source< ? > source, final int timepoint )
	{
		final Object type = source.getType();

		if ( TileAccess.isSupportedType( type ) )
		{
			RandomAccessible< ? > rai = source.getSource( timepoint, 0 );
			if ( rai instanceof VolatileView )
				rai = ( ( VolatileView ) rai ).getVolatileViewData().getImg();

			if ( rai instanceof AbstractCellImg )
				return MULTIRESOLUTION;
		}

		return SIMPLE;
	}

	static abstract class Stack3DImp< T > implements Stack3D< T >
	{
		final int timepoint;

		final Source< T > source;

		private final AffineTransform3D sourceTransform;

		private final int generation;

		Stack3DImp( final Source< T > source, final int timepoint, final int generation )
		{
			this.timepoint = timepoint;
			this.source = source;
			this.sourceTransform = new AffineTransform3D();
			source.getSourceTransform( timepoint, 0, sourceTransform );
			this.generation = generation;
		}

		@Override
		public AffineTransform3D getSourceTransform()
		{
			return sourceTransform;
		}

		@Override
		public T getType()
		{
			return source.getType();
		}

		@Override
		public boolean equals( final Object o )
		{
			if ( this == o )
				return true;
			if ( o == null || getClass() != o.getClass() )
				return false;

			final Stack3DImp< ? > that = ( Stack3DImp< ? > ) o;

			if ( timepoint != that.timepoint )
				return false;
			if ( generation != that.generation )
				return false;
			return source.equals( that.source );
		}

		@Override
		public int hashCode()
		{
			int result = timepoint;
			result = 31 * result + generation;
			result = 31 * result + source.hashCode();
			return result;
		}
	}

	static class SimpleStack3DImp< T > extends Stack3DImp< T > implements SimpleStack3D< T >
	{
		SimpleStack3DImp( final Source< T > source, final int timepoint, final int generation )
		{
			super( source, timepoint, generation );
		}

		@Override
		public RandomAccessibleInterval< T > getImage()
		{
			return source.getSource( timepoint, 0 );
		}
	}

	static class MultiResolutionStack3DImp< T > extends Stack3DImp< T > implements MultiResolutionStack3D< T >
	{
		private final ArrayList< ResolutionLevel3DImp< T > > resolutions;

		MultiResolutionStack3DImp( final Source< T > source, final int timepoint )
		{
			super( source, timepoint, 0 /*TODO*/ );

			final SourceStackResolutions ssr = SourceStacks.sourceStackResolutions.computeIfAbsent( source, s -> new SourceStackResolutions( source, timepoint ) );

			resolutions = new ArrayList<>();
			for ( int level = 0; level < source.getNumMipmapLevels(); level++ )
				resolutions.add( new ResolutionLevel3DImp<>( source, timepoint, level, ssr ) );
		}

		@Override
		public List< ResolutionLevel3DImp< T > > resolutions()
		{
			return resolutions;
		}
	}

	static class ResolutionLevel3DImp< T > implements ResolutionLevel3D< T >
	{
		private final int level;

		private final int timepoint;

		private final Source< T > source;

		private final int[] resolution;

		private final double[] scale;

		private final AffineTransform3D levelt;

		ResolutionLevel3DImp( final Source< T > source, final int timepoint, final int level, final SourceStackResolutions sourceStackResolutions )
		{
			this.level = level;
			this.timepoint = timepoint;
			this.source = source;

			resolution = sourceStackResolutions.resolutions[ level ];
			scale = sourceStackResolutions.scales[ level ];
			levelt = sourceStackResolutions.levelts[ level ];
		}

		@Override
		public int getLevel()
		{
			return level;
		}

		@Override
		public int[] getR()
		{
			return resolution;
		}

		@Override
		public double[] getS()
		{
			return scale;
		}

		@Override
		public AffineTransform3D getLevelTransform()
		{
			return levelt;
		}

		@Override
		public RandomAccessibleInterval< T > getImage()
		{
			return source.getSource( timepoint, level );
		}

		@Override
		public T getType()
		{
			return source.getType();
		}

		@Override
		public boolean equals( final Object o )
		{
			if ( this == o )
				return true;
			if ( o == null || getClass() != o.getClass() )
				return false;

			final ResolutionLevel3DImp< ? > that = ( ResolutionLevel3DImp< ? > ) o;

			if ( timepoint != that.timepoint )
				return false;
			if ( level != that.level )
				return false;
			return source.equals( that.source );
		}

		@Override
		public int hashCode()
		{
			int result = level;
			result = 31 * result + timepoint;
			result = 31 * result + source.hashCode();
			return result;
		}
	}

	/**
	 * Caches resolution level parameters extracted from Sources
	 */
	private static final Map< Source< ? >, SourceStackResolutions > sourceStackResolutions = new WeakHashMap<>();

	/**
	 * Extract resolution level parameters from a Source
	 */
	static class SourceStackResolutions
	{
		final int[][] resolutions;

		final double[][] scales;

		final AffineTransform3D[] levelts;

		SourceStackResolutions( final Source< ? > source, final int timepoint )
		{
			final int numLevels = source.getNumMipmapLevels();

			resolutions = new int[ numLevels ][];
			scales = new double[ numLevels ][];
			levelts = new AffineTransform3D[ numLevels ];

			resolutions[ 0 ] = new int[] { 1, 1, 1 };
			scales[ 0 ] = new double[] { 1, 1, 1 };
			levelts[ 0 ] = new AffineTransform3D();

			final AffineTransform3D sourceTransform = new AffineTransform3D();
			source.getSourceTransform( timepoint, 0, sourceTransform );
			for ( int level = 1; level < numLevels; level++ )
			{
				final int[] resolution = resolutions[ level ] = new int[ 3 ];
				final double[] scale = scales[ level ] = new double[ 3 ];
				final AffineTransform3D levelt = levelts[ level ] = new AffineTransform3D();

				final AffineTransform3D levelTransform = new AffineTransform3D();
				source.getSourceTransform( timepoint, level, levelTransform );
				levelTransform.preConcatenate( sourceTransform.inverse() );
				for ( int d = 0; d < 3; ++d )
				{
					resolution[ d ] = ( int ) Math.round( levelTransform.get( d, d ) );
					scale[ d ] = 1.0 / resolution[ d ];
					levelt.set( resolution[ d ], d, d );
					levelt.set( 0.5 * ( resolution[ d ] - 1 ), d, 3 );
				}

				// TODO: sanity check: levelt * levelTransform^-1 ~= identity
			}
		}
	}
}
