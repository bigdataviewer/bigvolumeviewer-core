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
import static javax.swing.SwingConstants.HORIZONTAL;

import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;

import java.awt.BorderLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import net.imglib2.FinalInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.iotiming.CacheIoTiming;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.volatiles.VolatileUnsignedShortType;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.util.Intervals;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import bdv.ViewerImgLoader;
import bdv.ViewerSetupImgLoader;
import bdv.cache.CacheControl;
import bdv.img.cache.VolatileCachedCellImg;
import bdv.spimdata.SequenceDescriptionMinimal;
import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import bdv.volume.RequiredBlocks;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.TimePoint;
import tpietzsch.blockmath2.LookupTexture;
import tpietzsch.blockmath3.ArrayGridCopy3D;
import tpietzsch.blockmath3.BlockKey;
import tpietzsch.blockmath3.MipmapSizes;
import tpietzsch.blockmath3.RaiLevel;
import tpietzsch.blockmath3.RaiLevels;
import tpietzsch.blockmath3.TextureBlock;
import tpietzsch.day10.OffScreenFrameBuffer;
import tpietzsch.day2.Shader;
import tpietzsch.day4.InputFrame;
import tpietzsch.day4.ScreenPlane1;
import tpietzsch.day4.TransformHandler;
import tpietzsch.day4.WireframeBox1;
import tpietzsch.util.MatrixMath;
import tpietzsch.util.Syncd;

/**
 * Use blocks from all levels. Kind-of-ok fetching of hdf5 cache data.
 */
