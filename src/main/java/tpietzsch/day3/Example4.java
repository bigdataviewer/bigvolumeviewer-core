package tpietzsch.day3;

//https://learnopengl.com/Getting-started/Coordinate-Systems

import static com.jogamp.opengl.GL.GL_COLOR_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_DEPTH_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_DEPTH_TEST;
import static com.jogamp.opengl.GL.GL_FLOAT;
import static com.jogamp.opengl.GL.GL_RGB;
import static com.jogamp.opengl.GL.GL_RGBA;
import static com.jogamp.opengl.GL.GL_TEXTURE0;
import static com.jogamp.opengl.GL.GL_TEXTURE1;
import static com.jogamp.opengl.GL.GL_TEXTURE_2D;
import static com.jogamp.opengl.GL.GL_TRIANGLES;
import static com.jogamp.opengl.GL.GL_UNSIGNED_BYTE;

import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import org.joml.Matrix4f;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Actions;
import org.scijava.ui.behaviour.util.Behaviours;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;

import net.imglib2.realtransform.AffineTransform3D;
import tpietzsch.day2.Shader;
import tpietzsch.util.Images;

/**
 * Textured rectangle with <em>projection * view * model</em> in vertex shader
 */
public class Example4 implements GLEventListener
{
	private final BehaviourTransformEventHandler3DMamut tfHandler;

	private Runnable requestRepaint = () -> {};

	public Example4()
	{
		tfHandler = new BehaviourTransformEventHandler3DMamut( this::transformChanged );
	}

	private void transformChanged( final AffineTransform3D t )
	{
		System.out.println( t );
		synchronized ( transform )
		{
			transform.set( t );
		}
		requestRepaint.run();
	}

	void setRequestRepaint( final Runnable requestRepaint )
	{
		this.requestRepaint = requestRepaint;
	}

	private int vao;

	private Shader prog;

	private int texture1;

	private int texture2;

	private final AffineTransform3D transform = new AffineTransform3D();

