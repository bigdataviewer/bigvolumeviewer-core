package tpietzsch.day7;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.imglib2.RandomAccess;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.FloatType;
import tpietzsch.day1.SimpleFrame;
import tpietzsch.day2.Shader;

import static com.jogamp.opengl.GL.GL_COLOR_ATTACHMENT0;
import static com.jogamp.opengl.GL.GL_COLOR_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_DEPTH24_STENCIL8;
import static com.jogamp.opengl.GL.GL_ELEMENT_ARRAY_BUFFER;
import static com.jogamp.opengl.GL.GL_FLOAT;
import static com.jogamp.opengl.GL.GL_FRAMEBUFFER;
import static com.jogamp.opengl.GL.GL_FRAMEBUFFER_COMPLETE;
import static com.jogamp.opengl.GL.GL_NEAREST;
import static com.jogamp.opengl.GL.GL_RENDERBUFFER;
import static com.jogamp.opengl.GL.GL_RGB;
import static com.jogamp.opengl.GL.GL_RGB32F;
import static com.jogamp.opengl.GL.GL_TEXTURE_2D;
import static com.jogamp.opengl.GL.GL_TEXTURE_MAG_FILTER;
import static com.jogamp.opengl.GL.GL_TEXTURE_MIN_FILTER;
import static com.jogamp.opengl.GL.GL_TRIANGLES;
import static com.jogamp.opengl.GL.GL_UNSIGNED_BYTE;
import static com.jogamp.opengl.GL.GL_UNSIGNED_INT;
import static com.jogamp.opengl.GL2ES3.GL_DEPTH_STENCIL_ATTACHMENT;

/**
 * Paint a triangle to a texture, and then paint texture to quad.
 * Read the texture into RGB bytes and print some values.
 */
public class Example3 implements GLEventListener
{
	private int vao;

	private int vaoQuad;

	private Shader prog;

	private Shader progQuad;

	private int framebuffer;

	private int texColorBuffer;

	private final int fbWidth = 40;

	private final int fbHeight = 30;

	private int viewportWidth;

	private int viewportHeight;

