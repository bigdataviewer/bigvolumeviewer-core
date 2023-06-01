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
package bvv.vistools.examples.scene;

import bvv.core.backend.jogl.JoglGpuContext;
import bvv.core.shadergen.DefaultShader;
import bvv.core.shadergen.Shader;
import bvv.core.shadergen.generate.Segment;
import bvv.core.shadergen.generate.SegmentTemplate;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static com.jogamp.opengl.GL.GL_ELEMENT_ARRAY_BUFFER;
import static com.jogamp.opengl.GL.GL_FLOAT;
import static com.jogamp.opengl.GL.GL_RGB;
import static com.jogamp.opengl.GL.GL_TEXTURE0;
import static com.jogamp.opengl.GL.GL_TEXTURE_2D;
import static com.jogamp.opengl.GL.GL_TRIANGLES;
import static com.jogamp.opengl.GL.GL_UNSIGNED_BYTE;
import static com.jogamp.opengl.GL.GL_UNSIGNED_INT;

public class TexturedDepthRamp
{
	private final Shader prog;

	private int vao;

	private int texId;

	public TexturedDepthRamp()
	{
		final Segment rampVp = new SegmentTemplate( TexturedDepthRamp.class, "ramp.vp" ).instantiate();
		final Segment rampFp = new SegmentTemplate( TexturedDepthRamp.class, "ramp.fp" ).instantiate();
		prog = new DefaultShader( rampVp.getCode(), rampFp.getCode() );
	}

	private boolean initialized;

	private void init( GL3 gl )
	{
		initialized = true;

		// ..:: VERTEX BUFFER ::..

		final float vertices[] = { // 3 pos, 2 tex
				 1,  1, -1,   1, 1,
				 1, -1, -1,   1, 0,
				-2, -2, -2,   0, 0,
				-2,  2, -2,   0, 1,
		};
		final int[] tmp = new int[ 2 ];
		gl.glGenBuffers( 1, tmp, 0 );
		final int vbo = tmp[ 0 ];
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, vbo );
		gl.glBufferData( GL.GL_ARRAY_BUFFER, vertices.length * Float.BYTES, FloatBuffer.wrap( vertices ), GL.GL_STATIC_DRAW );
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, 0 );

		// ..:: ELEMENT BUFFER ::..

		int indices[] = {  // note that we start from 0!
				0, 1, 3,   // first triangle
				1, 2, 3    // second triangle
		};
		gl.glGenBuffers( 1, tmp, 0 );
		final int ebo = tmp[ 0 ];
		gl.glBindBuffer( GL_ELEMENT_ARRAY_BUFFER, ebo );
		gl.glBufferData( GL_ELEMENT_ARRAY_BUFFER, indices.length * Integer.BYTES, IntBuffer.wrap( indices ), GL.GL_STATIC_DRAW );
		gl.glBindBuffer( GL_ELEMENT_ARRAY_BUFFER, 0 );

		// ..:: TEXTURES ::..

		gl.glGenTextures( 1, tmp, 0 );
		texId = tmp[ 0 ];
		byte[] data = null;
		try
		{
			data = Images.loadBytesRGB( TexturedDepthRamp.class.getResourceAsStream( "awesomeface.png" ) );
		}
		catch ( final IOException e )
		{
			e.printStackTrace();
		}
		gl.glActiveTexture( GL_TEXTURE0 );
		gl.glBindTexture( GL_TEXTURE_2D, texId );
		gl.glTexImage2D( GL_TEXTURE_2D, 0, GL_RGB, 512, 512, 0, GL_RGB, GL_UNSIGNED_BYTE, ByteBuffer.wrap( data ) );
		gl.glGenerateMipmap( GL_TEXTURE_2D );

		// ..:: VERTEX ARRAY OBJECT ::..

		gl.glGenVertexArrays( 1, tmp, 0 );
		vao = tmp[ 0 ];
		gl.glBindVertexArray( vao );
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, vbo );
		gl.glVertexAttribPointer( 0, 3, GL_FLOAT, false, 5 * Float.BYTES, 0 );
		gl.glEnableVertexAttribArray( 0 );
		gl.glVertexAttribPointer( 1, 2, GL_FLOAT, false, 5 * Float.BYTES, 3 * Float.BYTES );
		gl.glEnableVertexAttribArray( 1 );
		gl.glBindBuffer( GL_ELEMENT_ARRAY_BUFFER, ebo );
		gl.glBindVertexArray( 0 );
	}

	public void draw( GL3 gl )
	{
		if ( !initialized )
			init( gl );

		JoglGpuContext context = JoglGpuContext.get( gl );
		prog.use( context );

		gl.glActiveTexture( GL_TEXTURE0 );
		gl.glBindTexture( GL_TEXTURE_2D, texId );
		gl.glBindVertexArray( vao );
		gl.glDrawElements( GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0 );
		gl.glBindVertexArray( 0 );
	}
}