	@Override
	public void init( final GLAutoDrawable drawable )
	{
		final GL gl = drawable.getGL();
		final GL3 gl3 = drawable.getGL().getGL3();

		// ..:: VERTEX BUFFER ::..

		final float vertices[] = {
				// 3 pos, 2 tex
				-0.5f, -0.5f, -0.5f, 0.0f, 0.0f,
				0.5f, -0.5f, -0.5f, 1.0f, 0.0f,
				0.5f, 0.5f, -0.5f, 1.0f, 1.0f,
				0.5f, 0.5f, -0.5f, 1.0f, 1.0f,
				-0.5f, 0.5f, -0.5f, 0.0f, 1.0f,
				-0.5f, -0.5f, -0.5f, 0.0f, 0.0f,

				-0.5f, -0.5f, 0.5f, 0.0f, 0.0f,
				0.5f, -0.5f, 0.5f, 1.0f, 0.0f,
				0.5f, 0.5f, 0.5f, 1.0f, 1.0f,
				0.5f, 0.5f, 0.5f, 1.0f, 1.0f,
				-0.5f, 0.5f, 0.5f, 0.0f, 1.0f,
				-0.5f, -0.5f, 0.5f, 0.0f, 0.0f,

				-0.5f, 0.5f, 0.5f, 1.0f, 0.0f,
				-0.5f, 0.5f, -0.5f, 1.0f, 1.0f,
				-0.5f, -0.5f, -0.5f, 0.0f, 1.0f,
				-0.5f, -0.5f, -0.5f, 0.0f, 1.0f,
				-0.5f, -0.5f, 0.5f, 0.0f, 0.0f,
				-0.5f, 0.5f, 0.5f, 1.0f, 0.0f,

				0.5f, 0.5f, 0.5f, 1.0f, 0.0f,
				0.5f, 0.5f, -0.5f, 1.0f, 1.0f,
				0.5f, -0.5f, -0.5f, 0.0f, 1.0f,
				0.5f, -0.5f, -0.5f, 0.0f, 1.0f,
				0.5f, -0.5f, 0.5f, 0.0f, 0.0f,
				0.5f, 0.5f, 0.5f, 1.0f, 0.0f,

				-0.5f, -0.5f, -0.5f, 0.0f, 1.0f,
				0.5f, -0.5f, -0.5f, 1.0f, 1.0f,
				0.5f, -0.5f, 0.5f, 1.0f, 0.0f,
				0.5f, -0.5f, 0.5f, 1.0f, 0.0f,
				-0.5f, -0.5f, 0.5f, 0.0f, 0.0f,
				-0.5f, -0.5f, -0.5f, 0.0f, 1.0f,

				-0.5f, 0.5f, -0.5f, 0.0f, 1.0f,
				0.5f, 0.5f, -0.5f, 1.0f, 1.0f,
				0.5f, 0.5f, 0.5f, 1.0f, 0.0f,
				0.5f, 0.5f, 0.5f, 1.0f, 0.0f,
				-0.5f, 0.5f, 0.5f, 0.0f, 0.0f,
				-0.5f, 0.5f, -0.5f, 0.0f, 1.0f
		};
		final int[] tmp = new int[ 2 ];
		gl.glGenBuffers( 1, tmp, 0 );
		final int vbo = tmp[ 0 ];
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, vbo );
		gl.glBufferData( GL.GL_ARRAY_BUFFER, vertices.length * Float.BYTES, FloatBuffer.wrap( vertices ), GL.GL_STATIC_DRAW );
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, 0 );

		// ..:: TEXTURES ::..

		gl.glGenTextures( 2, tmp, 0 );
		texture1 = tmp[ 0 ];
		texture2 = tmp[ 1 ];
		byte[] data = null;
		try
		{
			data = Images.loadBytesRGB( Example4.class.getResourceAsStream( "container.jpg" ) );
		}
		catch ( final IOException e )
		{
			e.printStackTrace();
		}
		gl.glBindTexture( GL_TEXTURE_2D, texture1 );
		gl.glTexImage2D( GL_TEXTURE_2D, 0, GL_RGB, 512, 512, 0, GL_RGB, GL_UNSIGNED_BYTE, ByteBuffer.wrap( data ) );
		gl.glGenerateMipmap( GL_TEXTURE_2D );
		try
		{
			data = Images.loadBytesRGBA( Example4.class.getResourceAsStream( "awesomeface.png" ), true );
		}
		catch ( final IOException e )
		{
			e.printStackTrace();
		}
		gl.glBindTexture( GL_TEXTURE_2D, texture2 );
		gl.glTexImage2D( GL_TEXTURE_2D, 0, GL_RGBA, 512, 512, 0, GL_RGBA, GL_UNSIGNED_BYTE, ByteBuffer.wrap( data ) );
		gl.glGenerateMipmap( GL_TEXTURE_2D );

		// ..:: SHADERS ::..

		prog = new Shader( gl3, "ex1", "ex1" );

		// ..:: VERTEX ARRAY OBJECT ::..

		gl3.glGenVertexArrays( 1, tmp, 0 );
		vao = tmp[ 0 ];
		gl3.glBindVertexArray( vao );
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, vbo );
		gl3.glVertexAttribPointer( 0, 3, GL_FLOAT, false, 5 * Float.BYTES, 0 );
		gl3.glEnableVertexAttribArray( 0 );
		gl3.glVertexAttribPointer( 1, 2, GL_FLOAT, false, 5 * Float.BYTES, 3 * Float.BYTES );
		gl3.glEnableVertexAttribArray( 1 );
		gl3.glBindVertexArray( 0 );

		// ..:: MISC ::..

		gl3.glEnable( GL_DEPTH_TEST );
	}

	@Override
	public void dispose( final GLAutoDrawable drawable )
	{

	}

	public static void setMatrix4f( final AffineTransform3D transform, final Matrix4f matrix )
	{
		final float[] m = new float[ 16 ];
		for ( int c = 0; c < 4; ++c )
			for ( int r = 0; r < 3; ++r )
				m[ c * 4 + r ] = ( float ) transform.get( r, c );
		m[ 3 ] = 0f;
		m[ 7 ] = 0f;
		m[ 11 ] = 0f;
		m[ 15 ] = 1f;
		matrix.set( m );
	}

	@Override
	public void display( final GLAutoDrawable drawable )
	{
		final GL gl = drawable.getGL();
		final GL3 gl3 = drawable.getGL().getGL3();

		gl.glClearColor( 0.2f, 0.3f, 0.3f, 1.0f );
		gl.glClear( GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT );

		final Matrix4f tf = new Matrix4f();
		synchronized ( transform )
		{
			setMatrix4f( transform, tf );
		}

		final float s = 100;
		final Matrix4f model = new Matrix4f();
		model.scale( 1f / s ).mul( tf ).scale( s );

		final Matrix4f view = new Matrix4f();
		view.translate( 0.0f, 0.0f, -3.0f );

		final Matrix4f projection = new Matrix4f();
		projection.perspective( (float) Math.toRadians( 45.0 ), width / height, 0.1f, 100.0f );

		prog.use( gl3 );
		prog.setUniform( gl3, "texture1", 0 );
		prog.setUniform( gl3, "texture2", 1 );
		prog.setUniform( gl3, "model", model );
		prog.setUniform( gl3, "view", view );
		prog.setUniform( gl3, "projection", projection );
		gl3.glActiveTexture( GL_TEXTURE0 );
		gl3.glBindTexture( GL_TEXTURE_2D, texture1 );
		gl3.glActiveTexture( GL_TEXTURE1 );
		gl3.glBindTexture( GL_TEXTURE_2D, texture2 );
		gl3.glBindVertexArray( vao );
		gl3.glDrawArrays( GL_TRIANGLES, 0, 36 );
	}

	int width = 1;
	int height = 1;

	@Override
	public void reshape( final GLAutoDrawable drawable, final int x, final int y, final int width, final int height )
	{
		this.width = width;
		this.height = height;
	}

	public static void main( final String[] args )
	{
		final Example4 example = new Example4();
		example.tfHandler.setCanvasSize( 640, 480, false );
		final InputFrame frame = new InputFrame( "Example4", 640, 480, example );
		example.setRequestRepaint( frame.painterThread::requestRepaint );

		final Actions actions = new Actions( new InputTriggerConfig(), "all" );
		actions.install( frame.getKeybindings(), "opengl" );
		actions.runnableAction( () -> System.out.println( "action!" ), "Action", "A" );

		final Behaviours behaviours = new Behaviours( new InputTriggerConfig(), "all" );
		behaviours.install( frame.getTriggerbindings(), "opengl" );
		example.tfHandler.install( behaviours );

		final AffineTransform3D init = new AffineTransform3D();
		final double s = 2;
		init.set( s, 0, 0, 0,
				  0, s, 0, 0,
				  0, 0, s, 0);
		example.tfHandler.setTransform( init );
		frame.painterThread.requestRepaint();

		frame.getCanvas().addComponentListener( new ComponentAdapter()
		{
			@Override
			public void componentResized( final ComponentEvent e )
			{
				final int w = frame.getCanvas().getWidth();
				final int h = frame.getCanvas().getHeight();
				example.tfHandler.setCanvasSize( w, h, true );
			}
		} );

	}
}
