package tpietzsch.example2;

import bdv.cache.CacheControl;
import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.nio.Buffer;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;
import mpicbg.spim.data.SpimDataException;
import net.imglib2.FinalInterval;
import net.imglib2.cache.iotiming.CacheIoTiming;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.volatiles.VolatileUnsignedShortType;
import net.imglib2.util.Intervals;
import org.joml.Matrix4f;
import tpietzsch.backend.jogl.JoglGpuContext;
import tpietzsch.blocks.ByteUtils;
import tpietzsch.cache.CacheSpec;
import tpietzsch.cache.FillTask;
import tpietzsch.cache.PboChain;
import tpietzsch.cache.ProcessFillTasks;
import tpietzsch.cache.TextureCache;
import tpietzsch.multires.MultiResolutionStack3D;
import tpietzsch.multires.SpimDataStacks;
import tpietzsch.offscreen.OffScreenFrameBuffer;
import tpietzsch.shadergen.DefaultShader;
import tpietzsch.shadergen.Shader;
import tpietzsch.shadergen.Uniform3f;
import tpietzsch.shadergen.Uniform3fv;
import tpietzsch.shadergen.generate.Segment;
import tpietzsch.shadergen.generate.SegmentTemplate;
import tpietzsch.shadergen.generate.SegmentedShader;
import tpietzsch.shadergen.generate.SegmentedShaderBuilder;
import tpietzsch.util.InputFrame;
import tpietzsch.util.MatrixMath;
import tpietzsch.util.ScreenPlane;
import tpietzsch.util.Syncd;
import tpietzsch.util.TransformHandler;
import tpietzsch.util.WireframeBox;

import static com.jogamp.opengl.GL.GL_COLOR_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_DEPTH_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_DEPTH_TEST;
import static com.jogamp.opengl.GL.GL_RGB8;
import static com.jogamp.opengl.GL.GL_TEXTURE0;
import static com.jogamp.opengl.GL.GL_TEXTURE1;
import static com.jogamp.opengl.GL.GL_UNPACK_ALIGNMENT;
import static com.jogamp.opengl.GL.GL_UNSIGNED_SHORT;
import static com.jogamp.opengl.GL2ES2.GL_RED;
import static com.jogamp.opengl.GL2ES2.GL_TEXTURE_3D;
import static tpietzsch.backend.Texture.InternalFormat.R16;

public class Example3 implements GLEventListener
{
	private final OffScreenFrameBuffer offscreen;

	private final Shader prog;

	private final SegmentedShader progvol;

	private WireframeBox box;

	private ScreenPlane screenPlane;

	private final CacheSpec cacheSpec = new CacheSpec( R16, new int[] { 32, 32, 32 } );

	private static final int NUM_BLOCK_SCALES = 10;

	private LookupTextureARGB lookupTexture;
	private VolumeBlocks volume;

	private final TextureCache textureCache;
	private final PboChain pboChain;
	private final ForkJoinPool forkJoinPool;


	final Syncd< AffineTransform3D > worldToScreen = Syncd.affine3D();

	final AtomicReference< MultiResolutionStack3D< VolatileUnsignedShortType > > aMultiResolutionStack = new AtomicReference<>();

	private MultiResolutionStack3D< VolatileUnsignedShortType > multiResolutionStack;

	private int viewportWidth = 100;

	private int viewportHeight = 100;

	enum Mode { VOLUME, SLICE }

	private Mode mode = Mode.VOLUME;

	private boolean freezeRequiredBlocks = false;

	private final CacheControl cacheControl;

	private final Runnable requestRepaint;

