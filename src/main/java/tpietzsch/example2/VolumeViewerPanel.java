package tpietzsch.example2;

import bdv.tools.brightness.ConverterSetup;
import bdv.viewer.RequestRepaint;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.TimePointListener;
import bdv.viewer.VisibilityAndGrouping;
import bdv.viewer.state.SourceGroup;
import bdv.viewer.state.ViewerState;
import bdv.viewer.state.XmlIoViewerState;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import net.imglib2.cache.iotiming.CacheIoTiming;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.volatiles.VolatileUnsignedShortType;
import net.imglib2.ui.PainterThread;
import net.imglib2.ui.TransformListener;
import org.jdom2.Element;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import tpietzsch.multires.MultiResolutionStack3D;
import tpietzsch.multires.ResolutionLevel3D;
import tpietzsch.multires.SpimDataStacks;
import tpietzsch.offscreen.OffScreenFrameBuffer;
import tpietzsch.offscreen.OffScreenFrameBufferWithDepth;
import tpietzsch.util.MatrixMath;
import tpietzsch.util.TransformHandler;

import static bdv.viewer.VisibilityAndGrouping.Event.VISIBILITY_CHANGED;
import static com.jogamp.opengl.GL.GL_DEPTH_TEST;
import static com.jogamp.opengl.GL.GL_LESS;
import static com.jogamp.opengl.GL.GL_RGB8;
import static tpietzsch.example2.VolumeRenderer.RepaintType.FULL;
import static tpietzsch.example2.VolumeRenderer.RepaintType.LOAD;
import static tpietzsch.example2.VolumeRenderer.RepaintType.NONE;

