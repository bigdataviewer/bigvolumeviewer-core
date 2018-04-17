package tpietzsch.day4;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.imglib2.realtransform.AffineTransform3D;
import org.joml.Matrix4f;
import tpietzsch.day2.Shader;
import tpietzsch.util.MatrixMath;
import tpietzsch.util.Syncd;

import static com.jogamp.opengl.GL.GL_COLOR_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_DEPTH_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_DEPTH_TEST;
import static com.jogamp.opengl.GL.GL_ELEMENT_ARRAY_BUFFER;
import static com.jogamp.opengl.GL.GL_FLOAT;
import static com.jogamp.opengl.GL.GL_LINES;
import static com.jogamp.opengl.GL.GL_UNSIGNED_INT;

/**
 * Perspective projection looking at BDV window (z=0 plane).
 * Interactive transform editing.
 */
public class Example2 implements GLEventListener
{
	private int vao;

	private Shader prog;

	@Override
	public void init( final GLAutoDrawable drawable )
	{
		final GL gl = drawable.getGL();
		final GL3 gl3 = drawable.getGL().getGL3();

		// ..:: VERTEX BUFFER ::..

		final float vertices[] = {
				// 3 pos
				  0.0f,   0.0f, 0.0f,
				640.0f,   0.0f, 0.0f,
				640.0f, 480.0f, 0.0f,
				  0.0f, 480.0f, 0.0f,
		};
		final int[] tmp = new int[ 2 ];
		gl.glGenBuffers( 1, tmp, 0 );
		final int vbo = tmp[ 0 ];
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, vbo );
		gl.glBufferData( GL.GL_ARRAY_BUFFER, vertices.length * Float.BYTES, FloatBuffer.wrap( vertices ), GL.GL_STATIC_DRAW );
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, 0 );

		// ..:: ELEMENT BUFFER ::..

		final int indices[] = {  // note that we start from 0!
				0, 1,    // first line
				1, 2,    // second line
				2, 3,
				3, 0
		};
		gl.glGenBuffers( 1, tmp, 0 );
		final int ebo = tmp[ 0 ];
		gl.glBindBuffer( GL_ELEMENT_ARRAY_BUFFER, ebo );
		gl.glBufferData( GL.GL_ELEMENT_ARRAY_BUFFER, indices.length * Integer.BYTES, IntBuffer.wrap( indices ), GL.GL_STATIC_DRAW );
		gl.glBindBuffer( GL_ELEMENT_ARRAY_BUFFER, 0 );

		// ..:: SHADERS ::..

		prog = new Shader( gl3, "ex1", "ex1" );

		// ..:: VERTEX ARRAY OBJECT ::..

		gl3.glGenVertexArrays( 1, tmp, 0 );
		vao = tmp[ 0 ];
		gl3.glBindVertexArray( vao );
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, vbo );
		gl3.glVertexAttribPointer( 0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0 );
		gl3.glEnableVertexAttribArray( 0 );
		gl.glBindBuffer( GL_ELEMENT_ARRAY_BUFFER, ebo );
		gl3.glBindVertexArray( 0 );

		// ..:: MISC ::..

		gl3.glEnable( GL_DEPTH_TEST );
	}

	@Override
	public void dispose( final GLAutoDrawable drawable )
	{}

	final Syncd< AffineTransform3D > worldToScreen = Syncd.affine3D();

	private final double screenPadding = 100;

	private double dCam = 2000;
	private double dClip = 1000;
	private double screenWidth = 640;
	private double screenHeight = 480;

	@Override
	public void display( final GLAutoDrawable drawable )
	{
		final GL gl = drawable.getGL();
		final GL3 gl3 = drawable.getGL().getGL3();

		gl.glClearColor( 0.2f, 0.3f, 0.3f, 1.0f );
		gl.glClear( GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT );

		final Matrix4f model = new Matrix4f();
		final Matrix4f view = MatrixMath.affine( worldToScreen.get(), new Matrix4f() );
		final Matrix4f projection = MatrixMath.screenPerspective( dCam, dClip, screenWidth, screenHeight, screenPadding, new Matrix4f() );

		final Matrix4f pvm = new Matrix4f( projection ).mul( view ).mul( model );

		prog.use( gl3 );
		prog.setUniform( gl3, "pvm", pvm );
		gl3.glBindVertexArray( vao );
		gl3.glDrawElements( GL_LINES, 8, GL_UNSIGNED_INT, 0 );
	}

	@Override
	public void reshape( final GLAutoDrawable drawable, final int x, final int y, final int width, final int height )
	{}

	public static void main( final String[] args ) throws InterruptedException
	{
		final InputFrame frame = new InputFrame( "Example2", 640, 480 );
		Example2 glPainter = new Example2();
		frame.setGlEventListener( glPainter );
		frame.setupDefaultTransformHandler( glPainter.worldToScreen::set );
		frame.show();
	}

}