	public Example3( final CacheControl cacheControl, final Runnable requestRepaint )
	{
		this.cacheControl = cacheControl;
		this.requestRepaint = requestRepaint;
		offscreen = new OffScreenFrameBuffer( 640, 480, GL_RGB8 );

		final int maxMemoryInMB = 200;
		final int[] cacheGridDimensions = TextureCache.findSuitableGridSize( cacheSpec, maxMemoryInMB );
		textureCache = new TextureCache( cacheGridDimensions, cacheSpec );
		pboChain = new PboChain( 5, 100, textureCache );
		final int parallelism = Math.max( 1, Runtime.getRuntime().availableProcessors() / 2 );
		forkJoinPool = new ForkJoinPool( parallelism );
		lookupTexture = new LookupTextureARGB();
		volume = new VolumeBlocks( textureCache );

		final Segment ex1vp = new SegmentTemplate("ex1.vp" ).instantiate();
		final Segment ex1fp = new SegmentTemplate("ex1.fp" ).instantiate();
		prog = new DefaultShader( ex1vp.getCode(), ex1fp.getCode() );

		final SegmentTemplate templateIntersectBox = new SegmentTemplate(
				"intersectbox.fp" );
		final Segment intersectBox = templateIntersectBox.instantiate();
		final SegmentTemplate templateBlkVol = new SegmentTemplate(
				"blkvol.fp",
				"im", "sourcemin", "sourcemax", "intersectBoundingBox",
				"lutSampler", "blockScales", "lutScale", "lutOffset", "blockTexture" );
		final Segment blkVol1 = templateBlkVol.instantiate();
		final SegmentTemplate templateEx3Vol = new SegmentTemplate(
				"ex3vol.fp",
				"intersectBoundingBox", "blockTexture" );
		final Segment ex3Vol = templateEx3Vol.instantiate()
				.bind( "intersectBoundingBox", blkVol1, "intersectBoundingBox" )
				.bind( "blockTexture", blkVol1, "blockTexture" );
		progvol = new SegmentedShaderBuilder()
				.fragment( intersectBox )
				.fragment( blkVol1 )
				.fragment( ex3Vol )
				.vertex( ex1vp )
				.build();

		final StringBuilder vertexShaderCode = progvol.getVertexShaderCode();
		System.out.println( "vertexShaderCode = " + vertexShaderCode );
		System.out.println( "\n\n--------------------------------\n\n");
		final StringBuilder fragementShaderCode = progvol.getFragementShaderCode();
		System.out.println( "fragementShaderCode = " + fragementShaderCode );
		System.out.println( "\n\n--------------------------------\n\n");
	}

	@Override
	public void init( final GLAutoDrawable drawable )
	{
		final GL3 gl = drawable.getGL().getGL3();
		gl.glPixelStorei( GL_UNPACK_ALIGNMENT, 2 );

		box = new WireframeBox();
		screenPlane = new ScreenPlane();
		screenPlane.updateVertices( gl, new FinalInterval( 640, 480 ) );

		gl.glEnable( GL_DEPTH_TEST );
	}

	@Override
	public void dispose( final GLAutoDrawable drawable )
	{}

	private final double screenPadding = 0;
	private final double dCam = 2000;
	private final double dClip = 1000;
	private double screenWidth = 640;
	private double screenHeight = 480;

