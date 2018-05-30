package tpietzsch.blockmath4;

import static bdv.volume.FindRequiredBlocks.getRequiredLevelBlocksFrustum;
import static com.jogamp.opengl.GL.GL_COLOR_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_DEPTH_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_DEPTH_TEST;
import static com.jogamp.opengl.GL.GL_RGB16F;
import static com.jogamp.opengl.GL.GL_RGB8;
import static com.jogamp.opengl.GL.GL_TEXTURE0;
import static com.jogamp.opengl.GL.GL_TEXTURE1;
import static com.jogamp.opengl.GL.GL_TEXTURE2;
import static com.jogamp.opengl.GL.GL_UNPACK_ALIGNMENT;

import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import net.imglib2.FinalInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.iotiming.CacheIoTiming;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.volatiles.VolatileUnsignedShortType;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.util.Intervals;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import bdv.cache.CacheControl;
import bdv.img.cache.VolatileCachedCellImg;
import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import bdv.volume.RequiredBlocks;
import mpicbg.spim.data.SpimDataException;
import tpietzsch.blockmath2.LookupTexture;
import tpietzsch.blockmath3.TextureBlock;
import tpietzsch.blocks.CopyGridBlock;
import tpietzsch.blocks.CopySubArray;
import tpietzsch.blocks.CopySubArrayImp;
import tpietzsch.blocks.VolatileShortGridDataAccess;
import tpietzsch.day10.OffScreenFrameBuffer;
import tpietzsch.day2.Shader;
import tpietzsch.day4.InputFrame;
import tpietzsch.day4.ScreenPlane1;
import tpietzsch.day4.TransformHandler;
import tpietzsch.day4.WireframeBox1;
import tpietzsch.multires.MultiResolutionStack3D;
import tpietzsch.multires.ResolutionLevel3D;
import tpietzsch.multires.SpimDataStacks;
import tpietzsch.util.MatrixMath;
import tpietzsch.util.Syncd;

/**
 * Consolidate cache texture loading
 */
public class Example2 implements GLEventListener
{
	private final OffScreenFrameBuffer offscreen;

	private Shader prog;

	private Shader progslice;

	private Shader progvol;

	private WireframeBox1 box;

	private ScreenPlane1 screenPlane;

	private final int[] blockSize = { 32, 32, 32 };

	private final int[] paddedBlockSize = { 34, 34, 34 };

	private final int[] cachePadOffset = { 1, 1, 1 };

	private final TextureBlockCache< BlockKey > blockCache;

	private LookupTexture lookupTexture;

	final Syncd< AffineTransform3D > worldToScreen = Syncd.affine3D();

	final AtomicReference< MultiResolutionStack3D< VolatileUnsignedShortType > > aMultiResolutionStack = new AtomicReference<>();

	private MultiResolutionStack3D< VolatileUnsignedShortType > multiResolutionStack;

	private int viewportWidth = 100;

	private int viewportHeight = 100;

	private int baseLevel;

	enum Mode { VOLUME, SLICE };

	private Mode mode = Mode.VOLUME;

	private boolean freezeRequiredBlocks = false;

	private final CacheControl cacheControl;

	private final Runnable requestRepaint;

	public Example2( final CacheControl cacheControl, final Runnable requestRepaint )
	{
		this.cacheControl = cacheControl;
		this.requestRepaint = requestRepaint;
		offscreen = new OffScreenFrameBuffer( 640, 480, GL_RGB8 );
		final TextureBlockCache.BlockLoader< BlockKey > blockLoader = new TextureBlockCache.BlockLoader< BlockKey >()
		{
			@Override
			public boolean loadBlock( final BlockKey key, final ByteBuffer buffer )
			{
				return Example2.this.loadBlock( key, buffer );
			}

			@Override
			public boolean canLoadBlock( final BlockKey key )
			{
				return Example2.this.canLoadBlock( key );
			}
		};
		blockCache = new TextureBlockCache<>( paddedBlockSize, 200, blockLoader );
	}

