package tpietzsch.example2;

import bdv.cache.CacheControl;
import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import bdv.tools.ToggleDialogAction;
import bdv.tools.VisibilityAndGroupingDialog;
import bdv.tools.brightness.BrightnessDialog;
import bdv.tools.brightness.ConverterSetup;
import bdv.tools.brightness.MinMaxGroup;
import bdv.tools.brightness.SetupAssignments;
import bdv.viewer.RequestRepaint;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.VisibilityAndGrouping;
import bdv.viewer.state.SourceGroup;
import bdv.viewer.state.ViewerState;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import java.awt.BorderLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.nio.Buffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import mpicbg.spim.data.SpimDataException;
import net.imglib2.cache.iotiming.CacheIoTiming;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.volatiles.VolatileUnsignedShortType;
import net.imglib2.util.Intervals;
import net.imglib2.util.StopWatch;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import tpietzsch.backend.jogl.JoglGpuContext;
import tpietzsch.blocks.ByteUtils;
import tpietzsch.cache.CacheSpec;
import tpietzsch.cache.FillTask;
import tpietzsch.cache.PboChain;
import tpietzsch.cache.ProcessFillTasks;
import tpietzsch.cache.TextureCache;
import tpietzsch.dither.DitherBuffer;
import tpietzsch.multires.MultiResolutionStack3D;
import tpietzsch.multires.SpimDataStacks;
import tpietzsch.offscreen.OffScreenFrameBuffer;
import tpietzsch.offscreen.OffScreenFrameBufferWithDepth;
import tpietzsch.scene.TexturedUnitCube;
import tpietzsch.shadergen.DefaultShader;
import tpietzsch.shadergen.Shader;
import tpietzsch.shadergen.generate.Segment;
import tpietzsch.shadergen.generate.SegmentTemplate;
import tpietzsch.util.DefaultQuad;
import tpietzsch.util.InputFrame;
import tpietzsch.util.MatrixMath;
import tpietzsch.util.TransformHandler;
import tpietzsch.util.WireframeBox;

import static bdv.BigDataViewer.initSetups;
import static bdv.viewer.VisibilityAndGrouping.Event.VISIBILITY_CHANGED;
import static com.jogamp.opengl.GL.GL_ALWAYS;
import static com.jogamp.opengl.GL.GL_BLEND;
import static com.jogamp.opengl.GL.GL_COLOR_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_DEPTH_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_DEPTH_TEST;
import static com.jogamp.opengl.GL.GL_LESS;
import static com.jogamp.opengl.GL.GL_ONE_MINUS_SRC_ALPHA;
import static com.jogamp.opengl.GL.GL_RGB8;
import static com.jogamp.opengl.GL.GL_SRC_ALPHA;
import static com.jogamp.opengl.GL.GL_UNPACK_ALIGNMENT;
import static com.jogamp.opengl.GL.GL_UNSIGNED_SHORT;
import static com.jogamp.opengl.GL2ES2.GL_RED;
import static com.jogamp.opengl.GL2ES2.GL_TEXTURE_3D;
import static tpietzsch.backend.Texture.InternalFormat.R16;
import static tpietzsch.example2.Example9.RepaintType.DITHER;
import static tpietzsch.example2.Example9.RepaintType.FULL;
import static tpietzsch.example2.Example9.RepaintType.LOAD;

public class Example9 implements GLEventListener, RequestRepaint
{
	private final OffScreenFrameBuffer offscreen;

	private final Shader prog;

	private final ArrayList< MultiVolumeShaderMip9 > progvols;
	private MultiVolumeShaderMip9 progvol;

	private final WireframeBox box;

	private final DefaultQuad quad;

	private final CacheSpec cacheSpec;
	private final TextureCache textureCache;
	private final PboChain pboChain;
	private final ForkJoinPool forkJoinPool;

	private final ArrayList< VolumeBlocks > volumes;

	private final ArrayList< MultiResolutionStack3D< VolatileUnsignedShortType > > renderStacks = new ArrayList<>();
	private final ArrayList< ConverterSetup > renderConverters = new ArrayList<>();
	private final AffineTransform3D renderTransformWorldToScreen = new AffineTransform3D();

	private int viewportWidth = 100;
	private int viewportHeight = 100;

	private final CacheControl cacheControl;
	private final Runnable frameRequestRepaint;


	// ... "pre-existing" scene...
	private final TexturedUnitCube cube = new TexturedUnitCube();
	private final OffScreenFrameBufferWithDepth sceneBuf;


	// ... dithering ...
	private final DitherBuffer dither;
	private final int numDitherSteps;