	@Override
	public void display( final GLAutoDrawable drawable )
	{
		final GL3 gl = drawable.getGL().getGL3();

		gl.glClearColor( 0.2f, 0.3f, 0.3f, 1.0f );
		gl.glClear( GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT );

		multiResolutionStack = aMultiResolutionStack.get();
		if ( multiResolutionStack == null )
			return;

		offscreen.bind( gl );
		final Matrix4f model = MatrixMath.affine( multiResolutionStack.getSourceTransform(), new Matrix4f() );
		final Matrix4f view = MatrixMath.affine( worldToScreen.get(), new Matrix4f() );
		final Matrix4f projection = MatrixMath.screenPerspective( dCam, dClip, screenWidth, screenHeight, screenPadding, new Matrix4f() );

		final JoglGpuContext context = JoglGpuContext.get( gl );

		final Matrix4f pv = new Matrix4f( projection ).mul( view );
		if ( !freezeRequiredBlocks )
		{
			updateBlocks( context, pv );
		}

		// TODO: revise: model shouldn't be needed here
		prog.getUniformMatrix4f( "model" ).set( model );
		prog.getUniformMatrix4f( "view" ).set( view );
		prog.getUniformMatrix4f( "projection" ).set( projection );
		prog.getUniform4f( "color" ).set( 1.0f, 0.5f, 0.2f, 1.0f );

		prog.use( context );
		prog.setUniforms( context );
		box.updateVertices( gl, multiResolutionStack.resolutions().get( 0 ).getImage() );
		box.draw( gl );

		progvol.use( context );
		progvol.getUniformMatrix4f( "model" ).set( new Matrix4f() );
		progvol.getUniformMatrix4f( "view" ).set( new Matrix4f() );
		progvol.getUniformMatrix4f( "projection" ).set( projection );
		progvol.getUniform2f( "viewportSize" ).set( viewportWidth, viewportHeight );
		progvol.getUniformMatrix4f( "ipv" ).set( pv.invert( new Matrix4f() ) );

		// for source box intersection
		progvol.getUniformMatrix4f( "im" ).set( volume.getIms() );
		progvol.getUniform3f( "sourcemin" ).set( volume.getSourceLevelMin() );
		progvol.getUniform3f( "sourcemax" ).set( volume.getSourceLevelMax() );

		// textures
		progvol.getUniform1i( "lutSampler" ).set( 0 );
		progvol.getUniform1i( "volumeCache" ).set( 1 );

		// TODO: fix hacks
//			lookupTexture.bindTextures( gl, GL_TEXTURE0 );
			gl.glActiveTexture( GL_TEXTURE0 );
			gl.glBindTexture( GL_TEXTURE_3D, context.getTextureIdHack( lookupTexture ) );

		// TODO: fix hacks
//			blockCache.bindTextures( gl, GL_TEXTURE1 );
			gl.glActiveTexture( GL_TEXTURE1 );
			gl.glBindTexture( GL_TEXTURE_3D, context.getTextureIdHack( textureCache ) );

		// TODO: fix hacks (initialize OOB block init)
			final int[] ts = cacheSpec.paddedBlockSize();
			final Buffer oobBuffer = Buffers.newDirectShortBuffer( ( int ) Intervals.numElements( ts ) );
			ByteUtils.setShorts( ( short ) 0x0fff, ByteUtils.addressOf( oobBuffer ), ( int ) Intervals.numElements( ts ) );
			gl.glTexSubImage3D( GL_TEXTURE_3D, 0, 0, 0, 0, ts[ 0 ], ts[ 1 ], ts[ 2 ], GL_RED, GL_UNSIGNED_SHORT, oobBuffer );

		// comes from CacheSpec
		progvol.getUniform3f( "blockSize" ).set( cacheSpec.blockSize()[ 0 ], cacheSpec.blockSize()[ 1 ], cacheSpec.blockSize()[ 2 ] );
		progvol.getUniform3f( "paddedBlockSize" ).set( cacheSpec.paddedBlockSize()[ 0 ], cacheSpec.paddedBlockSize()[ 1 ], cacheSpec.paddedBlockSize()[ 2 ] );
		progvol.getUniform3f( "cachePadOffset" ).set( cacheSpec.padOffset()[ 0 ], cacheSpec.padOffset()[ 1 ], cacheSpec.padOffset()[ 2 ] );

		// comes from TextureCache -- not really necessary
		progvol.getUniform3f( "cacheSize" ).set( textureCache.texWidth(), textureCache.texHeight(), textureCache.texDepth() );

		// comes from LUT
		final Uniform3fv uniformBlockScales = progvol.getUniform3fv( "blockScales" );
		final Uniform3f uniformLutScale = progvol.getUniform3f( "lutScale" );
		final Uniform3f uniformLutOffset = progvol.getUniform3f( "lutOffset" );
		volume.setUniforms( lookupTexture, NUM_BLOCK_SCALES, uniformBlockScales, uniformLutScale, uniformLutOffset );

		final double min = 962; // weber
		final double max = 6201;
//		final double min = 33.8; // tassos channel 0
//		final double max = 1517.2;
//		final double min = 10.0; // tassos channel 1
//		final double max = 3753.0;
//		final double min = 0; // mette channel 1
//		final double max = 120;
		final double fmin = min / 0xffff;
		final double fmax = max / 0xffff;
		final double s = 1.0 / ( fmax - fmin );
		final double o = -fmin * s;
		progvol.getUniform1f( "intensity_offset" ).set( ( float ) o );
		progvol.getUniform1f( "intensity_scale" ).set( ( float ) s );

		screenPlane.updateVertices( gl, new FinalInterval( ( int ) screenWidth, ( int ) screenHeight ) );
//		screenPlane.draw( gl );

		progvol.getUniform2f( "viewportSize" ).set( offscreen.getWidth(), offscreen.getHeight() );
		progvol.setUniforms( context );
		screenPlane.draw( gl );
		offscreen.unbind( gl, false );
		offscreen.drawQuad( gl );
	}

	private final long[] iobudget = new long[] { 100L * 1000000L, 10L * 1000000L };

	private void updateBlocks( final JoglGpuContext context, final Matrix4f pv )
	{
		CacheIoTiming.getIoTimeBudget().reset( iobudget );
		cacheControl.prepareNextFrame();

		final int vw = offscreen.getWidth();
//		final int vw = viewportWidth;

		volume.init( multiResolutionStack, vw, pv );
		final List< FillTask > fillTasks = volume.getFillTasks();

		try
		{
			ProcessFillTasks.parallel( textureCache, pboChain, context, forkJoinPool, fillTasks );
		}
		catch ( final InterruptedException e )
		{
			e.printStackTrace();
		}

		boolean needsRepaint = !volume.makeLut( lookupTexture );
		if ( needsRepaint )
			requestRepaint.run();

		lookupTexture.upload( context );
	}