public class VolumeViewerPanel
		extends JPanel
		implements RequestRepaint
{
	/**
	 * TODO should be more general...
	 */
	protected final SpimDataStacks stacks;
	protected final List< ? extends ConverterSetup > converterSetups;

	public static class RenderData
	{
		private final Matrix4f pv;

		public RenderData( final Matrix4fc pv )
		{
			this.pv = new Matrix4f( pv );
		}

		public Matrix4f getPv()
		{
			return pv;
		}
	}

	public interface RenderScene
	{
		void render( final GL3 gl, RenderData data );
	}

	private final RenderScene renderScene;

	private class Repaint
	{
		private VolumeRenderer.RepaintType type;

		protected Repaint()
		{
			this.type = FULL;
		}

		protected synchronized void request( VolumeRenderer.RepaintType type )
		{
			if ( this.type.ordinal() < type.ordinal() )
			{
				this.type = type;
				painterThread.requestRepaint();
			}
		}

		protected synchronized VolumeRenderer.RepaintType getAndClear()
		{
			VolumeRenderer.RepaintType t = type;
			type = NONE;
			return t;
		}
	}

	private final Repaint repaint = new Repaint();

	protected final OffScreenFrameBufferWithDepth sceneBuf;

	protected final OffScreenFrameBuffer offscreen;

	// TODO: should be settable
	private long[] iobudget = new long[] { 100l * 1000000l,  10l * 1000000l };

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
	 * Manages visibility and currentness of sources and groups, as well as
	 * grouping of sources, and display mode.
	 */
	protected final VisibilityAndGrouping visibilityAndGrouping;

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

	protected final TransformHandler transformHandler;

	/**
	 * @param sources
	 *            the {@link SourceAndConverter sources} to display.
	 * @param optional
	 *            optional parameters. See {@link VolumeViewerOptions}.
	 */
	public VolumeViewerPanel(
			final List< SourceAndConverter< ? > > sources,
			final List< ? extends ConverterSetup > converterSetups,
			final SpimDataStacks stacks,
			final RenderScene renderScene,
			final VolumeViewerOptions optional )
	{
		super( new BorderLayout() );

		this.converterSetups = converterSetups;
		this.stacks = stacks;
		this.renderScene = renderScene;

		final VolumeViewerOptions.Values options = optional.values;

		final int numGroups = options.getNumSourceGroups();
		final ArrayList< SourceGroup > groups = new ArrayList<>( numGroups );
		for ( int i = 0; i < numGroups; ++i )
			groups.add( new SourceGroup( "group " + Integer.toString( i + 1 ) ) );
		final int numTimepoints = stacks.getNumTimepoints();
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

		final GLCapabilities capsReqUser = new GLCapabilities( GLProfile.getMaxProgrammableCore( true ) );
		canvas = new GLCanvas( capsReqUser );
		canvas.setPreferredSize( new Dimension( options.getWidth(), options.getHeight() ) );
		canvas.addGLEventListener( glEventListener );

		visibilityAndGrouping = new VisibilityAndGrouping( state );
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
		transformHandler.setCanvasSize( canvas.getWidth(), canvas.getHeight(), false );
		transformHandler.setTransform( viewerTransform );
		transformHandler.listeners().add( this::transformChanged );

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

		painterThread = new PainterThread( canvas::display );
		painterThread.setDaemon( true );
		painterThread.start();
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

	/**
	 * Repaint as soon as possible.
	 */
	@Override
	public void requestRepaint()
	{
		repaint.request( FULL );
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
	private final List< MultiResolutionStack3D< VolatileUnsignedShortType > > renderStacks = new ArrayList<>();
	private final List< ConverterSetup > renderConverters = new ArrayList<>();
	private final Matrix4f pv = new Matrix4f();
	private double dCam;
	private double dClipNear;
	private double dClipFar;
	private double screenWidth;
	private double screenHeight;

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

	private void setScreenSize(final double screenWidth, final double screenHeight)
	{
		this.screenWidth = screenWidth;
		this.screenHeight = screenHeight;
	}

	@SuppressWarnings( "unchecked" )
	private void setRenderState()
	{
		final List< Integer > visibleSourceIndices;
		final int currentTimepoint;
		synchronized ( state )
		{
			visibleSourceIndices = state.getVisibleSourceIndices();
			currentTimepoint = state.getCurrentTimepoint();
			final AffineTransform3D renderTransformWorldToScreen = new AffineTransform3D();
			state.getViewerTransform( renderTransformWorldToScreen );
			final Matrix4f view = MatrixMath.affine( renderTransformWorldToScreen, new Matrix4f() );
			MatrixMath.screenPerspective( dCam, dClipNear, dClipFar, screenWidth, screenHeight, 0, pv ).mul( view );

			renderStacks.clear();
			renderConverters.clear();
			for( int i : visibleSourceIndices )
			{
				final MultiResolutionStack3D< VolatileUnsignedShortType > stack = ( MultiResolutionStack3D< VolatileUnsignedShortType > )
						stacks.getStack(
								stacks.timepointId( currentTimepoint ),
								stacks.setupId( i ),
								true );
				final AffineTransform3D sourceTransform = new AffineTransform3D();
				state.getSources().get( i ).getSpimSource().getSourceTransform( currentTimepoint, 0, sourceTransform );
				final MultiResolutionStack3D< VolatileUnsignedShortType > wrappedStack = new MultiResolutionStack3D< VolatileUnsignedShortType >()
				{
					@Override
					public VolatileUnsignedShortType getType()
					{
						return stack.getType();
					}

					@Override
					public AffineTransform3D getSourceTransform()
					{
						return sourceTransform;
					}

					@Override
					public List< ? extends ResolutionLevel3D< VolatileUnsignedShortType > > resolutions()
					{
						return stack.resolutions();
					}
				};
				renderStacks.add( wrappedStack );
				final ConverterSetup converter = converterSetups.get( i );
				renderConverters.add( converter );
			}
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

			final VolumeRenderer.RepaintType type = repaint.getAndClear();

			if ( type == FULL )
			{
				setRenderState();

				sceneBuf.bind( gl );
				gl.glEnable( GL_DEPTH_TEST );
				gl.glDepthFunc( GL_LESS );
				renderScene.render( gl, new RenderData( pv ) );
				sceneBuf.unbind( gl, false );
			}

			if ( type == FULL || type == LOAD )
			{
				CacheIoTiming.getIoTimeBudget().reset( iobudget );
				stacks.getCacheControl().prepareNextFrame();
			}

			offscreen.bind( gl, false );
			gl.glDisable( GL_DEPTH_TEST );
			sceneBuf.drawQuad( gl );
			VolumeRenderer.RepaintType rerender = renderer.draw( gl, type, sceneBuf, renderStacks, renderConverters, pv, maxRenderMillis );
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
}
