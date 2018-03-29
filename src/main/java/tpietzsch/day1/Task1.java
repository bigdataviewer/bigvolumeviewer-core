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
 * Try to draw 2 triangles next to each other using glDrawArrays by adding more vertices to your data.
 */
public class Task1 implements GLEventListener
{
	private int vao;

	private ShaderProgram prog;

	@Override
	public void init( final GLAutoDrawable drawable )
	{
		final GL gl = drawable.getGL();
		final GL3 gl3 = drawable.getGL().getGL3();

		// ..:: VERTEX BUFFER ::..

		float vertices[] = {
				-1.0f, -0.5f, 0.0f,
				0.0f, -0.5f, 0.0f,
				-0.5f, 0.5f, 0.0f,

				0.0f, -0.5f, 0.0f,
				1.0f, -0.5f, 0.0f,
				0.5f, 0.5f, 0.0f
		};
		int indices[] = {  // note that we start from 0!
			0, 1, 2,   // first triangle
			3, 4, 5    // second triangle
		};

		/*
		unsigned int VBO;
		glGenBuffers( 1, & VBO);

		OpenGL has many types of buffer objects and the buffer type of a vertex buffer object is GL_ARRAY_BUFFER.
		*/

		final int[] tmp = new int[ 1 ];
		gl.glGenBuffers( 1, tmp, 0 );
		final int vbo = tmp[ 0 ];

		/*
		OpenGL allows us to bind to several buffers at once as long as they have a different buffer type.
		We can bind the newly created buffer to the GL_ARRAY_BUFFER target with the glBindBuffer function:
		*/
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, vbo );

		/*
		glBufferData is a function to copy user-defined data into the currently bound buffer.
		*/
		gl.glBufferData( GL.GL_ARRAY_BUFFER, vertices.length * Float.BYTES, FloatBuffer.wrap( vertices ), GL.GL_STATIC_DRAW );

		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, 0 );



		// ..:: EleBO ::..

		gl.glGenBuffers( 1, tmp, 0 );
		final int ebo = tmp[ 0 ];
		gl.glBindBuffer( GL_ELEMENT_ARRAY_BUFFER, ebo );
		gl.glBufferData( GL.GL_ELEMENT_ARRAY_BUFFER, indices.length * Integer.BYTES, IntBuffer.wrap( indices ), GL.GL_STATIC_DRAW );
		gl.glBindBuffer( GL_ELEMENT_ARRAY_BUFFER, 0 );



		// ..:: SHADERS ::..

		ShaderCode vs = ShaderCode.create( gl3, GL2.GL_VERTEX_SHADER, Task1.class, "", null, "ex1", true );
		ShaderCode fs = ShaderCode.create( gl3, GL2.GL_FRAGMENT_SHADER, Task1.class, "", null, "ex1", true );
		vs.defaultShaderCustomization( gl3, true, false );
		fs.defaultShaderCustomization( gl3, true, false );

		prog = new ShaderProgram();
		prog.add( vs );
		prog.add( fs );
		prog.link( gl3, System.err );
		vs.destroy( gl3 );
		fs.destroy( gl3 );



		// ..:: VERTEX ARRAY OBJECT ::..

		gl3.glGenVertexArrays( 1, tmp, 0 );
		vao = tmp[ 0 ];

		gl3.glBindVertexArray( vao );
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, vbo );
		/*
		vertex attribute index 0, as in "layout (location = 0)"
		*/
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
		gl3.glBindVertexArray( vao );
		gl3.glDrawElements( GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0 );
	}

	@Override
	public void reshape( final GLAutoDrawable drawable, final int x, final int y, final int width, final int height )
	{
		drawable.getGL().glViewport(0, 0, width, height );
	}

	public static void main( String[] args )
	{
		new SimpleFrame( "Example2", 640, 480, new Task1() );
	}
}
