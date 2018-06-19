package tpietzsch.shadergen.example;

// https://learnopengl.com/Getting-started/Hello-Triangle

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.util.glsl.ShaderCode;
import java.nio.FloatBuffer;
import java.util.Arrays;
import tpietzsch.day1.SimpleFrame;
import tpietzsch.shadergen.Uniform3f;
import tpietzsch.shadergen.backend.jogl.JoglGpuContext;
import tpietzsch.shadergen.generate.Segment;
import tpietzsch.shadergen.generate.SegmentTemplate;
import tpietzsch.shadergen.generate.SegmentedShader;
import tpietzsch.shadergen.generate.SegmentedShaderBuilder;

import static com.jogamp.opengl.GL.GL_FLOAT;
import static com.jogamp.opengl.GL.GL_TRIANGLES;

/**
 * Paint a triangle
 */
public class Example1 implements GLEventListener
{
	private int vao;

	private Uniform3f rgb;

	private SegmentedShader shader;

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


		final StringBuilder code = new StringBuilder();

		SegmentTemplate templateMain = new SegmentTemplate(
				Example1.class,
				"ex1.fp",
				Arrays.asList( "rgb", "convert" ) );
		Segment fragMain = templateMain.instantiate();
		SegmentTemplate templateVertMain = new SegmentTemplate(
				Example1.class,
				"ex1.vp",
				Arrays.asList() );
		Segment vertMain = templateVertMain.instantiate();
		shader = new SegmentedShaderBuilder().fragment( fragMain ).vertex( vertMain ).build();
		rgb = shader.getUniform3f( "rgb" );

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

		final JoglGpuContext context = JoglGpuContext.get( gl3 );
		shader.use( context );
		shader.setUniforms( context );

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
