package tpietzsch.multires;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.realtransform.AffineTransform3D;

import bdv.ViewerImgLoader;
import bdv.ViewerSetupImgLoader;
import bdv.cache.CacheControl;
import mpicbg.spim.data.generic.AbstractSpimData;
import mpicbg.spim.data.generic.sequence.BasicMultiResolutionSetupImgLoader;
import mpicbg.spim.data.generic.sequence.BasicSetupImgLoader;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.TimePoint;

public class SpimDataStacks
{
	private final AbstractSpimData< ? > spimData;

	private final ViewRegistrations registrations;

	private final CacheControl cacheControl;

	private final List< ? extends BasicViewSetup > setups;

	private final List< TimePoint > timepoints;

	public SpimDataStacks( final AbstractSpimData< ? > spimData )
	{
		this.spimData = spimData;
		registrations = spimData.getViewRegistrations();

		setups = spimData.getSequenceDescription().getViewSetupsOrdered();
		timepoints = spimData.getSequenceDescription().getTimePoints().getTimePointsOrdered();

		cacheControl = ( ( ViewerImgLoader ) spimData.getSequenceDescription().getImgLoader() ).getCacheControl();
	}

	public int timepointId( final int timepointIndex )
	{
		return timepoints.get( timepointIndex ).getId();
	}

	public int setupId( final int setupIndex )
	{
		return setups.get( setupIndex ).getId();
	}

	public MultiResolutionStack3D< ? > getStack( final int timepointId, final int setupId, final boolean volatil )
	{
		final AffineTransform3D model = registrations.getViewRegistration( timepointId, setupId ).getModel();

		final BasicSetupImgLoader< ? > sil = spimData.getSequenceDescription().getImgLoader().getSetupImgLoader( setupId );
		int numMipmapLevels = 1;
		int[][] resolutions = { { 1, 1, 1 } };
		if ( sil instanceof BasicMultiResolutionSetupImgLoader )
		{
			final BasicMultiResolutionSetupImgLoader< ? > msil = ( BasicMultiResolutionSetupImgLoader< ? > ) sil;
			numMipmapLevels = msil.numMipmapLevels();
			resolutions = new int[ numMipmapLevels ][ 3 ];
			for ( int level = 0; level < numMipmapLevels; level++ )
				for ( int d = 0; d < 3; ++d )
					resolutions[ level ][ d ] = ( int ) msil.getMipmapResolutions()[ level ][ d ];
		}

		Object type;
		final RandomAccessibleInterval< ? >[] rais = new RandomAccessibleInterval[ numMipmapLevels ];
		if ( sil instanceof ViewerSetupImgLoader )
		{
			final ViewerSetupImgLoader< ?, ? > vsil = ( ViewerSetupImgLoader< ?, ? > ) sil;
			type = volatil ? vsil.getVolatileImageType() : vsil.getImageType();
			for ( int level = 0; level < numMipmapLevels; level++ )
				rais[ level ] = vsil.getVolatileImage( timepointId, level );
		}
		else
		{
			type = sil.getImageType();
			if ( volatil && ! ( type instanceof Volatile ) )
				throw new IllegalArgumentException();
			if ( sil instanceof BasicMultiResolutionSetupImgLoader )
			{
				final BasicMultiResolutionSetupImgLoader< ? > msil = ( BasicMultiResolutionSetupImgLoader< ? > ) sil;
				for ( int level = 0; level < numMipmapLevels; level++ )
					rais[ level ] = msil.getImage( timepointId, level );
			}
			else
				rais[ 0 ] = sil.getImage( timepointId );
		}

		final ResolutionLevel3DImp< ? >[] resolutionLevels = new ResolutionLevel3DImp[ numMipmapLevels ];
		for ( int level = 0; level < numMipmapLevels; level++ )
			resolutionLevels[ level ] = new ResolutionLevel3DImp( level, timepointId, setupId, spimData, resolutions[ level ], rais[ level], type );

		return new MultiResolutionStack3DImp( timepointId, setupId, spimData, model, resolutionLevels, type );
	}

	public CacheControl getCacheControl()
	{
		return cacheControl;
	}

	static class MultiResolutionStack3DImp< T > implements MultiResolutionStack3D< T >
	{
		private final int timepointId;

		private final int setupId;

		private final AbstractSpimData< ? > spimData;

		private final AffineTransform3D sourceTransform;

		private final ArrayList< ResolutionLevel3DImp< T > > resolutions;

		private final T type;

		public MultiResolutionStack3DImp(
				final int timepointId,
				final int setupId,
				final AbstractSpimData< ? > spimData,
				final AffineTransform3D sourceTransform,
				final ResolutionLevel3DImp< T >[] resolutions,
				final T type )
		{
			this.timepointId = timepointId;
			this.setupId = setupId;
			this.spimData = spimData;
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
			if ( setupId != that.setupId )
				return false;
			return spimData.equals( that.spimData );
		}

		@Override
		public int hashCode()
		{
			int result = timepointId;
			result = 31 * result + setupId;
			result = 31 * result + spimData.hashCode();
			return result;
		}
	}

	static class ResolutionLevel3DImp< T > implements ResolutionLevel3D< T >
	{
		private final int level;

		private final int timepointId;

		private final int setupId;

		private final AbstractSpimData< ? > spimData;

		private final int[] resolution;

		private final RandomAccessibleInterval< T > rai;

		private final T type;

		private final double[] scale;

		private final AffineTransform3D levelt;

		public ResolutionLevel3DImp(
				final int level,
				final int timepointId,
				final int setupId,
				final AbstractSpimData< ? > spimData,
				final int[] resolution,
				final RandomAccessibleInterval< T > rai,
				final T type )
		{
			this.level = level;
			this.timepointId = timepointId;
			this.setupId = setupId;
			this.spimData = spimData;
			this.resolution = resolution;
			this.rai = rai;
			this.type = type;

			scale = new double[ 3 ];
			levelt = new AffineTransform3D();
			for ( int d = 0; d < 3; ++d )
			{
				scale[ d ] = 1.0  / resolution[ d ];
				levelt.set( resolution[ d ], d, d );
				levelt.set( 0.5 * ( resolution[ d ] - 1 ), d, 3 );
			}
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
			if ( setupId != that.setupId )
				return false;
			return spimData.equals( that.spimData );
		}

		@Override
		public int hashCode()
		{
			int result = level;
			result = 31 * result + timepointId;
			result = 31 * result + setupId;
			result = 31 * result + spimData.hashCode();
			return result;
		}
	}
}
