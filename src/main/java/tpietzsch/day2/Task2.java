package tpietzsch.day2;

// https://learnopengl.com/Getting-started/Hello-Triangle

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import java.nio.FloatBuffer;
import tpietzsch.day1.SimpleFrame;

import static com.jogamp.opengl.GL.GL_COLOR_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_FLOAT;
import static com.jogamp.opengl.GL.GL_TRIANGLES;

/**
 * Specify a horizontal offset via a uniform and
 * move the triangle to the right side of the screen in the vertex shade using this offset value.
 */
public class Task2 implements GLEventListener
{
	private int vao;

	private Shader prog;

	private SimpleFrame frame;

	@Override
	public void init( final GLAutoDrawable drawable )
	{
		final GL gl = drawable.getGL();
		final GL3 gl3 = drawable.getGL().getGL3();

		// ..:: VERTEX BUFFER ::..

		float[] vertices = new float[] {
				 // positions         // colors
				 0.5f, -0.5f, 0.0f,   1.0f, 0.0f, 0.0f,   // bottom right
				-0.5f, -0.5f, 0.0f,   0.0f, 1.0f, 0.0f,   // bottom left
				 0.0f,  0.5f, 0.0f,   0.0f, 0.0f, 1.0f    // top
		};
		final int[] tmp = new int[ 1 ];
		gl.glGenBuffers( 1, tmp, 0 );
		final int vbo = tmp[ 0 ];
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, vbo );
		gl.glBufferData( GL.GL_ARRAY_BUFFER, vertices.length * Float.BYTES, FloatBuffer.wrap( vertices ), GL.GL_STATIC_DRAW );
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, 0 );


		// ..:: SHADERS ::..

		prog = new Shader( gl3, "task2", "ex5" );

		// ..:: VERTEX ARRAY OBJECT ::..

		gl3.glGenVertexArrays( 1, tmp, 0 );
		vao = tmp[ 0 ];
		gl3.glBindVertexArray( vao );
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, vbo );
		gl3.glVertexAttribPointer(0, 3, GL_FLOAT, false, 6 * Float.BYTES, 0 );
		gl3.glEnableVertexAttribArray(0);
		gl3.glVertexAttribPointer(1, 3, GL_FLOAT, false, 6 * Float.BYTES, 3 * Float.BYTES );
		gl3.glEnableVertexAttribArray(1);
		gl3.glBindVertexArray( 0 );
	}

	@Override
	public void dispose( final GLAutoDrawable drawable )
	{

	}

	int f = 0;

	@Override
	public void display( final GLAutoDrawable drawable )
	{
		double timeValue = frame == null ? 0 : frame.dTimeMillis() / 1000.0;
		frame.painterThread.requestRepaint();

		if ( ++f % 100 ==  0 )
			System.out.println( ( f / timeValue ) + " fps" );

		final GL gl = drawable.getGL();
		final GL3 gl3 = drawable.getGL().getGL3();

		gl.glClearColor( 0.2f, 0.3f, 0.3f, 1.0f );
		gl.glClear( GL_COLOR_BUFFER_BIT );

		prog.use( gl3 );
		prog.setUniform( gl3, "shift", ( float ) ( Math.sin( 4 * timeValue ) * 0.2 ) );
		gl3.glBindVertexArray( vao );
		gl3.glDrawArrays( GL_TRIANGLES, 0, 3 );
	}

	@Override
	public void reshape( final GLAutoDrawable drawable, final int x, final int y, final int width, final int height )
	{
		drawable.getGL().glViewport(0, 0, width, height );
	}

	public static void main( String[] args )
	{
		final Task2 task2 = new Task2();
		task2.frame = new SimpleFrame( "Task2", 640, 480, task2 );
		SimpleFrame.DEBUG = false;
	}
}