	@Override
	public void init( final GLAutoDrawable drawable )
	{
		final GL3 gl = drawable.getGL().getGL3();
		gl.glPixelStorei( GL_UNPACK_ALIGNMENT, 2 );

		box = new WireframeBox1();
		screenPlane = new ScreenPlane1();
		screenPlane.updateVertices( gl, new FinalInterval( 640, 480 ) );

		prog = new Shader( gl, "ex1", "ex1", tpietzsch.day10.Example5.class );
		progvol = new Shader( gl, "ex1", "ex1vol" );
		progslice = new Shader( gl, "ex1", "ex1slice" );

		lookupTexture = new LookupTexture( new int[] { 64, 64, 64 }, GL_RGB16F );

		gl.glEnable( GL_DEPTH_TEST );
	}

	public boolean canLoadBlock( final BlockKey key )
	{
		final RandomAccessibleInterval< VolatileUnsignedShortType > rai = ( ( ResolutionLevel3D< VolatileUnsignedShortType > ) key.getStack() ).getImage();
		final int[] gridPos = key.getGridPos();
		final int[] min = new int[ 3 ];
		for ( int d = 0; d < 3; ++d )
			min[ d ] = gridPos[ d ] * blockSize[ d ] - cachePadOffset[ d ];
		return new Copier( rai, paddedBlockSize ).canLoadCompletely( min );
	}

	public boolean loadBlock( final BlockKey key, final ByteBuffer buffer )
	{
		final RandomAccessibleInterval< VolatileUnsignedShortType > rai = ( ( ResolutionLevel3D< VolatileUnsignedShortType > ) key.getStack() ).getImage();
		final int[] gridPos = key.getGridPos();
		final int[] min = new int[ 3 ];
		for ( int d = 0; d < 3; ++d )
			min[ d ] = gridPos[ d ] * blockSize[ d ] - cachePadOffset[ d ];
		return new Copier( rai, paddedBlockSize ).toBuffer( buffer, min );
	}

	public static class Copier
	{
		private final CopyGridBlock gcopy = new CopyGridBlock();

		private final VolatileShortGridDataAccess dataAccess;

		private final CopySubArray< short[], Buffer > subArrayCopy = new CopySubArrayImp.ShortToBuffer();

		private final CellGrid grid;

		private final int[] blocksize;

		public Copier( final RandomAccessibleInterval< VolatileUnsignedShortType > rai, final int[] blocksize )
		{
			final VolatileCachedCellImg< VolatileUnsignedShortType, ? > img = ( VolatileCachedCellImg< VolatileUnsignedShortType, ? > ) rai;
			grid = img.getCellGrid();
			dataAccess = new VolatileShortGridDataAccess( ( RandomAccess ) img.getCells().randomAccess() );

			this.blocksize = blocksize;
		}

		/**
		 * @return {@code true}, if this block can be completely loaded from data currently in the cache
		 */
		public boolean canLoadCompletely( final int[] min )
		{
			return gcopy.canLoadCompletely( min, blocksize, grid, dataAccess );
		}

		/**
		 * @return {@code true}, if this block was completely loaded
		 */
		public boolean toBuffer( final ByteBuffer buffer, final int[] min )
		{
			final boolean complete = gcopy.copy( min, blocksize, grid, buffer, dataAccess, subArrayCopy );
			return complete;
		}
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

//		long size = 100;
//		gl.glBufferData( GL_PIXEL_UNPACK_BUFFER, size, null, GL_STREAM_DRAW );
//		final ByteBuffer buffer = gl.glMapBuffer( GL_PIXEL_UNPACK_BUFFER, GL_WRITE_ONLY );

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

		prog.use( gl );
		prog.setUniform( gl, "model", model );
		prog.setUniform( gl, "view", view );
		prog.setUniform( gl, "projection", projection );

		prog.setUniform( gl, "color", 1.0f, 0.5f, 0.2f, 1.0f );
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

		modeprog.use( gl );
		modeprog.setUniform( gl, "model", new Matrix4f() );
		modeprog.setUniform( gl, "view", new Matrix4f() );
		modeprog.setUniform( gl, "projection", projection );
		modeprog.setUniform( gl, "viewportSize", viewportWidth, viewportHeight );
		progslice.setUniform( gl, "ip", ip );
		progslice.setUniform( gl, "ivm", ivm );
		progvol.setUniform( gl, "ipvm", ipvm );
		modeprog.setUniform( gl, "sourcemin", sourceLevelMin );
		modeprog.setUniform( gl, "sourcemax", sourceLevelMax );

