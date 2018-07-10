package tpietzsch.example2;

import bdv.cache.CacheControl;
import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import bdv.tools.ToggleDialogAction;
import bdv.tools.brightness.BrightnessDialog;
import bdv.tools.brightness.ConverterSetup;
import bdv.tools.brightness.MinMaxGroup;
import bdv.tools.brightness.RealARGBColorConverterSetup;
import bdv.tools.brightness.SetupAssignments;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.nio.Buffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;
import mpicbg.spim.data.SpimDataException;
import net.imglib2.cache.iotiming.CacheIoTiming;
import net.imglib2.display.ColorConverter;
import net.imglib2.display.RealARGBColorConverter;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
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
import tpietzsch.shadergen.Uniform4f;
import tpietzsch.shadergen.UniformMatrix4f;
import tpietzsch.shadergen.UniformSampler;
import tpietzsch.shadergen.generate.Segment;
import tpietzsch.shadergen.generate.SegmentTemplate;
import tpietzsch.shadergen.generate.SegmentedShader;
import tpietzsch.shadergen.generate.SegmentedShaderBuilder;
import tpietzsch.util.DefaultQuad;
import tpietzsch.util.InputFrame;
import tpietzsch.util.MatrixMath;
import tpietzsch.util.Syncd;
import tpietzsch.util.TransformHandler;
import tpietzsch.util.WireframeBox;

import static com.jogamp.opengl.GL.GL_COLOR_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_DEPTH_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_DEPTH_TEST;
import static com.jogamp.opengl.GL.GL_RGB8;
import static com.jogamp.opengl.GL.GL_UNPACK_ALIGNMENT;
import static com.jogamp.opengl.GL.GL_UNSIGNED_SHORT;
import static com.jogamp.opengl.GL2ES2.GL_RED;
import static com.jogamp.opengl.GL2ES2.GL_TEXTURE_3D;
import static tpietzsch.backend.Texture.InternalFormat.R16;

public class Example5 implements GLEventListener
{
	private final OffScreenFrameBuffer offscreen;

	private final Shader prog;

	private final SegmentedShader progvol;

	private final WireframeBox box;

	private final DefaultQuad quad;

	private final CacheSpec cacheSpec = new CacheSpec( R16, new int[] { 32, 32, 32 } );

	private static final int NUM_BLOCK_SCALES = 10;

	private final ArrayList< VolumeBlocks > volumes;

	private final ArrayList< VolumeSegment > volumeSegments = new ArrayList<>();
	private final ArrayList< ConverterSegment >converterSegments = new ArrayList<>();

	private final TextureCache textureCache;
	private final PboChain pboChain;
	private final ForkJoinPool forkJoinPool;


	final Syncd< AffineTransform3D > worldToScreen = Syncd.affine3D();

	final ArrayList< AtomicReference< MultiResolutionStack3D< VolatileUnsignedShortType > > > aMultiResolutionStacks = new ArrayList<>( Arrays.asList(
			new AtomicReference<>(),
			new AtomicReference<>(),
			new AtomicReference<>() ) );

	final ArrayList< RealARGBColorConverter > convs = new ArrayList<>( Arrays.asList(
			new RealARGBColorConverter.Imp0<>( 0, 1 ),
			new RealARGBColorConverter.Imp0<>( 0, 1 ),
			new RealARGBColorConverter.Imp0<>( 0, 1 ) ) );

	private ArrayList< MultiResolutionStack3D< VolatileUnsignedShortType > > multiResolutionStacks = new ArrayList<>(
			Arrays.asList( null, null, null ) );

	private int viewportWidth = 100;

	private int viewportHeight = 100;

	enum Mode { VOLUME, SLICE }

	private Mode mode = Mode.VOLUME;

	private boolean freezeRequiredBlocks = false;

	private final CacheControl cacheControl;

	private final Runnable requestRepaint;

