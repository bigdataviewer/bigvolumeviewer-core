package tpietzsch.dither;

import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import tpietzsch.backend.jogl.JoglGpuContext;
import tpietzsch.contextsharing.SimpleFrame;
import tpietzsch.offscreen.OffScreenFrameBuffer;
import tpietzsch.shadergen.DefaultShader;
import tpietzsch.shadergen.generate.Segment;
import tpietzsch.shadergen.generate.SegmentTemplate;
import tpietzsch.util.DefaultQuad;

import static com.jogamp.opengl.GL.GL_RGB8;

public class DitherExample implements GLEventListener
{
	private SimpleFrame frame;

	private final DefaultQuad quad;

	private final DefaultShader progRender;

	private final OffScreenFrameBuffer offscreen;

	private final DitherBuffer dither;

	public DitherExample( final int windowWidth, final int windowHeight )
	{
		quad = new DefaultQuad();

		final Segment ex1vp = new SegmentTemplate("circle.vp" ).instantiate();
		final Segment ex1fp = new SegmentTemplate("circle.fp" ).instantiate();
		progRender = new DefaultShader( ex1vp.getCode(), ex1fp.getCode() );

		dither = new DitherBuffer( windowWidth, windowHeight, 4, 9, 8 );
		offscreen = new OffScreenFrameBuffer( windowWidth, windowHeight, GL_RGB8 );
	}

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

	int timesDisplayCalled = 0;

	@Override
	public void display( final GLAutoDrawable drawable )
	{
		final GL3 gl = drawable.getGL().getGL3();
		final JoglGpuContext context = JoglGpuContext.get( gl );

		progRender.use( context );
		progRender.getUniform2f( "viewportSize" ).set( dither.effectiveViewportWidth(), dither.effectiveViewportHeight() );

		final int step = timesDisplayCalled % dither.numSteps();
		dither.bind( gl );
		progRender.getUniform2f( "dsp" ).set( dither.fragShift( step ) );
		progRender.getUniformMatrix4f( "transform" ).set( dither.ndcTransform( step ) );
		progRender.setUniforms( context );
		quad.draw( gl );
		dither.unbind( gl );

		offscreen.bind( gl );
		dither.dither( gl, step + 1, offscreen.getWidth(), offscreen.getHeight() );
//		if ( timesDisplayCalled % 2 == 0)
//			dither.getStitchBuffer().drawQuad( gl );
//		else
//			dither.getDitherBuffer().drawQuad( gl );
		offscreen.unbind( gl, false );
		offscreen.drawQuad( gl );

		++timesDisplayCalled;
//		frame.painterThread.requestRepaint();
	}

	public static void main( final String[] args )
	{
		SimpleFrame.DEBUG = false;
		final int windowWidth = 640;
		final int windowHeight = 480;
		final DitherExample listener = new DitherExample( windowWidth / 4 - 1, windowHeight / 4);
		listener.frame = new SimpleFrame( "DitherExample", windowWidth, windowHeight, listener );

		new Thread(() -> {
			try
			{
				while( true )
				{
					Thread.sleep( 500 );
					listener.frame.painterThread.requestRepaint();
				}
			}
			catch ( InterruptedException e )
			{
				e.printStackTrace();
			}
		} ).start();

	}
}
