package tpietzsch.example2;

import static bdv.viewer.VisibilityAndGrouping.Event.NUM_GROUPS_CHANGED;
import static bdv.viewer.VisibilityAndGrouping.Event.NUM_SOURCES_CHANGED;
import static bdv.viewer.VisibilityAndGrouping.Event.VISIBILITY_CHANGED;
import static com.jogamp.opengl.GL.GL_DEPTH_TEST;
import static com.jogamp.opengl.GL.GL_LESS;
import static com.jogamp.opengl.GL.GL_RGB8;
import static tpietzsch.example2.VolumeRenderer.RepaintType.FULL;
import static tpietzsch.example2.VolumeRenderer.RepaintType.LOAD;
import static tpietzsch.example2.VolumeRenderer.RepaintType.NONE;
import static tpietzsch.example2.VolumeRenderer.RepaintType.SCENE;

import bdv.util.InvokeOnEDT;
import bdv.viewer.Source;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelListener;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import java.util.stream.Collectors;
import javax.swing.DefaultBoundedRangeModel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;

import net.imglib2.Positionable;
import net.imglib2.cache.iotiming.CacheIoTiming;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.LinAlgHelpers;

import org.jdom2.Element;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import bdv.cache.CacheControl;
import bdv.tools.brightness.ConverterSetup;
import bdv.util.Affine3DHelpers;
import bdv.viewer.RequestRepaint;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.TimePointListener;
import bdv.viewer.TransformListener;
import bdv.viewer.VisibilityAndGrouping;
import bdv.viewer.animate.AbstractTransformAnimator;
import bdv.viewer.animate.RotationAnimator;
import bdv.viewer.render.PainterThread;
import bdv.viewer.state.SourceGroup;
import bdv.viewer.state.SourceState;
import bdv.viewer.state.ViewerState;
import bdv.viewer.state.XmlIoViewerState;
import tpietzsch.example2.VolumeRenderer.RepaintType;
import tpietzsch.multires.SourceStacks;
import tpietzsch.multires.Stack3D;
import tpietzsch.offscreen.OffScreenFrameBuffer;
import tpietzsch.offscreen.OffScreenFrameBufferWithDepth;
import tpietzsch.util.MatrixMath;
import tpietzsch.util.TransformHandler;

