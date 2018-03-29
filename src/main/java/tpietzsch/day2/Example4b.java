package tpietzsch.day2;

// https://learnopengl.com/Getting-started/Shaders

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.util.glsl.ShaderCode;
import com.jogamp.opengl.util.glsl.ShaderProgram;
import java.nio.FloatBuffer;
import tpietzsch.day1.SimpleFrame;

import static com.jogamp.opengl.GL.GL_COLOR_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_FLOAT;
import static com.jogamp.opengl.GL.GL_TRIANGLES;

/**
 * Paint a triangle.
 * Animate color as uniform.
 * Using Shader helper class.
 */
public class Example4b implements GLEventListener
{
	private int vao;

	private SimpleFrame frame;

	private Shader prog;

	@Override
	public void init( final GLAutoDrawable drawable )
	{
		final GL gl = drawable.getGL();
		final GL3 gl3 = drawable.getGL().getGL3();

		// ..:: VERTEX BUFFER ::..

		float[] vertices = new float[] {
				-0.5f, -0.5f, 0.0f,
				0.5f, -0.5f, 0.0f,
				0.0f, 0.5f, 0.0f
		};
		final int[] tmp = new int[ 1 ];
		gl.glGenBuffers( 1, tmp, 0 );
		final int vbo = tmp[ 0 ];
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, vbo );
		gl.glBufferData( GL.GL_ARRAY_BUFFER, vertices.length * Float.BYTES, FloatBuffer.wrap( vertices ), GL.GL_STATIC_DRAW );
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, 0 );


		// ..:: SHADERS ::..

		prog = new Shader( gl3, "ex3", "ex4", Example4b.class );

		// ..:: VERTEX ARRAY OBJECT ::..

		gl3.glGenVertexArrays( 1, tmp, 0 );
		vao = tmp[ 0 ];
		gl3.glBindVertexArray( vao );
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, vbo );
		gl3.glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0 );
		gl3.glEnableVertexAttribArray(0);
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

		float greenValue = ( float ) ( Math.sin( timeValue ) / 2.0 + 0.5 );
		prog.setUniform( gl3, "ourColor",0.0f, greenValue, 0.0f, 1.0f );

		prog.use( gl3 );
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
		final Example4b example = new Example4b();
		example.frame = new SimpleFrame( "Example4b", 640, 480, example );
		SimpleFrame.DEBUG = false;
	}
}
