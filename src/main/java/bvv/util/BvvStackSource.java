package bvv.util;

import bdv.tools.brightness.ConverterSetup;
import bdv.tools.brightness.MinMaxGroup;
import bdv.tools.brightness.SetupAssignments;
import bdv.util.BdvHandle;
import bdv.util.BdvSource;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.state.SourceState;
import bdv.viewer.state.ViewerState;
import java.util.HashSet;
import java.util.List;
import net.imglib2.type.numeric.ARGBType;
import tpietzsch.multires.SourceStacks;

public class BvvStackSource< T > extends BvvSource
{
	private final T type;

	private final List< ConverterSetup > converterSetups;

	private final List< SourceAndConverter< T > > sources;

	protected BvvStackSource(
			final BvvHandle bvv,
			final int numTimepoints,
			final T type,
			final List< ConverterSetup > converterSetups,
			final List< SourceAndConverter< T > > sources )
	{
		super( bvv, numTimepoints );
		this.type = type;
		this.converterSetups = converterSetups;
		this.sources = sources;
	}

	@Override
	public void removeFromBdv()
	{
		getBvvHandle().remove( converterSetups, sources, null, null, null );
		getBvvHandle().removeBvvSource( this );
		setBdvHandle( null );
	}

	@Override
	protected boolean isPlaceHolderSource()
	{
		return false;
	}

	@Override
	public void setColor( final ARGBType color )
	{
		for ( final ConverterSetup setup : converterSetups )
			setup.setColor( color );
	}

	@Override
	public void setDisplayRange( final double min, final double max )
	{
		final HashSet< MinMaxGroup > groups = new HashSet<>();
		final SetupAssignments sa = getBvvHandle().getSetupAssignments();
		for ( final ConverterSetup setup : converterSetups )
			groups.add( sa.getMinMaxGroup( setup ) );
		for ( final MinMaxGroup group : groups )
		{
			group.getMinBoundedValue().setCurrentValue( min );
			group.getMaxBoundedValue().setCurrentValue( max );
		}
	}

	@Override
	public void setDisplayRangeBounds( final double min, final double max )
	{
		final HashSet< MinMaxGroup > groups = new HashSet<>();
		final SetupAssignments sa = getBvvHandle().getSetupAssignments();
		for ( final ConverterSetup setup : converterSetups )
			groups.add( sa.getMinMaxGroup( setup ) );
		for ( final MinMaxGroup group : groups )
			group.setRange( min, max );
	}

	@Override
	public void setCurrent()
	{
		getBvvHandle().getViewerPanel().getVisibilityAndGrouping().setCurrentSource( sources.get( 0 ).getSpimSource() );
	}

	@Override
	public boolean isCurrent()
	{
		final ViewerState state = getBvvHandle().getViewerPanel().getState();
		final List< SourceState< ? > > ss = state.getSources();
		final int i = state.getCurrentSource();
		if ( i >= 0 && i < ss.size() )
		{
			final Source< ? > spimSource = ss.get( i ).getSpimSource();
			for ( final SourceAndConverter< T > source : sources )
				if ( spimSource.equals( source.getSpimSource() ) )
					return true;
		}
		return false;
	}

	@Override
	public void setActive( final boolean isActive )
	{
		for ( final SourceAndConverter< T > source : sources )
			getBvvHandle().getViewerPanel().getVisibilityAndGrouping().setSourceActive( source.getSpimSource(), isActive );
	}

//	public T getType()
//	{
//		return type;
//	}

	public List< ConverterSetup > getConverterSetups()
	{
		return converterSetups;
	}

	public List< SourceAndConverter< T > > getSources()
	{
		return sources;
	}

	public void invalidate()
	{
		for ( final SourceAndConverter< T > source : sources )
			SourceStacks.invalidate( source.getSpimSource() );

		final BvvHandle bvv = getBvvHandle();
		if ( bvv != null )
			bvv.getViewerPanel().requestRepaint();
	}
}
