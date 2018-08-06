package tpietzsch.example2;

import bdv.cache.CacheControl;
import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import bdv.tools.InitializeViewerState;
import bdv.tools.ToggleDialogAction;
import bdv.tools.VisibilityAndGroupingDialog;
import bdv.tools.brightness.BrightnessDialog;
import bdv.tools.brightness.ConverterSetup;
import bdv.tools.brightness.MinMaxGroup;
import bdv.tools.brightness.SetupAssignments;
import bdv.tools.transformation.ManualTransformation;
import bdv.viewer.RequestRepaint;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.VisibilityAndGrouping;
import bdv.viewer.state.SourceGroup;
import bdv.viewer.state.SourceState;
import bdv.viewer.state.ViewerState;
import bdv.viewer.state.XmlIoViewerState;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import java.awt.BorderLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import mpicbg.spim.data.SpimDataException;
import net.imglib2.Interval;
import net.imglib2.cache.iotiming.CacheIoTiming;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.volatiles.VolatileUnsignedShortType;
import net.imglib2.util.LinAlgHelpers;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import tpietzsch.backend.jogl.JoglGpuContext;
import tpietzsch.example2.BvvRenderer.RepaintType;
import tpietzsch.multires.MultiResolutionStack3D;
import tpietzsch.multires.ResolutionLevel3D;
import tpietzsch.multires.SpimDataStacks;
import tpietzsch.offscreen.OffScreenFrameBuffer;
import tpietzsch.offscreen.OffScreenFrameBufferWithDepth;
import tpietzsch.scene.TexturedUnitCube;
import tpietzsch.util.InputFrame;
import tpietzsch.util.MatrixMath;
import tpietzsch.util.TransformHandler;

import static bdv.BigDataViewer.initSetups;
import static bdv.viewer.VisibilityAndGrouping.Event.VISIBILITY_CHANGED;
import static com.jogamp.opengl.GL.GL_DEPTH_TEST;
import static com.jogamp.opengl.GL.GL_LESS;
import static com.jogamp.opengl.GL.GL_RGB8;
import static com.jogamp.opengl.GL.GL_UNPACK_ALIGNMENT;
import static tpietzsch.example2.BvvRenderer.RepaintType.FULL;
import static tpietzsch.example2.BvvRenderer.RepaintType.LOAD;
import static tpietzsch.example2.BvvRenderer.RepaintType.NONE;

public class Example10 implements GLEventListener, RequestRepaint
{
	private final OffScreenFrameBuffer offscreen;

	private final BvvRenderer renderer;



	// ... RenderState ...
	private final List< MultiResolutionStack3D< VolatileUnsignedShortType > > renderStacks = new ArrayList<>();
	private final List< ConverterSetup > renderConverters = new ArrayList<>();
	private final Matrix4f pv = new Matrix4f();




	private final double screenPadding = 0;
	private final double dCam;
	private final double dClip;

	private final CacheControl cacheControl;
	private final Runnable frameRequestRepaint;


	// ... "pre-existing" scene...
	private final TexturedUnitCube[] cubes = new TexturedUnitCube[]{
			new TexturedUnitCube("imglib2.png" ),
			new TexturedUnitCube("fiji.png" ),
			new TexturedUnitCube("imagej2.png" ),
			new TexturedUnitCube("scijava.png" ),
			new TexturedUnitCube("container.jpg" )
	};
	static class CubeAndTransform {
		final TexturedUnitCube cube;
		final Matrix4f model;
		public CubeAndTransform( final TexturedUnitCube cube, final Matrix4f model )
		{
			this.cube = cube;
			this.model = model;
		}
	}
	private final ArrayList< CubeAndTransform > cubeAndTransforms = new ArrayList<>();
	private final OffScreenFrameBufferWithDepth sceneBuf;


	// ... BDV ...
	private final ViewerState state;
	private final VisibilityAndGrouping visibilityAndGrouping;
	private final SpimDataStacks stacks;
	private final ArrayList< ConverterSetup > converterSetups;
	private final JSlider sliderTime;
	private final ManualTransformation manualTransformation;
	private final SetupAssignments setupAssignments;


