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
package tpietzsch.scene.mesh;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.imagej.mesh.nio.BufferMesh;
import org.joml.Matrix4fc;
import tpietzsch.backend.jogl.JoglGpuContext;
import tpietzsch.shadergen.DefaultShader;
import tpietzsch.shadergen.Shader;
import tpietzsch.shadergen.generate.Segment;
import tpietzsch.shadergen.generate.SegmentTemplate;
import tpietzsch.util.Images;

import static com.jogamp.opengl.GL.GL_FLOAT;
import static com.jogamp.opengl.GL.GL_RGB;
import static com.jogamp.opengl.GL.GL_TEXTURE0;
import static com.jogamp.opengl.GL.GL_TEXTURE_2D;
import static com.jogamp.opengl.GL.GL_TRIANGLES;
import static com.jogamp.opengl.GL.GL_UNSIGNED_BYTE;
import static com.jogamp.opengl.GL.GL_UNSIGNED_INT;

public class StupidMesh
{
	private final Shader prog;

	private int vao;

	private BufferMesh mesh;

	public StupidMesh( final BufferMesh mesh )
	{
		this.mesh = mesh;

		final Segment meshVp = new SegmentTemplate( StupidMesh.class, "mesh.vp" ).instantiate();
		final Segment meshFp = new SegmentTemplate( StupidMesh.class, "mesh.fp" ).instantiate();
		prog = new DefaultShader( meshVp.getCode(), meshFp.getCode() );
	}

	private boolean initialized;

	private void init( GL3 gl )
	{
		initialized = true;

		final int[] tmp = new int[ 3 ];
		gl.glGenBuffers( 3, tmp, 0 );
		final int meshPosVbo = tmp[ 0 ];
		final int meshNormalVbo = tmp[ 1 ];
		final int meshEbo = tmp[ 2 ];

		final FloatBuffer vertices = mesh.vertices().verts();
		vertices.rewind();
		System.out.println( "vertices = " + vertices.limit() );
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, meshPosVbo );
		gl.glBufferData( GL.GL_ARRAY_BUFFER, vertices.limit() * Float.BYTES, vertices, GL.GL_STATIC_DRAW );
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, 0 );

		final FloatBuffer normals = mesh.vertices().normals();
		normals.rewind();
		System.out.println( "normals = " + normals.limit() );
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, meshNormalVbo );
		gl.glBufferData( GL.GL_ARRAY_BUFFER, normals.limit() * Float.BYTES, vertices, GL.GL_STATIC_DRAW );
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, 0 );

		final IntBuffer indices = mesh.triangles().indices();
		indices.rewind();
		System.out.println( "indices = " + indices.limit() );
		gl.glBindBuffer( GL.GL_ELEMENT_ARRAY_BUFFER, meshEbo );
		gl.glBufferData( GL.GL_ELEMENT_ARRAY_BUFFER, indices.limit() * Integer.BYTES, indices, GL.GL_STATIC_DRAW );
		gl.glBindBuffer( GL.GL_ELEMENT_ARRAY_BUFFER, 0 );



		gl.glGenVertexArrays( 1, tmp, 0 );
		vao = tmp[ 0 ];
		gl.glBindVertexArray( vao );
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, meshPosVbo );
		gl.glVertexAttribPointer( 0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0 );
		gl.glEnableVertexAttribArray( 0 );
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, meshNormalVbo );
		gl.glVertexAttribPointer( 0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0 );
		gl.glEnableVertexAttribArray( 1 );
//		gl.glVertexAttribPointer( 1, 2, GL_FLOAT, false, 5 * Float.BYTES, 3 * Float.BYTES );
//		gl.glEnableVertexAttribArray( 1 );
		gl.glBindBuffer( GL.GL_ELEMENT_ARRAY_BUFFER, meshEbo );
		gl.glBindVertexArray( 0 );
	}

	public void draw( GL3 gl, Matrix4fc pvm, Matrix4fc vm )
	{
		if ( !initialized )
			init( gl );

		JoglGpuContext context = JoglGpuContext.get( gl );

		prog.getUniformMatrix4f( "pvm" ).set( pvm );
		prog.getUniformMatrix4f( "vm" ).set( vm );
		prog.setUniforms( context );
		prog.use( context );

		gl.glBindVertexArray( vao );
		gl.glDrawElements( GL_TRIANGLES, ( int ) mesh.triangles().size() * 3, GL_UNSIGNED_INT, 0 );
		gl.glBindVertexArray( 0 );
	}
}
