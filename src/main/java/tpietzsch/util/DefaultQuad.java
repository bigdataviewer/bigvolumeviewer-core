package tpietzsch.util;

import com.jogamp.opengl.GL3;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static com.jogamp.opengl.GL.GL_ARRAY_BUFFER;
import static com.jogamp.opengl.GL.GL_ELEMENT_ARRAY_BUFFER;
import static com.jogamp.opengl.GL.GL_FLOAT;
import static com.jogamp.opengl.GL.GL_STATIC_DRAW;
import static com.jogamp.opengl.GL.GL_TRIANGLES;
import static com.jogamp.opengl.GL.GL_UNSIGNED_INT;

public class DefaultQuad
{
	private boolean initialized;

	private int vao;

	private void init( GL3 gl )
	{
		if ( initialized )
			return;
		initialized = true;

		final float z = 0;
		final float vertices[] = {
				 1,  1, z,  // top right
				 1, -1, z,  // bottom right
				-1, -1, z,  // bottom left
				-1,  1, z,  // top left
		};
		final int indices[] = {
				0, 1, 3,
				1, 2, 3
		};

		final int[] tmp = new int[ 2 ];

		gl.glGenVertexArrays( 1, tmp, 0 );
		vao = tmp[ 0 ];
		gl.glGenBuffers( 2, tmp, 0 );
		final int vbo = tmp[ 0 ];
		final int ebo = tmp[ 1 ];

		gl.glBindVertexArray( vao );
		gl.glBindBuffer( GL_ARRAY_BUFFER, vbo );
		gl.glBufferData( GL_ARRAY_BUFFER, vertices.length * Float.BYTES, FloatBuffer.wrap( vertices ), GL_STATIC_DRAW );
		gl.glVertexAttribPointer( 0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0 );
		gl.glEnableVertexAttribArray( 0 );
		gl.glBindBuffer( GL_ELEMENT_ARRAY_BUFFER, ebo );
		gl.glBufferData( GL_ELEMENT_ARRAY_BUFFER, indices.length * Integer.BYTES, IntBuffer.wrap( indices ), GL_STATIC_DRAW );
		gl.glBindVertexArray( 0 );
	}

	public void draw( GL3 gl )
	{
		if ( !initialized )
			init( gl );
		gl.glBindVertexArray( vao );
		gl.glDrawElements( GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0 );
		gl.glBindVertexArray( 0 );
	}
}
