package tpietzsch.day4;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import net.imglib2.FinalInterval;
import net.imglib2.realtransform.AffineTransform3D;
import org.joml.Matrix4f;
import tpietzsch.day2.Shader;
import tpietzsch.util.MatrixMath;
import tpietzsch.util.Syncd;

import static com.jogamp.opengl.GL.GL_COLOR_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_DEPTH_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_DEPTH_TEST;

/**
 * Perspective projection looking at BDV window (z=0 plane).
 * Interactive transform editing.
 */
public class Example4 implements GLEventListener
{
	private int vao;

	private Shader prog;

	private WireframeBox1 box;

	private WireframeBox1 box2;

	@Override
	public void init( final GLAutoDrawable drawable )
	{
		final GL gl = drawable.getGL();
		final GL3 gl3 = drawable.getGL().getGL3();

		box = new WireframeBox1();
		box.updateVertices( gl3, new FinalInterval( 800, 600, 300 ) );
		box2 = new WireframeBox1();
		box2.updateVertices( gl3, new FinalInterval( 640, 480, 300 ) );
		prog = new Shader( gl3, "ex4", "ex3" );
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

		prog.use( gl3 );
		prog.setUniform( gl3, "model", model );
		prog.setUniform( gl3, "view", view );
		prog.setUniform( gl3, "projection", projection );

		prog.setUniform( gl3, "color", 1.0f, 0.5f, 0.2f, 1.0f );
		box.draw( gl3 );

		model.translate( 100, 100, 0 );
		prog.setUniform( gl3, "model", model );
		prog.setUniform( gl3, "color", 0.2f, 0.5f, 1.0f, 1.0f );
		box2.draw( gl3 );
	}

	@Override
	public void reshape( final GLAutoDrawable drawable, final int x, final int y, final int width, final int height )
	{}

	public static void main( final String[] args ) throws InterruptedException
	{
		final InputFrame frame = new InputFrame( "Example4", 640, 480 );
		InputFrame.DEBUG = false;
		Example4 glPainter = new Example4();
		frame.setGlEventListener( glPainter );
		final TransformHandler tf = frame.setupDefaultTransformHandler( glPainter.worldToScreen::set );
		frame.getDefaultActions().runnableAction( () -> {
			tf.setTransform( new AffineTransform3D() );
		}, "reset transform", "R" );
		frame.show();
	}

}
