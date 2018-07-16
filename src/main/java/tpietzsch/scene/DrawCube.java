package tpietzsch.scene;

//https://learnopengl.com/Getting-started/Coordinate-Systems

import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import ij.ImageJ;
import net.imglib2.img.display.imagej.ImageJFunctions;
import org.joml.Matrix4f;
import tpietzsch.contextsharing.SimpleFrame;
import tpietzsch.offscreen.OffScreenFrameBuffer;
import tpietzsch.offscreen.OffScreenFrameBufferWithDepth;

import static com.jogamp.opengl.GL.GL_ALWAYS;
import static com.jogamp.opengl.GL.GL_BLEND;
import static com.jogamp.opengl.GL.GL_COLOR_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_DEPTH_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_DEPTH_TEST;
import static com.jogamp.opengl.GL.GL_ONE_MINUS_SRC_ALPHA;
import static com.jogamp.opengl.GL.GL_RGBA8;
import static com.jogamp.opengl.GL.GL_SRC_ALPHA;

/**
 * Textured rectangle with <em>projection * view * model</em> in vertex shader
 */
public class DrawCube implements GLEventListener
{
	private final TexturedUnitCube cube = new TexturedUnitCube();

	private final TexturedDepthRamp ramp = new TexturedDepthRamp();

//	private OffScreenFrameBuffer offscreen = new OffScreenFrameBuffer( 160, 120, GL_RGBA8 );
//	private OffScreenFrameBuffer offscreen = new OffScreenFrameBuffer( 640, 480, GL_RGBA8 );
	private OffScreenFrameBufferWithDepth offscreen = new OffScreenFrameBufferWithDepth( 640, 480, GL_RGBA8 );

	@Override
	public void init( final GLAutoDrawable drawable )
	{
	}

	@Override
	public void dispose( final GLAutoDrawable drawable )
	{
	}

	@Override
	public void reshape( final GLAutoDrawable drawable, final int x, final int y, final int width, final int height )
	{
	}

	@Override
	public void display( final GLAutoDrawable drawable )
	{
		final GL3 gl = drawable.getGL().getGL3();

		gl.glClearColor( 0.2f, 0.3f, 0.3f, 1.0f );
		gl.glClear( GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT );

		gl.glEnable( GL_DEPTH_TEST );
		gl.glDepthFunc( GL_ALWAYS );

		gl.glEnable( GL_BLEND );
		gl.glBlendFunc( GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA );

		final Matrix4f model = new Matrix4f();
		model.rotate( (float) Math.toRadians( 45.0 ), 0.0f, 1.0f, 0.0f );
		model.rotate( (float) Math.toRadians( -55.0 ), 1.0f, 0.0f, 0.0f );

		final Matrix4f view = new Matrix4f();
		view.translate( 0.0f, 0.0f, -3.0f );

		final Matrix4f projection = new Matrix4f();
		projection.perspective( (float) Math.toRadians( 45.0 ), 1, 0.1f, 100.0f );

		final Matrix4f pvm = new Matrix4f( projection ).mul( view ).mul( model );

		offscreen.bind( gl );
		ramp.draw( gl );
		cube.draw( gl, pvm );
		offscreen.unbind( gl, true );
		offscreen.drawQuad( gl );

		ImageJFunctions.show( offscreen.getDepthImg() );
	}

	public static void main( final String[] args )
	{
		new ImageJ();
		new SimpleFrame( "DrawCube", 640, 480, new DrawCube() );
	}


//		final byte[] tmp = new byte[ 1 ];
//		gl.glGetBooleanv( GL_DEPTH_TEST, tmp, 0 );
//		boolean restoreDepth = tmp[ 0 ] != 0;
//		gl.glGetBooleanv( GL_BLEND, tmp, 0 );
//		boolean restoreBlend = tmp[ 0 ] != 0;

//		if ( !restoreDepth )
//			gl.glDisable( GL_DEPTH_TEST );
//		if ( !restoreBlend )
//			gl.glDisable( GL_BLEND );
}