	// ... BDV ...
	private final ViewerState state;
	private final VisibilityAndGrouping visibilityAndGrouping;
	private final SpimDataStacks stacks;
	private final ArrayList< ConverterSetup > converterSetups;
	private final JSlider sliderTime;


	public Example9(
			final SpimDataMinimal spimData,
			final Runnable frameRequestRepaint,
			final int renderWidth,
			final int renderHeight,
			final int ditherWidth,
			final int ditherStep,
			final int numDitherSamples,
			final int cacheBlockSize
		)
	{
		stacks = new SpimDataStacks( spimData );

		final int maxTimepoint = spimData.getSequenceDescription().getTimePoints().getTimePointsOrdered().size() - 1;
		final int numVolumes = spimData.getSequenceDescription().getViewSetupsOrdered().size();



		converterSetups = new ArrayList<>();
		final ArrayList< SourceAndConverter< ? > > sources = new ArrayList<>();
		initSetups( spimData, converterSetups, sources );

		final int numGroups = 10;
		final ArrayList< SourceGroup > groups = new ArrayList<>( numGroups );
		for ( int i = 0; i < numGroups; ++i )
			groups.add( new SourceGroup( "group " + Integer.toString( i + 1 ) ) );
		state = new ViewerState( sources, groups, maxTimepoint + 1 );
		for ( int i = Math.min( numGroups, sources.size() ) - 1; i >= 0; --i )
			state.getSourceGroups().get( i ).addSource( i );

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


		this.cacheControl = stacks.getCacheControl();
		this.frameRequestRepaint = frameRequestRepaint;
		sceneBuf = new OffScreenFrameBufferWithDepth( renderWidth, renderHeight, GL_RGB8 );
		offscreen = new OffScreenFrameBuffer( renderWidth, renderHeight, GL_RGB8 );
		if ( ditherWidth == 1 )
		{
			dither = null;
			numDitherSteps = 1;
		}
		else
		{
			dither = new DitherBuffer( renderWidth, renderHeight, ditherWidth, ditherStep, numDitherSamples );
			numDitherSteps = dither.numSteps();
		}
		box = new WireframeBox();
		quad = new DefaultQuad();

		cacheSpec = new CacheSpec( R16, new int[] { 32, 32, 32 } );
		final int maxMemoryInMB = 300;
		final int[] cacheGridDimensions = TextureCache.findSuitableGridSize( cacheSpec, maxMemoryInMB );
		textureCache = new TextureCache( cacheGridDimensions, cacheSpec );
		pboChain = new PboChain( 5, 100, textureCache );
		final int parallelism = Math.max( 1, Runtime.getRuntime().availableProcessors() / 2 );
		forkJoinPool = new ForkJoinPool( parallelism );

		volumes = new ArrayList<>();
		for ( int i = 0; i < numVolumes; i++ )
			volumes.add( new VolumeBlocks( textureCache ) );

		final Segment ex1vp = new SegmentTemplate("ex1.vp" ).instantiate();
		final Segment ex1fp = new SegmentTemplate("ex1.fp" ).instantiate();
		prog = new DefaultShader( ex1vp.getCode(), ex1fp.getCode() );

		progvols = new ArrayList<>();
		progvols.add( null );
		for ( int i = 1; i <= numVolumes; ++i )
		{
			final MultiVolumeShaderMip9 progvol = new MultiVolumeShaderMip9( i, true, 1.0 );
			progvol.setTextureCache( textureCache );
			progvols.add( progvol );
		}
	}

	@Override
	public void init( final GLAutoDrawable drawable )
	{
		final GL3 gl = drawable.getGL().getGL3();
		gl.glPixelStorei( GL_UNPACK_ALIGNMENT, 2 );
		gl.glEnable( GL_DEPTH_TEST );
		gl.glEnable( GL_BLEND );
		gl.glBlendFunc( GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA );
	}

	@Override
	public void dispose( final GLAutoDrawable drawable )
	{}

	private final double screenPadding = 0;
	private final double dCam = 2000;
	private final double dClip = 1000;
	private double screenWidth = 640;
	private double screenHeight = 480;

	enum RepaintType
	{
		FULL,
		LOAD,
		DITHER
	}

	private class Repaint
	{
		private RepaintType next = FULL;

		synchronized void requestRepaint( RepaintType type )
		{
			switch ( type )
			{
			case FULL:
				next = FULL;
				break;
			case LOAD:
				if ( next != FULL )
					next = LOAD;
				break;
			case DITHER:
				break;
			}
			frameRequestRepaint.run();
		}

