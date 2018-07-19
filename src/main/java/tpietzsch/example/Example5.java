package tpietzsch.example;

import bdv.cache.CacheControl;
import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import tpietzsch.blockmath.LookupTextureARGB;
import tpietzsch.blockmath.MipmapSizes;
import tpietzsch.blockmath.RequiredBlocks;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.nio.Buffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicReference;
import mpicbg.spim.data.SpimDataException;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.cache.iotiming.CacheIoTiming;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.volatiles.VolatileUnsignedShortType;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.util.Intervals;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import tpietzsch.backend.jogl.JoglGpuContext;
import tpietzsch.blocks.ByteUtils;
import tpietzsch.blocks.TileAccess;
import tpietzsch.cache.CacheSpec;
import tpietzsch.cache.DefaultFillTask;
import tpietzsch.cache.FillTask;
import tpietzsch.cache.ImageBlockKey;
import tpietzsch.cache.PboChain;
import tpietzsch.cache.ProcessFillTasks;
import tpietzsch.cache.TextureCache;
import tpietzsch.cache.TextureCache.Tile;
import tpietzsch.cache.UploadBuffer;
import tpietzsch.offscreen.OffScreenFrameBuffer;
import tpietzsch.util.InputFrame;
import tpietzsch.util.ScreenPlane;
import tpietzsch.util.TransformHandler;
import tpietzsch.util.WireframeBox;
import tpietzsch.multires.MultiResolutionStack3D;
import tpietzsch.multires.ResolutionLevel3D;
import tpietzsch.multires.SpimDataStacks;
import tpietzsch.shadergen.DefaultShader;
import tpietzsch.shadergen.Shader;
import tpietzsch.shadergen.generate.Segment;
import tpietzsch.shadergen.generate.SegmentTemplate;
import tpietzsch.util.MatrixMath;
import tpietzsch.util.Syncd;

import static tpietzsch.blockmath.FindRequiredBlocks.getRequiredLevelBlocksFrustum;
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
import static tpietzsch.cache.TextureCache.ContentState.INCOMPLETE;

public class Example5 implements GLEventListener
{
	private final OffScreenFrameBuffer offscreen;

	private final Shader prog;

	private final Shader progslice;

	private final Shader progvol;

	private WireframeBox box;

	private ScreenPlane screenPlane;

	private final CacheSpec cacheSpec = new CacheSpec( R16, new int[] { 32, 32, 32 } );

	private static final int NUM_BLOCK_SCALES = 10;

	private final float[][] lookupBlockScales = new float[ NUM_BLOCK_SCALES ][ 3 ];

	private LookupTextureARGB lookupTexture;

	private final TextureCache textureCache;
	private final PboChain pboChain;
	private final ForkJoinPool forkJoinPool;


	final Syncd< AffineTransform3D > worldToScreen = Syncd.affine3D();

	final AtomicReference< MultiResolutionStack3D< VolatileUnsignedShortType > > aMultiResolutionStack = new AtomicReference<>();

	private MultiResolutionStack3D< VolatileUnsignedShortType > multiResolutionStack;

	private int viewportWidth = 100;

	private int viewportHeight = 100;

	private int baseLevel;

	enum Mode { VOLUME, SLICE }

	private Mode mode = Mode.VOLUME;

	private boolean freezeRequiredBlocks = false;

	private final CacheControl cacheControl;

	private final Runnable requestRepaint;

	public Example5( final CacheControl cacheControl, final Runnable requestRepaint )
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

		final Segment ex1vp = new SegmentTemplate("ex1.vp" ).instantiate();
		final Segment ex1fp = new SegmentTemplate("ex1.fp" ).instantiate();
		final Segment ex2volfp = new SegmentTemplate("ex1vol.fp" ).instantiate();
		final Segment ex1slicefp = new SegmentTemplate("ex2slice.fp" ).instantiate();

