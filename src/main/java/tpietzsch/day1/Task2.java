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
 * Now create the same 2 triangles using two different VAOs and VBOs for their data.
 */
public class Task2 implements GLEventListener
{
	private int vao1;

	private int vao2;

	private ShaderProgram prog;

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

		ShaderCode vs = ShaderCode.create( gl3, GL2.GL_VERTEX_SHADER, Task2.class, "", null, "ex1", true );
		ShaderCode fs = ShaderCode.create( gl3, GL2.GL_FRAGMENT_SHADER, Task2.class, "", null, "ex1", true );
		vs.defaultShaderCustomization( gl3, true, false );
		fs.defaultShaderCustomization( gl3, true, false );

		prog = new ShaderProgram();
		prog.add( vs );
		prog.add( fs );
		prog.link( gl3, System.err );
		vs.destroy( gl3 );
		fs.destroy( gl3 );



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

		prog.useProgram( gl3, true );
		gl3.glBindVertexArray( vao1 );
		gl3.glDrawElements( GL_TRIANGLES, 3, GL_UNSIGNED_INT, 0 );
		gl3.glBindVertexArray( vao2 );
		gl3.glDrawElements( GL_TRIANGLES, 3, GL_UNSIGNED_INT, 0 );
	}

	@Override
	public void reshape( final GLAutoDrawable drawable, final int x, final int y, final int width, final int height )
	{
		drawable.getGL().glViewport(0, 0, width, height );
	}

	public static void main( String[] args )
	{
		new SimpleFrame( "Example2", 640, 480, new Task2() );
	}
}