	public Example10(
			final SpimDataMinimal spimData,
			final Runnable frameRequestRepaint,
			final int renderWidth,
			final int renderHeight,
			final int ditherWidth,
			final int ditherStep,
			final int numDitherSamples,
			final int cacheBlockSize,
			final int maxCacheSizeInMB,
			final double dCam,
			final double dClip
	)
	{
		stacks = new SpimDataStacks( spimData );

		final int maxTimepoint = spimData.getSequenceDescription().getTimePoints().getTimePointsOrdered().size() - 1;
		final int numVolumes = spimData.getSequenceDescription().getViewSetupsOrdered().size();


		// ---- BDV stuff ----------------------------------------------------
		converterSetups = new ArrayList<>();
		final ArrayList< SourceAndConverter< ? > > sources = new ArrayList<>();
		initSetups( spimData, converterSetups, sources );
		manualTransformation = new ManualTransformation( sources );

		final int numGroups = 10;
		final ArrayList< SourceGroup > groups = new ArrayList<>( numGroups );
		for ( int i = 0; i < numGroups; ++i )
			groups.add( new SourceGroup( "group " + Integer.toString( i + 1 ) ) );
		state = new ViewerState( sources, groups, maxTimepoint + 1 );
		for ( int i = Math.min( numGroups, sources.size() ) - 1; i >= 0; --i )
			state.getSourceGroups().get( i ).addSource( i );

		setupAssignments = new SetupAssignments( converterSetups, 0, 65535 );
		if ( setupAssignments.getMinMaxGroups().size() > 0 )
		{
			final MinMaxGroup group = setupAssignments.getMinMaxGroups().get( 0 );
			for ( final ConverterSetup setup : setupAssignments.getConverterSetups() )
				setupAssignments.moveSetupToGroup( setup, group );
		}

		for ( final ConverterSetup setup : converterSetups )
			setup.setViewer( this );

		visibilityAndGrouping = new VisibilityAndGrouping( state );
		visibilityAndGrouping.addUpdateListener( e -> {
			if ( e.id == VISIBILITY_CHANGED )
				requestRepaint();
		} );

		sliderTime = new JSlider( SwingConstants.HORIZONTAL, 0, maxTimepoint, 0 );
		sliderTime.addChangeListener( e -> {
			if ( e.getSource().equals( sliderTime ) )
				setTimepoint( sliderTime.getValue() );
		} );
		// -------------------------------------------------------------------


		this.cacheControl = stacks.getCacheControl();
		this.frameRequestRepaint = frameRequestRepaint;
		sceneBuf = new OffScreenFrameBufferWithDepth( renderWidth, renderHeight, GL_RGB8 );
		offscreen = new OffScreenFrameBuffer( renderWidth, renderHeight, GL_RGB8 );

		this.dCam = dCam;
		this.dClip = dClip;

		renderer = new BvvRenderer(
				renderWidth,
				renderHeight,
				ditherWidth,
				ditherStep,
				numDitherSamples,
				cacheBlockSize,
				maxCacheSizeInMB );
	}

	@Override
	public void init( final GLAutoDrawable drawable )
	{
		final GL3 gl = drawable.getGL().getGL3();
		gl.glPixelStorei( GL_UNPACK_ALIGNMENT, 2 );
	}

	@Override
	public void dispose( final GLAutoDrawable drawable )
	{}

	private double screenWidth = 640;
	private double screenHeight = 480;


	class Repaint
	{
		private RepaintType type;

		public Repaint()
		{
			this.type = FULL;
		}

		public synchronized void request( RepaintType type )
		{
			if ( this.type.ordinal() < type.ordinal() )
			{
				this.type = type;
				frameRequestRepaint.run();
			}
		}

		public synchronized RepaintType getAndClear()
		{
			RepaintType t = type;
			type = NONE;
			return t;
		}
	}

	private final Repaint repaint = new Repaint();

