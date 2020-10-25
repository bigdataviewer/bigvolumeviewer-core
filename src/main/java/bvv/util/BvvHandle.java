package bvv.util;

import bdv.cache.CacheControl.CacheControls;
import bdv.tools.InitializeViewerState;
import bdv.tools.brightness.ConverterSetup;
import bdv.tools.brightness.MinMaxGroup;
import bdv.tools.brightness.SetupAssignments;
import bdv.util.BdvFunctions;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.TimePointListener;
import bdv.viewer.TransformListener;
import bdv.viewer.VisibilityAndGrouping.UpdateListener;
import bdv.viewer.state.ViewerState;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ui.behaviour.util.InputActionBindings;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;
import tpietzsch.example2.VolumeViewerPanel;
import tpietzsch.frombdv.ManualTransformationEditor;

/**
 * Represents a BigVolumeViewer frame or panel and can be used to get to the bvv
 * internals.
 *
 * @author Tobias Pietzsch &lt;tobias.pietzsch@gmail.com&gt;
 */
public abstract class BvvHandle implements Bvv
{
	protected VolumeViewerPanel viewer;

	protected SetupAssignments setupAssignments;

	protected final ArrayList< BvvSource > bvvSources;

	protected final BvvOptions bvvOptions;

	protected boolean hasPlaceHolderSources;

	protected final int origNumTimepoints;

	protected CacheControls cacheControls;

	public BvvHandle( final BvvOptions options )
	{
		bvvOptions = options;
		bvvSources = new ArrayList<>();
		origNumTimepoints = 1;
	}

	@Override
	public BvvHandle getBvvHandle()
	{
		return this;
	}

	public VolumeViewerPanel getViewerPanel()
	{
		return viewer;
	}

	public SetupAssignments getSetupAssignments()
	{
		return setupAssignments;
	}

	CacheControls getCacheControls()
	{
		return cacheControls;
	}

	int getUnusedSetupId()
	{
		return BdvFunctions.getUnusedSetupId( setupAssignments );
	}

	@Override
	public abstract void close();

	public abstract ManualTransformationEditor getManualTransformEditor();

	public abstract InputActionBindings getKeybindings();

	public abstract TriggerBehaviourBindings getTriggerbindings();

	abstract boolean createViewer(
			final List< ? extends ConverterSetup > converterSetups,
			final List< ? extends SourceAndConverter< ? > > sources,
			final int numTimepoints );

	void add(
			final List< ? extends ConverterSetup > converterSetups,
			final List< ? extends SourceAndConverter< ? > > sources,
			final int numTimepoints )
	{
		final boolean initTransform;
		if ( viewer == null )
		{
			initTransform = createViewer( converterSetups, sources, numTimepoints );
		}
		else
		{
			initTransform = ( viewer.getState().numSources() == 0 ) && !sources.isEmpty();

			if ( converterSetups != null )
			{
				for ( final ConverterSetup setup : converterSetups )
				{
					setupAssignments.addSetup( setup );
					setup.setViewer( viewer );
				}

				final int g = setupAssignments.getMinMaxGroups().size() - 1;
				final MinMaxGroup group = setupAssignments.getMinMaxGroups().get( g );
				for ( final ConverterSetup setup : converterSetups )
					setupAssignments.moveSetupToGroup( setup, group );
			}

			if ( sources != null )
			{
				for ( int i = 0; i < sources.size(); i++ )
					viewer.addSource( sources.get( i ), converterSetups.get( i ) );
			}
		}

		if ( initTransform )
		{
			synchronized ( this )
			{
				initTransformPending = true;
				tryInitTransform();
			}
		}

		updateHasPlaceHolderSources();
		updateNumTimepoints();
	}

	private boolean initTransformPending;

	protected synchronized void tryInitTransform()
	{
		if ( viewer.getDisplay().getWidth() <= 0 || viewer.getDisplay().getHeight() <= 0 )
			return;

		if ( initTransformPending )
		{
			initTransformPending = false;

			final Dimension dim = viewer.getDisplay().getSize();
			final ViewerState state = viewer.getState();
			final AffineTransform3D viewerTransform = InitializeViewerState.initTransform( dim.width, dim.height, false, state );
			viewer.setCurrentViewerTransform( viewerTransform );
		}
	}

	void remove(
			final List< ? extends ConverterSetup > converterSetups,
			final List< ? extends SourceAndConverter< ? > > sources,
			final List< TransformListener< AffineTransform3D > > transformListeners,
			final List< TimePointListener > timepointListeners,
			final List< UpdateListener > visibilityUpdateListeners )
	{
		if ( viewer == null )
			return;

		if ( converterSetups != null )
			for ( final ConverterSetup setup : converterSetups )
				setupAssignments.removeSetup( setup );

		if ( transformListeners != null )
			for ( final TransformListener< AffineTransform3D > l : transformListeners )
				viewer.removeTransformListener( l );

		if ( timepointListeners != null )
			for ( final TimePointListener l : timepointListeners )
				viewer.removeTimePointListener( l );

		if ( visibilityUpdateListeners != null )
			for ( final UpdateListener l : visibilityUpdateListeners )
				viewer.getVisibilityAndGrouping().removeUpdateListener( l );

		if ( sources != null )
			for ( final SourceAndConverter< ? > soc : sources )
				viewer.removeSource( soc.getSpimSource() );
	}

	void addBvvSource( final BvvSource bvvSource )
	{
		bvvSources.add( bvvSource );
		updateHasPlaceHolderSources();
		updateNumTimepoints();
	}

	void removeBvvSource( final BvvSource bvvSource )
	{
		bvvSources.remove( bvvSource );
		updateHasPlaceHolderSources();
		updateNumTimepoints();
	}

	void updateHasPlaceHolderSources()
	{
		for ( final BvvSource s : bvvSources )
			if ( s.isPlaceHolderSource() )
			{
				hasPlaceHolderSources = true;
				return;
			}
		hasPlaceHolderSources = false;
	}

	void updateNumTimepoints()
	{
		int numTimepoints = origNumTimepoints;
		for ( final BvvSource s : bvvSources )
			numTimepoints = Math.max( numTimepoints, s.getNumTimepoints() );
		if ( viewer != null )
			viewer.setNumTimepoints( numTimepoints );
	}
}
