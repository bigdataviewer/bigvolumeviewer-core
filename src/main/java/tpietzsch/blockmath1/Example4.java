package tpietzsch.blockmath1;

import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import bdv.volume.RequiredBlocks;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.util.FPSAnimator;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.MultiResolutionSetupImgLoader;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import tpietzsch.day10.BlockKey;
import tpietzsch.day10.LRUBlockCache;
import tpietzsch.day10.LRUBlockCache.TextureBlock;
import tpietzsch.day10.LookupTexture;
import tpietzsch.day10.OffScreenFrameBuffer;
import tpietzsch.day10.TextureCache;
import tpietzsch.day2.Shader;
import tpietzsch.day4.InputFrame;
import tpietzsch.day4.ScreenPlane1;
import tpietzsch.day4.TransformHandler;
import tpietzsch.day4.WireframeBox1;
import tpietzsch.day8.BlockTextureUtils;
import tpietzsch.util.MatrixMath;
import tpietzsch.util.Syncd;

import static bdv.volume.FindRequiredBlocks.getRequiredBlocksFrustum;
import static com.jogamp.opengl.GL.GL_COLOR_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_DEPTH_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_DEPTH_TEST;
import static com.jogamp.opengl.GL.GL_RGB16F;
import static com.jogamp.opengl.GL.GL_RGB8;
import static com.jogamp.opengl.GL.GL_TEXTURE0;
import static com.jogamp.opengl.GL.GL_TEXTURE1;
import static com.jogamp.opengl.GL.GL_TEXTURE2;
import static com.jogamp.opengl.GL.GL_UNPACK_ALIGNMENT;

/**
 * Rendering slices and volume with BlockTexture and TextureCache.
 */
public class Example4 implements GLEventListener
{
	private final List< RaiLevel> raiLevels;

	private final AffineTransform3D sourceTransform;

	private OffScreenFrameBuffer offscreen;

	private Shader prog;

	private Shader progslice;

	private Shader progvol;

	private WireframeBox1 box;

	private ScreenPlane1 screenPlane;

	private ColoredLines coloredLines;

	private TextureCache textureCache;

	private final int[] blockSize = { 32, 32, 32 };

	private final int[] paddedBlockSize = { 34, 34, 34 };

	private final int[] cachePadOffset = { 1, 1, 1 };

	private final int[] imgGridSize;

	private LRUBlockCache< BlockKey > lruBlockCache;

	private LookupTexture lookupTexture;

	final Syncd< AffineTransform3D > worldToScreen = Syncd.affine3D();

	private int viewportWidth = 100;

	private int viewportHeight = 100;

	enum Mode { VOLUME, SLICE };

	private Mode mode = Mode.VOLUME;

	private boolean freezeRequiredBlocks = false;

	public Example4( List< RaiLevel > raiLevels, final AffineTransform3D sourceTransform )
	{
		this.raiLevels = raiLevels;
		this.sourceTransform = sourceTransform;
		imgGridSize = raiLevels.get( 0 ).imgGridSize( blockSize );
		offscreen = new OffScreenFrameBuffer( 640, 480, GL_RGB8 );
		coloredLines = new ColoredLines();
	}

	@Override
	public void init( final GLAutoDrawable drawable )
	{
		final GL3 gl = drawable.getGL().getGL3();
		gl.glPixelStorei( GL_UNPACK_ALIGNMENT, 2 );

		box = new WireframeBox1();
		box.updateVertices( gl, raiLevels.get( 0 ).rai );
		screenPlane = new ScreenPlane1();
		screenPlane.updateVertices( gl, new FinalInterval( 640, 480 ) );

		prog = new Shader( gl, "ex1", "ex1", tpietzsch.day10.Example5.class );
		progvol = new Shader( gl, "ex1", "ex4vol" );
		progslice = new Shader( gl, "ex1", "ex1slice" );

		loadTexture( gl );

		lookupTexture = new LookupTexture( new int[] { 64, 64, 64 }, GL_RGB16F );
	}

	private void getBlockData( ByteBuffer buffer, RandomAccessibleInterval< UnsignedShortType > rai, final int ... gridPos )
	{
		long[] min = new long[ 3 ];
		long[] max = new long[ 3 ];
		for ( int d = 0; d < 3; ++d )
		{
			min[ d ] = gridPos[ d ] * blockSize[ d ] - cachePadOffset[ d ];
			max[ d ] = min[ d ] + paddedBlockSize[ d ] - 1;
		}
		BlockTextureUtils.imgToBuffer( Views.interval( Views.extendZero( rai ), min, max ), buffer );
	}