		synchronized RepaintType nextRepaint()
		{
			final RepaintType type = next;
			next = DITHER;
			return type;
		}
	}

	private final Repaint repaint = new Repaint();

	private int ditherStep = 0;

	private int targetDitherSteps = 0;

	@Override
	public void display( final GLAutoDrawable drawable )
	{
		final GL3 gl = drawable.getGL().getGL3();
		final JoglGpuContext context = JoglGpuContext.get( gl );

		final RepaintType type = repaint.nextRepaint();

		if ( type == FULL )
		{
			setRenderState();

			ditherStep = 0;
			targetDitherSteps = numDitherSteps;
		}
		else if ( type == LOAD )
		{
			targetDitherSteps = ditherStep + numDitherSteps;
		}

		if ( ditherStep != targetDitherSteps )
		{
			if ( type == FULL || type == LOAD )
			{
				gl.glClearColor( 0.2f, 0.3f, 0.3f, 1.0f );
				gl.glClear( GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT );

				final Matrix4f view = MatrixMath.affine( renderTransformWorldToScreen, new Matrix4f() );
				final Matrix4f projection = MatrixMath.screenPerspective( dCam, dClip, screenWidth, screenHeight, screenPadding, new Matrix4f() );
				final Matrix4f pv = new Matrix4f( projection ).mul( view );

				if ( type == FULL )
				{
					sceneBuf.bind( gl );
					gl.glEnable( GL_DEPTH_TEST );
					gl.glDepthFunc( GL_LESS );
					cube.draw( gl, new Matrix4f( pv ).translate( 200, 200, 50 ).scale( 100 ) );
					cube.draw( gl, new Matrix4f( pv ).translate( 500, 100, 100 ).scale( 100 ).rotate( 1f, new Vector3f( 1, 1, 0 ).normalize() ) );
					cube.draw( gl, new Matrix4f( pv ).translate( 300, 50, 150 ).scale( 100 ).rotate( 1f, new Vector3f( 1, 0, 1 ).normalize() ) );

//					// draw volume boxes
//					prog.use( context );
//					prog.getUniformMatrix4f( "view" ).set( view );
//					prog.getUniformMatrix4f( "projection" ).set( projection );
//					prog.getUniform4f( "color" ).set( 1.0f, 0.5f, 0.2f, 1.0f );
//					for ( int i = 0; i < renderStacks.size(); i++ )
//					{
//						final MultiResolutionStack3D< VolatileUnsignedShortType > stack = renderStacks.get( i );
//						prog.getUniformMatrix4f( "model" ).set( MatrixMath.affine( stack.getSourceTransform(), new Matrix4f() ) );
//						prog.setUniforms( context );
//						box.updateVertices( gl, stack.resolutions().get( 0 ).getImage() );
//						box.draw( gl );
//					}

					sceneBuf.unbind( gl, false );
				}

				updateBlocks( context, pv );

				// TODO: fix hacks (initialize OOB block init)
				context.bindTexture( textureCache );
				final int[] ts = cacheSpec.paddedBlockSize();
				final Buffer oobBuffer = Buffers.newDirectShortBuffer( ( int ) Intervals.numElements( ts ) );
				ByteUtils.setShorts( ( short ) 0, ByteUtils.addressOf( oobBuffer ), ( int ) Intervals.numElements( ts ) );
				gl.glTexSubImage3D( GL_TEXTURE_3D, 0, 0, 0, 0, ts[ 0 ], ts[ 1 ], ts[ 2 ], GL_RED, GL_UNSIGNED_SHORT, oobBuffer );

				double minWorldVoxelSize = Double.POSITIVE_INFINITY;
				progvol = progvols.get( renderStacks.size() );
				if ( progvol != null )
				{
					for ( int i = 0; i < renderStacks.size(); i++ )
					{
						progvol.setConverter( i, renderConverters.get( i ) );
						progvol.setVolume( i, volumes.get( i ) );
						minWorldVoxelSize = Math.min( minWorldVoxelSize, volumes.get( i ).getBaseLevelVoxelSizeInWorldCoordinates() );
					}
					progvol.setDepthTexture( sceneBuf.getDepthTexture() );
					progvol.setViewportWidth( offscreen.getWidth() );
					progvol.setProjectionViewMatrix( pv, minWorldVoxelSize );
				}
			}

			if ( dither != null && progvol != null )
			{
				dither.bind( gl );
				progvol.use( context );
				progvol.bindSamplers( context );
				gl.glDepthFunc( GL_ALWAYS );
				gl.glDisable( GL_BLEND );
				final StopWatch stopWatch = new StopWatch();
				stopWatch.start();
//				final int start = ditherStep;
				while ( ditherStep < targetDitherSteps )
				{
					progvol.setDither( dither, ditherStep % numDitherSteps );
					progvol.setUniforms( context );
					quad.draw( gl );
					gl.glFinish();
					++ditherStep;
					if ( stopWatch.nanoTime() > maxRenderNanos )
						break;
				}
//				final int steps = ditherStep - start;
//				stepList.add( steps );
//				if ( stepList.size() == 1000 )
//				{
//					for ( int step : stepList )
//						System.out.println( "step = " + step );
//					System.out.println();
//					stepList.clear();
//				}
				dither.unbind( gl );
			}
			else
			{
				offscreen.bind( gl, false );
				gl.glDisable( GL_DEPTH_TEST );
				sceneBuf.drawQuad( gl );
				if ( progvol != null )
				{
					gl.glEnable( GL_DEPTH_TEST );
					gl.glDepthFunc( GL_ALWAYS );
					gl.glEnable( GL_BLEND );
					progvol.use( context );
					progvol.bindSamplers( context );
					progvol.setEffectiveViewportSize( offscreen.getWidth(), offscreen.getHeight() );
					progvol.setUniforms( context );
					quad.draw( gl );
				}
				offscreen.unbind( gl, false );
				offscreen.drawQuad( gl );
			}
		}

		if ( dither != null )
		{
			offscreen.bind( gl, false );
			gl.glDisable( GL_DEPTH_TEST );
			sceneBuf.drawQuad( gl );
			if ( progvol != null )
			{
				gl.glEnable( GL_BLEND );
				final int stepsCompleted = Math.min( ditherStep, numDitherSteps );
				dither.dither( gl, stepsCompleted, offscreen.getWidth(), offscreen.getHeight() );
			}
			offscreen.unbind( gl, false );
			offscreen.drawQuad( gl );

			if ( ditherStep != targetDitherSteps )
				repaint.requestRepaint( DITHER );
		}
	}

//	private final ArrayList< Integer > stepList = new ArrayList<>();

