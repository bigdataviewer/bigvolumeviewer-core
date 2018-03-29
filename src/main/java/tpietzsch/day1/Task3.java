package tpietzsch.day1;

// https://learnopengl.com/Getting-started/Hello-Triangle

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.util.glsl.ShaderCode;
import com.jogamp.opengl.util.glsl.ShaderProgram;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static com.jogamp.opengl.GL.GL_ELEMENT_ARRAY_BUFFER;
import static com.jogamp.opengl.GL.GL_FLOAT;
import static com.jogamp.opengl.GL.GL_TRIANGLES;
import static com.jogamp.opengl.GL.GL_UNSIGNED_INT;

/**
 * Create two shader programs where the second program uses a different fragment shader that outputs the color yellow;
 * draw both triangles again where one outputs the color yellow.
 */
public class Task3 implements GLEventListener
{
	private int vao1;

	private int vao2;

	private ShaderProgram prog1;

	private ShaderProgram prog2;

	@Override
	public void init( final GLAutoDrawable drawable )
	{
		final GL gl = drawable.getGL();
		final GL3 gl3 = drawable.getGL().getGL3();

		// ..:: VERTEX BUFFER ::..

		float vertices1[] = {
				-1.0f, -0.5f, 0.0f,
				0.0f, -0.5f, 0.0f,
				-0.5f, 0.5f, 0.0f
		};
		float vertices2[] = {
				0.0f, -0.5f, 0.0f,
				1.0f, -0.5f, 0.0f,
				0.5f, 0.5f, 0.0f
		};
		int indices[] = {
			0, 1, 2
		};

		final int[] tmp = new int[ 2 ];
		gl.glGenBuffers( 2, tmp, 0 );
		final int vbo1 = tmp[ 0 ];
		final int vbo2 = tmp[ 1 ];
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, vbo1 );
		gl.glBufferData( GL.GL_ARRAY_BUFFER, vertices1.length * Float.BYTES, FloatBuffer.wrap( vertices1 ), GL.GL_STATIC_DRAW );
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, vbo2 );
		gl.glBufferData( GL.GL_ARRAY_BUFFER, vertices2.length * Float.BYTES, FloatBuffer.wrap( vertices2 ), GL.GL_STATIC_DRAW );
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, 0 );




		// ..:: EleBO ::..

		gl.glGenBuffers( 1, tmp, 0 );
		final int ebo = tmp[ 0 ];
		gl.glBindBuffer( GL_ELEMENT_ARRAY_BUFFER, ebo );
		gl.glBufferData( GL.GL_ELEMENT_ARRAY_BUFFER, indices.length * Integer.BYTES, IntBuffer.wrap( indices ), GL.GL_STATIC_DRAW );
		gl.glBindBuffer( GL_ELEMENT_ARRAY_BUFFER, 0 );



		// ..:: SHADERS ::..

		ShaderCode vs = ShaderCode.create( gl3, GL2.GL_VERTEX_SHADER, Task3.class, "", null, "ex1", true );
		ShaderCode fs = ShaderCode.create( gl3, GL2.GL_FRAGMENT_SHADER, Task3.class, "", null, "ex1", true );
		ShaderCode fs2 = ShaderCode.create( gl3, GL2.GL_FRAGMENT_SHADER, Task3.class, "", null, "task3", true );
		vs.defaultShaderCustomization( gl3, true, false );
		fs.defaultShaderCustomization( gl3, true, false );
		fs2.defaultShaderCustomization( gl3, true, false );

		prog1 = new ShaderProgram();
		prog1.add( vs );
		prog1.add( fs );
		prog1.link( gl3, System.err );

		prog2 = new ShaderProgram();
		prog2.add( vs );
		prog2.add( fs2 );
		prog2.link( gl3, System.err );

		vs.destroy( gl3 );
		fs.destroy( gl3 );
		fs2.destroy( gl3 );



		// ..:: VERTEX ARRAY OBJECT ::..

		gl3.glGenVertexArrays( 2, tmp, 0 );
		vao1 = tmp[ 0 ];
		vao2 = tmp[ 1 ];

		gl3.glBindVertexArray( vao1 );
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, vbo1 );
		gl3.glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0 );
		gl3.glEnableVertexAttribArray(0);
		gl.glBindBuffer( GL.GL_ELEMENT_ARRAY_BUFFER, ebo );

		gl3.glBindVertexArray( vao2 );
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, vbo2 );
		gl3.glVertexAttribPointer(0, 3, GL_FLOAT, false, 0, 0 );
		gl3.glEnableVertexAttribArray(0);
		gl.glBindBuffer( GL.GL_ELEMENT_ARRAY_BUFFER, ebo );

		gl3.glBindVertexArray( 0 );
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

		prog1.useProgram( gl3, true );
		gl3.glBindVertexArray( vao1 );
		gl3.glDrawElements( GL_TRIANGLES, 3, GL_UNSIGNED_INT, 0 );
		prog1.useProgram( gl3, false );
		prog2.useProgram( gl3, true );
		gl3.glBindVertexArray( vao2 );
		gl3.glDrawElements( GL_TRIANGLES, 3, GL_UNSIGNED_INT, 0 );
		prog2.useProgram( gl3, false );
	}

	@Override
	public void reshape( final GLAutoDrawable drawable, final int x, final int y, final int width, final int height )
	{
		drawable.getGL().glViewport(0, 0, width, height );
	}

	public static void main( String[] args )
	{
		new SimpleFrame( "Example2", 640, 480, new Task3() );
	}
}
