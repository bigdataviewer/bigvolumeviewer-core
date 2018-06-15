package tpietzsch.shadergen;

// https://learnopengl.com/Getting-started/Hello-Triangle

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.util.glsl.ShaderCode;
import com.jogamp.opengl.util.glsl.ShaderProgram;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import tpietzsch.day1.SimpleFrame;
import tpietzsch.shadergen.Playground.AbstractJoglUniform;
import tpietzsch.shadergen.Playground.JoglUniform3f;
import tpietzsch.shadergen.Playground.JoglUniformContext;
import tpietzsch.shadergen.Playground.Uniform3f;

import static com.jogamp.opengl.GL.GL_FLOAT;
import static com.jogamp.opengl.GL.GL_TRIANGLES;
import static com.jogamp.opengl.GL2ES2.GL_FRAGMENT_SHADER;

/**
 * Paint a triangle
 */
public class Example1 implements GLEventListener
{
	private int vao;

	private ShaderProgram prog;

	private Uniform3f rgb;

	private ArrayList< AbstractJoglUniform > uniforms = new ArrayList<>();

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


		final Class< ? > resourceContext = Playground.class;
		final String resourceName = "ex1.fp";
		final List< String > keys = Arrays.asList( "rgb" );
		Playground.ShaderFragmentTemplate template = null;
		try
		{
			template = new Playground.ShaderFragmentTemplate( resourceContext, resourceName, keys );
		}
		catch ( IOException e )
		{
			throw new RuntimeException( e );
		}
		final Playground.ShaderFragment shaderFragment = template.instantiate();
		final ShaderCode fs = new ShaderCode( GL_FRAGMENT_SHADER, 1, new CharSequence[][] { { new StringBuilder( shaderFragment.getCode() ) } } );

		final JoglUniform3f joglUniform3f = new JoglUniform3f( shaderFragment.getName( "rgb" ) );
		uniforms.add( joglUniform3f );
		rgb = joglUniform3f;

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
	public void display( final GLAutoDrawable drawable )
	{
		System.out.println( "Example1.display" );
		final GL gl = drawable.getGL();
		final GL3 gl3 = drawable.getGL().getGL3();

		prog.useProgram( gl3, true );

		final JoglUniformContext context = new JoglUniformContext( gl3, prog.program() );
		uniforms.forEach( context::updateUniformValues );
//		gl3.glProgramUniform3f( prog.program(), gl3.glGetUniformLocation( prog.program(), "rgb__0__" ), 1, 0, 1 );

		gl3.glBindVertexArray( vao );
		gl3.glDrawArrays( GL_TRIANGLES, 0, 3 );
	}

	@Override
	public void dispose( final GLAutoDrawable drawable )
	{}

	int i = 0;

	@Override
	public void reshape( final GLAutoDrawable drawable, final int x, final int y, final int width, final int height )
	{
		++i;
		if ( i % 20 == 0 )
			rgb.set( 1, 0, 1 );
		else if ( i % 20 == 10 )
			rgb.set( 0, 1, 0 );
	}

	public static void main( String[] args )
	{
		new SimpleFrame( "Example1", 640, 480, new Example1() );
	}
}
