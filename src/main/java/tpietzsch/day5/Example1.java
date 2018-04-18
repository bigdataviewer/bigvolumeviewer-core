package tpietzsch.day5;

import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.concurrent.atomic.AtomicReference;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.MultiResolutionSetupImgLoader;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.Views;
import tpietzsch.day2.Shader;
import tpietzsch.day4.InputFrame;

import static com.jogamp.opengl.GL.GL_COLOR_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_DEPTH_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_ELEMENT_ARRAY_BUFFER;
import static com.jogamp.opengl.GL.GL_FLOAT;
import static com.jogamp.opengl.GL.GL_LINEAR;
import static com.jogamp.opengl.GL.GL_MAX_TEXTURE_SIZE;
import static com.jogamp.opengl.GL.GL_NEAREST;
import static com.jogamp.opengl.GL.GL_R16F;
import static com.jogamp.opengl.GL.GL_TEXTURE0;
import static com.jogamp.opengl.GL.GL_TEXTURE_2D;
import static com.jogamp.opengl.GL.GL_TEXTURE_MAG_FILTER;
import static com.jogamp.opengl.GL.GL_TEXTURE_MIN_FILTER;
import static com.jogamp.opengl.GL.GL_TRIANGLES;
import static com.jogamp.opengl.GL.GL_UNPACK_ALIGNMENT;
import static com.jogamp.opengl.GL.GL_UNSIGNED_INT;
import static com.jogamp.opengl.GL.GL_UNSIGNED_SHORT;
import static com.jogamp.opengl.GL2ES2.GL_MAX_3D_TEXTURE_SIZE;
import static com.jogamp.opengl.GL2ES2.GL_RED;
import static com.jogamp.opengl.GL2ES2.GL_TEXTURE_3D;
import static com.jogamp.opengl.GL2ES3.GL_R16UI;

/**
 * RAI as 3D texture
 */
public class Example1 implements GLEventListener
{
	public final AtomicReference< RandomAccessibleInterval< UnsignedShortType > > imgRef = new AtomicReference<>();

	private volatile boolean textureValid = false;

	private int texture;

	private int vao;

	private Shader prog;

	private void tryLoadImage( final GL3 gl )
	{
		final RandomAccessibleInterval< UnsignedShortType > img = imgRef.getAndSet( null );
		if ( img == null)
		{
			return;
		}

		if ( img.dimension( 0 ) % 2 != 0 )
			System.err.println( "possible GL_UNPACK_ALIGNMENT problem. glPixelStorei ..." );

		// ..:: TEXTURES ::..

		final int[] tmp = new int[ 1 ];
		gl.glGenTextures( 1, tmp, 0 );
		texture = tmp[ 0 ];
		gl.glBindTexture( GL_TEXTURE_3D, texture );
		int w = ( int ) img.dimension( 0 );
		int h = ( int ) img.dimension( 1 );
		int d = ( int ) img.dimension( 2 );
		final ByteBuffer data = imgToBuffer( img );
//		for ( int i = 0; i < w * h * d * 2; ++i )
//			data.put( i, ( byte ) ( i & 0x00ff ) );

		gl.glTexImage3D( GL_TEXTURE_3D, 0, GL_R16F, w, h, d, 0, GL_RED, GL_UNSIGNED_SHORT, data );
		gl.glTexParameteri( GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_NEAREST );
		gl.glTexParameteri( GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_LINEAR );
//		gl.glTexStorage3D();
//		gl.glTexImage3DMultisample();

		System.out.println( "Image Loaded" );
		textureValid = true;
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
	public void init( final GLAutoDrawable drawable )
	{
		final GL3 gl = drawable.getGL().getGL3();

		int[] ts = new int[ 1 ];
		gl.glGetIntegerv( GL_MAX_3D_TEXTURE_SIZE, ts, 0 );
		System.out.println( "GL_MAX_3D_TEXTURE_SIZE = " + ts[ 0 ] );
		gl.glGetIntegerv( GL_MAX_TEXTURE_SIZE, ts, 0 );
		System.out.println( "GL_MAX_TEXTURE_SIZE = " + ts[ 0 ] );
		gl.glGetIntegerv( GL_UNPACK_ALIGNMENT, ts, 0 );
		System.out.println( "GL_UNPACK_ALIGNMENT = " + ts[ 0 ] );

		// ..:: TEXTURES ::..

		tryLoadImage( gl );

		// ..:: VERTEX BUFFER ::..

		float vertices[] = {
				// positions         // texture coords
				0.5f,  0.5f, 0.0f,   1.0f, 1.0f, 0.5f,  // top right
				0.5f, -0.5f, 0.0f,   1.0f, 0.0f, 0.5f,  // bottom right
				-0.5f, -0.5f, 0.0f,   0.0f, 0.0f, 0.5f,  // bottom left
				-0.5f,  0.5f, 0.0f,   0.0f, 1.0f, 0.5f   // top left
		};
		final int[] tmp = new int[ 1 ];
		gl.glGenBuffers( 1, tmp, 0 );
		final int vbo = tmp[ 0 ];
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, vbo );
		gl.glBufferData( GL.GL_ARRAY_BUFFER, vertices.length * Float.BYTES, FloatBuffer.wrap( vertices ), GL.GL_STATIC_DRAW );
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, 0 );