	public Example5( final CacheControl cacheControl, final Runnable requestRepaint )
	{
		this.cacheControl = cacheControl;
		this.requestRepaint = requestRepaint;
		offscreen = new OffScreenFrameBuffer( 160, 120, GL_RGB8 );
		box = new WireframeBox();
		quad = new DefaultQuad();

		final int maxMemoryInMB = 400;
		final int[] cacheGridDimensions = TextureCache.findSuitableGridSize( cacheSpec, maxMemoryInMB );
		textureCache = new TextureCache( cacheGridDimensions, cacheSpec );
		pboChain = new PboChain( 5, 100, textureCache );
		final int parallelism = Math.max( 1, Runtime.getRuntime().availableProcessors() / 2 );
		forkJoinPool = new ForkJoinPool( parallelism );

		final int numVolumes = 3;

		volumes = new ArrayList<>();
		for ( int i = 0; i < numVolumes; i++ )
			volumes.add( new VolumeBlocks( textureCache ) );

		final Segment ex1vp = new SegmentTemplate("ex1.vp" ).instantiate();
		final Segment ex1fp = new SegmentTemplate("ex1.fp" ).instantiate();
		prog = new DefaultShader( ex1vp.getCode(), ex1fp.getCode() );


		final Segment ex5VolVp = new SegmentTemplate("ex5vol.vp" ).instantiate();
		final SegmentTemplate templateIntersectBox = new SegmentTemplate(
				"intersectbox.fp" );
		final SegmentTemplate templateBlkVol = new SegmentTemplate(
				"blkvol.fp",
				"im", "sourcemin", "sourcemax", "intersectBoundingBox",
				"lutSampler", "blockScales", "lutScale", "lutOffset", "blockTexture" );
		final SegmentTemplate templateColConv = new SegmentTemplate(
				"colconv.fp",
				"convert", "offset", "scale" );
		final SegmentTemplate templateEx4Vol = new SegmentTemplate(
				"ex5vol.fp",
				"intersectBoundingBox", "blockTexture", "convert", "vis" );

		final SegmentedShaderBuilder builder = new SegmentedShaderBuilder();
		builder.vertex( ex5VolVp );
		builder.fragment( templateIntersectBox.instantiate() );

		final Segment ex4Vol = templateEx4Vol.instantiate();
		ex4Vol.repeat( "vis", numVolumes );
		final ArrayList< Segment > blkVols = new ArrayList<>();
		final ArrayList< Segment > colConvs = new ArrayList<>();
		for ( int i = 0; i < numVolumes; ++i )
		{
			final Segment blkVol = templateBlkVol.instantiate();
			builder.fragment( blkVol );
			ex4Vol.bind( "intersectBoundingBox", i, blkVol, "intersectBoundingBox" );
			ex4Vol.bind( "blockTexture", i, blkVol, "blockTexture" );
			blkVols.add( blkVol );

			final Segment colConv = templateColConv.instantiate();
			builder.fragment( colConv );
			ex4Vol.bind( "convert", i, colConv, "convert" );
			colConvs.add( colConv );
		}
		builder.fragment( ex4Vol );
		progvol = builder.build();

		for ( int i = 0; i < numVolumes; ++i )
		{
			volumeSegments.add( new VolumeSegment( progvol, blkVols.get( i ) ) );
			converterSegments.add( new ConverterSegment( progvol, colConvs.get( i ) ) );
		}

		progvol.getUniform3f( "blockSize" ).set( cacheSpec.blockSize()[ 0 ], cacheSpec.blockSize()[ 1 ], cacheSpec.blockSize()[ 2 ] );
		progvol.getUniform3f( "paddedBlockSize" ).set( cacheSpec.paddedBlockSize()[ 0 ], cacheSpec.paddedBlockSize()[ 1 ], cacheSpec.paddedBlockSize()[ 2 ] );
		progvol.getUniform3f( "cachePadOffset" ).set( cacheSpec.padOffset()[ 0 ], cacheSpec.padOffset()[ 1 ], cacheSpec.padOffset()[ 2 ] );
		progvol.getUniform3f( "cacheSize" ).set( textureCache.texWidth(), textureCache.texHeight(), textureCache.texDepth() );
		progvol.getUniformSampler( "volumeCache" ).set( textureCache );

//		final StringBuilder vertexShaderCode = progvol.getVertexShaderCode();
//		System.out.println( "vertexShaderCode = " + vertexShaderCode );
//		System.out.println( "\n\n--------------------------------\n\n");
//		final StringBuilder fragementShaderCode = progvol.getFragementShaderCode();
//		System.out.println( "fragementShaderCode = " + fragementShaderCode );
//		System.out.println( "\n\n--------------------------------\n\n");
	}

	public static class MultiVolumeShaderMip
	{

	}

