package tpietzsch.blockmath3;

import bdv.ViewerSetupImgLoader;
import bdv.img.cache.VolatileCachedCellImg;
import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import bdv.volume.RequiredBlocks;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import mpicbg.spim.data.SpimDataException;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.volatiles.VolatileUnsignedShortType;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.util.Intervals;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import tpietzsch.blockmath2.LookupTexture;
import tpietzsch.day10.OffScreenFrameBuffer;
import tpietzsch.day2.Shader;
import tpietzsch.day4.InputFrame;
import tpietzsch.day4.ScreenPlane1;
import tpietzsch.day4.TransformHandler;
import tpietzsch.day4.WireframeBox1;
import tpietzsch.util.MatrixMath;
import tpietzsch.util.Syncd;

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

/**
 * Rendering slices and volume with BlockTexture and TextureCache.
 */
public class Example2 implements GLEventListener
{
	private final List< RaiLevel > raiLevels;

	private final AffineTransform3D sourceTransform;

	private OffScreenFrameBuffer offscreen;

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

	private int viewportWidth = 100;

	private int viewportHeight = 100;

	private int baseLevel;

	enum Mode { VOLUME, SLICE };

	private Mode mode = Mode.VOLUME;

	private boolean freezeRequiredBlocks = false;

	public Example2( List< RaiLevel > raiLevels, final AffineTransform3D sourceTransform )
	{
		this.raiLevels = raiLevels;
		this.sourceTransform = sourceTransform;
		offscreen = new OffScreenFrameBuffer( 640, 480, GL_RGB8 );
		blockCache = new TextureBlockCache<>( paddedBlockSize, 100, this::loadBlock );
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

		lookupTexture = new LookupTexture( new int[] { 64, 64, 64 }, GL_RGB16F );

		gl.glEnable( GL_DEPTH_TEST );
	}

	public boolean loadBlock( final BlockKey key, final ByteBuffer buffer )
	{
		RandomAccessibleInterval< VolatileUnsignedShortType > rai = raiLevels.get( key.getLevel() ).rai;
		final int[] gridPos = key.getGridPos();
		int[] min = new int[ 3 ];
		for ( int d = 0; d < 3; ++d )
			min[ d ] = gridPos[ d ] * blockSize[ d ] - cachePadOffset[ d ];
		return new Copier( rai, paddedBlockSize ).toBuffer( buffer, min );
	}

	public static class Copier
	{
		private final ArrayGridCopy3D gcopy = new ArrayGridCopy3D();

		private final ArrayGridCopy3D.VolatileShortCellDataAccess dataAccess;

		private final ArrayGridCopy3D.ShortSubArrayCopy subArrayCopy = new ArrayGridCopy3D.ShortSubArrayCopy();

		private final CellGrid grid;

		private final int[] blocksize;

		private final short[] data;

		public Copier( RandomAccessibleInterval< VolatileUnsignedShortType > rai, final int[] blocksize )
		{
			final VolatileCachedCellImg< VolatileUnsignedShortType, ? > img = ( VolatileCachedCellImg< VolatileUnsignedShortType, ? > ) rai;
			grid = img.getCellGrid();
			dataAccess = new ArrayGridCopy3D.VolatileShortCellDataAccess( ( RandomAccess ) img.getCells().randomAccess() );

			data = new short[ ( int ) Intervals.numElements( blocksize ) ];

			this.blocksize = blocksize;
		}

		/**
		 * @return {@code true}, if this block was completely loaded
		 */
		public boolean toBuffer( final ByteBuffer buffer, final int[] min )
		{
			final boolean complete = gcopy.copy( min, blocksize, grid, data, dataAccess, subArrayCopy );

			final ShortBuffer sbuffer = buffer.asShortBuffer();
			for ( int i = 0; i < data.length; i++ )
				sbuffer.put( i, data[ i ] );

			return complete;
		}
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
		box.draw( gl );



		final int[] baseScale = raiLevels.get( baseLevel ).r;
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

		Shader modeprog = mode == Mode.SLICE ? progslice : progvol;

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

	private final Vector3f sourceLevelMin = new Vector3f();
	private final Vector3f sourceLevelMax = new Vector3f();

