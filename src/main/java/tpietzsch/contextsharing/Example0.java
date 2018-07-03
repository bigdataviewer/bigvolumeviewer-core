package tpietzsch.contextsharing;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.concurrent.locks.ReentrantLock;

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

	private int texture2;

	private SimpleFrame frame;

	private GLOffscreenAutoDrawable offscreenAutoDrawable;

	private ReentrantLock lock = new ReentrantLock();

	@Override
	public void init( final GLAutoDrawable drawable )
	{
		final GL3 gl = drawable.getGL().getGL3();

		offscreenAutoDrawable = drawable.getFactory().createOffscreenAutoDrawable( null, drawable.getRequestedGLCapabilities(), null, 1, 1 );
		offscreenAutoDrawable.setSharedAutoDrawable( drawable );
		offscreenAutoDrawable.addGLEventListener( new GLEventListener()
		{
			@Override
			public void init( final GLAutoDrawable drawable )
			{
				SimpleFrame.dbgln( "  offscreen.init" );
//				final GL3 gl = drawable.getGL().getGL3();
//
//				final int[] tmp = new int[ 1 ];
//				gl.glGenTextures( 1, tmp, 0 );
//				texture2 = tmp[ 0 ];
//				gl.glBindTexture( GL_TEXTURE_2D, texture2 );
//				gl.glTexStorage2D( GL_TEXTURE_2D, 1, GL_R16, 512, 512 );
//				gl.glTexParameteri( GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST );
//				gl.glTexParameteri( GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR );
//				gl.glBindTexture( GL_TEXTURE_2D, 0 );
			}

			@Override
			public void dispose( final GLAutoDrawable drawable )
			{
				SimpleFrame.dbgln( "  offscreen.dispose" );
			}

			int j = 0;

			@Override
			public void display( final GLAutoDrawable drawable )
			{
				lock.lock();
				try
				{
					SimpleFrame.dbgln( "  offscreen.display" );
					final GL3 gl = drawable.getGL().getGL3();
					gl.glBindTexture( GL_TEXTURE_2D, texture );
					final int size = 512 * 512 * 2;
					final ByteBuffer data = ByteBuffer.allocateDirect( size );
					for ( int i = 0; i < size; ++i )
						data.put( i, ( byte ) ( ( i + j ) & 0x00ff ) );
					SimpleFrame.dbgln( "  offscreen.display -- 1" );
					gl.glTexSubImage2D( GL_TEXTURE_2D, 0, 0, 0, 512, 512, GL_RED, GL_UNSIGNED_SHORT, data );
					SimpleFrame.dbgln( "  offscreen.display -- 2" );
					gl.glBindTexture( GL_TEXTURE_2D, 0 );
					SimpleFrame.dbgln( "  offscreen.display -- 3" );
					gl.glFlush();
					j += 1;
					SimpleFrame.dbgln( "  offscreen.display -- end" );
				}
				finally
				{
					lock.unlock();
				}
			}

			@Override
			public void reshape( final GLAutoDrawable drawable, final int x, final int y, final int width, final int height )
			{
				SimpleFrame.dbgln( "  offscreen.reshape" );
			}
		} );
		SimpleFrame.dbgln( "offscreenAutoDrawable = " + offscreenAutoDrawable );


		// ..:: VERTEX BUFFER ::..

		final float vertices[] = {
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

		final int indices[] = {  // note that we start from 0!
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

		prog = new Shader( gl, "ex0", "ex0", Example0.class );

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

		gl.glFinish();

		new Thread( () -> {
			try
			{
				Thread.sleep( 1000 );
				while( true )
				{
					offscreenAutoDrawable.display();
				}
			}
			catch ( final InterruptedException e )
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
		lock.lock();
		try
		{
			SimpleFrame.dbgln( "Example0.display" );
			final GL3 gl = drawable.getGL().getGL3();

			gl.glClearColor( 0.2f, 0.3f, 0.3f, 1.0f );
			gl.glClear( GL_COLOR_BUFFER_BIT );

			SimpleFrame.dbgln( "Example0.display -- 1" );
			prog.use( gl );
			SimpleFrame.dbgln( "Example0.display -- 2" );
			gl.glBindTexture( GL_TEXTURE_2D, texture );
			SimpleFrame.dbgln( "Example0.display -- 2.5" );
			gl.glBindVertexArray( vao );
			gl.glDrawElements( GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0 );
			SimpleFrame.dbgln( "Example0.display -- 3" );
			gl.glBindTexture( GL_TEXTURE_2D, 0 );
			gl.glBindVertexArray( 0 );

			gl.glFlush();

			frame.painterThread.requestRepaint();
		}
		finally
		{
			lock.unlock();
		}
	}

	@Override
	public void reshape( final GLAutoDrawable drawable, final int x, final int y, final int width, final int height )
	{
//		System.out.println( "Example0.reshape" );
//		drawable.getGL().glViewport(0, 0, width, height );
	}

	public static void main( final String[] args )
	{
		SimpleFrame.DEBUG = false;
		final Example0 listener = new Example0();
		listener.frame = new SimpleFrame( "Example0", 640, 480, listener );
	}
}
