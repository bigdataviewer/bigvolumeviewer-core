package tpietzsch.day6;

import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.util.FPSAnimator;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.MultiResolutionSetupImgLoader;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;
import org.joml.Matrix4f;
import tpietzsch.day2.Shader;
import tpietzsch.day4.InputFrame;
import tpietzsch.day4.ScreenPlane1;
import tpietzsch.day4.TransformHandler;
import tpietzsch.day4.WireframeBox1;
import tpietzsch.util.MatrixMath;
import tpietzsch.util.Syncd;

import static com.jogamp.opengl.GL.GL_BLEND;
import static com.jogamp.opengl.GL.GL_COLOR_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_DEPTH_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_DEPTH_TEST;
import static com.jogamp.opengl.GL.GL_LINEAR;
import static com.jogamp.opengl.GL.GL_ONE_MINUS_SRC_ALPHA;
import static com.jogamp.opengl.GL.GL_R16F;
import static com.jogamp.opengl.GL.GL_SRC_ALPHA;
import static com.jogamp.opengl.GL.GL_TEXTURE0;
import static com.jogamp.opengl.GL.GL_TEXTURE_MAG_FILTER;
import static com.jogamp.opengl.GL.GL_TEXTURE_MIN_FILTER;
import static com.jogamp.opengl.GL.GL_UNSIGNED_SHORT;
import static com.jogamp.opengl.GL2ES2.GL_RED;
import static com.jogamp.opengl.GL2ES2.GL_TEXTURE_3D;

/**
 * Draw bounding box of source 0 timepoint 0.
 */
public class Example4 implements GLEventListener
{
	private final RandomAccessibleInterval< UnsignedShortType > rai;

	private final AffineTransform3D sourceTransform;

	private int vao;

	private Shader prog;

	private Shader progslice;

	private Shader progvol;

	private WireframeBox1 box;

	private ScreenPlane1 screenPlane;

	private int texture;

	final Syncd< AffineTransform3D > worldToScreen = Syncd.affine3D();

	private int viewportWidth = 100;

	private int viewportHeight = 100;

	enum Mode { VOLUME, SLICE };

	private Mode mode = Mode.VOLUME;

	public Example4( final RandomAccessibleInterval< UnsignedShortType > rai, final AffineTransform3D sourceTransform )
	{
		this.rai = rai;
		this.sourceTransform = sourceTransform;
	}

	@Override
	public void init( final GLAutoDrawable drawable )
	{
		final GL3 gl = drawable.getGL().getGL3();

		box = new WireframeBox1();
		box.updateVertices( gl, rai );
		screenPlane = new ScreenPlane1();
		screenPlane.updateVertices( gl, new FinalInterval( 640, 480 ) );

		prog = new Shader( gl, "ex1", "ex1" );
		progvol = new Shader( gl, "ex1", "ex4vol" );
		progslice = new Shader( gl, "ex1", "ex4slice" );

		loadTexture( gl );

		gl.glEnable( GL_DEPTH_TEST );
	}

	private void loadTexture( final GL3 gl )
	{
		if ( rai.dimension( 0 ) % 2 != 0 )
			System.err.println( "possible GL_UNPACK_ALIGNMENT problem. glPixelStorei ..." );

		// ..:: TEXTURES ::..
		final int[] tmp = new int[ 1 ];
		gl.glGenTextures( 1, tmp, 0 );
		texture = tmp[ 0 ];
		gl.glBindTexture( GL_TEXTURE_3D, texture );
		int w = ( int ) rai.dimension( 0 );
		int h = ( int ) rai.dimension( 1 );
		int d = ( int ) rai.dimension( 2 );

		final ByteBuffer data = imgToBuffer( rai );

		gl.glTexImage3D( GL_TEXTURE_3D, 0, GL_R16F, w, h, d, 0, GL_RED, GL_UNSIGNED_SHORT, data );
		gl.glTexParameteri( GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_LINEAR );
		gl.glTexParameteri( GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_LINEAR );
//		gl.glTexStorage3D();
//		gl.glTexImage3DMultisample();

		System.out.println( "Image Loaded" );
	}