	@Override
	public void init( final GLAutoDrawable drawable )
	{
		final GL3 gl = drawable.getGL().getGL3();

		// ..:: VERTEX BUFFER ::..

		float[] vertices = new float[] {
				-0.5f, -0.5f, 0.0f,
				0.5f, -0.5f, 0.0f,
				0.0f, 0.5f, 0.0f
		};

		final float verticesQuad[] = {
				//    pos      texture
				 1,  1, 0,     1, 1,   // top right
				 1, -1, 0,     1, 0,   // bottom right
				-1, -1, 0,     0, 0,   // bottom left
				-1,  1, 0,     0, 1    // top left
		};

		final int[] tmp = new int[ 2 ];
		gl.glGenBuffers( 2, tmp, 0 );
		final int vbo = tmp[ 0 ];
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, vbo );
		gl.glBufferData( GL.GL_ARRAY_BUFFER, vertices.length * Float.BYTES, FloatBuffer.wrap( vertices ), GL.GL_STATIC_DRAW );
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, 0 );

		final int vboQuad = tmp[ 1 ];
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, vboQuad );
		gl.glBufferData( GL.GL_ARRAY_BUFFER, verticesQuad.length * Float.BYTES, FloatBuffer.wrap( verticesQuad ), GL.GL_STATIC_DRAW );
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, 0 );

		// ..:: ELEMENT BUFFER ::..

		final int indices[] = {  // note that we start from 0!
				0, 1, 3,   // first triangle
				1, 2, 3    // second triangle
		};
		gl.glGenBuffers( 1, tmp, 0 );
		final int eboQuad = tmp[ 0 ];
		gl.glBindBuffer( GL_ELEMENT_ARRAY_BUFFER, eboQuad );
		gl.glBufferData( GL_ELEMENT_ARRAY_BUFFER, indices.length * Integer.BYTES, IntBuffer.wrap( indices ), GL.GL_STATIC_DRAW );
		gl.glBindBuffer( GL_ELEMENT_ARRAY_BUFFER, 0 );

		// ..:: SHADERS ::..

		prog = new Shader( gl, "ex1", "ex3" );
		progQuad = new Shader( gl, "ex1quad", "ex1quad" );

		// ..:: FRAMEBUFFER ::..

		gl.glGenFramebuffers( 1, tmp, 0 );
		framebuffer = tmp[ 0 ];
		gl.glBindFramebuffer( GL_FRAMEBUFFER, framebuffer );

		// generate texture
		gl.glGenTextures( 1, tmp, 0 );
		texColorBuffer = tmp[ 0 ];
		gl.glBindTexture( GL_TEXTURE_2D, texColorBuffer );
		gl.glTexImage2D( GL_TEXTURE_2D, 0, GL_RGB32F, fbWidth, fbHeight, 0, GL_RGB, GL_UNSIGNED_BYTE, null );
		gl.glTexParameteri( GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST );
		gl.glTexParameteri( GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST );
		gl.glBindTexture( GL_TEXTURE_2D, 0 );

		// attach it to currently bound framebuffer object
		gl.glFramebufferTexture2D( GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texColorBuffer, 0 );

		// create depth & stencil renderbuffer
		int rbo;
		gl.glGenRenderbuffers( 1, tmp, 0 );
		rbo = tmp[ 0 ];
		gl.glBindRenderbuffer( GL_RENDERBUFFER, rbo );
		gl.glRenderbufferStorage( GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, fbWidth, fbHeight );
		gl.glBindRenderbuffer( GL_RENDERBUFFER, 0 );

		// attach depth & stencil renderbuffer
		gl.glFramebufferRenderbuffer( GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, rbo );
		if ( gl.glCheckFramebufferStatus( GL_FRAMEBUFFER ) != GL_FRAMEBUFFER_COMPLETE )
			System.err.println( "ERROR::FRAMEBUFFER:: Framebuffer is not complete!" );
		gl.glBindFramebuffer( GL_FRAMEBUFFER, 0 );

		// ..:: VERTEX ARRAY OBJECT ::..

		gl.glGenVertexArrays( 2, tmp, 0 );
		vao = tmp[ 0 ];

		gl.glBindVertexArray( vao );
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, vbo );
		/*
		vertex attribute index 0, as in "layout (location = 0)"
		*/
		gl.glVertexAttribPointer( 0, 3, GL_FLOAT, false, 0, 0 );
		gl.glEnableVertexAttribArray( 0 );
		gl.glBindVertexArray( 0 );

		vaoQuad = tmp[ 1 ];
		gl.glBindVertexArray( vaoQuad );
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, vboQuad );
		gl.glVertexAttribPointer( 0, 3, GL_FLOAT, false, 5 * Float.BYTES, 0 );
		gl.glEnableVertexAttribArray( 0 );
		gl.glVertexAttribPointer( 1, 2, GL_FLOAT, false, 5 * Float.BYTES, 3 * Float.BYTES );
		gl.glEnableVertexAttribArray( 1 );
		gl.glBindBuffer( GL_ELEMENT_ARRAY_BUFFER, eboQuad );
		gl.glBindVertexArray( 0 );

		// ..:: PAINT TO TEXTURE ..::

		gl.glBindFramebuffer( GL_FRAMEBUFFER, framebuffer );
		gl.glViewport( 0, 0, fbWidth, fbHeight );
		gl.glClearColor( 0, 0, 0, 1 );
		gl.glClear( GL_COLOR_BUFFER_BIT );
		prog.use( gl );
		gl.glBindVertexArray( vao );
		gl.glDrawArrays( GL_TRIANGLES, 0, 3 );
		gl.glBindFramebuffer( GL_FRAMEBUFFER, 0 );

		// ..:: READ TEXTURE ..::

		gl.glBindTexture( GL_TEXTURE_2D, texColorBuffer );
		float[] rgbFloats = new float[ fbWidth * fbHeight * 3 ];
		gl.glGetTexImage( GL_TEXTURE_2D, 0, GL_RGB, GL_FLOAT, FloatBuffer.wrap( rgbFloats ) );
		gl.glBindTexture( GL_TEXTURE_2D, 0 );

		final Img< FloatType > img = ArrayImgs.floats( rgbFloats, 3, fbWidth, fbHeight );
		final RandomAccess< FloatType > a = img.randomAccess();
		for ( int i = 0; i < 3; ++i )
		{
			a.setPosition( new long[] { i, 20, 15 } );
			System.out.println( i + ": " + a.get().get() );
		}
	}

	@Override
	public void dispose( final GLAutoDrawable drawable )
	{
	}

	@Override
	public void display( final GLAutoDrawable drawable )
	{
		final GL3 gl = drawable.getGL().getGL3();

		gl.glViewport( 0, 0, viewportWidth, viewportHeight );
		progQuad.use( gl );
		gl.glBindTexture( GL_TEXTURE_2D, texColorBuffer );
		gl.glBindVertexArray( vaoQuad );
		gl.glDrawElements( GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0 );
	}

	@Override
	public void reshape( final GLAutoDrawable drawable, final int x, final int y, final int width, final int height )
	{
		viewportWidth = width;
		viewportHeight = height;
	}

	public static void main( String[] args )
	{
		SimpleFrame.DEBUG = false;
		new SimpleFrame( "Example3", 640, 480, new Example3() );
	}
}
