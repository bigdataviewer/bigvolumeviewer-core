package tpietzsch.blockmath1;

import com.jogamp.opengl.GL3;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import net.imglib2.Interval;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import tpietzsch.day10.OffScreenFrameBuffer;
import tpietzsch.day2.Shader;

import static com.jogamp.opengl.GL.GL_ARRAY_BUFFER;
import static com.jogamp.opengl.GL.GL_ELEMENT_ARRAY_BUFFER;
import static com.jogamp.opengl.GL.GL_FLOAT;
import static com.jogamp.opengl.GL.GL_LINES;
import static com.jogamp.opengl.GL.GL_STATIC_DRAW;
import static com.jogamp.opengl.GL.GL_UNSIGNED_INT;

public class ColoredLines
{
	private boolean initialized;

	private boolean updated;

	private int vbo;

	private int vao;

	private Shader prog;

	private final ArrayList< ColoredLine > lines = new ArrayList<>();

	static class ColoredLine
	{
		final float[] p0 = new float[ 3 ];
		final float[] p1 = new float[ 3 ];
		final float[] color = new float[ 4 ];

		ColoredLine(Vector3f p0, Vector3f p1, Vector4f color)
		{
			this.p0[ 0 ] = p0.x();
			this.p0[ 1 ] = p0.y();
			this.p0[ 2 ] = p0.z();
			this.p1[ 0 ] = p1.x();
			this.p1[ 1 ] = p1.y();
			this.p1[ 2 ] = p1.z();
			this.color[ 0 ] = color.x();
			this.color[ 1 ] = color.y();
			this.color[ 2 ] = color.z();
			this.color[ 3 ] = color.w();
		}
	}

	public synchronized void add( Vector3f p0, Vector3f p1, Vector4f color )
	{
		lines.add( new ColoredLine( p0, p1, color ) );
		updated = false;
	}

	public synchronized void add( float p0x, float p0y, float p0z, float p1x, float p1y, float p1z, float r, float g, float b, float a )
	{
		lines.add( new ColoredLine( new Vector3f( p0x, p0y, p0z ), new Vector3f( p1x, p1y, p1z ), new Vector4f( r, g, b, a ) ) );
		updated = false;
	}

	public synchronized void clear()
	{
		lines.clear();
		updated = false;
	}

	private void init( GL3 gl )
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

		prog = new Shader( gl, "colored", "colored", ColoredLines.class );
	}

	private synchronized void update( GL3 gl )
	{
		init( gl );

		if ( updated )
			return;
		updated = true;

		final float[] vertices = new float[ lines.size() * 14 ];
		int i = 0;
		for ( ColoredLine line : lines )
		{
			vertices[ i++ ] = line.p0[ 0 ];
			vertices[ i++ ] = line.p0[ 1 ];
			vertices[ i++ ] = line.p0[ 2 ];

			vertices[ i++ ] = line.color[ 0 ];
			vertices[ i++ ] = line.color[ 1 ];
			vertices[ i++ ] = line.color[ 2 ];
			vertices[ i++ ] = line.color[ 3 ];

			vertices[ i++ ] = line.p1[ 0 ];
			vertices[ i++ ] = line.p1[ 1 ];
			vertices[ i++ ] = line.p1[ 2 ];

			vertices[ i++ ] = line.color[ 0 ];
			vertices[ i++ ] = line.color[ 1 ];
			vertices[ i++ ] = line.color[ 2 ];
			vertices[ i++ ] = line.color[ 3 ];
		}

		gl.glBindBuffer( GL_ARRAY_BUFFER, vbo );
		gl.glBufferData( GL_ARRAY_BUFFER, vertices.length * Float.BYTES, FloatBuffer.wrap( vertices ), GL_STATIC_DRAW );
		gl.glBindBuffer( GL_ARRAY_BUFFER, 0 );

		gl.glBindVertexArray( vao );
		gl.glBindBuffer( GL_ARRAY_BUFFER, vbo );
		// pos
		gl.glVertexAttribPointer(0, 3, GL_FLOAT, false, 7 * Float.BYTES, 0 );
		gl.glEnableVertexAttribArray(0);
		// color
		gl.glVertexAttribPointer(1, 4, GL_FLOAT, false, 7 * Float.BYTES, 3 * Float.BYTES );
		gl.glEnableVertexAttribArray(1);
		gl.glBindVertexArray( 0 );
	}

	public void draw( GL3 gl,final Matrix4fc model, final Matrix4fc view, final Matrix4fc projection )
	{
		int count = 0;
		synchronized ( this )
		{
			update( gl );
			count = lines.size();
		}

		if ( count == 0 )
			return;

		prog.use( gl );
		prog.setUniform( gl, "model", model );
		prog.setUniform( gl, "view", view );
		prog.setUniform( gl, "projection", projection );
		gl.glBindVertexArray( vao );
		gl.glDrawArrays( GL_LINES, 0, 2 * count );
		gl.glBindVertexArray( 0 );
	}
}