	@Override
	public void reshape( final GLAutoDrawable drawable, final int x, final int y, final int width, final int height )
	{
		viewportWidth = width;
		viewportHeight = height;
	}

	private void toggleVolumeSliceMode()
	{
		if ( mode == Mode.VOLUME )
			mode = Mode.SLICE;
		else
			mode = Mode.VOLUME;
	}

	private void toggleFreezeRequiredBlocks()
	{
		freezeRequiredBlocks = !freezeRequiredBlocks;
	}

	int currentTimepoint = 0;

	int currentSetup = 0;

	@SuppressWarnings( "unchecked" )
	void updateCurrentStack(final SpimDataStacks stacks)
	{
		aMultiResolutionStack.set(
				( MultiResolutionStack3D< VolatileUnsignedShortType > )
				stacks.getStack(
						stacks.timepointId( currentTimepoint ),
						stacks.setupId( currentSetup ),
						true ) );
		requestRepaint.run();
	}

	public static void main( final String[] args ) throws SpimDataException
	{
		final String xmlFilename = "/Users/pietzsch/workspace/data/111010_weber_full.xml";
//		final String xmlFilename = "/Users/pietzsch/Desktop/data/TGMM_METTE/Pdu_H2BeGFP_CAAXmCherry_0123_20130312_192018.corrected/dataset_hdf5.xml";
//		final String xmlFilename = "/Users/pietzsch/Desktop/data/MAMUT/MaMuT_demo_dataset/MaMuT_Parhyale_demo.xml";
		final SpimDataMinimal spimData = new XmlIoSpimDataMinimal().load( xmlFilename );
		final SpimDataStacks stacks = new SpimDataStacks( spimData );

		final int maxTimepoint = spimData.getSequenceDescription().getTimePoints().getTimePointsOrdered().size() - 1;

		final InputFrame frame = new InputFrame( "Example3", 640, 480 );
		InputFrame.DEBUG = false;
		final Example3 glPainter = new Example3( stacks.getCacheControl(), frame::requestRepaint );
		frame.setGlEventListener( glPainter );
		final TransformHandler tf = frame.setupDefaultTransformHandler( glPainter.worldToScreen::set );
		frame.getDefaultActions().runnableAction( () -> {
			tf.setTransform( new AffineTransform3D() );
		}, "reset transform", "R" );
		frame.getDefaultActions().runnableAction( () -> {
			glPainter.toggleVolumeSliceMode();
			frame.requestRepaint();
		}, "volume/slice mode", "M" );
		frame.getDefaultActions().runnableAction( () -> {
			glPainter.toggleFreezeRequiredBlocks();
			frame.requestRepaint();
		}, "freeze/unfreeze required block computation", "F" );
		frame.getDefaultActions().runnableAction( () -> {
			glPainter.currentTimepoint = Math.max( 0, glPainter.currentTimepoint - 1 );
			System.out.println( "currentTimepoint = " + glPainter.currentTimepoint );
			glPainter.updateCurrentStack( stacks );
		}, "previous timepoint", "OPEN_BRACKET" );
		frame.getDefaultActions().runnableAction( () -> {
			glPainter.currentTimepoint = Math.min( maxTimepoint, glPainter.currentTimepoint + 1 );
			System.out.println( "currentTimepoint = " + glPainter.currentTimepoint );
			glPainter.updateCurrentStack( stacks );
		}, "next timepoint", "CLOSE_BRACKET" );
		frame.getDefaultActions().runnableAction( () -> {
			glPainter.currentSetup = 0;
			System.out.println( "currentSetup = " + glPainter.currentSetup );
			glPainter.updateCurrentStack( stacks );
		}, "setup 1", "1" );
		frame.getDefaultActions().runnableAction( () -> {
			glPainter.currentSetup = 1;
			System.out.println( "currentSetup = " + glPainter.currentSetup );
			glPainter.updateCurrentStack( stacks );
		}, "setup 2", "2" );
		frame.getDefaultActions().runnableAction( () -> {
			glPainter.currentSetup = 2;
			System.out.println( "currentSetup = " + glPainter.currentSetup );
			glPainter.updateCurrentStack( stacks );
		}, "setup 3", "3" );
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
				frame.requestRepaint();
			}
		} );
		glPainter.updateCurrentStack( stacks );
		frame.show();

//		// print fps
//		FPSAnimator animator = new FPSAnimator( frame.getCanvas(), 200 );
//		animator.setUpdateFPSFrames(100, System.out );
//		animator.start();
	}
}