	public static ByteBuffer imgToBuffer( RandomAccessibleInterval< UnsignedShortType > img )
	{
		assert img.numDimensions() == 3;

		final int bytesPerPixel = 2;
		int size = ( int ) Intervals.numElements( img ) * bytesPerPixel;
		final ByteBuffer buffer = ByteBuffer.allocateDirect( size );
		return imgToBuffer( img, buffer );
	}

	public static ByteBuffer imgToBuffer( RandomAccessibleInterval< UnsignedShortType > img, ByteBuffer buffer )
	{
		assert img.numDimensions() == 3;

		buffer.order( ByteOrder.LITTLE_ENDIAN );
		final ShortBuffer sbuffer = buffer.asShortBuffer();
		Cursor< UnsignedShortType > c = Views.iterable( img ).localizingCursor();
		while( c.hasNext() )
		{
			c.fwd();
			final int i = ( int ) IntervalIndexer.positionToIndex( c, img );
			sbuffer.put( i, c.get().getShort() );
		}
		return buffer;
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

		final Matrix4f model = MatrixMath.affine( sourceTransform, new Matrix4f() );
		final Matrix4f view = MatrixMath.affine( worldToScreen.get(), new Matrix4f() );
		final Matrix4f projection = MatrixMath.screenPerspective( dCam, dClip, screenWidth, screenHeight, screenPadding, new Matrix4f() );

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
		modeprog.setUniform( gl, "sourcemin", rai.min( 0 ), rai.min( 1 ), rai.min( 2 ) );
		modeprog.setUniform( gl, "sourcemax", rai.max( 0 ), rai.max( 1 ), rai.max( 2 ) );
		modeprog.setUniform( gl, "volume", 0 );
		modeprog.setUniform( gl, "invVolumeSize",
				( float ) ( 1.0 / rai.dimension( 0 ) ),
				( float ) ( 1.0 / rai.dimension( 1 ) ),
				( float ) ( 1.0 / rai.dimension( 2 ) ) );
		gl.glActiveTexture( GL_TEXTURE0 );
		gl.glBindTexture( GL_TEXTURE_3D, texture );
		double min = 962;
		double max = 6201;
		double fmin = min / 0xffff;
		double fmax = max / 0xffff;
		double s = 1.0 / ( fmax - fmin );
		double o = -fmin * s;
		modeprog.setUniform( gl, "intensity_offset", ( float ) o );
		modeprog.setUniform( gl, "intensity_scale", ( float ) s );

		gl.glEnable( GL_BLEND );
		gl.glBlendFunc( GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA );
		screenPlane.updateVertices( gl, new FinalInterval( ( int ) screenWidth, ( int ) screenHeight ) );
		screenPlane.draw( gl );
		gl.glDisable( GL_BLEND );
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

	public static void main( final String[] args ) throws SpimDataException
	{
		final String xmlFilename = "/Users/pietzsch/workspace/data/111010_weber_full.xml";
		final SpimDataMinimal spimData = new XmlIoSpimDataMinimal().load( xmlFilename );
		MultiResolutionSetupImgLoader< UnsignedShortType > sil = ( MultiResolutionSetupImgLoader< UnsignedShortType > ) spimData.getSequenceDescription().getImgLoader().getSetupImgLoader( 0 );
		final int level = 0; //source.getNumMipmapLevels() - 1;
		final RandomAccessibleInterval< UnsignedShortType > rai = sil.getImage( 1, level );
		final AffineTransform3D sourceTransform = spimData.getViewRegistrations().getViewRegistration( 1, 0 ).getModel();

		final InputFrame frame = new InputFrame( "Example4", 640, 480 );
		InputFrame.DEBUG = false;
		Example4 glPainter = new Example4( rai, sourceTransform );
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
