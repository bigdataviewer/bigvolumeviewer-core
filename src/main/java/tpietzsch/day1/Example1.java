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

import static com.jogamp.opengl.GL.GL_FALSE;
import static com.jogamp.opengl.GL.GL_FLOAT;
import static com.jogamp.opengl.GL.GL_TRIANGLES;

/**
 * Paint a triangle
 */
public class Example1 implements GLEventListener
{
	private int vao;

	private ShaderProgram prog;

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



		// ..:: SHADERS ::..

		ShaderCode vs = ShaderCode.create( gl3, GL2.GL_VERTEX_SHADER, Example1.class, "", null, "ex1", true );
		ShaderCode fs = ShaderCode.create( gl3, GL2.GL_FRAGMENT_SHADER, Example1.class, "", null, "ex1", true );
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
		gl3.glDrawArrays( GL_TRIANGLES, 0, 3 );
	}

	@Override
	public void reshape( final GLAutoDrawable drawable, final int x, final int y, final int width, final int height )
	{
		System.out.println( "drawable = [" + drawable + "], x = [" + x + "], y = [" + y + "], width = [" + width + "], height = [" + height + "]" );
		drawable.getGL().glViewport(0, 0, width, height );
	}

	public static void main( String[] args )
	{
		new SimpleFrame( "Example1", 640, 480, new Example1() );
	}
}
