package tpietzsch.blockmath1;

import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import bdv.volume.RequiredBlocks;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.MultiResolutionSetupImgLoader;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.iterator.IntervalIterator;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import org.joml.Matrix4f;
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
public class Example2 implements GLEventListener
{
	private final List< RaiLevel> raiLevels;

	private final AffineTransform3D sourceTransform;

	private OffScreenFrameBuffer offscreen;

	private Shader prog;

	private Shader progslice;

	private Shader progvol;

	private WireframeBox1 box;

	private ScreenPlane1 screenPlane;

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

	public Example2( List< RaiLevel > raiLevels, final AffineTransform3D sourceTransform )
	{
		this.raiLevels = raiLevels;
		this.sourceTransform = sourceTransform;
		imgGridSize = raiLevels.get( 0 ).imgGridSize( blockSize );
		offscreen = new OffScreenFrameBuffer( 640, 480, GL_RGB8 );
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
		progvol = new Shader( gl, "ex1", "ex1vol" );
		progslice = new Shader( gl, "ex1", "ex1slice" );

		loadTexture( gl );

		lookupTexture = new LookupTexture( new int[] { 64, 64, 64 }, GL_RGB16F );

		gl.glEnable( GL_DEPTH_TEST );
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
		lruBlockCache = new LRUBlockCache<>( paddedBlockSize, LRUBlockCache.findSuitableGridSize( paddedBlockSize, 2, 100 ) );
		textureCache = new TextureCache( paddedBlockSize, lruBlockCache.getGridSize() );

		final ByteBuffer buffer = BlockTextureUtils.allocateBlockBuffer( paddedBlockSize );

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

	final int[] padSize = { 1, 1, 1 };

	private void updateLookupTexture( final GL3 gl, final RequiredBlocks requiredBlocks )
	{
		final int[] lutSize = lookupTexture.getSize();
		final int[] cacheSize = textureCache.getSize();

		final int dataSize = 3 * ( int ) Intervals.numElements( lutSize );
		final float[] qsData = new float[ dataSize ];
		final float[] qdData = new float[ dataSize ];

		// offset for IntervalIndexer
		final int[] padOffset = new int[] { -padSize[ 0 ], -padSize[ 1 ], -padSize[ 2 ] };

		final ArrayList< int[] > gridPositions = requiredBlocks.getGridPositions();
		final int[] gj = new int[ 3 ];
		int level = 0;
		for ( int[] g0 : gridPositions )
		{
			final double[] sj = raiLevels.get( level ).s;

			for ( int d = 0; d < 3; ++d )
				gj[ d ] = ( int ) ( g0[ d ] * sj[ d ] );

			final TextureBlock textureBlock = lruBlockCache.get( new BlockKey( gj, level ) );
			if ( textureBlock == null )
			{
				System.out.println( "gj = " + Arrays.toString( gj ) + ", level = " + level );
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

			level = ( level + 1 ) % 3;
		}

		lookupTexture.set( gl, qsData, qdData );
	}

	@Override
	public void dispose( final GLAutoDrawable drawable )
	{}

	private final double screenPadding = 0;

	private double dCam = 2000;
	private double dClip = 1000;
	private double screenWidth = 640;
	private double screenHeight = 480;

	@Override
	public void display( final GLAutoDrawable drawable )
	{
		final GL3 gl = drawable.getGL().getGL3();

		gl.glClearColor( 0.2f, 0.3f, 0.3f, 1.0f );
		gl.glClear( GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT );

		offscreen.bind( gl );
		final Matrix4f model = MatrixMath.affine( sourceTransform, new Matrix4f() );
		final Matrix4f view = MatrixMath.affine( worldToScreen.get(), new Matrix4f() );
		final Matrix4f projection = MatrixMath.screenPerspective( dCam, dClip, screenWidth, screenHeight, screenPadding, new Matrix4f() );

		final RequiredBlocks requiredBlocks = computeRequiredBlocks( model, view, projection, 0 );
		updateLookupTexture( gl, requiredBlocks );

		final Matrix4f ip = new Matrix4f( projection ).invert();
		final Matrix4f ivm = new Matrix4f( view ).mul( model ).invert();
		final Matrix4f ipvm = new Matrix4f( projection ).mul( view ).mul( model ).invert();

		prog.use( gl );
		prog.setUniform( gl, "model", model );
		prog.setUniform( gl, "view", view );
		prog.setUniform( gl, "projection", projection );

		prog.setUniform( gl, "color", 1.0f, 0.5f, 0.2f, 1.0f );
		box.draw( gl );


		model.identity();
		view.identity();

		Shader modeprog = mode == Mode.SLICE ? progslice : progvol;

		modeprog.use( gl );
		modeprog.setUniform( gl, "model", model );
		modeprog.setUniform( gl, "view", view );
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
		offscreen.unbind( gl, false );
		offscreen.drawQuad( gl );
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

	public static class RaiLevel
	{
		final int level;

		final int[] r;

		final double[] s;

		final RandomAccessibleInterval< UnsignedShortType > rai;

		public RaiLevel( final int level, final double[] resolution, final RandomAccessibleInterval< UnsignedShortType > rai )
		{
			this.level = level;
			this.r = new int[] { (int) resolution[ 0 ], (int) resolution[ 1 ], (int) resolution[ 2 ]  };
			this.s = new double[] { 1 / resolution[ 0 ], 1 / resolution[ 1 ], 1 / resolution[ 2 ] };
			this.rai = rai;
		}

		public int[] imgGridSize( final int[] blockSize )
		{
			final int[] imgGridSize = new int[ 3 ];
			for ( int d = 0; d < 3; ++d )
				imgGridSize[ d ] = ( int ) ( rai.dimension( d ) - 1 ) / blockSize[ d ] + 1;
			return imgGridSize;
		}

		@Override
		public String toString()
		{
			return "RaiLevel{level=" + level + ", r=" + Arrays.toString( r ) + ", s=" + Arrays.toString( s ) + ", rai=" + Util.printInterval( rai ) + '}';
		}
	}

	private RequiredBlocks computeRequiredBlocks( final Matrix4f model, final Matrix4f view, final Matrix4f projection, final int level )
	{
		final Matrix4f pvm = new Matrix4f( projection ).mul( view ).mul( model );
		final RaiLevel raiLevel = raiLevels.get( level );
		long[] sourceSize = new long[ 3 ];
		raiLevel.rai.dimensions( sourceSize );
		return getRequiredBlocksFrustum( pvm, blockSize, sourceSize, raiLevel.r );
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

		final InputFrame frame = new InputFrame( "Example2", 640, 480 );
		InputFrame.DEBUG = false;
		Example2 glPainter = new Example2( raiLevels, sourceTransform );
		frame.setGlEventListener( glPainter );
		final TransformHandler tf = frame.setupDefaultTransformHandler( glPainter.worldToScreen::set );
		frame.getDefaultActions().runnableAction( () -> {
			tf.setTransform( new AffineTransform3D() );
		}, "reset transform", "R" );
		frame.getDefaultActions().runnableAction( () -> {
			glPainter.toggleVolumeSliceMode();
			frame.requestRepaint();
		}, "volume/slice mode", "M" );
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
//		animator.setUpdateFPSFrames(10, System.out );
//		animator.start();
	}
}