		// ..:: ELEMENT BUFFER ::..

		int indices[] = {  // note that we start from 0!
				0, 1, 3,   // first triangle
				1, 2, 3    // second triangle
		};
		gl.glGenBuffers( 1, tmp, 0 );
		final int ebo = tmp[ 0 ];
		gl.glBindBuffer( GL_ELEMENT_ARRAY_BUFFER, ebo );
		gl.glBufferData( GL.GL_ELEMENT_ARRAY_BUFFER, indices.length * Integer.BYTES, IntBuffer.wrap( indices ), GL.GL_STATIC_DRAW );
		gl.glBindBuffer( GL_ELEMENT_ARRAY_BUFFER, 0 );

		// ..:: SHADERS ::..

		prog = new Shader( gl, "ex1", "ex1" );

		// ..:: VERTEX ARRAY OBJECT ::..

		gl.glGenVertexArrays( 1, tmp, 0 );
		vao = tmp[ 0 ];
		gl.glBindVertexArray( vao );
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, vbo );
		gl.glVertexAttribPointer(0, 3, GL_FLOAT, false, 6 * Float.BYTES, 0 );
		gl.glEnableVertexAttribArray(0);
		gl.glVertexAttribPointer(1, 3, GL_FLOAT, false, 6 * Float.BYTES, 3 * Float.BYTES );
		gl.glEnableVertexAttribArray(1);
		gl.glBindBuffer( GL_ELEMENT_ARRAY_BUFFER, ebo );
		gl.glBindVertexArray( 0 );
	}

	@Override
	public void dispose( final GLAutoDrawable drawable )
	{}

	@Override
	public void display( final GLAutoDrawable drawable )
	{
		final GL3 gl = drawable.getGL().getGL3();

		tryLoadImage( gl );

		gl.glClearColor( 0.2f, 0.3f, 0.3f, 1.0f );
		gl.glClear( GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT );

		if ( textureValid )
		{
			prog.use( gl );
			prog.setUniform( gl, "ourTexture", 0 );
			double min = 962;
			double max = 4101;
			double fmin = min / 0xffff;
			double fmax = max / 0xffff;
			double s = 1.0 / ( fmax - fmin );
			double o = -fmin * s;
			prog.setUniform( gl, "offset", ( float ) o );
			prog.setUniform( gl, "scale", ( float ) s );
			gl.glActiveTexture( GL_TEXTURE0 );
			gl.glBindTexture( GL_TEXTURE_3D, texture );
			gl.glBindVertexArray( vao );
			gl.glDrawElements( GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0 );
			gl.glBindVertexArray( 0 );

		}
	}

	@Override
	public void reshape( final GLAutoDrawable drawable, final int x, final int y, final int width, final int height )
	{}

	public static void main( final String[] args ) throws InterruptedException, SpimDataException
	{
		final InputFrame frame = new InputFrame( "Example1", 640, 480 );
		Example1 glPainter = new Example1();
		frame.setGlEventListener( glPainter );
		frame.getDefaultActions().runnableAction( () -> {
			System.out.println( "refresh..." );
			frame.requestRepaint();
		}, "refresh", "R" );
		frame.show();

		final String xmlFilename = "/Users/pietzsch/workspace/data/111010_weber_full.xml";
		final SpimDataMinimal spimData = new XmlIoSpimDataMinimal().load( xmlFilename );
		MultiResolutionSetupImgLoader< UnsignedShortType > sil = ( MultiResolutionSetupImgLoader< UnsignedShortType > ) spimData.getSequenceDescription().getImgLoader().getSetupImgLoader( 0 );
		final int level = 0; //source.getNumMipmapLevels() - 1;
		final RandomAccessibleInterval< UnsignedShortType > rai = sil.getImage( 1, level );
		System.out.println( "rai = " + Util.printInterval(rai ) );
		glPainter.imgRef.set( rai );
	}


}