	@Override
	public void requestRepaint()
	{
		repaint.requestRepaint( FULL );
	}

	private final long[] iobudget = new long[] { 100L * 1000000L, 10L * 1000000L };

	private final long maxRenderNanos = 30L * 1000000L;

	private void updateBlocks( final JoglGpuContext context, final Matrix4f pv )
	{
		CacheIoTiming.getIoTimeBudget().reset( iobudget );
		cacheControl.prepareNextFrame();

		final int vw = offscreen.getWidth();
//		final int vw = viewportWidth;

		final ArrayList< FillTask > fillTasks = new ArrayList<>();
		for ( int i = 0; i < renderStacks.size(); i++ )
		{
			final VolumeBlocks volume = volumes.get( i );
			volume.init( renderStacks.get( i ), vw, pv );
			fillTasks.addAll( volume.getFillTasks() );
		}

		try
		{
			ProcessFillTasks.parallel( textureCache, pboChain, context, forkJoinPool, fillTasks );
		}
		catch ( final InterruptedException e )
		{
			e.printStackTrace();
		}

		boolean needsRepaint = false;
		for ( int i = 0; i < renderStacks.size(); i++ )
		{
			final VolumeBlocks volume = volumes.get( i );
			final boolean complete = volume.makeLut();
			if ( !complete )
				needsRepaint = true;
			volume.getLookupTexture().upload( context );
		}

		if ( needsRepaint )
			repaint.requestRepaint( LOAD );
	}

	@Override
	public void reshape( final GLAutoDrawable drawable, final int x, final int y, final int width, final int height )
	{
		viewportWidth = width;
		viewportHeight = height;
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
			state.getViewerTransform( renderTransformWorldToScreen );
		}

		renderStacks.clear();
		renderConverters.clear();
		for( int i : visibleSourceIndices )
		{
			final MultiResolutionStack3D< VolatileUnsignedShortType > stack = ( MultiResolutionStack3D< VolatileUnsignedShortType > )
					stacks.getStack(
							stacks.timepointId( currentTimepoint ),
							stacks.setupId( i ),
							true );
			renderStacks.add( stack );
			final ConverterSetup converter = converterSetups.get( i );
			renderConverters.add( converter );
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

	// -------------------------------------------------------------------------------------------------------

	public static void run(
			final String xmlFilename,
			final int windowWidth,
			final int windowHeight,
			final int renderWidth,
			final int renderHeight,
			final int ditherWidth,
			final int numDitherSamples,
			final int cacheBlockSize
	) throws SpimDataException
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
		case 4:
			ditherStep = 9;
			break;
		case 8:
			ditherStep = 29;
			break;
		default:
			throw new IllegalArgumentException( "unsupported dither width" );
		}

