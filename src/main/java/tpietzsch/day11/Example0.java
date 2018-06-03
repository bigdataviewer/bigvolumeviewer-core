package tpietzsch.day11;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import tpietzsch.day1.SimpleFrame;
import tpietzsch.day2.Shader;

import static com.jogamp.opengl.GL.GL_COLOR_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_ELEMENT_ARRAY_BUFFER;
import static com.jogamp.opengl.GL.GL_FLOAT;
import static com.jogamp.opengl.GL.GL_LINEAR;
import static com.jogamp.opengl.GL.GL_NEAREST;
import static com.jogamp.opengl.GL.GL_TEXTURE_2D;
import static com.jogamp.opengl.GL.GL_TEXTURE_MAG_FILTER;
import static com.jogamp.opengl.GL.GL_TEXTURE_MIN_FILTER;
import static com.jogamp.opengl.GL.GL_TRIANGLES;
import static com.jogamp.opengl.GL.GL_UNSIGNED_INT;
import static com.jogamp.opengl.GL.GL_UNSIGNED_SHORT;
import static com.jogamp.opengl.GL2ES2.GL_RED;
import static com.jogamp.opengl.GL2GL3.GL_R16;

/**
 * Texture upload in separate thread with GLAutoDrawable
 */
public class Example0 implements GLEventListener
{
	private int vao;

	private Shader prog;

	private int texture;

	private SimpleFrame frame;

	@Override
	public void init( final GLAutoDrawable drawable )
	{
		final GL3 gl = drawable.getGL().getGL3();

		final GLOffscreenAutoDrawable offscreenAutoDrawable = drawable.getFactory().createOffscreenAutoDrawable( null, drawable.getRequestedGLCapabilities(), null, 1, 1 );
		offscreenAutoDrawable.setSharedAutoDrawable( drawable );
		offscreenAutoDrawable.addGLEventListener( new GLEventListener()
		{
			@Override
			public void init( final GLAutoDrawable drawable )
			{
				System.out.println( "Example0.init" );
			}

			@Override
			public void dispose( final GLAutoDrawable drawable )
			{
				System.out.println( "Example0.dispose" );
			}

			int j = 0;

			@Override
			public void display( final GLAutoDrawable drawable )
			{
				gl.glBindTexture( GL_TEXTURE_2D, texture );
				int size = 512 * 512 * 2;
				final ByteBuffer data = ByteBuffer.allocateDirect( size );
				for ( int i = 0; i < size; ++i )
					data.put( i, ( byte ) ( ( i + j ) & 0x00ff ) );
				gl.glTexSubImage2D( GL_TEXTURE_2D, 0,0,0, 512, 512, GL_RED, GL_UNSIGNED_SHORT, data );
				gl.glBindTexture( GL_TEXTURE_2D, 0 );
				gl.glFlush();
				j += 1;
			}

			@Override
			public void reshape( final GLAutoDrawable drawable, final int x, final int y, final int width, final int height )
			{
				System.out.println( "Example0.reshape" );
			}
		} );
		System.out.println( "offscreenAutoDrawable = " + offscreenAutoDrawable );


		// ..:: VERTEX BUFFER ::..

		float vertices[] = {
				 // positions         // texture coords
				 0.5f,  0.5f, 0.0f,   1.0f, 1.0f, 0.0f,   // top right
				 0.5f, -0.5f, 0.0f,   1.0f, 0.0f, 0.0f,   // bottom right
				-0.5f, -0.5f, 0.0f,   0.0f, 0.0f, 0.0f,   // bottom left
				-0.5f,  0.5f, 0.0f,   0.0f, 1.0f, 0.0f    // top left
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

		// ..:: TEXTURES ::..

		gl.glGenTextures( 1, tmp, 0 );
		texture = tmp[ 0 ];
		gl.glBindTexture( GL_TEXTURE_2D, texture );
//		int size = 512 * 512 * 2;
//		final ByteBuffer data = ByteBuffer.allocateDirect( size );
//		for ( int i = 0; i < size; ++i )
//			data.put( i, ( byte ) ( i & 0x00ff ) );
//		gl.glTexSubImage2D( GL_TEXTURE_2D, 0,0,0, 512, 512, GL_RED, GL_UNSIGNED_SHORT, data );
		gl.glTexStorage2D( GL_TEXTURE_2D, 1, GL_R16, 512, 512 );
		gl.glTexParameteri( GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST );
		gl.glTexParameteri( GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR );
		gl.glBindTexture( GL_TEXTURE_2D, 0 );

		// ..:: SHADERS ::..

		prog = new Shader( gl, "ex0", "ex0", tpietzsch.day5.Example0.class );

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

		new Thread( () -> {
			try
			{
				Thread.sleep( 1000 );
				while( true )
				{
					offscreenAutoDrawable.display();
				}
			}
			catch ( InterruptedException e )
			{
				e.printStackTrace();
			}

		}).start();
	}

	@Override
	public void dispose( final GLAutoDrawable drawable )
	{

	}

	@Override
	public void display( final GLAutoDrawable drawable )
	{
		final GL gl = drawable.getGL();
		final GL3 gl3 = drawable.getGL().getGL3();

		gl.glClearColor( 0.2f, 0.3f, 0.3f, 1.0f );
		gl.glClear( GL_COLOR_BUFFER_BIT );

		prog.use( gl3 );
		gl3.glBindTexture( GL_TEXTURE_2D, texture );
		gl3.glBindVertexArray( vao );
		gl3.glDrawElements( GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0 );
		gl.glBindTexture( GL_TEXTURE_2D, 0 );
		gl3.glBindVertexArray( 0 );

		frame.painterThread.requestRepaint();
	}

	@Override
	public void reshape( final GLAutoDrawable drawable, final int x, final int y, final int width, final int height )
	{
		drawable.getGL().glViewport(0, 0, width, height );
	}

	public static void main( String[] args )
	{
		SimpleFrame.DEBUG = false;
		final Example0 listener = new Example0();
		listener.frame = new SimpleFrame( "Example0", 640, 480, listener );
	}
}
