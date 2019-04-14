package tpietzsch.multires;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.volatiles.VolatileUnsignedShortType;

import bdv.cache.CacheControl;
import bdv.util.RandomAccessibleIntervalMipmapSource;

public class SourceListStacks implements Stacks
{
	private final CacheControl cacheControl;

	private final List< RandomAccessibleIntervalMipmapSource< VolatileUnsignedShortType > > sources;

	public SourceListStacks( final List< RandomAccessibleIntervalMipmapSource< VolatileUnsignedShortType > > sources )
	{
		this.sources = sources;
		cacheControl = new CacheControl.Dummy(); // TODO
	}

	public void resTEST()
	{
		final int timepointId = 0;
		final int setupId = 0;

		final RandomAccessibleIntervalMipmapSource< VolatileUnsignedShortType > source = sources.get( setupId );
		final int numMipmapLevels = source.getNumMipmapLevels();

		final RandomAccessibleInterval< ? >[] rais = new RandomAccessibleInterval[ numMipmapLevels ];
		for ( int level = 0; level < numMipmapLevels; level++ )
			rais[ level ] = source.getSource( timepointId, level );

		final AffineTransform3D baseTransform = new AffineTransform3D();
		source.getSourceTransform( timepointId, 0, baseTransform );

		final int[][] resolutions = new int[ numMipmapLevels ][ 3 ];
		for ( int level = 0; level < numMipmapLevels; level++ )
		{
			final AffineTransform3D levelTransform = new AffineTransform3D();
			source.getSourceTransform( timepointId, level, levelTransform );
			levelTransform.concatenate( baseTransform.inverse() );
			for ( int d = 0; d < 3; ++d )
				resolutions[ level ][ d ] = ( int ) levelTransform.get( d, d  );
		}
	}

	@Override
	@SuppressWarnings( { "rawtypes", "unchecked" } )
	public MultiResolutionStack3D< ? > getStack( final int timepointId, final int setupId, final boolean volatil )
	{
		final RandomAccessibleIntervalMipmapSource< VolatileUnsignedShortType > source = sources.get( setupId );
		final int numMipmapLevels = source.getNumMipmapLevels();

		final RandomAccessibleInterval< ? >[] rais = new RandomAccessibleInterval[ numMipmapLevels ];
		for ( int level = 0; level < numMipmapLevels; level++ )
			rais[ level ] = source.getSource( timepointId, level );

		final AffineTransform3D baseTransform = new AffineTransform3D();
		source.getSourceTransform( timepointId, 0, baseTransform );

		final int[][] resolutions = new int[ numMipmapLevels ][ 3 ];
		final AffineTransform3D[] levelTransforms = new AffineTransform3D[ numMipmapLevels ];
		for ( int level = 0; level < numMipmapLevels; level++ )
		{
			final AffineTransform3D levelTransform = new AffineTransform3D();
			levelTransforms[ level ] = levelTransform;
			source.getSourceTransform( timepointId, level, levelTransform );
			levelTransform.concatenate( baseTransform.inverse() );
			for ( int d = 0; d < 3; ++d )
				resolutions[ level ][ d ] = ( int ) levelTransform.get( d, d  );
		}

		final ResolutionLevel3DImp< ? >[] resolutionLevels = new ResolutionLevel3DImp[ numMipmapLevels ];
		for ( int level = 0; level < numMipmapLevels; level++ )
			resolutionLevels[ level ] = new ResolutionLevel3DImp(
					level, timepointId, setupId, resolutions[ level ], levelTransforms[ level ], rais[ level ], source.getType() );

		return new MultiResolutionStack3DImp( timepointId, setupId, baseTransform, resolutionLevels, source.getType() );
	}

	@Override
	public CacheControl getCacheControl()
	{
		return cacheControl;
	}

	static class MultiResolutionStack3DImp< T > implements MultiResolutionStack3D< T >
	{
		private final int timepointId;

		private final int setupId;

		private final AffineTransform3D sourceTransform;

		private final ArrayList< ResolutionLevel3DImp< T > > resolutions;

		private final T type;

		public MultiResolutionStack3DImp(
				final int timepointId,
				final int setupId,
				final AffineTransform3D sourceTransform,
				final ResolutionLevel3DImp< T >[] resolutions,
				final T type )
		{
			this.timepointId = timepointId;
			this.setupId = setupId;
			this.sourceTransform = sourceTransform;
			this.resolutions = new ArrayList<>( Arrays.asList( resolutions ) );
			this.type = type;
		}

		@Override
		public AffineTransform3D getSourceTransform()
		{
			return sourceTransform;
		}

		@Override
		public List< ResolutionLevel3DImp< T > > resolutions()
		{
			return resolutions;
		}

		@Override
		public T getType()
		{
			return type;
		}

		@Override
		public boolean equals( final Object o )
		{
			if ( this == o )
				return true;
			if ( o == null || getClass() != o.getClass() )
				return false;

			final MultiResolutionStack3DImp< ? > that = ( MultiResolutionStack3DImp< ? > ) o;

			if ( timepointId != that.timepointId )
				return false;
			return setupId == that.setupId;
		}

		@Override
		public int hashCode()
		{
			int result = timepointId;
			result = 31 * result + setupId;
			return result;
		}
	}

	static class ResolutionLevel3DImp< T > implements ResolutionLevel3D< T >
	{
		private final int level;

		private final int timepointId;

		private final int setupId;

		private final int[] resolution;

		private final RandomAccessibleInterval< T > rai;

		private final T type;

		private final double[] scale;

		private final AffineTransform3D levelt;

		public ResolutionLevel3DImp(
				final int level,
				final int timepointId,
				final int setupId,
				final int[] resolution,
				final AffineTransform3D levelt,
				final RandomAccessibleInterval< T > rai,
				final T type )
		{
			this.level = level;
			this.timepointId = timepointId;
			this.setupId = setupId;
			this.resolution = resolution;
			this.levelt = levelt;
			this.rai = rai;
			this.type = type;

			scale = new double[ 3 ];
			for ( int d = 0; d < 3; ++d )
				scale[ d ] = 1.0  / resolution[ d ];
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
			return rai;
		}

		@Override
		public T getType()
		{
			return type;
		}

		@Override
		public boolean equals( final Object o )
		{
			if ( this == o )
				return true;
			if ( o == null || getClass() != o.getClass() )
				return false;

			final ResolutionLevel3DImp< ? > that = ( ResolutionLevel3DImp< ? > ) o;

			if ( level != that.level )
				return false;
			if ( timepointId != that.timepointId )
				return false;
			return setupId == that.setupId;
		}

		@Override
		public int hashCode()
		{
			int result = level;
			result = 31 * result + timepointId;
			result = 31 * result + setupId;
			return result;
		}
	}
}