	private void updateBlocks( final GL3 gl, final Matrix4f pvm )
	{
		final int vw = offscreen.getWidth();
//		final int vw = viewportWidth;
		final MipmapSizes sizes = new MipmapSizes();
		sizes.init( pvm, vw, raiLevels );
		baseLevel = sizes.getBaseLevel();
		System.out.println( "baseLevel = " + baseLevel );

		final RaiLevel raiLevel = raiLevels.get( baseLevel );

		final int bsx = raiLevel.r[ 0 ];
		final int bsy = raiLevel.r[ 1 ];
		final int bsz = raiLevel.r[ 2 ];
		final Matrix4f upscale = new Matrix4f(
				bsx, 0, 0, 0,
				0, bsy, 0, 0,
				0, 0, bsz, 0,
				0.5f * ( bsx - 1 ), 0.5f * ( bsy - 1 ), 0.5f * ( bsz - 1 ), 1 );
		final Matrix4fc pvms = pvm.mul( upscale, new Matrix4f() );
		final Matrix4fc ipvm =	pvm.invert( new Matrix4f() );

		final RandomAccessibleInterval< VolatileUnsignedShortType > rai = raiLevel.rai;
		sourceLevelMin.set( rai.min( 0 ), rai.min( 1 ), rai.min( 2 ) ); // TODO -0.5 offset?
		sourceLevelMax.set( rai.max( 0 ), rai.max( 1 ), rai.max( 2 ) ); // TODO -0.5 offset?

		final Vector3f fbbmin = new Vector3f();
		final Vector3f fbbmax = new Vector3f();
		ipvm.frustumAabb( fbbmin, fbbmax );
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

		final int[] requiredLutSize = new int[] {
				( int ) ( gridMax[ 0 ] - gridMin[ 0 ] + 1 ),
				( int ) ( gridMax[ 1 ] - gridMin[ 1 ] + 1 ),
				( int ) ( gridMax[ 2 ] - gridMin[ 2 ] + 1 )
		};
		System.out.println( "requiredLutSize = " + Arrays.toString( requiredLutSize ) );

		final RequiredBlocks requiredBlocks = getRequiredLevelBlocksFrustum( pvms, blockSize, gridMin, gridMax );
		System.out.println( "requiredBlocks = " + requiredBlocks );
		updateLookupTexture( gl, requiredBlocks, baseLevel, sizes );
	}

	final int[] padSize = { 1, 1, 1 };

	final int[] pad = { 1, 1, 1 };

	private void updateLookupTexture( final GL3 gl, final RequiredBlocks requiredBlocks, final int baseLevel, MipmapSizes sizes )
	{
		final long t0 = System.currentTimeMillis();
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

		final int[] r = raiLevels.get( baseLevel ).r;
		final Vector3f blockCenter = new Vector3f();
		final Vector3f tmp = new Vector3f();

		// offset for IntervalIndexer
		pad[ 0 ] = padSize[ 0 ] - rmin[ 0 ];
		pad[ 1 ] = padSize[ 1 ] - rmin[ 1 ];
		pad[ 2 ] = padSize[ 2 ] - rmin[ 2 ];
		final int[] padOffset = new int[] { -pad[ 0 ], -pad[ 1 ], -pad[ 2 ] };

		final ArrayList< int[] > gridPositions = requiredBlocks.getGridPositions();
		final int[] gj = new int[ 3 ];

		for ( int[] g0 : gridPositions )
		{
			for ( int d = 0; d < 3; ++d )
				blockCenter.setComponent( d, ( g0[ d ] + 0.5f ) * blockSize[ d ] * r[ d ] );
			final int level = Math.max( baseLevel, sizes.bestLevel( blockCenter, tmp ) );

			final double[] sj = raiLevels.get( level ).s;
			final double[] sij = new double[] { sj[ 0 ] * r[ 0 ], sj[ 1 ] * r[ 1 ], sj[ 2 ] * r[ 2 ] };
			for ( int d = 0; d < 3; ++d )
				gj[ d ] = ( int ) ( g0[ d ] * sij[ d ] );

			final TextureBlock textureBlock = blockCache.get( gl, new BlockKey( gj, level, 0, 0 ) );
			final int[] texpos = textureBlock.getPos();

			final int i = IntervalIndexer.positionWithOffsetToIndex( g0, lutSize, padOffset );
			for ( int d = 0; d < 3; ++d )
			{
				double qs = sij[ d ] * lutSize[ d ] * blockSize[ d ] / cacheSize[ d ];
				double p = g0[ d ] * blockSize[ d ];
				double hj = 0.5 * ( sij[ d ] - 1 );
				double c0 = texpos[ d ] + cachePadOffset[ d ] + p * sij[ d ] - gj[ d ] * blockSize[ d ] + hj;
				double qd = ( c0 - sij[ d ] * ( pad[ d ] * blockSize[ d ] + p ) + 0.5 ) / cacheSize[ d ];
				qsData[ 3 * i + d ] = ( float ) qs;
				qdData[ 3 * i + d ] = ( float ) qd;
			}
		}
		final long t1 = System.currentTimeMillis();
		lookupTexture.resize( gl, lutSize );
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
		ViewerSetupImgLoader< UnsignedShortType, VolatileUnsignedShortType > sil = ( ViewerSetupImgLoader< UnsignedShortType, VolatileUnsignedShortType > ) spimData.getSequenceDescription().getImgLoader().getSetupImgLoader( 0 );

		ArrayList< RaiLevel > raiLevels = new ArrayList<>();
		final int numMipmapLevels = sil.numMipmapLevels();
		for ( int level = 0; level < numMipmapLevels; level++ )
		{
			final RandomAccessibleInterval< VolatileUnsignedShortType > rai = sil.getVolatileImage( 1, level );
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
