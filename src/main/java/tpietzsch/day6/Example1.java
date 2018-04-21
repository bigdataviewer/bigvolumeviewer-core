package tpietzsch.day6;

import bdv.spimdata.SequenceDescriptionMinimal;
import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.MultiResolutionSetupImgLoader;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.joml.Matrix4f;
import tpietzsch.day2.Shader;
import tpietzsch.day4.InputFrame;
import tpietzsch.day4.ScreenPlane1;
import tpietzsch.day4.TransformHandler;
import tpietzsch.day4.WireframeBox1;
import tpietzsch.util.MatrixMath;
import tpietzsch.util.Syncd;

import static com.jogamp.opengl.GL.GL_BLEND;
import static com.jogamp.opengl.GL.GL_COLOR_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_DEPTH_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_DEPTH_TEST;
import static com.jogamp.opengl.GL.GL_ONE_MINUS_SRC_ALPHA;
import static com.jogamp.opengl.GL.GL_SRC_ALPHA;

/**
 * Draw bounding box of source 0 timepoint 0.
 */
public class Example1 implements GLEventListener
{
	private final RandomAccessibleInterval< UnsignedShortType > rai;

	private final AffineTransform3D sourceTransform;

	private int vao;

	private Shader prog;

	private WireframeBox1 box;

	private ScreenPlane1 screenPlane;

	public Example1( final RandomAccessibleInterval< UnsignedShortType > rai, final AffineTransform3D sourceTransform )
	{
		this.rai = rai;
		this.sourceTransform = sourceTransform;
	}

	@Override
	public void init( final GLAutoDrawable drawable )
	{
		final GL gl = drawable.getGL();
		final GL3 gl3 = drawable.getGL().getGL3();

		box = new WireframeBox1();
		box.updateVertices( gl3, rai );
		screenPlane = new ScreenPlane1();
		screenPlane.updateVertices( gl3, new FinalInterval( 640, 480 ) );

		prog = new Shader( gl3, "ex1", "ex1" );
		gl3.glEnable( GL_DEPTH_TEST );
	}

	@Override
	public void dispose( final GLAutoDrawable drawable )
	{}

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

		gl.glClearColor( 0f, 0f, 0f, 1f );
		gl.glClear( GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT );

		final Matrix4f model = new Matrix4f();
		final Matrix4f view = new Matrix4f();
		final Matrix4f projection = new Matrix4f();
		MatrixMath.affine( sourceTransform, model );
		view.set( new float[] {0.56280f, -0.13956f, 0.23033f, 0.00000f, 0.00395f, 0.53783f, 0.31621f, 0.00000f, -0.26928f, -0.28378f, 0.48603f, 0.00000f, 96.02715f, 211.68768f, -186.46806f, 1.00000f } );
		projection.set( new float[] {5.40541f, 0.00000f, 0.00000f, 0.00000f, -0.00000f, -6.89655f, -0.00000f, -0.00000f, -0.00000f, -0.00000f, 2.00000f, 1.00000f, -1729.72974f, 1655.17236f, 1000.00000f, 2000.00000f } );

		prog.use( gl3 );
		prog.setUniform( gl3, "model", model );
		prog.setUniform( gl3, "view", view );
		prog.setUniform( gl3, "projection", projection );

		prog.setUniform( gl3, "color", 1.0f, 0.5f, 0.2f, 1.0f );
		box.draw( gl3 );

		model.identity();
		view.identity();
		prog.setUniform( gl3, "model", model );
		prog.setUniform( gl3, "view", view );
		prog.setUniform( gl3, "color", 0.2f, 0.3f, 0.3f, 0.6f );
		gl3.glEnable( GL_BLEND );
		gl3.glBlendFunc( GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA );
		screenPlane.draw( gl3 );
		gl3.glDisable( GL_BLEND );
	}

	@Override
	public void reshape( final GLAutoDrawable drawable, final int x, final int y, final int width, final int height )
	{}

	public static void main( final String[] args ) throws SpimDataException
	{
		final String xmlFilename = "/Users/pietzsch/workspace/data/111010_weber_full.xml";
		final SpimDataMinimal spimData = new XmlIoSpimDataMinimal().load( xmlFilename );
		MultiResolutionSetupImgLoader< UnsignedShortType > sil = ( MultiResolutionSetupImgLoader< UnsignedShortType > ) spimData.getSequenceDescription().getImgLoader().getSetupImgLoader( 0 );
		final int level = 0; //source.getNumMipmapLevels() - 1;
		final RandomAccessibleInterval< UnsignedShortType > rai = sil.getImage( 1, level );
		final AffineTransform3D sourceTransform = spimData.getViewRegistrations().getViewRegistration( 1, 0 ).getModel();

		final InputFrame frame = new InputFrame( "Example5", 640, 480 );
		InputFrame.DEBUG = false;
		Example1 glPainter = new Example1( rai, sourceTransform );
		frame.setGlEventListener( glPainter );
		frame.show();
	}
}