	private void loadTexture( final GL3 gl )
	{
		lruBlockCache = new LRUBlockCache<>( paddedBlockSize, LRUBlockCache.findSuitableGridSize( paddedBlockSize, 2, 100 ), 1 );
		textureCache = new TextureCache( paddedBlockSize, lruBlockCache.getGridSize() );

		final ByteBuffer buffer = BlockTextureUtils.allocateBlockBuffer( paddedBlockSize );
		buffer.order( ByteOrder.LITTLE_ENDIAN );
		final ShortBuffer sbuffer = buffer.asShortBuffer();
		final int cap = sbuffer.capacity();
		for ( int i = 0; i < cap; i++ )
			sbuffer.put( i, ( short ) 0x0fff );
		final TextureBlock oobBlock = new TextureBlock( new int[] { 0, 0, 0 }, new int[] { 0, 0, 0 } );
		textureCache.putBlockData( gl, oobBlock, buffer );

		final RandomAccessibleInterval< UnsignedShortType > rai0 = raiLevels.get( 0 ).rai;
		final long sx = rai0.dimension( 0 );
		final long sy = rai0.dimension( 1 );
		final long sz = rai0.dimension( 2 );
		for ( RaiLevel raiLevel : raiLevels )
		{
			final RandomAccessibleInterval< UnsignedShortType > rai = raiLevel.rai;
			final int level = raiLevel.level;
			final int[] r = raiLevel.r;
			for ( int z = 0; z * blockSize[ 2 ] * r[ 2 ] < sz; ++z )
				for ( int y = 0; y * blockSize[ 1 ] * r[ 1 ] < sy; ++y )
					for ( int x = 0; x * blockSize[ 0 ] * r[ 0 ] < sx; ++x )
					{
						final BlockKey key = new BlockKey( x, y, z, level );
						final TextureBlock block = lruBlockCache.add( key );
						getBlockData( buffer, rai, x, y, z );
						textureCache.putBlockData( gl, block, buffer );
					}
		}

		System.out.println( "TextureCache Loaded" );
	}

	@Override
	public void dispose( final GLAutoDrawable drawable )
	{}

	private final double screenPadding = 0;

	private double dCam = 1000;
	private double dClip = 900;
	private double screenWidth = 640;
	private double screenHeight = 480;

	@Override
	public void display( final GLAutoDrawable drawable )
	{
		final GL3 gl = drawable.getGL().getGL3();

		gl.glClearColor( 0.2f, 0.3f, 0.3f, 1.0f );
		gl.glClear( GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT );
		gl.glEnable( GL_DEPTH_TEST );

		offscreen.bind( gl );
		final Matrix4f model = MatrixMath.affine( sourceTransform, new Matrix4f() );
		final Matrix4f view = MatrixMath.affine( worldToScreen.get(), new Matrix4f() );
		final Matrix4f projection = MatrixMath.screenPerspective( dCam, dClip, screenWidth, screenHeight, screenPadding, new Matrix4f() );

		if ( !freezeRequiredBlocks )
		{
			final Matrix4f pvm = new Matrix4f( projection ).mul( view ).mul( model );
			updateBlocks( gl, pvm );
		}

		final Matrix4f ip = new Matrix4f( projection ).invert();
		final Matrix4f ivm = new Matrix4f( view ).mul( model ).invert();
		final Matrix4f ipvm = new Matrix4f( projection ).mul( view ).mul( model ).invert();

		prog.use( gl );
		prog.setUniform( gl, "model", model );
		prog.setUniform( gl, "view", view );
		prog.setUniform( gl, "projection", projection );

		prog.setUniform( gl, "color", 1.0f, 0.5f, 0.2f, 1.0f );
		box.draw( gl );


		Shader modeprog = mode == Mode.SLICE ? progslice : progvol;

		modeprog.use( gl );
		modeprog.setUniform( gl, "model", new Matrix4f() );
		modeprog.setUniform( gl, "view", new Matrix4f() );
		modeprog.setUniform( gl, "projection", projection );
		modeprog.setUniform( gl, "viewportSize", viewportWidth, viewportHeight );
		progslice.setUniform( gl, "ip", ip );
		progslice.setUniform( gl, "ivm", ivm );
		progvol.setUniform( gl, "ipvm", ipvm );
		final RandomAccessibleInterval< UnsignedShortType > rai = raiLevels.get( 0 ).rai;
		modeprog.setUniform( gl, "sourcemin", rai.min( 0 ), rai.min( 1 ), rai.min( 2 ) );
		modeprog.setUniform( gl, "sourcemax", rai.max( 0 ), rai.max( 1 ), rai.max( 2 ) );

		modeprog.setUniform( gl, "scaleLut", 0 );
		modeprog.setUniform( gl, "offsetLut", 1 );
		modeprog.setUniform( gl, "volumeCache", 2 );
		lookupTexture.bindTextures( gl, GL_TEXTURE0, GL_TEXTURE1 );
		textureCache.bindTextures( gl, GL_TEXTURE2 );

		modeprog.setUniform( gl, "blockSize", blockSize[ 0 ], blockSize[ 1 ], blockSize[ 2 ] );
		final int[] lutSize = lookupTexture.getSize();
		modeprog.setUniform( gl, "lutSize", lutSize[ 0 ], lutSize[ 1 ], lutSize[ 2 ] );
		modeprog.setUniform( gl, "padSize", padSize[ 0 ], padSize[ 1 ], padSize[ 2 ] );

		double min = 962;
		double max = 6201;
		double fmin = min / 0xffff;
		double fmax = max / 0xffff;
		double s = 1.0 / ( fmax - fmin );
		double o = -fmin * s;
		modeprog.setUniform( gl, "intensity_offset", ( float ) o );
		modeprog.setUniform( gl, "intensity_scale", ( float ) s );

		screenPlane.updateVertices( gl, new FinalInterval( ( int ) screenWidth, ( int ) screenHeight ) );
//		screenPlane.draw( gl );

		modeprog.setUniform( gl, "viewportSize", offscreen.getWidth(), offscreen.getHeight() );
		screenPlane.draw( gl );
		gl.glDisable( GL_DEPTH_TEST );
		coloredLines.draw( gl, model, view, projection );
		offscreen.unbind( gl, false );
		offscreen.drawQuad( gl );
	}