	public static class ConverterSegment
	{
		private final SegmentedShader prog;
		private final Segment segment;

		private final Uniform4f uniformOffset;
		private final Uniform4f uniformScale;

		public ConverterSegment( final SegmentedShader prog, final Segment segment )
		{
			this.prog = prog;
			this.segment = segment;

			uniformOffset = prog.getUniform4f( segment,"offset" );
			uniformScale = prog.getUniform4f( segment,"scale" );
		}

		public void setData( ColorConverter converter )
		{
			final double fmin = converter.getMin() / 0xffff;
			final double fmax = converter.getMax() / 0xffff;
			final double s = 1.0 / ( fmax - fmin );
			final double o = -fmin * s;

			final int color = converter.getColor().get();
			final double r = ( double ) ARGBType.red( color ) / 255.0;
			final double g = ( double ) ARGBType.green( color ) / 255.0;
			final double b = ( double ) ARGBType.blue( color ) / 255.0;

			uniformOffset.set(
					( float ) ( o * r ),
					( float ) ( o * g ),
					( float ) ( o * b ),
					1f );
			uniformScale.set(
					( float ) ( s * r ),
					( float ) ( s * g ),
					( float ) ( s * b ),
					0f );
		}
	}

	public static class VolumeSegment
	{
		private final SegmentedShader prog;
		private final Segment volume;

		private final Uniform3fv uniformBlockScales;
		private final UniformSampler uniformLutSampler;
		private final Uniform3f uniformLutScale;
		private final Uniform3f uniformLutOffset;
		private final UniformMatrix4f uniformIm;
		private final Uniform3f uniformSourcemin;
		private final Uniform3f uniformSourcemax;

		public VolumeSegment( final SegmentedShader prog, final Segment volume )
		{
			this.prog = prog;
			this.volume = volume;

			uniformBlockScales = prog.getUniform3fv( volume, "blockScales" );
			uniformLutSampler = prog.getUniformSampler( volume,"lutSampler" );
			uniformLutScale = prog.getUniform3f( volume, "lutScale" );
			uniformLutOffset = prog.getUniform3f( volume, "lutOffset" );
			uniformIm = prog.getUniformMatrix4f( volume, "im" );
			uniformSourcemin = prog.getUniform3f( volume,"sourcemin" );
			uniformSourcemax = prog.getUniform3f( volume,"sourcemax" );
		}

		public void setData( VolumeBlocks blocks )
		{
			uniformBlockScales.set( blocks.getLutBlockScales( NUM_BLOCK_SCALES ) );
			uniformLutSampler.set( blocks.getLookupTexture() );
			uniformLutScale.set( blocks.getLutScale() );
			uniformLutOffset.set( blocks.getLutOffset() );
			uniformIm.set( blocks.getIms() );
			uniformSourcemin.set( blocks.getSourceLevelMin() );
			uniformSourcemax.set( blocks.getSourceLevelMax() );
		}
	}