		prog = new DefaultShader( ex1vp.getCode(), ex1fp.getCode() );
		progvol = new DefaultShader( ex1vp.getCode(), ex2volfp.getCode() );
		progslice = new DefaultShader( ex1vp.getCode(), ex1slicefp.getCode() );
	}

	@Override
	public void init( final GLAutoDrawable drawable )
	{
		final GL3 gl = drawable.getGL().getGL3();
		gl.glPixelStorei( GL_UNPACK_ALIGNMENT, 2 );

		box = new WireframeBox();
		screenPlane = new ScreenPlane();
		screenPlane.updateVertices( gl, new FinalInterval( 640, 480 ) );

		lookupTexture = new LookupTextureARGB( new int[] { 64, 64, 64 } );

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

		if ( !freezeRequiredBlocks )
		{
			final Matrix4f pvm = new Matrix4f( projection ).mul( view ).mul( model );
			updateBlocks( gl, pvm );
		}

		final JoglGpuContext context = JoglGpuContext.get( gl );

		prog.getUniformMatrix4f( "model" ).set( model );
		prog.getUniformMatrix4f( "view" ).set( view );
		prog.getUniformMatrix4f( "projection" ).set( projection );
		prog.getUniform4f( "color" ).set( 1.0f, 0.5f, 0.2f, 1.0f );

		prog.use( context );
		prog.setUniforms( context );
		box.updateVertices( gl, multiResolutionStack.resolutions().get( 0 ).getImage() );
		box.draw( gl );



		final int[] baseScale = multiResolutionStack.resolutions().get( baseLevel ).getR();
		final int x = baseScale[ 0 ];
		final int y = baseScale[ 1 ];
		final int z = baseScale[ 2 ];
		final Matrix4f upscale = new Matrix4f(
				x, 0, 0, 0,
				0, y, 0, 0,
				0, 0, z, 0,
				0.5f * ( x - 1 ), 0.5f * ( y - 1 ), 0.5f * ( z - 1 ), 1 );
		model.mul( upscale );

		final Matrix4f ip = new Matrix4f( projection ).invert();
		final Matrix4f ivm = new Matrix4f( view ).mul( model ).invert();
		final Matrix4f ipvm = new Matrix4f( projection ).mul( view ).mul( model ).invert();

		final Shader modeprog = mode == Mode.SLICE ? progslice : progvol;

		modeprog.use( context );
		modeprog.getUniformMatrix4f( "model" ).set( new Matrix4f() );
		modeprog.getUniformMatrix4f( "view" ).set( new Matrix4f() );
		modeprog.getUniformMatrix4f( "projection" ).set( projection );
		modeprog.getUniform2f( "viewportSize" ).set( viewportWidth, viewportHeight );
		progslice.getUniformMatrix4f( "ip" ).set( ip );
		progslice.getUniformMatrix4f( "ivm" ).set( ivm );
		progvol.getUniformMatrix4f( "ipvm" ).set( ipvm );
		modeprog.getUniform3f( "sourcemin" ).set( sourceLevelMin );
		modeprog.getUniform3f( "sourcemax" ).set( sourceLevelMax );

		modeprog.getUniform1i( "lut" ).set( 0 );
		modeprog.getUniform1i( "volumeCache" ).set( 1 );
		lookupTexture.bindTextures( gl, GL_TEXTURE0 );

		// TODO: fix hacks
//			blockCache.bindTextures( gl, GL_TEXTURE1 );
			gl.glActiveTexture( GL_TEXTURE1 );
			gl.glBindTexture( GL_TEXTURE_3D, context.getTextureIdHack( textureCache ) );

		// TODO: fix hacks (initialize OOB block init)
			final int[] ts = cacheSpec.paddedBlockSize();
			final Buffer oobBuffer = Buffers.newDirectShortBuffer( ( int ) Intervals.numElements( ts ) );
			ByteUtils.setShorts( ( short ) 0x0fff, ByteUtils.addressOf( oobBuffer ), ( int ) Intervals.numElements( ts ) );
			gl.glTexSubImage3D( GL_TEXTURE_3D, 0, 0, 0, 0, ts[ 0 ], ts[ 1 ], ts[ 2 ], GL_RED, GL_UNSIGNED_SHORT, oobBuffer );

		modeprog.getUniform3f( "blockSize" ).set( cacheSpec.blockSize()[ 0 ], cacheSpec.blockSize()[ 1 ], cacheSpec.blockSize()[ 2 ] );
		modeprog.getUniform3f( "paddedBlockSize" ).set( cacheSpec.paddedBlockSize()[ 0 ], cacheSpec.paddedBlockSize()[ 1 ], cacheSpec.paddedBlockSize()[ 2 ] );
		modeprog.getUniform3f( "cachePadOffset" ).set( cacheSpec.padOffset()[ 0 ], cacheSpec.padOffset()[ 1 ], cacheSpec.padOffset()[ 2 ] );
		modeprog.getUniform3f( "cacheSize" ).set( textureCache.texWidth(), textureCache.texHeight(), textureCache.texDepth() );
		modeprog.getUniform3fv( "blockScales" ).set( lookupBlockScales );
		final int[] lutSize = lookupTexture.getSize();

		modeprog.getUniform3f( "lutScale" ).set(
				( float ) ( 1.0 / ( cacheSpec.blockSize()[ 0 ] * lutSize[ 0 ] ) ),
				( float ) ( 1.0 / ( cacheSpec.blockSize()[ 1 ] * lutSize[ 1 ] ) ),
				( float ) ( 1.0 / ( cacheSpec.blockSize()[ 2 ] * lutSize[ 2 ] ) ) );
		modeprog.getUniform3f( "lutOffset" ).set(
				( float ) ( ( double ) lutOffset[ 0 ] / lutSize[ 0 ] ),
				( float ) ( ( double ) lutOffset[ 1 ] / lutSize[ 1 ] ),
				( float ) ( ( double ) lutOffset[ 2 ] / lutSize[ 2 ] ) );


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
		modeprog.getUniform1f( "intensity_offset" ).set( ( float ) o );
		modeprog.getUniform1f( "intensity_scale" ).set( ( float ) s );

		screenPlane.updateVertices( gl, new FinalInterval( ( int ) screenWidth, ( int ) screenHeight ) );
//		screenPlane.draw( gl );

		modeprog.getUniform2f( "viewportSize" ).set( offscreen.getWidth(), offscreen.getHeight() );
		modeprog.setUniforms( context );
		screenPlane.draw( gl );
		offscreen.unbind( gl, false );
		offscreen.drawQuad( gl );
	}

	private final Vector3f sourceLevelMin = new Vector3f();
	private final Vector3f sourceLevelMax = new Vector3f();

	private final long[] iobudget = new long[] { 100L * 1000000L, 10L * 1000000L };

	private void updateBlocks( final GL3 gl, final Matrix4f pvm )
	{
		CacheIoTiming.getIoTimeBudget().reset( iobudget );
		cacheControl.prepareNextFrame();

		final int vw = offscreen.getWidth();
//		final int vw = viewportWidth;
		final MipmapSizes sizes = new MipmapSizes();
		sizes.init( pvm, vw, multiResolutionStack.resolutions() );
		baseLevel = sizes.getBaseLevel();
//		System.out.println( "baseLevel = " + baseLevel );

		final ResolutionLevel3D< ? > baseResolution = multiResolutionStack.resolutions().get( baseLevel );

		final int bsx = baseResolution.getR()[ 0 ];
		final int bsy = baseResolution.getR()[ 1 ];
		final int bsz = baseResolution.getR()[ 2 ];
		final Matrix4f upscale = new Matrix4f(
				bsx, 0, 0, 0,
				0, bsy, 0, 0,
				0, 0, bsz, 0,
				0.5f * ( bsx - 1 ), 0.5f * ( bsy - 1 ), 0.5f * ( bsz - 1 ), 1 );
		final Matrix4fc pvms = pvm.mul( upscale, new Matrix4f() );
		final Matrix4fc ipvms =	pvms.invert( new Matrix4f() );

		final Interval rai = baseResolution.getImage();
		sourceLevelMin.set( rai.min( 0 ), rai.min( 1 ), rai.min( 2 ) ); // TODO -0.5 offset?
		sourceLevelMax.set( rai.max( 0 ), rai.max( 1 ), rai.max( 2 ) ); // TODO -0.5 offset?

		final Vector3f fbbmin = new Vector3f();
		final Vector3f fbbmax = new Vector3f();
		ipvms.frustumAabb( fbbmin, fbbmax );
		fbbmin.max( sourceLevelMin );
		fbbmax.min( sourceLevelMax );
		final long[] gridMin = {
				( long ) ( fbbmin.x() / cacheSpec.blockSize()[ 0 ] ),
				( long ) ( fbbmin.y() / cacheSpec.blockSize()[ 1 ] ),
				( long ) ( fbbmin.z() / cacheSpec.blockSize()[ 2 ] )
		};
		final long[] gridMax = {
				( long ) ( fbbmax.x() / cacheSpec.blockSize()[ 0 ] ),
				( long ) ( fbbmax.y() / cacheSpec.blockSize()[ 1 ] ),
				( long ) ( fbbmax.z() / cacheSpec.blockSize()[ 2 ] )
		};

		final RequiredBlocks requiredBlocks = getRequiredLevelBlocksFrustum( pvms, cacheSpec.blockSize(), gridMin, gridMax );
//		System.out.println( "requiredBlocks = " + requiredBlocks );
		updateLookupTexture( gl, requiredBlocks, sizes );
	}

	// border around lut (points to oob blocks)
	final int[] lutPadSize = { 1, 1, 1 };

	// offset into lut (source block to lut coords)
	final int[] lutOffset = { 1, 1, 1 };

	TileAccess.Cache tileAccess = new TileAccess.Cache();

	private boolean canLoadCompletely( final ImageBlockKey< ResolutionLevel3D< ? > > key )
	{
		return tileAccess.get( key.image(), cacheSpec ).canLoadCompletely( key.pos(), false );
	}

	private boolean loadTile( final ImageBlockKey< ResolutionLevel3D< ? > > key, final UploadBuffer buffer )
	{
		return tileAccess.get( key.image(), cacheSpec ).loadTile( key.pos(), buffer );
	}


	static class ReqiredBlock
	{
		final int[] gridPos;

		final int bestLevel;

		public ReqiredBlock( final int[] gridPos, final int bestLevel )
		{
			this.gridPos = gridPos;
			this.bestLevel = bestLevel;
		}
	}

	/**
	 * Determine best resolution level for each block.
	 * Best resolution is capped at {@code sizes.getBaseLevel()}.
	 */
	private List< ReqiredBlock > bestLevels( final List< int[] > gridPositions, final MipmapSizes sizes )
	{
		ArrayList< ReqiredBlock > blocks = new ArrayList<>();

		final int baseLevel = sizes.getBaseLevel();
		final int[] r = multiResolutionStack.resolutions().get( baseLevel ).getR();
		final int[] blockSize = cacheSpec.blockSize();
		final int[] scale = new int[] {
				blockSize[ 0 ] * r[ 0 ],
				blockSize[ 1 ] * r[ 1 ],
				blockSize[ 2 ] * r[ 2 ]
		};
		final Vector3f blockCenter = new Vector3f();
		final Vector3f tmp = new Vector3f();
		for ( final int[] g0 : gridPositions )
		{
			blockCenter.set(
					( g0[ 0 ] + 0.5f ) * scale[ 0 ],
					( g0[ 1 ] + 0.5f ) * scale[ 1 ],
					( g0[ 2 ] + 0.5f ) * scale[ 2 ] );
			final int bestLevel = Math.max( baseLevel, sizes.bestLevel( blockCenter, tmp ) );
			blocks.add( new ReqiredBlock( g0, bestLevel ) );
		}

		return blocks;
	}

	private void updateCache( final GL3 gl, List< ReqiredBlock > blocks, final MipmapSizes sizes )
	{
		final int maxLevel = multiResolutionStack.resolutions().size() - 1;
		final int baseLevel = sizes.getBaseLevel();

		final int[] r = multiResolutionStack.resolutions().get( baseLevel ).getR();
		final HashSet< ImageBlockKey< ? > > existingKeys = new HashSet<>();
		final ArrayList< FillTask > fillTasks = new ArrayList<>();
		final int[] gj = new int[ 3 ];
		for ( ReqiredBlock block : blocks )
		{
			final int[] g0 = block.gridPos;
			for ( int level = block.bestLevel; level <= maxLevel; ++level )
			{
				final ResolutionLevel3D< ? > resolution = multiResolutionStack.resolutions().get( level );
				final double[] sj = resolution.getS();
				for ( int d = 0; d < 3; ++d )
					gj[ d ] = ( int ) ( g0[ d ] * sj[ d ] * r[ d ] );

				final ImageBlockKey< ResolutionLevel3D< ? > > key = new ImageBlockKey<>( resolution, gj );
				if ( !existingKeys.contains( key ) )
				{
					existingKeys.add( key );
					final Tile tile = textureCache.get( key );
					if ( tile != null || level == maxLevel || canLoadCompletely( key ) )
					{
						fillTasks.add( new DefaultFillTask( key, buf -> loadTile( key, buf ) ) );
						break;
					}
				}
				else
					break; // TODO: is this always ok?
			}
		}

		// ============================================================

		try
		{
			ProcessFillTasks.parallel( textureCache, pboChain, JoglGpuContext.get( gl ), forkJoinPool, fillTasks );
		}
		catch ( final InterruptedException e )
		{
			e.printStackTrace();
		}
	}

	private void updateLookupTexture( final GL3 gl, final RequiredBlocks requiredBlocks, final MipmapSizes sizes )
	{
		final int maxLevel = multiResolutionStack.resolutions().size() - 1;
		final int baseLevel = sizes.getBaseLevel();

		final List< ReqiredBlock > blocks = bestLevels( requiredBlocks.getGridPositions(), sizes );
		updateCache( gl, blocks, sizes );

		final int[] lutSize = new int[ 3 ];
		final int[] rmin = requiredBlocks.getMin();
		final int[] rmax = requiredBlocks.getMax();
		lutSize[ 0 ] = rmax[ 0 ] - rmin[ 0 ] + 1 + 2 * lutPadSize[ 0 ];
		lutSize[ 1 ] = rmax[ 1 ] - rmin[ 1 ] + 1 + 2 * lutPadSize[ 1 ];
		lutSize[ 2 ] = rmax[ 2 ] - rmin[ 2 ] + 1 + 2 * lutPadSize[ 2 ];

		final int dataSize = 4 * ( int ) Intervals.numElements( lutSize );
		final byte[] lutData = new byte[ dataSize ];

		// offset for IntervalIndexer
		lutOffset[ 0 ] = lutPadSize[ 0 ] - rmin[ 0 ];
		lutOffset[ 1 ] = lutPadSize[ 1 ] - rmin[ 1 ];
		lutOffset[ 2 ] = lutPadSize[ 2 ] - rmin[ 2 ];
		final int[] padOffset = new int[] { -lutOffset[ 0 ], -lutOffset[ 1 ], -lutOffset[ 2 ] };


		// update lookupBlockScales
		// lookupBlockScales[0] for oob
		// lookupBlockScales[i+1] for relative scale between baseLevel and level baseLevel+i
		final int[] r = multiResolutionStack.resolutions().get( baseLevel ).getR();
		for ( int d = 0; d < 3; ++d )
			lookupBlockScales[ 0 ][ d ] = 0;
		for ( int level = baseLevel; level <= maxLevel; ++level )
		{
			final ResolutionLevel3D< ? > resolution = multiResolutionStack.resolutions().get( level );
			final double[] sj = resolution.getS();
			final int i = 1 + level - baseLevel;
			for ( int d = 0; d < 3; ++d )
				lookupBlockScales[ i ][ d ] = ( float ) ( sj[ d ] * r[ d ] );
		}


		boolean needsRepaint = false;
		final int[] gj = new int[ 3 ];
		for ( ReqiredBlock block : blocks )
		{
			final int[] g0 = block.gridPos;
			for ( int level = block.bestLevel; level <= maxLevel; ++level )
			{
				final ResolutionLevel3D< VolatileUnsignedShortType > resolution = multiResolutionStack.resolutions().get( level );
				final double[] sj = resolution.getS();
				for ( int d = 0; d < 3; ++d )
					gj[ d ] = ( int ) ( g0[ d ] * sj[ d ] * r[ d ] );
				final Tile tile = textureCache.get( new ImageBlockKey<>( resolution, gj ) );
				if ( tile != null )
				{
					final int i = IntervalIndexer.positionWithOffsetToIndex( g0, lutSize, padOffset );
					lutData[ i * 4 ]     = ( byte ) tile.x();
					lutData[ i * 4 + 1 ] = ( byte ) tile.y();
					lutData[ i * 4 + 2 ] = ( byte ) tile.z();
					lutData[ i * 4 + 3 ] = ( byte ) ( level - baseLevel + 1 );

					if ( level != block.bestLevel || tile.state() == INCOMPLETE )
						needsRepaint = true;

					break;
				}
			}
		}

		lookupTexture.resize( gl, lutSize );
		lookupTexture.set( gl, lutData );
		if ( needsRepaint )
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

		final InputFrame frame = new InputFrame( "Example", 640, 480 );
		InputFrame.DEBUG = false;
		final Example5 glPainter = new Example5( stacks.getCacheControl(), frame::requestRepaint );
		frame.setGlEventListener( glPainter );
		final TransformHandler tf = frame.setupDefaultTransformHandler( glPainter.worldToScreen::set, frame::requestRepaint );
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