		final InputFrame frame = new InputFrame( "Example9", windowWidth, windowHeight );
		InputFrame.DEBUG = false;
		final Example9 glPainter = new Example9(
				spimData,
				frame::requestRepaint,
				renderWidth,
				renderHeight,
				ditherWidth,
				ditherStep,
				numDitherSamples,
				cacheBlockSize );
		frame.setGlEventListener( glPainter );
		if ( glPainter.state.getNumTimepoints() > 1 )
		{
			frame.getFrame().getContentPane().add( glPainter.sliderTime, BorderLayout.SOUTH );
			frame.getFrame().pack();
		}

		final TransformHandler tf = frame.setupDefaultTransformHandler( glPainter::setCurrentViewerTransform, () -> {} );

		NavigationActions9.installActionBindings( frame.getKeybindings(), glPainter, new InputTriggerConfig() );
//		frame.getDefaultActions().runnableAction( () -> {
//			tf.setTransform( new AffineTransform3D() );
//		}, "reset transform", "R" );
//		frame.getDefaultActions().runnableAction( () -> {
//			glPainter.currentTimepoint = Math.max( 0, glPainter.currentTimepoint - 1 );
//			System.out.println( "currentTimepoint = " + glPainter.currentTimepoint );
//			glPainter.setRenderState( Arrays.asList( glPainter.currentSetup ) );
//		}, "previous timepoint", "OPEN_BRACKET" );
//		frame.getDefaultActions().runnableAction( () -> {
//			glPainter.currentTimepoint = Math.min( maxTimepoint, glPainter.currentTimepoint + 1 );
//			System.out.println( "currentTimepoint = " + glPainter.currentTimepoint );
//			glPainter.setRenderState( Arrays.asList( glPainter.currentSetup ) );
//		}, "next timepoint", "CLOSE_BRACKET" );
//		frame.getDefaultActions().runnableAction( () -> {
//			glPainter.currentSetup = 0;
//			System.out.println( "currentSetup = " + glPainter.currentSetup );
//			glPainter.setRenderState( Arrays.asList( glPainter.currentSetup ) );
//		}, "setup 1", "1" );
//		frame.getDefaultActions().runnableAction( () -> {
//			glPainter.currentSetup = 1;
//			System.out.println( "currentSetup = " + glPainter.currentSetup );
//			glPainter.setRenderState( Arrays.asList( glPainter.currentSetup ) );
//		}, "setup 2", "2" );
//		frame.getDefaultActions().runnableAction( () -> {
//			glPainter.currentSetup = 2;
//			System.out.println( "currentSetup = " + glPainter.currentSetup );
//			glPainter.setRenderState( Arrays.asList( glPainter.currentSetup ) );
//		}, "setup 3", "3" );

		for ( final ConverterSetup setup : glPainter.converterSetups )
		{
			setup.setDisplayRange( 962, 6201 ); // weber
			setup.setColor( new ARGBType( 0xffffffff ) );
			setup.setViewer( glPainter );
		}
//		glPainter.convs.get( 0 ).setColor( new ARGBType( 0xff8888 ) );
//		glPainter.convs.get( 1 ).setColor( new ARGBType( 0x88ff88 ) );
//		glPainter.convs.get( 2 ).setColor( new ARGBType( 0x8888ff ) );
		final SetupAssignments setupAssignments = new SetupAssignments( glPainter.converterSetups, 0, 65535 );
		if ( setupAssignments.getMinMaxGroups().size() > 0 )
		{
			final MinMaxGroup group = setupAssignments.getMinMaxGroups().get( 0 );
			for ( final ConverterSetup setup : setupAssignments.getConverterSetups() )
				setupAssignments.moveSetupToGroup( setup, group );
		}
		final BrightnessDialog brightnessDialog = new BrightnessDialog( frame.getFrame(), setupAssignments );
		frame.getDefaultActions().namedAction( new ToggleDialogAction( "toggle brightness dialog", brightnessDialog ), "S" );

		final VisibilityAndGroupingDialog activeSourcesDialog = new VisibilityAndGroupingDialog( frame.getFrame(), glPainter.visibilityAndGrouping );
		frame.getDefaultActions().namedAction( new ToggleDialogAction( "toggle active sources dialog", activeSourcesDialog ), "F6" );

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

		run( xmlFilename, windowWidth, windowHeight, renderWidth, renderHeight, ditherWidth, numDitherSamples, cacheBlockSize );
	}
}