public class VolumeViewerPanel
		extends JPanel
		implements RequestRepaint, PainterThread.Paintable
{
	protected final Map< Source< ? >, ConverterSetup > sourceToConverterSetup;

	protected final CacheControl cacheControl;

	public static class RenderData
	{
		private final Matrix4f pv;
		private int timepoint;
		private final AffineTransform3D renderTransformWorldToScreen;
		private double dCam;
		private double dClipNear;
		private double dClipFar;
		private double screenWidth;
		private double screenHeight;

		/**
		 * @param pv
		 * @param timepoint timepoint index
		 */
		public RenderData(
				final Matrix4fc pv,
				final int timepoint,
				final AffineTransform3D renderTransformWorldToScreen,
				final double dCam,
				final double dClipNear,
				final double dClipFar,
				final double screenWidth,
				final double screenHeight )
		{
			this.pv = new Matrix4f( pv );
			this.timepoint = timepoint;
			this.renderTransformWorldToScreen = new AffineTransform3D();
			this.renderTransformWorldToScreen.set( renderTransformWorldToScreen );
			this.dCam = dCam;
			this.dClipNear = dClipNear;
			this.dClipFar = dClipFar;
			this.screenWidth = screenWidth;
			this.screenHeight = screenHeight;
		}

		public RenderData()
		{
			this.pv = new Matrix4f();
			this.renderTransformWorldToScreen = new AffineTransform3D();
		}

		public void set( final RenderData other )
		{
			this.pv.set( other.pv );
			this.timepoint = other.timepoint;
			this.renderTransformWorldToScreen.set( other.renderTransformWorldToScreen );
			this.dCam = other.dCam;
			this.dClipNear = other.dClipNear;
			this.dClipFar = other.dClipFar;
			this.screenWidth = other.screenWidth;
			this.screenHeight = other.screenHeight;
		}

		public Matrix4f getPv()
		{
			return pv;
		}

		public int getTimepoint()
		{
			return timepoint;
		}

		public AffineTransform3D getRenderTransformWorldToScreen()
		{
			return renderTransformWorldToScreen;
		}

		public double getDCam()
		{
			return dCam;
		}

		public double getDClipNear()
		{
			return dClipNear;
		}

		public double getDClipFar()
		{
			return dClipFar;
		}

		public double getScreenWidth()
		{
			return screenWidth;
		}

		public double getScreenHeight()
		{
			return screenHeight;
		}
	}

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
	 * Currently rendered state (visible sources, transformation, timepoint,
	 * etc.) A copy can be obtained by {@link #getState()}.
	 */
	protected final ViewerState state;

	/**
	 * Renders the current state to gl context.
	 */
	protected final VolumeRenderer renderer;

	/**
	 * Transformation set by the interactive viewer.
	 */
	protected final AffineTransform3D viewerTransform;

	protected final GLCanvas canvas;

	protected final JSlider sliderTime;

	/**
	 * Thread that triggers repainting of the display.
	 */
	protected final PainterThread painterThread;

	/**
	 * Keeps track of the current mouse coordinates.
	 */
	protected final MouseCoordinateListener mouseCoordinates;

	/**
	 * Manages visibility and currentness of sources and groups, as well as
	 * grouping of sources, and display mode.
	 */
	protected final MyVisibilityAndGrouping visibilityAndGrouping;

	static class MyVisibilityAndGrouping extends VisibilityAndGrouping
	{
		public MyVisibilityAndGrouping( final ViewerState viewerState )
		{
			super( viewerState );
		}

		@Override
		protected void update( final int id )
		{
			super.update( id );
		}
	}

	/**
	 * These listeners will be notified about changes to the
	 * {@link #viewerTransform}. This is done <em>before</em> calling
	 * {@link #requestRepaint()} so listeners have the chance to interfere.
	 */
	protected final CopyOnWriteArrayList< TransformListener< AffineTransform3D > > transformListeners;

	/**
	 * These listeners will be notified about changes to the current timepoint
	 * {@link ViewerState#getCurrentTimepoint()}. This is done <em>before</em>
	 * calling {@link #requestRepaint()} so listeners have the chance to
	 * interfere.
	 */
	protected final CopyOnWriteArrayList< TimePointListener > timePointListeners;

	/**
	 * Current animator for viewer transform, or null. This is for example used
	 * to make smooth transitions when {@link #align(AlignPlane)} aligning to
	 * orthogonal planes}.
	 */
	protected AbstractTransformAnimator currentAnimator = null;

	protected final TransformHandler transformHandler;

	/**
	 * @param sources
	 *            the {@link SourceAndConverter sources} to display.
	 * @param converterSetups
	 *            list of {@link ConverterSetup} that control min/max and color
	 *            of sources.
	 * @param numTimepoints
	 *            number of available timepoints.
	 * @param cacheControl
	 *            to control IO budgeting and fetcher queue.
	 * @param optional
	 *            optional parameters. See {@link VolumeViewerOptions}.
	 */
	public VolumeViewerPanel(
			final List< SourceAndConverter< ? > > sources,
			final List< ConverterSetup > converterSetups,
			final int numTimepoints,
			final CacheControl cacheControl,
			final RenderScene renderScene,
			final VolumeViewerOptions optional )
	{
		super( new BorderLayout() );

		this.sourceToConverterSetup = new HashMap<>();
		for ( int i = 0; i < converterSetups.size(); i++ )
		{
			sourceToConverterSetup.put( sources.get( i ).getSpimSource(), converterSetups.get( i ) );
		}

		this.cacheControl = cacheControl;
		this.renderScene = renderScene;

		final VolumeViewerOptions.Values options = optional.values;

		final int numGroups = options.getNumSourceGroups();
		final ArrayList< SourceGroup > groups = new ArrayList<>( numGroups );
		for ( int i = 0; i < numGroups; ++i )
			groups.add( new SourceGroup( "group " + Integer.toString( i + 1 ) ) );
		state = new ViewerState( sources, groups, numTimepoints );
		for ( int i = Math.min( numGroups, sources.size() ) - 1; i >= 0; --i )
			state.getSourceGroups().get( i ).addSource( i );

		if ( !sources.isEmpty() )
			state.setCurrentSource( 0 );

		final int renderWidth = options.getRenderWidth();
		final int renderHeight = options.getRenderHeight();
		sceneBuf = new OffScreenFrameBufferWithDepth( renderWidth, renderHeight, GL_RGB8 );
		offscreen = new OffScreenFrameBuffer( renderWidth, renderHeight, GL_RGB8 );
		maxRenderMillis = options.getMaxRenderMillis();

		viewerTransform = new AffineTransform3D();
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

		final GLCapabilities capsReqUser = new GLCapabilities( GLProfile.getMaxProgrammableCore( true ) );
		canvas = new GLCanvas( capsReqUser );
		canvas.setPreferredSize( new Dimension( options.getWidth(), options.getHeight() ) );
		canvas.addGLEventListener( glEventListener );

		visibilityAndGrouping = new MyVisibilityAndGrouping( state );
		visibilityAndGrouping.addUpdateListener( e -> {
			if ( e.id == VISIBILITY_CHANGED )
				requestRepaint();
		} );

		sliderTime = new JSlider( SwingConstants.HORIZONTAL, 0, numTimepoints - 1, 0 );
		sliderTime.addChangeListener( e -> {
			if ( e.getSource().equals( sliderTime ) )
				setTimepoint( sliderTime.getValue() );
		} );

		add( canvas, BorderLayout.CENTER );
		if ( numTimepoints > 1 )
			add( sliderTime, BorderLayout.SOUTH );

		transformListeners = new CopyOnWriteArrayList<>();
		timePointListeners = new CopyOnWriteArrayList<>();

		transformHandler = new TransformHandler();
		transformHandler.setCanvasSize( options.getWidth(), options.getHeight(), false );
		transformHandler.setTransform( viewerTransform );
		transformHandler.listeners().add( this::transformChanged );

		mouseCoordinates = new MouseCoordinateListener();
		addHandlerToCanvas( mouseCoordinates );

		canvas.addComponentListener( new ComponentAdapter()
		{
			@Override
			public void componentResized( final ComponentEvent e )
			{
				final int w = canvas.getWidth();
				final int h = canvas.getHeight();
				transformHandler.setCanvasSize( w, h, true );
				setScreenSize( w, h );
				requestRepaint();
			}
		} );

//		canvas.addMouseListener( new MouseAdapter()
//		{
//			@Override
//			public void mousePressed( final MouseEvent e )
//			{
//				canvas.requestFocusInWindow();
//			}
//		} );

		painterThread = new PainterThread( this );
		painterThread.setDaemon( true );
		painterThread.start();
	}

	public void addSource( final SourceAndConverter< ? > sourceAndConverter, final ConverterSetup converterSetup )
	{
		synchronized ( visibilityAndGrouping )
		{
			sourceToConverterSetup.put( sourceAndConverter.getSpimSource(), converterSetup );
			state.addSource( sourceAndConverter );
			visibilityAndGrouping.update( NUM_SOURCES_CHANGED );
		}
		requestRepaint();
	}

	public void removeSource( final Source< ? > source )
	{
		synchronized ( visibilityAndGrouping )
		{
			sourceToConverterSetup.remove( source );
			state.removeSource( source );
			visibilityAndGrouping.update( NUM_SOURCES_CHANGED );
		}
		requestRepaint();
	}

	public void removeSources( final Collection< Source< ? > > sources )
	{
		synchronized ( visibilityAndGrouping )
		{
			sources.forEach( sourceToConverterSetup::remove );
			sources.forEach( state::removeSource );
			visibilityAndGrouping.update( NUM_SOURCES_CHANGED );
		}
		requestRepaint();
	}

	public void removeAllSources()
	{
		synchronized ( visibilityAndGrouping )
		{
			removeSources( getState().getSources().stream().map( SourceAndConverter::getSpimSource ).collect( Collectors.toList() ) );
		}
	}

	public void addGroup( final SourceGroup group )
	{
		synchronized ( visibilityAndGrouping )
		{
			state.addGroup( group );
			visibilityAndGrouping.update( NUM_GROUPS_CHANGED );
		}
		requestRepaint();
	}

	public void removeGroup( final SourceGroup group )
	{
		synchronized ( visibilityAndGrouping )
		{
			state.removeGroup( group );
			visibilityAndGrouping.update( NUM_GROUPS_CHANGED );
		}
		requestRepaint();
	}

	/**
	 * Set the viewer transform.
	 */
	public synchronized void setCurrentViewerTransform( final AffineTransform3D viewerTransform )
	{
		transformHandler.setTransform( viewerTransform );
	}

	/**
	 * Show the specified time-point.
	 *
	 * @param timepoint
	 *            time-point index.
	 */
	public synchronized void setTimepoint( final int timepoint )
	{
		if ( state.getCurrentTimepoint() != timepoint )
		{
			state.setCurrentTimepoint( timepoint );
			sliderTime.setValue( timepoint );
			for ( final TimePointListener l : timePointListeners )
				l.timePointChanged( timepoint );
			requestRepaint();
		}
	}

	/**
	 * Show the next time-point.
	 */
	public synchronized void nextTimePoint()
	{
		if ( state.getNumTimepoints() > 1 )
			sliderTime.setValue( sliderTime.getValue() + 1 );
	}

	/**
	 * Show the previous time-point.
	 */
	public synchronized void previousTimePoint()
	{
		if ( state.getNumTimepoints() > 1 )
			sliderTime.setValue( sliderTime.getValue() - 1 );
	}

	/**
	 * Set the number of available timepoints. If {@code numTimepoints == 1}
	 * this will hide the time slider, otherwise show it. If the currently
	 * displayed timepoint would be out of range with the new number of
	 * timepoints, the current timepoint is set to {@code numTimepoints - 1}.
	 *
	 * @param numTimepoints
	 *            number of available timepoints. Must be {@code >= 1}.
	 */
	public void setNumTimepoints( final int numTimepoints )
	{
		try
		{
			InvokeOnEDT.invokeAndWait( () -> setNumTimepointsSynchronized( numTimepoints ) );
		}
		catch ( InvocationTargetException | InterruptedException e )
		{
			e.printStackTrace();
		}
	}

	private synchronized void setNumTimepointsSynchronized( final int numTimepoints )
	{
		if ( numTimepoints < 1 || state.getNumTimepoints() == numTimepoints )
			return;
		else if ( numTimepoints == 1 && state.getNumTimepoints() > 1 )
			remove( sliderTime );
		else if ( numTimepoints > 1 && state.getNumTimepoints() == 1 )
			add( sliderTime, BorderLayout.SOUTH );

		state.setNumTimepoints( numTimepoints );
		if ( state.getCurrentTimepoint() >= numTimepoints )
		{
			final int timepoint = numTimepoints - 1;
			state.setCurrentTimepoint( timepoint );
			for ( final TimePointListener l : timePointListeners )
				l.timePointChanged( timepoint );
		}
		sliderTime.setModel( new DefaultBoundedRangeModel( state.getCurrentTimepoint(), 0, 0, numTimepoints - 1 ) );
		revalidate();
		requestRepaint();
	}

	public synchronized Element stateToXml()
	{
		return new XmlIoViewerState().toXml( state );
	}

	public synchronized void stateFromXml( final Element parent )
	{
		final XmlIoViewerState io = new XmlIoViewerState();
		io.restoreFromXml( parent.getChild( io.getTagName() ), state );
	}

	public TransformHandler getTransformEventHandler()
	{
		return transformHandler;
	}

	/**
	 * Get a copy of the current {@link ViewerState}.
	 *
	 * @return a copy of the current {@link ViewerState}.
	 */
	public ViewerState getState()
	{
		return state.copy();
	}

	@Override
	public void paint()
	{
		canvas.display();

		synchronized ( this )
		{
			if ( currentAnimator != null )
			{
				final AffineTransform3D transform = currentAnimator.getCurrent( System.currentTimeMillis() );
				transformHandler.setTransform( transform );
				transformChanged( transform );
				if ( currentAnimator.isComplete() )
					currentAnimator = null;
			}
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

	/**
	 * Repaint as soon as possible.
	 */
	public void requestRepaint( final RepaintType type )
	{
		repaint.request( type );
	}

	private final static double c = Math.cos( Math.PI / 4 );

	/**
	 * The planes which can be aligned with the viewer coordinate system: XY,
	 * ZY, and XZ plane.
	 */
	public static enum AlignPlane
	{
		XY( "XY", 2, new double[] { 1, 0, 0, 0 } ),
		ZY( "ZY", 0, new double[] { c, 0, -c, 0 } ),
		XZ( "XZ", 1, new double[] { c, c, 0, 0 } );

		private final String name;

		public String getName()
		{
			return name;
		}

		/**
		 * rotation from the xy-plane aligned coordinate system to this plane.
		 */
		private final double[] qAlign;

		/**
		 * Axis index. The plane spanned by the remaining two axes will be
		 * transformed to the same plane by the computed rotation and the
		 * "rotation part" of the affine source transform.
		 * @see Affine3DHelpers#extractApproximateRotationAffine(AffineTransform3D, double[], int)
		 */
		private final int coerceAffineDimension;

		private AlignPlane( final String name, final int coerceAffineDimension, final double[] qAlign )
		{
			this.name = name;
			this.coerceAffineDimension = coerceAffineDimension;
			this.qAlign = qAlign;
		}
	}

	/**
	 * Align the XY, ZY, or XZ plane of the local coordinate system of the
	 * currently active source with the viewer coordinate system.
	 *
	 * @param plane
	 *            to which plane to align.
	 */
	protected synchronized void align( final AlignPlane plane )
	{
		final SourceState< ? > source = state.getSources().get( state.getCurrentSource() );
		final AffineTransform3D sourceTransform = new AffineTransform3D();
		source.getSpimSource().getSourceTransform( state.getCurrentTimepoint(), 0, sourceTransform );

		final double[] qSource = new double[ 4 ];
		Affine3DHelpers.extractRotationAnisotropic( sourceTransform, qSource );

		final double[] qTmpSource = new double[ 4 ];
		Affine3DHelpers.extractApproximateRotationAffine( sourceTransform, qSource, plane.coerceAffineDimension );
		LinAlgHelpers.quaternionMultiply( qSource, plane.qAlign, qTmpSource );

		final double[] qTarget = new double[ 4 ];
		LinAlgHelpers.quaternionInvert( qTmpSource, qTarget );

		final AffineTransform3D transform = transformHandler.getTransform();
		double centerX;
		double centerY;
		if ( mouseCoordinates.isMouseInsidePanel() )
		{
			centerX = mouseCoordinates.getX();
			centerY = mouseCoordinates.getY();
		}
		else
		{
			centerY = getHeight() / 2.0;
			centerX = getWidth() / 2.0;
		}
		currentAnimator = new RotationAnimator( transform, centerX, centerY, qTarget, 300 );
		currentAnimator.setTime( System.currentTimeMillis() );
		transformChanged( transform );
	}

	public synchronized void setTransformAnimator( final AbstractTransformAnimator animator )
	{
		currentAnimator = animator;
		currentAnimator.setTime( System.currentTimeMillis() );
		requestRepaint();
	}

	/**
	 * Stop the painter thread.
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
		state.kill();
	}

	protected class MouseCoordinateListener implements MouseMotionListener, MouseListener
	{
		private int x;

		private int y;

		private boolean isInside;

		public synchronized void getMouseCoordinates( final Positionable p )
		{
			p.setPosition( x, 0 );
			p.setPosition( y, 1 );
		}

		@Override
		public synchronized void mouseDragged( final MouseEvent e )
		{
			x = e.getX();
			y = e.getY();
		}

		@Override
		public synchronized void mouseMoved( final MouseEvent e )
		{
			x = e.getX();
			y = e.getY();
		}

		public synchronized int getX()
		{
			return x;
		}

		public synchronized int getY()
		{
			return y;
		}

		public synchronized boolean isMouseInsidePanel()
		{
			return isInside;
		}

		@Override
		public synchronized void mouseEntered( final MouseEvent e )
		{
			isInside = true;
		}

		@Override
		public synchronized void mouseExited( final MouseEvent e )
		{
			isInside = false;
		}

		@Override
		public void mouseClicked( final MouseEvent e )
		{}

		@Override
		public void mousePressed( final MouseEvent e )
		{}

		@Override
		public void mouseReleased( final MouseEvent e )
		{}
	}

	public Component getDisplay()
	{
		return canvas;
	}

	/**
	 * Returns the {@link VisibilityAndGrouping} that can be used to modify
	 * visibility and currentness of sources and groups, as well as grouping of
	 * sources, and display mode.
	 */
	public VisibilityAndGrouping getVisibilityAndGrouping()
	{
		return visibilityAndGrouping;
	}


	/**
	 * Add a {@link TransformListener} to notify about viewer transformation
	 * changes. Listeners will be notified <em>before</em> calling
	 * {@link #requestRepaint()} so they have the chance to interfere.
	 *
	 * @param listener
	 *            the transform listener to add.
	 */
	public void addTransformListener( final TransformListener< AffineTransform3D > listener )
	{
		addTransformListener( listener, Integer.MAX_VALUE );
	}

	/**
	 * Add a {@link TransformListener} to notify about viewer transformation
	 * changes. Listeners will be notified <em>before</em> calling
	 * {@link #requestRepaint()} so they have the chance to interfere.
	 *
	 * @param listener
	 *            the transform listener to add.
	 * @param index
	 *            position in the list of listeners at which to insert this one.
	 */
	public void addTransformListener( final TransformListener< AffineTransform3D > listener, final int index )
	{
		synchronized ( transformListeners )
		{
			final int s = transformListeners.size();
			transformListeners.add( index < 0 ? 0 : index > s ? s : index, listener );
			listener.transformChanged( viewerTransform );
		}
	}

	/**
	 * Remove a {@link TransformListener}.
	 *
	 * @param listener
	 *            the transform listener to remove.
	 */
	public void removeTransformListener( final TransformListener< AffineTransform3D > listener )
	{
		synchronized ( transformListeners )
		{
			transformListeners.remove( listener );
		}
	}

	/**
	 * Add a {@link TimePointListener} to notify about time-point
	 * changes. Listeners will be notified <em>before</em> calling
	 * {@link #requestRepaint()} so they have the chance to interfere.
	 *
	 * @param listener
	 *            the listener to add.
	 */
	public void addTimePointListener( final TimePointListener listener )
	{
		addTimePointListener( listener, Integer.MAX_VALUE );
	}

	/**
	 * Add a {@link TimePointListener} to notify about time-point
	 * changes. Listeners will be notified <em>before</em> calling
	 * {@link #requestRepaint()} so they have the chance to interfere.
	 *
	 * @param listener
	 *            the listener to add.
	 * @param index
	 *            position in the list of listeners at which to insert this one.
	 */
	public void addTimePointListener( final TimePointListener listener, final int index )
	{
		synchronized ( timePointListeners )
		{
			final int s = timePointListeners.size();
			timePointListeners.add( index < 0 ? 0 : index > s ? s : index, listener );
			listener.timePointChanged( state.getCurrentTimepoint() );
		}
	}

	/**
	 * Remove a {@link TimePointListener}.
	 *
	 * @param listener
	 *            the listener to remove.
	 */
	public void removeTimePointListener( final TimePointListener listener )
	{
		synchronized ( timePointListeners )
		{
			timePointListeners.remove( listener );
		}
	}

	/**
	 * Add new event handler to the canvas. Depending on the interfaces implemented by
	 * {@code handler} calls {@link Component#addKeyListener(KeyListener)},
	 * {@link Component#addMouseListener(MouseListener)},
	 * {@link Component#addMouseMotionListener(MouseMotionListener)},
	 * {@link Component#addMouseWheelListener(MouseWheelListener)}.
	 *
	 * @param h
	 * 		handler to remove
	 */
	public void addHandlerToCanvas( final Object h )
	{
		if ( KeyListener.class.isInstance( h ) )
			canvas.addKeyListener( ( KeyListener ) h );

		if ( MouseMotionListener.class.isInstance( h ) )
			canvas.addMouseMotionListener( ( MouseMotionListener ) h );

		if ( MouseListener.class.isInstance( h ) )
			canvas.addMouseListener( ( MouseListener ) h );

		if ( MouseWheelListener.class.isInstance( h ) )
			canvas.addMouseWheelListener( ( MouseWheelListener ) h );

		if ( FocusListener.class.isInstance( h ) )
			canvas.addFocusListener( ( FocusListener ) h );
	}

	/**
	 * Remove an event handler form the canvas. Add new event handler. Depending on the
	 * interfaces implemented by {@code handler} calls
	 * {@link Component#removeKeyListener(KeyListener)},
	 * {@link Component#removeMouseListener(MouseListener)},
	 * {@link Component#removeMouseMotionListener(MouseMotionListener)},
	 * {@link Component#removeMouseWheelListener(MouseWheelListener)}.
	 *
	 * @param h handler to remove
	 */
	public void removeHandlerFromCanvas( final Object h )
	{
		if ( KeyListener.class.isInstance( h ) )
			canvas.removeKeyListener( ( KeyListener ) h );

		if ( MouseMotionListener.class.isInstance( h ) )
			canvas.removeMouseMotionListener( ( MouseMotionListener ) h );

		if ( MouseListener.class.isInstance( h ) )
			canvas.removeMouseListener( ( MouseListener ) h );

		if ( MouseWheelListener.class.isInstance( h ) )
			canvas.removeMouseWheelListener( ( MouseWheelListener ) h );

		if ( FocusListener.class.isInstance( h ) )
			canvas.removeFocusListener( ( FocusListener ) h );
	}

	private synchronized void transformChanged( final AffineTransform3D transform )
	{
		viewerTransform.set( transform );
		state.setViewerTransform( transform );
		for ( final TransformListener< AffineTransform3D > l : transformListeners )
			l.transformChanged( viewerTransform );
		requestRepaint();
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

	private void setScreenSize(final double screenWidth, final double screenHeight)
	{
		this.screenWidth = screenWidth;
		this.screenHeight = screenHeight;
	}

	private RenderData renderData;

	@SuppressWarnings( "unchecked" )
	private void setRenderState()
	{
		final List< Integer > visibleSourceIndices;
		final AffineTransform3D renderTransformWorldToScreen = new AffineTransform3D();
		final int currentTimepoint;
		synchronized ( state )
		{
			visibleSourceIndices = state.getVisibleSourceIndices();
			currentTimepoint = state.getCurrentTimepoint();
			state.getViewerTransform( renderTransformWorldToScreen );
			final Matrix4f view = MatrixMath.affine( renderTransformWorldToScreen, new Matrix4f() );
			MatrixMath.screenPerspective( dCam, dClipNear, dClipFar, screenWidth, screenHeight, 0, pv ).mul( view );

			renderStacks.clear();
			renderConverters.clear();
			for( final int i : visibleSourceIndices )
			{
				SourceState< ? > soc = state.getSources().get( i );
				final ConverterSetup converter = sourceToConverterSetup.get( soc.getSpimSource() );
				if ( soc.asVolatile() != null )
					soc = soc.asVolatile();
				final Stack3D< ? > stack3D = SourceStacks.getStack3D( soc.getSpimSource(), currentTimepoint );
				renderStacks.add( stack3D );
				renderConverters.add( converter );
			}
		}
		renderData = new RenderData( pv, currentTimepoint, renderTransformWorldToScreen, dCam, dClipNear, dClipFar, screenWidth, screenHeight );
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

	/**
	 * TODO overlay message as in BDV
	 */
	public void showMessage( final String msg )
	{
		System.out.println( msg );
	}
}