	@Override
	public void init( final GLAutoDrawable drawable )
	{
		final GL3 gl = drawable.getGL().getGL3();
		gl.glPixelStorei( GL_UNPACK_ALIGNMENT, 2 );
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

		for ( int i = 0; i < volumes.size(); i++ )
		{
			final MultiResolutionStack3D< VolatileUnsignedShortType > stack = aMultiResolutionStacks.get( i ).get();
			if ( stack == null )
				return;
			multiResolutionStacks.set( i, stack );
		}

		offscreen.bind( gl );
		final Matrix4f view = MatrixMath.affine( worldToScreen.get(), new Matrix4f() );
		final Matrix4f projection = MatrixMath.screenPerspective( dCam, dClip, screenWidth, screenHeight, screenPadding, new Matrix4f() );

		final JoglGpuContext context = JoglGpuContext.get( gl );

		final Matrix4f pv = new Matrix4f( projection ).mul( view );
		if ( !freezeRequiredBlocks )
		{
			updateBlocks( context, pv );
		}


		// draw volume boxes
		prog.use( context );
		prog.getUniformMatrix4f( "view" ).set( view );
		prog.getUniformMatrix4f( "projection" ).set( projection );
		prog.getUniform4f( "color" ).set( 1.0f, 0.5f, 0.2f, 1.0f );
		for ( int i = 0; i < volumes.size(); i++ )
		{
			final MultiResolutionStack3D< VolatileUnsignedShortType > stack = multiResolutionStacks.get( i );
			prog.getUniformMatrix4f( "model" ).set( MatrixMath.affine( stack.getSourceTransform(), new Matrix4f() ) );
			prog.setUniforms( context );
			box.updateVertices( gl, stack.resolutions().get( 0 ).getImage() );
			box.draw( gl );
		}


		progvol.use( context );
		progvol.getUniform2f( "viewportSize" ).set( viewportWidth, viewportHeight );
		progvol.getUniformMatrix4f( "ipv" ).set( pv.invert( new Matrix4f() ) );


		// TODO: fix hacks (initialize OOB block init)
			context.bindTexture( textureCache );
			final int[] ts = cacheSpec.paddedBlockSize();
			final Buffer oobBuffer = Buffers.newDirectShortBuffer( ( int ) Intervals.numElements( ts ) );
			ByteUtils.setShorts( ( short ) 0, ByteUtils.addressOf( oobBuffer ), ( int ) Intervals.numElements( ts ) );
			gl.glTexSubImage3D( GL_TEXTURE_3D, 0, 0, 0, 0, ts[ 0 ], ts[ 1 ], ts[ 2 ], GL_RED, GL_UNSIGNED_SHORT, oobBuffer );

		for ( int i = 0; i < volumes.size(); i++ )
			converterSegments.get( i ).setData( convs.get( i ) );

		for ( int i = 0; i < volumes.size(); i++ )
			volumeSegments.get( i ).setData( volumes.get( i ) );

		progvol.getUniform2f( "viewportSize" ).set( offscreen.getWidth(), offscreen.getHeight() );
		progvol.bindSamplers( context );
		progvol.setUniforms( context );
		quad.draw( gl );
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

		final ArrayList< FillTask > fillTasks = new ArrayList<>();
		for ( int i = 0; i < volumes.size(); i++ )
		{
			final VolumeBlocks volume = volumes.get( i );
			volume.init( multiResolutionStacks.get( i ), vw, pv );
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

		boolean repaint = false;
		for ( int i = 0; i < volumes.size(); i++ )
		{
			final VolumeBlocks volume = volumes.get( i );
			final boolean complete = volume.makeLut();
			if ( !complete )
				repaint = true;
			volume.getLookupTexture().upload( context );
		}

		if ( repaint )
			requestRepaint.run();
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
		for ( int i = 0; i < volumes.size(); i++ )
		{
			aMultiResolutionStacks.get( i ).set(
					( MultiResolutionStack3D< VolatileUnsignedShortType > )
							stacks.getStack(
									stacks.timepointId( currentTimepoint ),
									stacks.setupId( i ),
									true ) );
		}
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

		final InputFrame frame = new InputFrame( "Example5", 640, 480 );
		InputFrame.DEBUG = false;
		final Example5 glPainter = new Example5( stacks.getCacheControl(), frame::requestRepaint );
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


		final ArrayList< ConverterSetup > converterSetups = new ArrayList<>();
		for ( int i = 0; i < glPainter.convs.size(); i++ )
			converterSetups.add( new RealARGBColorConverterSetup( i, glPainter.convs.get( i ) ) );
		for ( ConverterSetup setup : converterSetups )
		{
			setup.setDisplayRange( 962, 6201 ); // weber
			setup.setColor( new ARGBType( 0xffffffff ) );
			setup.setViewer( frame::requestRepaint );
		}
		glPainter.convs.get( 0 ).setColor( new ARGBType( 0xff8888 ) );
		glPainter.convs.get( 1 ).setColor( new ARGBType( 0x88ff88 ) );
		glPainter.convs.get( 2 ).setColor( new ARGBType( 0x8888ff ) );
		final SetupAssignments setupAssignments = new SetupAssignments( converterSetups, 0, 65535 );
		if ( setupAssignments.getMinMaxGroups().size() > 0 )
		{
			final MinMaxGroup group = setupAssignments.getMinMaxGroups().get( 0 );
			for ( final ConverterSetup setup : setupAssignments.getConverterSetups() )
				setupAssignments.moveSetupToGroup( setup, group );
		}
		final BrightnessDialog brightnessDialog = new BrightnessDialog( frame.getFrame(), setupAssignments );
		frame.getDefaultActions().namedAction( new ToggleDialogAction( "toggle brightness dialog", brightnessDialog ), "S" );


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
