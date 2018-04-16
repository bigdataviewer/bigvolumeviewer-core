package tpietzsch.day4;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.joml.Matrix4f;
import tpietzsch.day1.SimpleFrame;
import tpietzsch.day2.Shader;
import tpietzsch.util.Images;

import static com.jogamp.opengl.GL.GL_COLOR_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_DEPTH_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_DEPTH_TEST;
import static com.jogamp.opengl.GL.GL_ELEMENT_ARRAY_BUFFER;
import static com.jogamp.opengl.GL.GL_FLOAT;
import static com.jogamp.opengl.GL.GL_LINES;
import static com.jogamp.opengl.GL.GL_RGB;
import static com.jogamp.opengl.GL.GL_RGBA;
import static com.jogamp.opengl.GL.GL_TEXTURE0;
import static com.jogamp.opengl.GL.GL_TEXTURE1;
import static com.jogamp.opengl.GL.GL_TEXTURE_2D;
import static com.jogamp.opengl.GL.GL_TRIANGLES;
import static com.jogamp.opengl.GL.GL_UNSIGNED_BYTE;
import static com.jogamp.opengl.GL.GL_UNSIGNED_INT;

/**
 * Perspective projection looking at BDV window (z=0 plane).
 * (hard coded width, height)
 */
public class Example1 implements GLEventListener
{
	private int vao;

	private Shader prog;

	private int texture1;

	private int texture2;

	@Override
	public void init( final GLAutoDrawable drawable )
	{
		final GL gl = drawable.getGL();
		final GL3 gl3 = drawable.getGL().getGL3();

		// ..:: VERTEX BUFFER ::..

		final float vertices[] = {
				// 3 pos
				  0.0f,   0.0f, 0.0f,
				640.0f,   0.0f, 0.0f,
				640.0f, 480.0f, 0.0f,
				  0.0f, 480.0f, 0.0f,
		};
		final int[] tmp = new int[ 2 ];
		gl.glGenBuffers( 1, tmp, 0 );
		final int vbo = tmp[ 0 ];
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, vbo );
		gl.glBufferData( GL.GL_ARRAY_BUFFER, vertices.length * Float.BYTES, FloatBuffer.wrap( vertices ), GL.GL_STATIC_DRAW );
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, 0 );

		// ..:: ELEMENT BUFFER ::..

		final int indices[] = {  // note that we start from 0!
				0, 1,    // first line
				1, 2,    // second line
				2, 3,
				3, 0
		};
		gl.glGenBuffers( 1, tmp, 0 );
		final int ebo = tmp[ 0 ];
		gl.glBindBuffer( GL_ELEMENT_ARRAY_BUFFER, ebo );
		gl.glBufferData( GL.GL_ELEMENT_ARRAY_BUFFER, indices.length * Integer.BYTES, IntBuffer.wrap( indices ), GL.GL_STATIC_DRAW );
		gl.glBindBuffer( GL_ELEMENT_ARRAY_BUFFER, 0 );

		// ..:: SHADERS ::..

		prog = new Shader( gl3, "ex1", "ex1" );

		// ..:: VERTEX ARRAY OBJECT ::..

		gl3.glGenVertexArrays( 1, tmp, 0 );
		vao = tmp[ 0 ];
		gl3.glBindVertexArray( vao );
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, vbo );
		gl3.glVertexAttribPointer( 0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0 );
		gl3.glEnableVertexAttribArray( 0 );
		gl.glBindBuffer( GL_ELEMENT_ARRAY_BUFFER, ebo );
		gl3.glBindVertexArray( 0 );

		// ..:: MISC ::..

		gl3.glEnable( GL_DEPTH_TEST );
	}

	@Override
	public void dispose( final GLAutoDrawable drawable )
	{}

	void projection( Matrix4f projection )
	{
		// distance camera to z=0 plane
		double dCam = 300;

		// visible depth away from z=0 in both directions
		double d = 100;

		double screenWidth = 640;
		double screenHeight = 480;

		double screenPadding = 100;


		double l0 = -screenPadding;
		double t0 = -screenPadding;
		double r0 = screenWidth + screenPadding;
		double b0 = screenHeight + screenPadding;

		double p = ( dCam - d ) / dCam;
		double l = l0 * p;
		double t = t0 * p;
		double r = r0 * p;
		double b = b0 * p;

		double n = dCam - d;
		double f = dCam + d;

		float m00 = ( float ) ( 2 * n / ( r - l ) );
		float m11 = ( float ) ( 2 * n / ( t - b ) );
		float m02 = ( float ) ( ( r + l ) / ( r - l ) );
		float m12 = ( float ) ( ( t + b ) / ( t - b ) );
		float m22 = ( float ) ( -( f + n ) / ( f - n ) );
		float m23 = ( float ) ( -2 * f * n / ( f - n ) );

		projection.set(
				m00, 0, 0, 0,
				0, m11, 0, 0,
				m02, m12, m22, -1,
				0,0,m23,0
		);
	}

	@Override
	public void display( final GLAutoDrawable drawable )
	{
		final GL gl = drawable.getGL();
		final GL3 gl3 = drawable.getGL().getGL3();

		gl.glClearColor( 0.2f, 0.3f, 0.3f, 1.0f );
		gl.glClear( GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT );

		final Matrix4f model = new Matrix4f();

		final Matrix4f view = new Matrix4f();
		view.translate( 0.0f, 0.0f, -300.0f );

		final Matrix4f projection = new Matrix4f();
		projection( projection );

		final Matrix4f pvm = new Matrix4f( projection ).mul( view ).mul( model );

		prog.use( gl3 );
		prog.setUniform( gl3, "pvm", pvm );
		gl3.glBindVertexArray( vao );
		gl3.glDrawElements( GL_LINES, 8, GL_UNSIGNED_INT, 0 );
	}

	int width = 1;
	int height = 1;

	@Override
	public void reshape( final GLAutoDrawable drawable, final int x, final int y, final int width, final int height )
	{
		this.width = width;
		this.height = height;
	}

	public static void main( final String[] args )
	{
		new SimpleFrame( "Example1", 640, 480, new Example1() );
	}
}