		modeprog.setUniform( gl, "scaleLut", 0 );
		modeprog.setUniform( gl, "offsetLut", 1 );
		modeprog.setUniform( gl, "volumeCache", 2 );
		lookupTexture.bindTextures( gl, GL_TEXTURE0, GL_TEXTURE1 );
		blockCache.bindTextures( gl, GL_TEXTURE2 );

		modeprog.setUniform( gl, "blockSize", blockSize[ 0 ], blockSize[ 1 ], blockSize[ 2 ] );
		final int[] lutSize = lookupTexture.getSize();
		modeprog.setUniform( gl, "lutSize", lutSize[ 0 ], lutSize[ 1 ], lutSize[ 2 ] );
		modeprog.setUniform( gl, "padSize", pad[ 0 ], pad[ 1 ], pad[ 2 ] );

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
		modeprog.setUniform( gl, "intensity_offset", ( float ) o );
		modeprog.setUniform( gl, "intensity_scale", ( float ) s );

		screenPlane.updateVertices( gl, new FinalInterval( ( int ) screenWidth, ( int ) screenHeight ) );
//		screenPlane.draw( gl );

		modeprog.setUniform( gl, "viewportSize", offscreen.getWidth(), offscreen.getHeight() );
		screenPlane.draw( gl );
		offscreen.unbind( gl, false );
		offscreen.drawQuad( gl );
	}

	private final Vector3f sourceLevelMin = new Vector3f();
	private final Vector3f sourceLevelMax = new Vector3f();

	protected long[] iobudget = new long[] { 100l * 1000000l,  10l * 1000000l };

	private void updateBlocks( final GL3 gl, final Matrix4f pvm )
	{
		CacheIoTiming.getIoTimeBudget().reset( iobudget );
		cacheControl.prepareNextFrame();

		final int vw = offscreen.getWidth();
//		final int vw = viewportWidth;
		final MipmapSizes sizes = new MipmapSizes();
		sizes.init( pvm, vw, multiResolutionStack.resolutions() );
		baseLevel = sizes.getBaseLevel();
		System.out.println( "baseLevel = " + baseLevel );

		final ResolutionLevel3D< VolatileUnsignedShortType > baseResolution = multiResolutionStack.resolutions().get( baseLevel );

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

		final RandomAccessibleInterval< VolatileUnsignedShortType > rai = baseResolution.getImage();
		sourceLevelMin.set( rai.min( 0 ), rai.min( 1 ), rai.min( 2 ) ); // TODO -0.5 offset?
		sourceLevelMax.set( rai.max( 0 ), rai.max( 1 ), rai.max( 2 ) ); // TODO -0.5 offset?

		final Vector3f fbbmin = new Vector3f();
		final Vector3f fbbmax = new Vector3f();
		ipvms.frustumAabb( fbbmin, fbbmax );
		fbbmin.max( sourceLevelMin );
		fbbmax.min( sourceLevelMax );
		final long[] gridMin = {
				( long ) ( fbbmin.x() / blockSize[ 0 ] ),
				( long ) ( fbbmin.y() / blockSize[ 1 ] ),
				( long ) ( fbbmin.z() / blockSize[ 2 ] )
		};
		final long[] gridMax = {
				( long ) ( fbbmax.x() / blockSize[ 0 ] ),
				( long ) ( fbbmax.y() / blockSize[ 1 ] ),
				( long ) ( fbbmax.z() / blockSize[ 2 ] )
		};

		final RequiredBlocks requiredBlocks = getRequiredLevelBlocksFrustum( pvms, blockSize, gridMin, gridMax );
		System.out.println( "requiredBlocks = " + requiredBlocks );
		updateLookupTexture( gl, requiredBlocks, baseLevel, sizes );
	}

	final int[] padSize = { 1, 1, 1 };

	final int[] pad = { 1, 1, 1 };

