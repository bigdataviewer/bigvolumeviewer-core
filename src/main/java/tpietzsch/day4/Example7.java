package tpietzsch.day4;

import bdv.BigDataViewer;
import bdv.export.ProgressWriterConsole;
import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import bdv.tools.InitializeViewerState;
import bdv.viewer.Source;
import bdv.viewer.ViewerOptions;
import bdv.viewer.ViewerPanel;
import bdv.viewer.state.SourceState;
import bdv.viewer.state.ViewerState;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import mpicbg.spim.data.SpimDataException;
import net.imglib2.FinalInterval;
import net.imglib2.Interval;
import net.imglib2.realtransform.AffineTransform3D;
import org.joml.Matrix4f;
import tpietzsch.day2.Shader;
import tpietzsch.util.MatrixMath;
import tpietzsch.util.SourceIntervalAndTransform;
import tpietzsch.util.Syncd;

import static com.jogamp.opengl.GL.GL_BLEND;
import static com.jogamp.opengl.GL.GL_COLOR_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_DEPTH_BUFFER_BIT;
import static com.jogamp.opengl.GL.GL_DEPTH_TEST;
import static com.jogamp.opengl.GL.GL_ONE_MINUS_SRC_ALPHA;
import static com.jogamp.opengl.GL.GL_SRC_ALPHA;

/**
 * Perspective projection looking at BDV window (z=0 plane).
 * Interactive transform editing.
 */
public class Example7 implements GLEventListener
{
	private int vao;

	private Shader prog;

	private WireframeBox1 box;

	private ScreenPlane1 screenPlane;

	@Override
	public void init( final GLAutoDrawable drawable )
	{
		final GL gl = drawable.getGL();
		final GL3 gl3 = drawable.getGL().getGL3();

		box = new WireframeBox1();
		box.updateVertices( gl3, new FinalInterval( 800, 600, 300 ) );
		screenPlane = new ScreenPlane1();
		screenPlane.updateVertices( gl3, new FinalInterval( 640, 480 ) );

		prog = new Shader( gl3, "ex4", "ex3" );
		gl3.glEnable( GL_DEPTH_TEST );
	}

	@Override
	public void dispose( final GLAutoDrawable drawable )
	{}

	final Syncd< AffineTransform3D > worldToScreen = Syncd.affine3D();

	final Syncd< SourceIntervalAndTransform > currentSource = Syncd.intervalAndTransform();

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
		final Matrix4f view = MatrixMath.affine( worldToScreen.get(), new Matrix4f() );
		final Matrix4f projection = MatrixMath.screenPerspective( dCam, dClip, screenWidth, screenHeight, screenPadding, new Matrix4f() );

		prog.use( gl3 );
		prog.setUniform( gl3, "model", model );
		prog.setUniform( gl3, "view", view );
		prog.setUniform( gl3, "projection", projection );

		SourceIntervalAndTransform it = currentSource.get();
		if ( it.getSourceInterval() != null )
		{
			box.updateVertices( gl3, it.getSourceInterval() );
			MatrixMath.affine( it.getSourceTransform(), model.identity() );
			prog.setUniform( gl3, "model", model );
			prog.setUniform( gl3, "color", 1.0f, 0.5f, 0.2f, 1.0f );
			box.draw( gl3 );
		}

		model.identity();
		view.identity();
		prog.setUniform( gl3, "model", model );
		prog.setUniform( gl3, "view", view );
		prog.setUniform( gl3, "color", 0.2f, 0.3f, 0.3f, 0.6f );
		gl3.glEnable( GL_BLEND );
		gl3.glBlendFunc( GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA );
		screenPlane.updateVertices( gl3, new FinalInterval( ( int ) screenWidth, ( int ) screenHeight ) );
		screenPlane.draw( gl3 );
		gl3.glDisable( GL_BLEND );
	}

	@Override
	public void reshape( final GLAutoDrawable drawable, final int x, final int y, final int width, final int height )
	{}

	public static void main( final String[] args ) throws InterruptedException, SpimDataException
	{
		System.setProperty( "apple.laf.useScreenMenuBar", "true" );

		final InputFrame frame = new InputFrame( "Example7", 640, 480 );
		InputFrame.DEBUG = false;
		Example7 glPainter = new Example7();
		frame.setGlEventListener( glPainter );
		final TransformHandler tf = frame.setupDefaultTransformHandler( glPainter.worldToScreen::set );
		frame.getDefaultActions().runnableAction( () -> {
			tf.setTransform( new AffineTransform3D() );
		}, "reset transform", "R" );
		frame.getCanvas().addComponentListener( new ComponentAdapter()
		{
			@Override
			public void componentResized( final ComponentEvent e )
			{
				final int w = frame.getCanvas().getWidth();
				final int h = frame.getCanvas().getHeight();
				tf.setCanvasSize( w, h, true );
				glPainter.screenWidth = w;
				glPainter.screenHeight = h;
				frame.requestRepaint();
			}
		} );
		frame.show();
		frame.requestRepaint();

		final String xmlFilename = "/Users/pietzsch/workspace/data/111010_weber_full.xml";
		final SpimDataMinimal spimData = new XmlIoSpimDataMinimal().load( xmlFilename );
		final BigDataViewer bdv = BigDataViewer.open( spimData, "bdv", new ProgressWriterConsole(), ViewerOptions.options() );
		final ViewerPanel viewer = bdv.getViewerFrame().getViewerPanel();
		if ( !bdv.tryLoadSettings( xmlFilename ) )
			InitializeViewerState.initBrightness( 0.001, 0.999, viewer, bdv.getSetupAssignments() );

		viewer.addTransformListener( t -> {
//			glPainter.worldToScreen.set( t );
			tf.setTransform( t );
			final ViewerState state = viewer.getState();
			final int currentSource = state.getCurrentSource();
			final int currentTimepoint = state.getCurrentTimepoint();
			final Source< ? > source = state.getSources().get( currentSource ).getSpimSource();
			final Interval sourceInterval = source.getSource( currentTimepoint, 0 );
			final AffineTransform3D sourceTransform = new AffineTransform3D();
			source.getSourceTransform( currentTimepoint, 0, sourceTransform );
			glPainter.currentSource.set( new SourceIntervalAndTransform( sourceInterval, sourceTransform ) );
			frame.requestRepaint();
		} );
	}
}
