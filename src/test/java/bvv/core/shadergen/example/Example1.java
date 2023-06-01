/*-
 * #%L
 * Volume rendering of bdv datasets
 * %%
 * Copyright (C) 2018 - 2021 Tobias Pietzsch
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package bvv.core.shadergen.example;

import bvv.core.shadergen.generate.Segment;
import bvv.core.shadergen.generate.SegmentTemplate;
import bvv.core.shadergen.generate.SegmentedShader;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import java.nio.FloatBuffer;
import bvv.core.backend.jogl.JoglGpuContext;
import bvv.core.shadergen.Uniform3f;
import bvv.core.shadergen.generate.SegmentedShaderBuilder;

import static com.jogamp.opengl.GL.GL_FLOAT;
import static com.jogamp.opengl.GL.GL_TRIANGLES;

// https://learnopengl.com/Getting-started/Hello-Triangle

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

		final float[] vertices = new float[] {
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

		final SegmentTemplate templateFragConvert = new SegmentTemplate(
				"convertlin.fp",
				"offset", "scale", "convert" );
		final Segment fragConvertR = templateFragConvert.instantiate();
		final Segment fragConvertG = templateFragConvert.instantiate();
		final Segment fragConvertB = templateFragConvert.instantiate();

		final SegmentTemplate templateFragMain = new SegmentTemplate(
				"ex1.fp",
				"rgb", "convertR", "convertG", "convertB" );
		final Segment fragMain = templateFragMain.instantiate()
				.bind( "convertR", fragConvertR, "convert" )
				.bind( "convertG", fragConvertG, "convert" )
				.bind( "convertB", fragConvertB, "convert" );

		final Segment vertMain = new SegmentTemplate("ex1.vp" ).instantiate();

		shader = new SegmentedShaderBuilder()
				.fragment( fragConvertR )
				.fragment( fragConvertG )
				.fragment( fragConvertB )
				.fragment( fragMain )
				.vertex( vertMain )
				.build();

		final StringBuilder vertexShaderCode = shader.getVertexShaderCode();
		System.out.println( "vertexShaderCode = " + vertexShaderCode );
		System.out.println( "\n\n--------------------------------\n\n");
		final StringBuilder fragmentShaderCode = shader.getFragmentShaderCode();
		System.out.println( "fragmentShaderCode = " + fragmentShaderCode );

		shader.getUniform1f( fragConvertR, "scale" ).set( 0.5f );
		shader.getUniform1f( fragConvertR, "offset" ).set( 0.5f );

		shader.getUniform1f( fragConvertG, "scale" ).set( -0.2f );
		shader.getUniform1f( fragConvertG, "offset" ).set( 0.5f );

		shader.getUniform1f( fragConvertB, "scale" ).set( -0.2f );
		shader.getUniform1f( fragConvertB, "offset" ).set( 0.5f );

		rgb = shader.getUniform3f( "rgb" );
		rgb.set( 0, 0, 1 );

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
		if ( ++i % 2 == 0 )
			rgb.set( 1, 1, 1 );
		else
			rgb.set( 0, 0, 0 );
	}

	public static void main( final String[] args )
	{
		InputFrame.DEBUG = false;
		final InputFrame frame = new InputFrame( "Example1", 640, 480 );
		frame.setGlEventListener( new Example1() );
		frame.show();
	}
}
