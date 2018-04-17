package tpietzsch.day4;

import com.jogamp.opengl.GL3;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.imglib2.Interval;

import static com.jogamp.opengl.GL.GL_ARRAY_BUFFER;
import static com.jogamp.opengl.GL.GL_ELEMENT_ARRAY_BUFFER;
import static com.jogamp.opengl.GL.GL_FLOAT;
import static com.jogamp.opengl.GL.GL_LINES;
import static com.jogamp.opengl.GL.GL_STATIC_DRAW;
import static com.jogamp.opengl.GL.GL_TRIANGLES;
import static com.jogamp.opengl.GL.GL_UNSIGNED_INT;

public class ScreenPlane1
{
	private final float[] vertices;

	private final int[] indices;

	private boolean initialized;

	private int vbo;

	private int vao;

	ScreenPlane1()
	{
		vertices = new float[ 12 ];
		indices = new int[] {
				0, 1, 3,
				3, 2, 0 };
	}

	public void init( GL3 gl )
	{
		if ( initialized )
			return;
		initialized = true;

		final int[] tmp = new int[ 2 ];

		gl.glGenVertexArrays( 1, tmp, 0 );
		vao = tmp[ 0 ];
		gl.glGenBuffers( 2, tmp, 0 );
		vbo = tmp[ 0 ];
		final int ebo = tmp[ 1 ];

		gl.glBindBuffer( GL_ELEMENT_ARRAY_BUFFER, ebo );
		gl.glBufferData( GL_ELEMENT_ARRAY_BUFFER, indices.length * Integer.BYTES, IntBuffer.wrap( indices ), GL_STATIC_DRAW );
		gl.glBindBuffer( GL_ELEMENT_ARRAY_BUFFER, 0 );

		gl.glBindVertexArray( vao );
		gl.glBindBuffer( GL_ARRAY_BUFFER, vbo );
		gl.glVertexAttribPointer( 0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0 );
		gl.glEnableVertexAttribArray( 0 );
		gl.glBindBuffer( GL_ELEMENT_ARRAY_BUFFER, ebo );
		gl.glBindVertexArray( 0 );
	}

	public void updateVertices( GL3 gl, Interval interval )
	{
		assert interval.numDimensions() == 2;

		if ( !initialized )
			init( gl );

		final int numSourceDims = 2;
		final int numCorners = 1 << numSourceDims;
		for ( int i = 0; i < numCorners; i++ )
			for ( int d = 0, b = 1; d < 2; ++d, b <<= 1 )
				vertices[ i * 3 + d ] = ( float) ( ( i & b ) == 0 ? interval.realMin( d ) : interval.realMax( d ) );

		gl.glBindBuffer( GL_ARRAY_BUFFER, vbo );
		gl.glBufferData( GL_ARRAY_BUFFER, vertices.length * Float.BYTES, FloatBuffer.wrap( vertices ), GL_STATIC_DRAW );
		gl.glBindBuffer( GL_ARRAY_BUFFER, 0 );
	}

	public void draw( GL3 gl )
	{
		if ( !initialized )
			return;

		gl.glBindVertexArray( vao );
		gl.glDrawElements( GL_TRIANGLES, 6, GL_UNSIGNED_INT, 0 );
		gl.glBindVertexArray( 0 );
	}
}