	private void updateBlocks( final GL3 gl, final Matrix4f pvm )
	{
		final int level = 0;
		final RequiredBlocks requiredBlocks = computeRequiredBlocks( pvm, level );

		final int vw = offscreen.getWidth();
//		final int vw = viewportWidth;
		final MipmapSizes sizes = new MipmapSizes();
		sizes.init( pvm, vw );

		coloredLines.clear();
		coloredLines.add( sizes.pNear, new Vector3f( sizes.pNear ).add( sizes.pFarMinusNear ), new Vector4f( 1, 1, 1, 1 ) );

		updateLookupTexture( gl, requiredBlocks, level, sizes );
	}

	private RequiredBlocks computeRequiredBlocks( final Matrix4f pvm, final int level )
	{
		final RaiLevel raiLevel = raiLevels.get( level );
		long[] sourceSize = new long[ 3 ];
		raiLevel.rai.dimensions( sourceSize );
		return getRequiredBlocksFrustum( pvm, blockSize, sourceSize, raiLevel.r );
	}

	final int[] padSize = { 1, 1, 1 };

	private void updateLookupTexture( final GL3 gl, final RequiredBlocks requiredBlocks, final int baseLevel, MipmapSizes sizes )
	{
		final long t0 = System.currentTimeMillis();
		final int[] lutSize = lookupTexture.getSize();
		final int[] cacheSize = textureCache.getSize();

		final int dataSize = 3 * ( int ) Intervals.numElements( lutSize );
		final float[] qsData = new float[ dataSize ];
		final float[] qdData = new float[ dataSize ];

		final float[] sls = sizes.sls( raiLevels );
		final int[] r = raiLevels.get( baseLevel ).r;
		final Vector3f blockCenter = new Vector3f();
		final Vector3f tmp = new Vector3f();

		// offset for IntervalIndexer
		final int[] padOffset = new int[] { -padSize[ 0 ], -padSize[ 1 ], -padSize[ 2 ] };

		final ArrayList< int[] > gridPositions = requiredBlocks.getGridPositions();
		final int[] gj = new int[ 3 ];

		// <!-- DEBUG
		float drelMin = Float.POSITIVE_INFINITY;
		float drelMax = Float.NEGATIVE_INFINITY;
		for ( int[] g0 : gridPositions )
		{
			for ( int d = 0; d < 3; ++d )
				blockCenter.setComponent( d, ( g0[ d ] + 0.5f ) * blockSize[ d ] * r[ d ] );
			if ( g0[ 1 ] % 3 == 0 && g0[ 0 ] % 3 == 0 && g0[ 2 ] == 0 )
			{
				float drel = sizes.getDrel( sls, blockCenter, tmp );
				drelMin = Math.min( drelMin, drel );
				drelMax = Math.max( drelMax, drel );
			}
		}
		System.out.println( "drels = " + drelMin + " ... " + drelMax );
		// DEBUG -->


		for ( int[] g0 : gridPositions )
		{
			for ( int d = 0; d < 3; ++d )
				blockCenter.setComponent( d, ( g0[ d ] + 0.5f ) * blockSize[ d ] * r[ d ] );
			final int level = sizes.bestLevel( sls, blockCenter, tmp );

			if ( g0[ 1 ] % 3 == 0 && g0[ 0 ] % 3 == 0 && g0[ 2 ] == 0 )
			{
				float drel = sizes.getDrel( sls, blockCenter, tmp );
				drelMin = Math.min( drelMin, drel );
				drelMax = Math.max( drelMax, drel );
				coloredLines.add( sizes.pNear, blockCenter, new Vector4f( 0.5f, ( drel - drelMin ) / ( drelMax - drelMin ), 0, 1 ) );
			}

			final double[] sj = raiLevels.get( level ).s;
			for ( int d = 0; d < 3; ++d )
				gj[ d ] = ( int ) ( g0[ d ] * sj[ d ] );

			final TextureBlock textureBlock = lruBlockCache.get( new BlockKey( gj, level ) );
			if ( textureBlock == null )
			{
				System.err.println( "gj = " + Arrays.toString( gj ) + ", level = " + level );
				continue;
			}
			final int[] texpos = textureBlock.getPos();

			final int i = IntervalIndexer.positionWithOffsetToIndex( g0, lutSize, padOffset );
			for ( int d = 0; d < 3; ++d )
			{
				double qs = sj[ d ] * lutSize[ d ] * blockSize[ d ] / cacheSize[ d ];
				double p = g0[ d ] * blockSize[ d ];
				double hj = 0.5 * ( sj[ d ] - 1 );
				double c0 = texpos[ d ] + cachePadOffset[ d ] + p * sj[ d ] - gj[ d ] * blockSize[ d ] + hj;
				double qd = ( c0 - sj[ d ] * ( padSize[ d ] * blockSize[ d ] + p ) + 0.5 ) / cacheSize[ d ];
				qsData[ 3 * i + d ] = ( float ) qs;
				qdData[ 3 * i + d ] = ( float ) qd;
			}
		}
		final long t1 = System.currentTimeMillis();
		lookupTexture.set( gl, qsData, qdData );
		final long t2 = System.currentTimeMillis();
		System.out.println( "lookup texture took " + ( t1 - t0 ) + " ms to compute, " + ( t2 - t1 ) + " ms to upload" );
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

	public static void main( final String[] args ) throws SpimDataException
	{
		final String xmlFilename = "/Users/pietzsch/workspace/data/111010_weber_full.xml";
		final SpimDataMinimal spimData = new XmlIoSpimDataMinimal().load( xmlFilename );
		MultiResolutionSetupImgLoader< UnsignedShortType > sil = ( MultiResolutionSetupImgLoader< UnsignedShortType > ) spimData.getSequenceDescription().getImgLoader().getSetupImgLoader( 0 );

		ArrayList< RaiLevel > raiLevels = new ArrayList<>();
		final int numMipmapLevels = sil.numMipmapLevels();
		for ( int level = 0; level < numMipmapLevels; level++ )
		{
			final RandomAccessibleInterval< UnsignedShortType > rai = sil.getImage( 1, level );
			final double[] resolution = sil.getMipmapResolutions()[ level ];
			final RaiLevel raiLevel = new RaiLevel( level, resolution, rai );
			raiLevels.add( raiLevel );
			System.out.println( raiLevel );
		}

		final AffineTransform3D sourceTransform = spimData.getViewRegistrations().getViewRegistration( 1, 0 ).getModel();

		final InputFrame frame = new InputFrame( "Example4", 640, 480 );
		InputFrame.DEBUG = false;
		Example4 glPainter = new Example4( raiLevels, sourceTransform );
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
		frame.show();

//		// print fps
//		FPSAnimator animator = new FPSAnimator( frame.getCanvas(), 100 );
//		animator.setUpdateFPSFrames(100, System.out );
//		animator.start();
	}
}