public class Example1 implements GLEventListener
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

	private final long thresholdUploadMillis = 10;

	private final TextureBlockCache< BlockKey > blockCache;

	private LookupTexture lookupTexture;

	final Syncd< AffineTransform3D > worldToScreen = Syncd.affine3D();

	final Syncd< RaiLevels > raiLevelsSyncd = Syncd.raiLevels();

	private RaiLevels raiLevels;

	private int viewportWidth = 100;

	private int viewportHeight = 100;

	private int baseLevel;

	enum Mode { VOLUME, SLICE };

	private Mode mode = Mode.VOLUME;

	private boolean freezeRequiredBlocks = false;

	private final CacheControl cacheControl;

	private final Runnable requestRepaint;

	public Example1( final CacheControl cacheControl, final Runnable requestRepaint )
	{
		this.cacheControl = cacheControl;
		this.requestRepaint = requestRepaint;
		offscreen = new OffScreenFrameBuffer( 640, 480, GL_RGB8 );
		final TextureBlockCache.BlockLoader< BlockKey > blockLoader = new TextureBlockCache.BlockLoader< BlockKey >()
		{
			@Override
			public boolean loadBlock( final BlockKey key, final Buffer buffer )
			{
				return Example1.this.loadBlock( key, ( ByteBuffer ) buffer );
			}

			@Override
			public boolean canLoadBlock( final BlockKey key )
			{
				return Example1.this.canLoadBlock( key );
			}
		};
		blockCache = new TextureBlockCache<>( paddedBlockSize, 200, blockLoader );
		raiLevels = raiLevelsSyncd.get();
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
		// TODO: use timepoint/setup of the BlockKey
		final RandomAccessibleInterval< VolatileUnsignedShortType > rai = raiLevels.getRaiLevels().get( key.getLevel() ).getRai();
		final int[] gridPos = key.getGridPos();
		final int[] min = new int[ 3 ];
		for ( int d = 0; d < 3; ++d )
			min[ d ] = gridPos[ d ] * blockSize[ d ] - cachePadOffset[ d ];
		return new Copier( rai, paddedBlockSize ).canLoadCompletely( min );
	}

	public boolean loadBlock( final BlockKey key, final ByteBuffer buffer )
	{
		// TODO: use timepoint/setup of the BlockKey
		final RandomAccessibleInterval< VolatileUnsignedShortType > rai = raiLevels.getRaiLevels().get( key.getLevel() ).getRai();
		final int[] gridPos = key.getGridPos();
		final int[] min = new int[ 3 ];
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

		public Copier( final RandomAccessibleInterval< VolatileUnsignedShortType > rai, final int[] blocksize )
		{
			final VolatileCachedCellImg< VolatileUnsignedShortType, ? > img = ( VolatileCachedCellImg< VolatileUnsignedShortType, ? > ) rai;
			grid = img.getCellGrid();
			dataAccess = new ArrayGridCopy3D.VolatileShortCellDataAccess( ( RandomAccess ) img.getCells().randomAccess() );

			data = new short[ ( int ) Intervals.numElements( blocksize ) ];

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

		raiLevels = raiLevelsSyncd.get();
		if ( raiLevels.getRaiLevels().isEmpty() )
			return;

		offscreen.bind( gl );
		final Matrix4f model = MatrixMath.affine( raiLevels.getSourceTransform(), new Matrix4f() );
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
		box.updateVertices( gl, raiLevels.getRaiLevels().get( 0 ).getRai() );
		box.draw( gl );



		final int[] baseScale = raiLevels.getRaiLevels().get( baseLevel ).getR();
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
		sizes.init( pvm, vw, raiLevels.getRaiLevels() );
		baseLevel = sizes.getBaseLevel();
		System.out.println( "baseLevel = " + baseLevel );

		final RaiLevel raiLevel = raiLevels.getRaiLevels().get( baseLevel );

		final int bsx = raiLevel.getR()[ 0 ];
		final int bsy = raiLevel.getR()[ 1 ];
		final int bsz = raiLevel.getR()[ 2 ];
		final Matrix4f upscale = new Matrix4f(
				bsx, 0, 0, 0,
				0, bsy, 0, 0,
				0, 0, bsz, 0,
				0.5f * ( bsx - 1 ), 0.5f * ( bsy - 1 ), 0.5f * ( bsz - 1 ), 1 );
		final Matrix4fc pvms = pvm.mul( upscale, new Matrix4f() );
		final Matrix4fc ipvms =	pvms.invert( new Matrix4f() );

		final RandomAccessibleInterval< VolatileUnsignedShortType > rai = raiLevel.getRai();
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
		final int timepoint = raiLevels.getTimepoint();
		final int setup = raiLevels.getSetup();
		final int maxLevel = raiLevels.getRaiLevels().size() - 1;

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

		final int[] r = raiLevels.getRaiLevels().get( baseLevel ).getR();
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
				final double[] sj = raiLevels.getRaiLevels().get( level ).getS();
				for ( int d = 0; d < 3; ++d )
				{
					sij[ d ] = sj[ d ] * r[ d ];
					gj[ d ] = ( int ) ( g0[ d ] * sij[ d ] );
				}
				final TextureBlock textureBlock =
						level == maxLevel
								? blockCache.get( gl, new BlockKey( gj, level, timepoint, setup ) )
								: blockCache.getIfPresentOrCompletable( gl, new BlockKey( gj, level, timepoint, setup ) );
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


	static class RaiLevelsMaker
	{
		private final SpimDataMinimal spimData;

		private final List< BasicViewSetup > setups;

		private final ViewRegistrations registrations;

		private final List< ViewerSetupImgLoader< UnsignedShortType, VolatileUnsignedShortType > > setupImgLoaders;

		private final List< TimePoint > timepoints;

		private final CacheControl cacheControl;

		public RaiLevelsMaker( final SpimDataMinimal spimData )
		{
			this.spimData = spimData;

			final SequenceDescriptionMinimal seq = spimData.getSequenceDescription();
			setups = seq.getViewSetupsOrdered();
			timepoints = seq.getTimePoints().getTimePointsOrdered();
			registrations = spimData.getViewRegistrations();
			setupImgLoaders = new ArrayList<>();

			if ( ! ( seq.getImgLoader() instanceof ViewerImgLoader ) )
				throw new IllegalArgumentException();
			final ViewerImgLoader imgLoader = ( ViewerImgLoader ) seq.getImgLoader();

			cacheControl = imgLoader.getCacheControl();

			for ( final BasicViewSetup setup : setups )
			{
				final ViewerSetupImgLoader< ?, ? > il = imgLoader.getSetupImgLoader( setup.getId() );
				if ( il.getImageType() instanceof UnsignedShortType && il.getVolatileImageType() instanceof VolatileUnsignedShortType )
					setupImgLoaders.add( ( ViewerSetupImgLoader< UnsignedShortType, VolatileUnsignedShortType > ) il );
				else
					throw new IllegalArgumentException();
			}
		}

		public RaiLevels get( final int timepoint, final int setup )
		{
			final int timepointId = timepoints.get( timepoint ).getId();
			final int setupId = setups.get( setup ).getId();
			final AffineTransform3D model = registrations.getViewRegistration( timepointId, setupId ).getModel();
			final ArrayList< RaiLevel > raiLevels = new ArrayList<>();

			final ViewerSetupImgLoader< UnsignedShortType, VolatileUnsignedShortType > sil = setupImgLoaders.get( setup );
			final int numMipmapLevels = sil.numMipmapLevels();
			for ( int level = 0; level < numMipmapLevels; level++ )
			{
				final RandomAccessibleInterval< VolatileUnsignedShortType > rai = sil.getVolatileImage( timepointId, level );
				final double[] resolution = sil.getMipmapResolutions()[ level ];
				final RaiLevel raiLevel = new RaiLevel( level, resolution, rai );
				raiLevels.add( raiLevel );
			}
			return new RaiLevels( raiLevels, model, timepoint, setup );
		}

		public CacheControl getCacheControl()
		{
			return cacheControl;
		}
	}

	int maxTimepoint;

	int currentTimepoint = 0;

	RaiLevelsMaker raiLevelsMaker;

	JSlider stime;

	void setCurrentTimepoint( final int t )
	{
		if ( currentTimepoint != t )
		{
			currentTimepoint = Math.min( maxTimepoint, Math.max( 0, t ) );
			System.out.println( "currentTimepoint = " + currentTimepoint );
			raiLevelsSyncd.set( raiLevelsMaker.get( currentTimepoint, currentSetup ) );
			stime.setValue( currentTimepoint );
			requestRepaint.run();
		}
	}

	int currentSetup = 0;

	public static void main( final String[] args ) throws SpimDataException
	{
		final String xmlFilename = "/Users/pietzsch/workspace/data/111010_weber_full.xml";
//		final String xmlFilename = "/Users/pietzsch/Desktop/data/TGMM_METTE/Pdu_H2BeGFP_CAAXmCherry_0123_20130312_192018.corrected/dataset_hdf5.xml";
//		final String xmlFilename = "/Users/pietzsch/Desktop/data/MAMUT/MaMuT_demo_dataset/MaMuT_Parhyale_demo.xml";
		final SpimDataMinimal spimData = new XmlIoSpimDataMinimal().load( xmlFilename );
		final RaiLevelsMaker raiLevelsMaker = new RaiLevelsMaker( spimData );


		final InputFrame frame = new InputFrame( "Example1", 640, 480 );
		InputFrame.DEBUG = false;
		final Example1 glPainter = new Example1( raiLevelsMaker.getCacheControl(), frame::requestRepaint );
		glPainter.maxTimepoint = spimData.getSequenceDescription().getTimePoints().getTimePointsOrdered().size() - 1;
		glPainter.raiLevelsMaker = raiLevelsMaker;
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
			glPainter.setCurrentTimepoint( glPainter.currentTimepoint - 1 );
		}, "previous timepoint", "OPEN_BRACKET" );
		frame.getDefaultActions().runnableAction( () -> {
			glPainter.setCurrentTimepoint( glPainter.currentTimepoint + 1 );
		}, "next timepoint", "CLOSE_BRACKET" );
		frame.getDefaultActions().runnableAction( () -> {
			glPainter.currentSetup = 0;
			System.out.println( "currentSetup = " + glPainter.currentSetup );
			glPainter.raiLevelsSyncd.set( raiLevelsMaker.get( glPainter.currentTimepoint, glPainter.currentSetup ) );
			frame.requestRepaint();
		}, "setup 1", "1" );
		frame.getDefaultActions().runnableAction( () -> {
			glPainter.currentSetup = 1;
			System.out.println( "currentSetup = " + glPainter.currentSetup );
			glPainter.raiLevelsSyncd.set( raiLevelsMaker.get( glPainter.currentTimepoint, glPainter.currentSetup ) );
			frame.requestRepaint();
		}, "setup 2", "2" );
		frame.getDefaultActions().runnableAction( () -> {
			glPainter.currentSetup = 2;
			System.out.println( "currentSetup = " + glPainter.currentSetup );
			glPainter.raiLevelsSyncd.set( raiLevelsMaker.get( glPainter.currentTimepoint, glPainter.currentSetup ) );
			frame.requestRepaint();
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
		glPainter.raiLevelsSyncd.set( raiLevelsMaker.get( 0, 0 ) );

		final JFrame jframe = frame.getFrame();
		final JSlider sliderTime = new JSlider( HORIZONTAL, 0, glPainter.maxTimepoint, 0 );
		sliderTime.addChangeListener( new ChangeListener()
		{
			@Override
			public void stateChanged( final ChangeEvent e )
			{
				if ( e.getSource().equals( sliderTime ) )
					glPainter.setCurrentTimepoint( sliderTime.getValue() );
			}
		} );
		glPainter.stime = sliderTime;
		jframe.getContentPane().add( glPainter.stime, BorderLayout.SOUTH );
		jframe.pack();

		frame.show();

//		// print fps
//		FPSAnimator animator = new FPSAnimator( frame.getCanvas(), 100 );
//		animator.setUpdateFPSFrames(100, System.out );
//		animator.start();
	}
}