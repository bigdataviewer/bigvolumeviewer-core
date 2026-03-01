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
package bvv.core;

import static bdv.ui.UIUtils.TextPosition.TOP_RIGHT;
import static bdv.viewer.DisplayMode.SINGLE;
import static bdv.viewer.Interpolation.NEARESTNEIGHBOR;
import static bvv.core.render.VolumeRenderer.RepaintType.FULL;
import static bvv.core.render.VolumeRenderer.RepaintType.LOAD;
import static bvv.core.render.VolumeRenderer.RepaintType.NONE;
import static bvv.core.render.VolumeRenderer.RepaintType.SCENE;
import static com.jogamp.opengl.GL.GL_DEPTH_TEST;
import static com.jogamp.opengl.GL.GL_LESS;
import static com.jogamp.opengl.GL.GL_RGB8;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import javax.swing.DefaultBoundedRangeModel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import org.jdom2.Element;
import org.joml.Matrix4f;
import org.scijava.listeners.Listeners;
import org.scijava.ui.behaviour.io.InputTriggerConfig;

import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;

import bdv.TransformEventHandler;
import bdv.TransformState;
import bdv.cache.CacheControl;
import bdv.tools.brightness.ConverterSetup;
import bdv.ui.UIUtils;
import bdv.util.Prefs;
import bdv.viewer.AbstractViewerPanel;
import bdv.viewer.BasicViewerState;
import bdv.viewer.ConverterSetups;
import bdv.viewer.DisplayMode;
import bdv.viewer.NavigationActions;
import bdv.viewer.OverlayRenderer;
import bdv.viewer.Source;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.SourceGroup;
import bdv.viewer.SynchronizedViewerState;
import bdv.viewer.ViewerState;
import bdv.viewer.TimePointListener;
import bdv.viewer.TransformListener;
import bdv.viewer.ViewerPanel;
import bdv.viewer.ViewerStateChange;
import bdv.viewer.ViewerStateChangeListener;
import bdv.viewer.VisibilityAndGrouping;
import bdv.viewer.animate.AbstractTransformAnimator;
import bdv.viewer.animate.MessageOverlayAnimator;
import bdv.viewer.animate.OverlayAnimator;
import bdv.viewer.animate.TextOverlayAnimator;
import bdv.viewer.animate.TextOverlayAnimator.TextPosition;
import bdv.viewer.overlay.MultiBoxOverlayRenderer;
import bdv.viewer.overlay.ScaleBarOverlayRenderer;
import bdv.viewer.overlay.SourceInfoOverlayRenderer;
import bdv.viewer.render.PainterThread;
import bdv.viewer.state.XmlIoViewerState;
import bvv.core.multires.SourceStacks;
import bvv.core.multires.Stack3D;
import bvv.core.offscreen.OffScreenFrameBuffer;
import bvv.core.offscreen.OffScreenFrameBufferWithDepth;
import bvv.core.render.RenderData;
import bvv.core.render.VolumeRenderer;
import bvv.core.render.VolumeRenderer.RepaintType;
import bvv.core.util.MatrixMath;
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.RealPositionable;
import net.imglib2.cache.iotiming.CacheIoTiming;
import net.imglib2.realtransform.AffineTransform3D;