	@Override
	public void display( final GLAutoDrawable drawable )
	{
		final GL3 gl = drawable.getGL().getGL3();
		final JoglGpuContext context = JoglGpuContext.get( gl );

		final RepaintType type = repaint.getAndClear();

		if ( type == FULL )
		{
			setRenderState();

			sceneBuf.bind( gl );
			gl.glEnable( GL_DEPTH_TEST );
			gl.glDepthFunc( GL_LESS );
			synchronized ( state )
			{
				for ( CubeAndTransform cubeAndTransform : cubeAndTransforms )
				{
					cubeAndTransform.cube.draw( gl, new Matrix4f( pv ).mul( cubeAndTransform.model ) );
				}
			}
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
		RepaintType rerender = renderer.draw( gl, type, sceneBuf, renderStacks, renderConverters, pv, maxRenderMillis );
		repaint.request( rerender );
		offscreen.unbind( gl, false );
		offscreen.drawQuad( gl );
	}

	@Override
	public void requestRepaint()
	{
		repaint.request( FULL );
	}

	private final long[] iobudget = new long[] { 100L * 1000000L, 10L * 1000000L };

	private final long maxRenderMillis = 30;

	@Override
	public void reshape( final GLAutoDrawable drawable, final int x, final int y, final int width, final int height )
	{
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
			MatrixMath.screenPerspective( dCam, dClip, screenWidth, screenHeight, screenPadding, pv ).mul( view );

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

	// -------------------------------------------------------------------------------------------------------
	// BDV ViewerPanel equivalents

	/**
	 * Set the viewer transform.
	 */
	public synchronized void setCurrentViewerTransform( final AffineTransform3D viewerTransform )
	{
		state.setViewerTransform( viewerTransform );
		requestRepaint();
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
//			for ( final TimePointListener l : timePointListeners )
//				l.timePointChanged( timepoint );
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

	public VisibilityAndGrouping getVisibilityAndGrouping()
	{
		return visibilityAndGrouping;
	}

	public void loadSettings( final String xmlFilename ) throws IOException, JDOMException
	{
		final SAXBuilder sax = new SAXBuilder();
		final Document doc = sax.build( xmlFilename );
		final Element root = doc.getRootElement();
		final XmlIoViewerState io = new XmlIoViewerState();
		io.restoreFromXml( root.getChild( io.getTagName() ), state );
		setupAssignments.restoreFromXml( root );
		manualTransformation.restoreFromXml( root );
		System.out.println( "Example9.loadSettings" );
	}

	public boolean tryLoadSettings( final String xmlFilename )
	{
		if ( xmlFilename.endsWith( ".xml" ) )
		{
			final String settings = xmlFilename.substring( 0, xmlFilename.length() - ".xml".length() ) + ".settings" + ".xml";
			final File proposedSettingsFile = new File( settings );
			if ( proposedSettingsFile.isFile() )
			{
				try
				{
					loadSettings( settings );
					return true;
				}
				catch ( final Exception e )
				{
					e.printStackTrace();
				}
			}
		}
		return false;
	}

	// -------------------------------------------------------------------------------------------------------

	private Random random = new Random();

	void removeRandomCube()
	{
		synchronized ( state )
		{
			if ( !cubeAndTransforms.isEmpty() )
				cubeAndTransforms.remove( random.nextInt( cubeAndTransforms.size() ) );
		}
		requestRepaint();
	}

	void addRandomCube()
	{
		final AffineTransform3D sourceToWorld = new AffineTransform3D();
		final Interval interval;
		synchronized ( state )
		{
			final int t = state.getCurrentTimepoint();
			final SourceState< ? > source = state.getSources().get( state.getCurrentSource() );
			source.getSpimSource().getSourceTransform( t, 0, sourceToWorld );
			interval = source.getSpimSource().getSource( t, 0 );
		}

		final double[] zero = new double[ 3 ];
		final double[] tzero = new double[ 3 ];
		for ( int d = 0; d < 3; ++d )
			zero[ d ] = interval.min( d );
		sourceToWorld.apply( zero, tzero );

		final double[] one = new double[ 3 ];
		final double[] tone = new double[ 3 ];
		final double[] size = new double[ 3 ];
		for ( int i = 0; i < 3; ++i )
		{
			for ( int d = 0; d < 3; ++d )
				one[ d ] = d == i ? interval.max( d ) + 1 : interval.min( d );
			sourceToWorld.apply( one, tone );
			LinAlgHelpers.subtract( tone, tzero, tone );
			size[ i ] = LinAlgHelpers.length( tone );
		}
		TexturedUnitCube cube = cubes[ random.nextInt( cubes.length ) ];
		Matrix4f model = new Matrix4f()
				.translation(
						( float ) ( tzero[ 0 ] + random.nextDouble() * size[ 0 ] ),
						( float ) ( tzero[ 1 ] + random.nextDouble() * size[ 1 ] ),
						( float ) ( tzero[ 2 ] + random.nextDouble() * size[ 1 ] ) )
				.scale(
						( float ) ( ( random.nextDouble() + 1 ) * size[ 0 ] * 0.05 )	)
				.rotate(
						( float ) ( random.nextDouble() * Math.PI ),
						new Vector3f( random.nextFloat(), random.nextFloat(), random.nextFloat() ).normalize()
				);

		synchronized ( state )
		{
			cubeAndTransforms.add( new CubeAndTransform( cube, model ) );
		}
		requestRepaint();
	}


	public static void run(
			final String xmlFilename,
			final int windowWidth,
			final int windowHeight,
			final int renderWidth,
			final int renderHeight,
			final int ditherWidth,
			final int numDitherSamples,
			final int cacheBlockSize,
			final int maxCacheSizeInMB,
			final double dCam,
			final double dClip ) throws SpimDataException
	{
		final SpimDataMinimal spimData = new XmlIoSpimDataMinimal().load( xmlFilename );
		final SpimDataStacks stacks = new SpimDataStacks( spimData );

		final int maxTimepoint = spimData.getSequenceDescription().getTimePoints().getTimePointsOrdered().size() - 1;

		final int ditherStep;
		switch ( ditherWidth )
		{
		case 1:
			ditherStep = 1;
			break;
		case 2:
			ditherStep = 3;
			break;
		case 3:
			ditherStep = 5;
			break;
		case 4:
			ditherStep = 9;
			break;
		case 5:
			ditherStep = 11;
			break;
		case 6:
			ditherStep = 19;
			break;
		case 7:
			ditherStep = 23;
			break;
		case 8:
			ditherStep = 29;
			break;
		default:
			throw new IllegalArgumentException( "unsupported dither width" );
		}

		final InputFrame frame = new InputFrame( "Example10", windowWidth, windowHeight );
		InputFrame.DEBUG = false;
		final Example10 glPainter = new Example10(
				spimData,
				frame::requestRepaint,
				renderWidth,
				renderHeight,
				ditherWidth,
				ditherStep,
				numDitherSamples,
				cacheBlockSize,
				maxCacheSizeInMB,
				dCam,
				dClip );
		frame.setGlEventListener( glPainter );
		if ( glPainter.state.getNumTimepoints() > 1 )
		{
			frame.getFrame().getContentPane().add( glPainter.sliderTime, BorderLayout.SOUTH );
			frame.getFrame().pack();
		}

		final TransformHandler tf = frame.setupDefaultTransformHandler( glPainter::setCurrentViewerTransform, () -> {} );

		NavigationActions10.installActionBindings( frame.getKeybindings(), glPainter, new InputTriggerConfig() );
		final BrightnessDialog brightnessDialog = new BrightnessDialog( frame.getFrame(), glPainter.setupAssignments );
		frame.getDefaultActions().namedAction( new ToggleDialogAction( "toggle brightness dialog", brightnessDialog ), "S" );

		final VisibilityAndGroupingDialog activeSourcesDialog = new VisibilityAndGroupingDialog( frame.getFrame(), glPainter.visibilityAndGrouping );
		frame.getDefaultActions().namedAction( new ToggleDialogAction( "toggle active sources dialog", activeSourcesDialog ), "F6" );

		final AffineTransform3D resetTransform = InitializeViewerState.initTransform( windowWidth, windowHeight, false, glPainter.state );
		tf.setTransform( resetTransform );
		frame.getDefaultActions().runnableAction( () -> {
			tf.setTransform( resetTransform );
		}, "reset transform", "R" );

		frame.getDefaultActions().runnableAction( glPainter::addRandomCube, "add random cube", "B" );
		frame.getDefaultActions().runnableAction( glPainter::removeRandomCube, "remove random cube", "shift B" );

		frame.getCanvas().addComponentListener( new ComponentAdapter()
		{
			@Override
			public void componentResized( final ComponentEvent e )
			{
				final int w = frame.getCanvas().getWidth();
				final int h = frame.getCanvas().getHeight();
				tf.setCanvasSize( w, h, true );
				glPainter.screenWidth = w;
				glPainter.screenHeight = h;
				glPainter.requestRepaint();
			}
		} );


		if ( ! glPainter.tryLoadSettings( xmlFilename ) )
			InitializeViewerState.initBrightness( 0.001, 0.999, glPainter.state, glPainter.setupAssignments );
		activeSourcesDialog.update();
		glPainter.requestRepaint();
		frame.show();

//		// print fps
//		FPSAnimator animator = new FPSAnimator( frame.getCanvas(), 200 );
//		animator.setUpdateFPSFrames(100, System.out );
//		animator.start();
	}

	public static void main( final String[] args ) throws SpimDataException
	{
		final String xmlFilename = "/Users/pietzsch/workspace/data/111010_weber_full.xml";
//		final String xmlFilename = "/Users/pietzsch/Desktop/data/TGMM_METTE/Pdu_H2BeGFP_CAAXmCherry_0123_20130312_192018.corrected/dataset_hdf5.xml";
//		final String xmlFilename = "/Users/pietzsch/Desktop/data/MAMUT/MaMuT_demo_dataset/MaMuT_Parhyale_demo.xml";

		final int windowWidth = 640;
		final int windowHeight = 480;
		final int renderWidth = 640;
		final int renderHeight = 480;
		final int ditherWidth = 8;
		final int numDitherSamples = 8;
		final int cacheBlockSize = 32;
		final int maxCacheSizeInMB = 300;
		final double dCam = 2000;
		final double dClip = 1000;

		run( xmlFilename, windowWidth, windowHeight, renderWidth, renderHeight, ditherWidth, numDitherSamples, cacheBlockSize, maxCacheSizeInMB, dCam, dClip );
	}
}