	private void updateLookupTexture( final GL3 gl, final RequiredBlocks requiredBlocks, final int baseLevel, final MipmapSizes sizes )
	{
		final long t0 = System.currentTimeMillis();
		blockCache.resetStats();
		final int maxLevel = multiResolutionStack.resolutions().size() - 1;

		final int[] lutSize = new int[ 3 ];
		final int[] rmin = requiredBlocks.getMin();
		final int[] rmax = requiredBlocks.getMax();
		lutSize[ 0 ] = rmax[ 0 ] - rmin[ 0 ] + 1 + 2 * padSize[ 0 ];
		lutSize[ 1 ] = rmax[ 1 ] - rmin[ 1 ] + 1 + 2 * padSize[ 1 ];
		lutSize[ 2 ] = rmax[ 2 ] - rmin[ 2 ] + 1 + 2 * padSize[ 2 ];
		final int[] cacheSize = blockCache.getCacheTextureSize();

		final int dataSize = 3 * ( int ) Intervals.numElements( lutSize );
		final float[] qsData = new float[ dataSize ];
		final float[] qdData = new float[ dataSize ];

		final int[] r = multiResolutionStack.resolutions().get( baseLevel ).getR();
		final Vector3f blockCenter = new Vector3f();
		final Vector3f tmp = new Vector3f();

		// offset for IntervalIndexer
		pad[ 0 ] = padSize[ 0 ] - rmin[ 0 ];
		pad[ 1 ] = padSize[ 1 ] - rmin[ 1 ];
		pad[ 2 ] = padSize[ 2 ] - rmin[ 2 ];
		final int[] padOffset = new int[] { -pad[ 0 ], -pad[ 1 ], -pad[ 2 ] };

		final ArrayList< int[] > gridPositions = requiredBlocks.getGridPositions();
		final double[] sij = new double[ 3 ];
		final int[] gj = new int[ 3 ];

		boolean needsRepaint = false;
		for ( final int[] g0 : gridPositions )
		{
			for ( int d = 0; d < 3; ++d )
				blockCenter.setComponent( d, ( g0[ d ] + 0.5f ) * blockSize[ d ] * r[ d ] );
			final int bestLevel = Math.max( baseLevel, sizes.bestLevel( blockCenter, tmp ) );

//			final int startLevel = ( blockCache.currentUploadMillis() < thresholdUploadMillis ) ? bestLevel : maxLevel;
			final int startLevel = bestLevel;
			for ( int level = startLevel; level <= maxLevel; ++level )
			{
				final ResolutionLevel3D< VolatileUnsignedShortType > resolution = multiResolutionStack.resolutions().get( level );
				final double[] sj = resolution.getS();
				for ( int d = 0; d < 3; ++d )
				{
					sij[ d ] = sj[ d ] * r[ d ];
					gj[ d ] = ( int ) ( g0[ d ] * sij[ d ] );
				}
				final TextureBlock textureBlock =
						level == maxLevel
								? blockCache.get( gl, new BlockKey( gj, resolution ) )
								: blockCache.getIfPresentOrCompletable( gl, new BlockKey( gj, resolution ) );
				if ( textureBlock != null )
				{
					final int[] texpos = textureBlock.getPos();
					final int i = IntervalIndexer.positionWithOffsetToIndex( g0, lutSize, padOffset );
					for ( int d = 0; d < 3; ++d )
					{
						final double qs = sij[ d ] * lutSize[ d ] * blockSize[ d ] / cacheSize[ d ];
						final double p = g0[ d ] * blockSize[ d ];
						final double hj = 0.5 * ( sij[ d ] - 1 );
						final double c0 = texpos[ d ] + cachePadOffset[ d ] + p * sij[ d ] - gj[ d ] * blockSize[ d ] + hj;
						final double qd = ( c0 - sij[ d ] * ( pad[ d ] * blockSize[ d ] + p ) + 0.5 ) / cacheSize[ d ];
						qsData[ 3 * i + d ] = ( float ) qs;
						qdData[ 3 * i + d ] = ( float ) qd;
					}

					if ( level != bestLevel || textureBlock.needsLoading() )
						needsRepaint = true;

					break;
				}
			}
		}
		final long t1 = System.currentTimeMillis();
		lookupTexture.resize( gl, lutSize );
		lookupTexture.set( gl, qsData, qdData );
		final long t2 = System.currentTimeMillis();
		blockCache.printStats();
		System.out.println( "lookup texture took " + ( t1 - t0 ) + " ms to compute, " + ( t2 - t1 ) + " ms to upload" );
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

		final InputFrame frame = new InputFrame( "Example2", 640, 480 );
		InputFrame.DEBUG = false;
		final Example2 glPainter = new Example2( stacks.getCacheControl(), frame::requestRepaint );
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
//		FPSAnimator animator = new FPSAnimator( frame.getCanvas(), 100 );
//		animator.setUpdateFPSFrames(100, System.out );
//		animator.start();
	}
}