public class VolumeViewerPanel
		extends AbstractViewerPanel
		implements OverlayRenderer, PainterThread.Paintable, ViewerStateChangeListener
{
	protected final CacheControl cacheControl;

	public interface RenderScene
	{
		void render( final GL3 gl, RenderData data );
	}

	public void setRenderScene( final RenderScene renderScene )
	{
		this.renderScene = renderScene;
	}

	private RenderScene renderScene;

	private class Repaint
	{
		private RepaintType type;

		protected Repaint()
		{
			this.type = FULL;
		}

		protected synchronized void request( final RepaintType type )
		{
			if ( this.type.ordinal() < type.ordinal() )
			{
				this.type = type;
				painterThread.requestRepaint();
			}
		}

		protected synchronized RepaintType getAndClear()
		{
			final RepaintType t = type;
			type = NONE;
			return t;
		}
	}

	private final Repaint repaint = new Repaint();

	protected final OffScreenFrameBufferWithDepth sceneBuf;

	protected final OffScreenFrameBuffer offscreen;

	// TODO: should be settable
	private final long[] iobudget = new long[] { 100l * 1000000l,  10l * 1000000l };

	protected final int maxRenderMillis;

	/**
	 * Currently rendered state (visible sources, transformation, timepoint, etc.)
	 */
	private final SynchronizedViewerState state;

	/**
	 * Legacy wrapper around {@code state} to support deprecated API
	 */
	private final bdv.viewer.state.ViewerState deprecatedState;

	private final ConverterSetups setups;

	/**
	 * Renders the current state to gl context.
	 */
	protected final VolumeRenderer renderer;

	/**
	 * Overlay navigation boxes.
	 */
	// TODO: move to specialized class
	private final MultiBoxOverlayRenderer multiBoxOverlayRenderer;

	private final TransformEventHandler transformEventHandler;

	/**
	 * Overlay current source name and current timepoint.
	 */
	// TODO: move to specialized class
	private final SourceInfoOverlayRenderer sourceInfoOverlayRenderer;

	/**
	 * TODO
	 */
	private final ScaleBarOverlayRenderer scaleBarOverlayRenderer;

	/**
	 * Canvas used for displaying the rendered {@code image} and overlays.
	 */
	protected final InteractiveGLDisplayCanvas< ? > display;

	protected final JSlider sliderTime;

	private boolean blockSliderTimeEvents;

	/**
	 * A {@link ThreadGroup} for (only) the threads used by this
	 * {@link ViewerPanel}, that is, at the moment only {@link #painterThread}.
	 */
	protected ThreadGroup threadGroup;

	/**
	 * Thread that triggers repainting of the display.
	 */
	protected final PainterThread painterThread;

	/**
	 * Manages visibility and currentness of sources and groups, as well as
	 * grouping of sources, and display mode.
	 */
	protected final VisibilityAndGrouping visibilityAndGrouping;

	/**
	 * These listeners will be notified about changes to the
	 * viewer-transform. This is done <em>before</em> calling
	 * {@link #requestRepaint()} so listeners have the chance to interfere.
	 */
	private final Listeners.List< TransformListener< AffineTransform3D > > transformListeners;

	/**
	 * These listeners will be notified about the transform that is associated
	 * to the currently rendered image. This is intended for example for
	 * {@link OverlayRenderer}s that need to exactly match the transform of
	 * their overlaid content to the transform of the image.
	 */
	private final Listeners.List< TransformListener< AffineTransform3D > > renderTransformListeners;

	/**
	 * These listeners will be notified about changes to the current timepoint
	 * {@link ViewerState#getCurrentTimepoint()}. This is done <em>before</em>
	 * calling {@link #requestRepaint()} so listeners have the chance to
	 * interfere.
	 */
	private final Listeners.List< TimePointListener > timePointListeners;

	/**
	 * Current animator for viewer transform, or null. This is for example used
	 * to make smooth transitions when {@link #align(AlignPlane) aligning to
	 * orthogonal planes}.
	 */
	private AtomicReference<AbstractTransformAnimator> currentAnimator = new AtomicReference<>();

	/**
	 * A list of currently incomplete (see {@link OverlayAnimator#isComplete()})
	 * animators. Initially, this contains a {@link TextOverlayAnimator} showing
	 * the "press F1 for help" message.
	 */
	private final ArrayList< OverlayAnimator > overlayAnimators;

	/**
	 * Fade-out overlay of recent messages. See {@link #showMessage(String)}.
	 */
	protected final MessageOverlayAnimator msgOverlay;

	protected final VolumeViewerOptions.Values options;

	/**
	 * @param sources
	 *            the {@link SourceAndConverter sources} to display.
	 * @param numTimepoints
	 *            number of available timepoints.
	 * @param cacheControl
	 *            to control IO budgeting and fetcher queue.
	 * @param optional
	 *            optional parameters. See {@link VolumeViewerOptions}.
	 */
	public VolumeViewerPanel(
			final List< SourceAndConverter< ? > > sources,
			final int numTimepoints,
			final CacheControl cacheControl,
			final VolumeViewerOptions optional )
	{
		super( new BorderLayout(), false );

		options = optional.values;
		final boolean useGLJPanel = true; // TODO should come from VolumeViewerOptions

		this.cacheControl = cacheControl;

		state = setupState( sources, numTimepoints, options.getNumSourceGroups() );
		deprecatedState = new bdv.viewer.state.ViewerState( state );

		multiBoxOverlayRenderer = new MultiBoxOverlayRenderer();
		sourceInfoOverlayRenderer = new SourceInfoOverlayRenderer();
		scaleBarOverlayRenderer = new ScaleBarOverlayRenderer();

		setups = new ConverterSetups( state );
		setups.listeners().add( s -> requestRepaint() );

		threadGroup = new ThreadGroup( this.toString() );
		painterThread = new PainterThread( threadGroup, this );
		painterThread.setDaemon( true );
		transformEventHandler = options.getTransformEventHandlerFactory().create(
				TransformState.from( state()::getViewerTransform, state()::setViewerTransform ) );

		final int renderWidth = options.getRenderWidth();
		final int renderHeight = options.getRenderHeight();
		sceneBuf = new OffScreenFrameBufferWithDepth( renderWidth, renderHeight, GL_RGB8 );
		offscreen = new OffScreenFrameBuffer( renderWidth, renderHeight, GL_RGB8, false, useGLJPanel );
		maxRenderMillis = options.getMaxRenderMillis();

		renderer = new VolumeRenderer(
				renderWidth,
				renderHeight,
				options.getDitherWidth(),
				getDitherStep( options.getDitherWidth() ),
				options.getNumDitherSamples(),
				options.getCacheBlockSize(),
				options.getMaxCacheSizeInMB() );

		dCam = options.getDCam();
		dClipNear = options.getDClipNear();
		dClipFar = options.getDClipFar();
		screenWidth = options.getWidth();
		screenHeight = options.getHeight();

		maxAllowedStepInVoxels = options.getMaxAllowedStepInVoxels();

		display = useGLJPanel
				? InteractiveGLDisplayCanvas.createGLJPanel( options.getWidth(), options.getHeight() )
				: InteractiveGLDisplayCanvas.createGLCanvas( options.getWidth(), options.getHeight() );
		display.setTransformEventHandler( transformEventHandler );
		display.addGLEventListener( glEventListener );
		display.overlays().add( this );

		display.addHandler( mouseCoordinates );

		sliderTime = new JSlider( SwingConstants.HORIZONTAL, 0, numTimepoints - 1, 0 );
		sliderTime.addChangeListener( e -> {
			if ( !blockSliderTimeEvents )
				setTimepoint( sliderTime.getValue() );
		} );

		add( display.getComponent(), BorderLayout.CENTER );
		if ( numTimepoints > 1 )
			add( sliderTime, BorderLayout.SOUTH );
		setFocusable( false );

		visibilityAndGrouping = new VisibilityAndGrouping( deprecatedState );

		transformListeners = new Listeners.SynchronizedList<>( l -> l.transformChanged( state().getViewerTransform() ) );
		renderTransformListeners = new Listeners.SynchronizedList<>( l -> {
			if ( renderData != null )
				l.transformChanged( renderData.getRenderTransformWorldToScreen() );
		} );
		timePointListeners = new Listeners.SynchronizedList<>( l -> l.timePointChanged( state.getCurrentTimepoint() ) );

		msgOverlay = options.getMsgOverlay();

		overlayAnimators = new ArrayList<>();
		overlayAnimators.add( msgOverlay );
		overlayAnimators.add( new TextOverlayAnimator( "Press <F1> for help.", 3000, TextPosition.CENTER ) );

		display.getComponent().addComponentListener( new ComponentAdapter()
		{
			@Override
			public void componentResized( final ComponentEvent e )
			{
				requestRepaint();
				display.getComponent().removeComponentListener( this );
			}
		} );

		state.changeListeners().add( this );

		painterThread.start();
	}

	/**
	 * Initialize ViewerState with the given {@code sources} and {@code numTimepoints}.
	 * Set up {@code numGroups} SourceGroups named "group 1", "group 2", etc. Add the
	 * first source to the first group, the second source to the second group etc.
	 *
	 * TODO: Setting up groups like this doesn't make a lot of sense. This just
	 *   replicates legacy behaviour. The remaining thing that stands in the way of
	 *   removing it is ViewerState serialization, which assumes that there are always 10
	 *   groups ... m(
	 */
	private static SynchronizedViewerState setupState( final List< SourceAndConverter< ? > > sources, final int numTimepoints, final int numGroups )
	{
		final SynchronizedViewerState state = new SynchronizedViewerState( new BasicViewerState() );
		state.addSources( sources );
		state.setSourcesActive( sources, true );
		for ( int i = 0; i < numGroups; ++i ) {
			final SourceGroup handle = new SourceGroup();
			state.addGroup( handle );
			state.setGroupName( handle,  "group " + ( i + 1 ) );
			state.setGroupActive( handle, true );
			if ( i < sources.size() )
				state.addSourceToGroup( sources.get( i ), handle );
		}
		state.setNumTimepoints( numTimepoints );
		state.setInterpolation( NEARESTNEIGHBOR );
		state.setDisplayMode( SINGLE );
		state.setCurrentSource( sources.isEmpty() ? null : sources.get( 0 ) );
		state.setCurrentGroup( numGroups <= 0 ? null : state.getGroups().get( 0 ) );

		return state;
	}

	/**
	 * @deprecated Modify {@link #state()} directly
	 */
	@Deprecated
	public void addSource( final SourceAndConverter< ? > sourceAndConverter )
	{
		state.addSource( sourceAndConverter );
		state.setSourceActive( sourceAndConverter, true );
	}

	/**
	 * @deprecated Modify {@link #state()} directly
	 */
	@Deprecated
	public void addSources( final Collection< SourceAndConverter< ? > > sourceAndConverter )
	{
		state.addSources( sourceAndConverter );
	}

	// helper for deprecated methods taking Source<?>
	@Deprecated
	private SourceAndConverter< ? > soc( final Source< ? > source )
	{
		for ( final SourceAndConverter< ? > soc : state.getSources() )
			if ( soc.getSpimSource() == source )
				return soc;
		return null;
	}

	/**
	 * @deprecated Modify {@link #state()} directly
	 */
	@Deprecated
	public void removeSource( final Source< ? > source )
	{
		state.removeSource( soc( source ) );
	}

	/**
	 * @deprecated Modify {@link #state()} directly
	 */
	@Deprecated
	public void removeSources( final Collection< Source< ? > > sources )
	{
		state.removeSources( sources.stream().map( this::soc ).collect( Collectors.toList() ) );
	}

	/**
	 * @deprecated Modify {@link #state()} directly
	 */
	@Deprecated
	public void removeAllSources()
	{
		state.clearSources();
	}

	/**
	 * @deprecated Modify {@link #state()} directly
	 */
	@Deprecated
	public void addGroup( final bdv.viewer.state.SourceGroup group )
	{
		synchronized ( state )
		{
			deprecatedState.addGroup( group );
		}
	}

	/**
	 * @deprecated Modify {@link #state()} directly
	 */
	@Deprecated
	public void removeGroup( final bdv.viewer.state.SourceGroup group )
	{
		synchronized ( state )
		{
			deprecatedState.removeGroup( group );
		}
	}

	/**
	 * Set {@code gPos} to the display coordinates at gPos transformed into the
	 * global coordinate system.
	 *
	 * @param gPos
	 *            is set to the corresponding global coordinates.
	 */
	public void displayToGlobalCoordinates( final double[] gPos )
	{
		assert gPos.length >= 3;
		state.getViewerTransform().applyInverse( gPos, gPos );
	}

	/**
	 * Set {@code gPos} to the display coordinates at gPos transformed into the
	 * global coordinate system.
	 *
	 * @param gPos
	 *            is set to the corresponding global coordinates.
	 */
	public < P extends RealLocalizable & RealPositionable > void displayToGlobalCoordinates( final P gPos )
	{
		assert gPos.numDimensions() >= 3;
		state.getViewerTransform().applyInverse( gPos, gPos );
	}

	/**
	 * Set {@code gPos} to the display coordinates (x,y,0)<sup>T</sup> transformed into the
	 * global coordinate system.
	 *
	 * @param gPos
	 *            is set to the global coordinates at display (x,y,0)<sup>T</sup>.
	 */
	public void displayToGlobalCoordinates( final double x, final double y, final RealPositionable gPos )
	{
		assert gPos.numDimensions() >= 3;
		final RealPoint lPos = new RealPoint( 3 );
		lPos.setPosition( x, 0 );
		lPos.setPosition( y, 1 );
		state.getViewerTransform().applyInverse( gPos, lPos );
	}

	@Override
	public void paint()
	{
		display.display();

		final AbstractTransformAnimator animator = currentAnimator.get();
		if ( animator != null )
		{
			final AffineTransform3D transform = animator.getCurrent( System.currentTimeMillis() );
			state.setViewerTransform( transform );
			if ( animator.isComplete() )
				currentAnimator.compareAndSet( animator, null );
			else
				requestRepaint( NONE ); // just to keep animator going. TODO: should really switch to timer-based animations
		}
	}

	/**
	 * Repaint as soon as possible.
	 */
	@Override
	public void requestRepaint()
	{
		repaint.request( FULL );
	}

	public void requestRepaint( final RepaintType type )
	{
		repaint.request( type );
	}

	@Override
	protected void onMouseMoved()
	{
		if ( Prefs.showTextOverlay() )
			// trigger repaint for showing updated mouse coordinates
			getDisplayComponent().repaint();
	}

	@Override
	public void drawOverlays( final Graphics g )
	{
		boolean requiresRepaint = false;
		if ( Prefs.showMultibox() )
		{
			multiBoxOverlayRenderer.setViewerState( state );
			multiBoxOverlayRenderer.updateVirtualScreenSize( display.getWidth(), display.getHeight() );
			multiBoxOverlayRenderer.paint( ( Graphics2D ) g );
			requiresRepaint = multiBoxOverlayRenderer.isHighlightInProgress();
		}

		if ( Prefs.showTextOverlay() )
		{
			final Font font = UIUtils.getFont( "monospaced.small.font" );
			sourceInfoOverlayRenderer.setViewerState( state );
			sourceInfoOverlayRenderer.setSourceNameOverlayPosition( Prefs.sourceNameOverlayPosition() );
			sourceInfoOverlayRenderer.paint( ( Graphics2D ) g );

			final double[] gPos = new double[ 3 ];
			getGlobalMouseCoordinates( RealPoint.wrap( gPos ) );
			final String mousePosGlobalString = String.format(
					Locale.ROOT, "%6.1f, %6.1f, %6.1f", gPos[ 0 ], gPos[ 1 ], gPos[ 2 ] );

			g.setFont( font );
			UIUtils.drawString( g, TOP_RIGHT, 1, mousePosGlobalString );
		}

		if ( Prefs.showScaleBar() )
		{
			scaleBarOverlayRenderer.setViewerState( state );
			scaleBarOverlayRenderer.paint( ( Graphics2D ) g );
		}

		final long currentTimeMillis = System.currentTimeMillis();
		final ArrayList< OverlayAnimator > overlayAnimatorsToRemove = new ArrayList<>();
		for ( final OverlayAnimator animator : overlayAnimators )
		{
			animator.paint( ( Graphics2D ) g, currentTimeMillis );
			requiresRepaint |= animator.requiresRepaint();
			if ( animator.isComplete() )
				overlayAnimatorsToRemove.add( animator );
		}
		overlayAnimators.removeAll( overlayAnimatorsToRemove );

		if ( requiresRepaint )
			getDisplayComponent().repaint();
	}

	@Override
	public void viewerStateChanged( final ViewerStateChange change )
	{
		switch ( change )
		{
		case CURRENT_SOURCE_CHANGED:
			multiBoxOverlayRenderer.highlight( state.getSources().indexOf( state.getCurrentSource() ) );
			getDisplayComponent().repaint();
			break;
		case DISPLAY_MODE_CHANGED:
			showMessage( state.getDisplayMode().getName() );
			getDisplayComponent().repaint();
			break;
		case GROUP_NAME_CHANGED:
			getDisplayComponent().repaint();
			break;
		case CURRENT_GROUP_CHANGED:
			// TODO multiBoxOverlayRenderer.highlight() all sources in group that became current
			break;
		case SOURCE_ACTIVITY_CHANGED:
			// TODO multiBoxOverlayRenderer.highlight() all sources that became visible
			break;
		case GROUP_ACTIVITY_CHANGED:
			// TODO multiBoxOverlayRenderer.highlight() all sources that became visible
			break;
		case VISIBILITY_CHANGED:
			requestRepaint();
			break;
//		case SOURCE_TO_GROUP_ASSIGNMENT_CHANGED:
//		case NUM_SOURCES_CHANGED:
//		case NUM_GROUPS_CHANGED:
//		case INTERPOLATION_CHANGED:
		case NUM_TIMEPOINTS_CHANGED:
		{
			final int numTimepoints = state.getNumTimepoints();
			final int timepoint = Math.max( 0, Math.min( state.getCurrentTimepoint(), numTimepoints - 1 ) );
			SwingUtilities.invokeLater( () -> {
				final boolean sliderVisible = Arrays.asList( getComponents() ).contains( sliderTime );
				if ( numTimepoints > 1 && !sliderVisible )
					add( sliderTime, BorderLayout.SOUTH );
				else if ( numTimepoints == 1 && sliderVisible )
					remove( sliderTime );
				sliderTime.setModel( new DefaultBoundedRangeModel( timepoint, 0, 0, numTimepoints - 1 ) );
				revalidate();
			} );
			break;
		}
		case CURRENT_TIMEPOINT_CHANGED:
		{
			final int timepoint = state.getCurrentTimepoint();
			SwingUtilities.invokeLater( () -> {
				blockSliderTimeEvents = true;
				if ( sliderTime.getValue() != timepoint )
					sliderTime.setValue( timepoint );
				blockSliderTimeEvents = false;
			} );
			timePointListeners.list.forEach( l -> l.timePointChanged( timepoint ) );
			requestRepaint();
			break;
		}
		case VIEWER_TRANSFORM_CHANGED:
			final AffineTransform3D transform = state.getViewerTransform();
			transformListeners.list.forEach( l -> l.transformChanged( transform ) );
			requestRepaint();
		}
	}

	@Override
	public void setTransformAnimator( final AbstractTransformAnimator animator )
	{
		animator.setTime( System.currentTimeMillis() );
		currentAnimator.set( animator );
		requestRepaint();
	}

	/**
	 * Set the {@link DisplayMode}.
	 */
	// TODO: Deprecate or leave as convenience?
	public void setDisplayMode( final DisplayMode displayMode )
	{
		state.setDisplayMode( displayMode );
	}

	/**
	 * @deprecated Modify {@link #state()} directly ({@code state().setViewerTransform(t)})
	 */
	@Deprecated
	public void setCurrentViewerTransform( final AffineTransform3D viewerTransform )
	{
		state.setViewerTransform( viewerTransform );
	}

	/**
	 * Show the specified time-point.
	 *
	 * @param timepoint
	 *            time-point index.
	 */
	// TODO: Deprecate or leave as convenience?
	public void setTimepoint( final int timepoint )
	{
		state.setCurrentTimepoint( timepoint );
	}

	/**
	 * Show the next time-point.
	 */
	// TODO: Deprecate or leave as convenience?
	public void nextTimePoint()
	{
		NavigationActions.nextTimePoint( state );
	}

	/**
	 * Show the previous time-point.
	 */
	// TODO: Deprecate or leave as convenience?
	public void previousTimePoint()
	{
		NavigationActions.previousTimePoint( state );
	}

	/**
	 * Set the number of available timepoints. If {@code numTimepoints == 1}
	 * this will hide the time slider, otherwise show it. If the currently
	 * displayed timepoint would be out of range with the new number of
	 * timepoints, the current timepoint is set to {@code numTimepoints - 1}.
	 * <p>
	 * This is equivalent to {@code state().setNumTimepoints(numTimepoints}}.
	 *
	 * @param numTimepoints
	 *            number of available timepoints. Must be {@code >= 1}.
	 */
	public void setNumTimepoints( final int numTimepoints )
	{
		state.setNumTimepoints( numTimepoints );
	}

	/**
	 * @deprecated Use {@link #state()} instead.
	 *
	 * Get a copy of the current {@link bdv.viewer.state.ViewerState}.
	 *
	 * @return a copy of the current {@link bdv.viewer.state.ViewerState}.
	 */
	@Deprecated
	public bdv.viewer.state.ViewerState getState()
	{
		return deprecatedState.copy();
	}

	/**
	 * Get the ViewerState. This can be directly used for modifications, e.g.,
	 * adding/removing sources etc. See {@link SynchronizedViewerState} for
	 * thread-safety considerations.
	 */
	@Override
	public SynchronizedViewerState state()
	{
		return state;
	}

	/**
	 * Get the viewer canvas.
	 *
	 * @return the viewer canvas.
	 */
	@Override
	public InteractiveGLDisplayCanvas< ? > getDisplay()
	{
		return display;
	}

	/**
	 * Get the AWT {@code Component} of the viewer canvas.
	 *
	 * @return the viewer canvas.
	 */
	@Override
	public Component getDisplayComponent()
	{
		return display.getComponent();
	}

	public TransformEventHandler getTransformEventHandler()
	{
		return transformEventHandler;
	}

	/**
	 * Display the specified message in a text overlay for a short time.
	 *
	 * @param msg
	 *            String to display. Should be just one line of text.
	 */
	@Override
	public void showMessage( final String msg )
	{
		msgOverlay.add( msg );
		getDisplayComponent().repaint();
	}

	/**
	 * Add a new {@link OverlayAnimator} to the list of animators. The animation
	 * is immediately started. The new {@link OverlayAnimator} will remain in
	 * the list of animators until it {@link OverlayAnimator#isComplete()}.
	 *
	 * @param animator
	 *            animator to add.
	 */
	@Override
	public void addOverlayAnimator( final OverlayAnimator animator )
	{
		overlayAnimators.add( animator );
		getDisplayComponent().repaint();
	}

	/**
	 * Add/remove {@code TransformListener}s to notify about viewer transformation
	 * changes. Listeners will be notified <em>before</em> calling
	 * {@link #requestRepaint()} so they have the chance to interfere.
	 */
	@Override
	public Listeners< TransformListener< AffineTransform3D > > transformListeners()
	{
		return transformListeners;
	}

	/**
	 * Add/remove {@code TransformListener}s to notify about viewer transformation
	 * changes. Listeners will be notified when a new image is painted with the viewer
	 * transform used to render that image.
	 */
	@Override
	public Listeners< TransformListener< AffineTransform3D > > renderTransformListeners()
	{
		return renderTransformListeners;
	}

	/**
	 * Add/remove {@link TimePointListener} to notify about time-point
	 * changes. Listeners will be notified <em>before</em> calling
	 * {@link #requestRepaint()} so listeners have the chance to interfere.
	 */
	@Override
	public Listeners< TimePointListener > timePointListeners()
	{
		return timePointListeners;
	}

	@Override
	public ConverterSetups getConverterSetups()
	{
		return setups;
	}

	/**
	 * @deprecated Use {@code timePointListeners().add( listener )}.
	 */
	@Deprecated
	public void addTimePointListener( final TimePointListener listener )
	{
		timePointListeners().add( listener );
	}

	/**
	 * @deprecated Use {@code timePointListeners().add( index, listener )}.
	 */
	@Deprecated
	public void addTimePointListener( final TimePointListener listener, final int index )
	{
		timePointListeners().add( index, listener );
	}

	/**
	 * @deprecated Use {@code timePointListeners().remove( listener )}.
	 */
	@Deprecated
	public void removeTimePointListener( final TimePointListener listener )
	{
		timePointListeners().remove( listener );
	}

	private static int getDitherStep( final int ditherWidth )
	{
		final int[] ditherSteps = new int[] {
		// width = 0, 1, 2, 3, 4,  5,  6,  7,  8
		           0, 1, 3, 5, 9, 11, 19, 23, 29
		};
		if ( ditherWidth >= ditherSteps.length )
			throw new IllegalArgumentException( "unsupported dither width" );
		return ditherSteps[ ditherWidth ];
	}

	// ... RenderState ...
	private final List< Stack3D< ? > > renderStacks = new ArrayList<>();
	private final List< ConverterSetup > renderConverters = new ArrayList<>();
	private final Matrix4f pv = new Matrix4f();
	private double dCam;
	private double dClipNear;
	private double dClipFar;
	private double screenWidth;
	private double screenHeight;
	private double maxAllowedStepInVoxels;

	public void setCamParams( final double dCam, final double dClip )
	{
		setCamParams( dCam, dClip, dClip );
	}

	public void setCamParams( final double dCam, final double dClipNear, final double dClipFar )
	{
		this.dCam = dCam;
		this.dClipNear = dClipNear;
		this.dClipFar = dClipFar;
	}

	public void setMaxAllowedStepInVoxels( final double maxAllowedStepInVoxels )
	{
		this.maxAllowedStepInVoxels = maxAllowedStepInVoxels;
	}

	@Override
	public void setCanvasSize( final int width, final int height )
	{
		setScreenSize( width, height );
	}

	private void setScreenSize(final double screenWidth, final double screenHeight)
	{
		this.screenWidth = screenWidth;
		this.screenHeight = screenHeight;
	}

	private RenderData renderData;

	@SuppressWarnings( "unchecked" )
	private void setRenderState()
	{
		synchronized ( state )
		{
			final int currentTimepoint = state.getCurrentTimepoint();
			final Set< SourceAndConverter< ? > > visibleSources = state.getVisibleAndPresentSources();
			final AffineTransform3D renderTransformWorldToScreen = state.getViewerTransform();

			final Matrix4f view = MatrixMath.affine( renderTransformWorldToScreen, new Matrix4f() );
			MatrixMath.screenPerspective( dCam, dClipNear, dClipFar, screenWidth, screenHeight, 0, pv ).mul( view );

			renderStacks.clear();
			renderConverters.clear();

			for ( SourceAndConverter< ? > source : visibleSources )
			{
				final ConverterSetup converter = setups.getConverterSetup( source );
				if ( source.asVolatile() != null )
					source = source.asVolatile();
				final Stack3D< ? > stack3D = SourceStacks.getStack3D( source.getSpimSource(), currentTimepoint );
				renderStacks.add( stack3D );
				renderConverters.add( converter );
			}
			renderData = new RenderData( pv, currentTimepoint, renderTransformWorldToScreen, dCam, dClipNear, dClipFar, screenWidth, screenHeight );
		}
	}

	private final GLEventListener glEventListener = new GLEventListener()
	{
		@Override
		public void init( final GLAutoDrawable drawable )
		{
			final GL3 gl = drawable.getGL().getGL3();
			renderer.init( gl );
		}

		@Override
		public void display( final GLAutoDrawable drawable )
		{
			final GL3 gl = drawable.getGL().getGL3();

			final RepaintType type = repaint.getAndClear();

			if ( type == FULL )
			{
				setRenderState();
				renderTransformListeners.list.forEach( l -> l.transformChanged( renderData.getRenderTransformWorldToScreen() ) );
			}

			if ( type == FULL || type == SCENE )
			{
				sceneBuf.bind( gl );
				gl.glEnable( GL_DEPTH_TEST );
				gl.glDepthFunc( GL_LESS );
				if ( renderScene != null )
					renderScene.render( gl, renderData );
				sceneBuf.unbind( gl, false );
			}

			if ( type == FULL || type == LOAD )
			{
				CacheIoTiming.getIoTimeBudget().reset( iobudget );
				cacheControl.prepareNextFrame();
			}

//			offscreen.flipY = true;
			offscreen.bind( gl, false );
			gl.glDisable( GL_DEPTH_TEST );
			sceneBuf.drawQuad( gl );
			final RepaintType rerender = renderer.draw( gl, type, sceneBuf, renderStacks, renderConverters, pv, maxRenderMillis, maxAllowedStepInVoxels );
			repaint.request( rerender );
			offscreen.unbind( gl, false );
			offscreen.drawQuad( gl );
		}

		@Override
		public void reshape( final GLAutoDrawable drawable, final int x, final int y, final int width, final int height )
		{
		}

		@Override
		public void dispose( final GLAutoDrawable drawable )
		{
		}
	};

	public Element stateToXml()
	{
		synchronized ( state )
		{
			return new XmlIoViewerState().toXml( deprecatedState );
		}
	}

	public void stateFromXml( final Element parent )
	{
		synchronized ( state )
		{
			final XmlIoViewerState io = new XmlIoViewerState();
			io.restoreFromXml( parent.getChild( io.getTagName() ), deprecatedState );
		}
	}

	/**
	 * @deprecated Modify {@link #state()} directly
	 *
	 * Returns the {@link VisibilityAndGrouping} that can be used to modify
	 * visibility and currentness of sources and groups, as well as grouping of
	 * sources, and display mode.
	 */
	@Deprecated
	public VisibilityAndGrouping getVisibilityAndGrouping()
	{
		return visibilityAndGrouping;
	}

	public VolumeViewerOptions.Values getOptionValues()
	{
		return options;
	}

	@Override
	public InputTriggerConfig getInputTriggerConfig()
	{
		return options.getInputTriggerConfig();
	}

	public SourceInfoOverlayRenderer getSourceInfoOverlayRenderer()
	{
		return sourceInfoOverlayRenderer;
	}

	/**
	 * Stop the {@link #painterThread}.
	 */
	public void stop()
	{
		painterThread.interrupt();
		try
		{
			painterThread.join( 0 );
		}
		catch ( final InterruptedException e )
		{
			e.printStackTrace();
		}
		state.clearGroups();
		state.clearSources();
	}
}
