package tpietzsch;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.Scanner;

import javax.swing.JFrame;

import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.util.GLArrayDataServer;
import com.jogamp.opengl.util.glsl.ShaderCode;
import com.jogamp.opengl.util.glsl.ShaderProgram;

import tpietzsch.util.GuiUtil;
import tpietzsch.util.PainterThread;

public class HelloTriangle extends GLCanvas implements GLEventListener
{
	private static String TITLE = "JOGL Setup (GLCanvas)";

	private static final int CANVAS_WIDTH = 640;

	private static final int CANVAS_HEIGHT = 480;

	private static JFrame frame;

	private static HelloTriangle canvas;

	/**
	 * Thread that triggers repainting of the display.
	 */
	private static PainterThread painterThread;

	public HelloTriangle( final GLCapabilities caps )
	{
		super( caps );
		addGLEventListener( this );
	}

	private static final String vsrc = "#version 330\n" +
			"\n" +
			"layout(location = 0) in vec4 position;\n" +
			"void main()\n" +
			"{\n" +
			"    gl_Position = position;\n" +
			"}";

	private static final String fsrc = "#version 330\n" +
			"\n" +
			"out vec4 outputColor;\n" +
			"void main()\n" +
			"{\n" +
			"   outputColor = vec4(1.0f, 1.0f, 1.0f, 0.5f);\n" +
			"}";

	private ShaderProgram prog;

	private GLArrayDataServer data;

	private final int[] VAOs = new int[1];

	@Override
	public void init( final GLAutoDrawable drawable )
	{
		final GL3 gl = drawable.getGL().getGL3();

//		final String vsrc;
//		final String fsrc;
//		try
//		{
//			vsrc = readFromStream( this.getClass().getResourceAsStream( "vertex.glsl" ) );
//			fsrc = readFromStream( this.getClass().getResourceAsStream( "fragment.glsl" ) );
//		}
//		catch ( final IOException e )
//		{
//			throw new RuntimeException( e );
//		}

		final ShaderCode vs = new ShaderCode( GL3.GL_VERTEX_SHADER, 1, new String[][]{ { vsrc } } );
		final ShaderCode fs = new ShaderCode( GL3.GL_FRAGMENT_SHADER, 1, new String[][]{ { fsrc } } );
		prog = new ShaderProgram();
		prog.add( vs );
		prog.add( fs );

		prog.link( gl, System.err );
		System.out.println( prog.program() );

		final float[] vertexPositions = new float[] {
				0.75f, 0.75f, 0.0f, 1.0f,
				0.75f, -0.75f, 0.0f, 1.0f,
				-0.75f, -0.75f, 0.0f, 1.0f,
				-0.75f, 0.2f, 0.0f, 1.0f,
				0.75f, -0.2f, 0.0f, 1.0f,
				-0.75f, -0.2f, 0.0f, 1.0f,
			};

		data = GLArrayDataServer.createGLSL( "name", 4, GL3.GL_FLOAT, false, 12, GL3.GL_STATIC_DRAW );
		data.put( FloatBuffer.wrap( vertexPositions ) );
		data.seal( gl, true );

		System.out.println( data );

//		gl.glGenVertexArrays( 1, IntBuffer.wrap( VAOs ) );
	}

	public static String readFromStream( final InputStream ins ) throws IOException
	{
		if ( ins == null ) { throw new IOException( "Could not read from stream." ); }
		final StringBuffer buffer = new StringBuffer();
		final Scanner scanner = new Scanner( ins );
		try
		{
			while ( scanner.hasNextLine() )
			{
				buffer.append( scanner.nextLine() + "\n" );
			}
		}
		finally
		{
			scanner.close();
		}

		return buffer.toString();
	}

	void createShader( final GL3 gl, final int shaderType, final String source ) throws IOException
	{
	}

	@Override
	public void dispose( final GLAutoDrawable drawable )
	{
		System.out.println( "dispose" );
	}

	@Override
	public void display( final GLAutoDrawable drawable )
	{
		System.out.println( "display" );
		final GL3 gl = drawable.getGL().getGL3();

		gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
		gl.glClear(GL3.GL_COLOR_BUFFER_BIT);

		prog.useProgram( gl, true );

		gl.glEnableVertexAttribArray( 0 );
		data.setLocation( 0 );
		data.bindBuffer( gl, true );
		gl.glVertexAttribPointer( data );

		gl.glEnable( GL3.GL_POLYGON_SMOOTH );
		gl.glHint( GL3.GL_POLYGON_SMOOTH_HINT, GL3.GL_NICEST );
		gl.glBlendFunc( GL3.GL_SRC_ALPHA, GL3.GL_ONE_MINUS_SRC_ALPHA );
		gl.glEnable( GL3.GL_BLEND );
		gl.glEnable( GL3.GL_MULTISAMPLE);

		gl.glDrawArrays( GL3.GL_TRIANGLES, 0, 6 );
		final GLU glu = new GLU();
		System.out.println( glu.gluErrorString( gl.glGetError() ) );

		gl.glFlush();
	}

	@Override
	public void reshape( final GLAutoDrawable drawable, final int x, final int y, final int width, final int height )
	{
		System.out.println( "reshape" );
		final GL3 gl = drawable.getGL().getGL3();
		gl.glViewport( 0, 0, width, height );
	}

	public static void main( final String[] args )
	{
		final GLProfile profile = GLProfile.getGL2GL3();
		final GLCapabilities caps = new GLCapabilities( profile );
		caps.setSampleBuffers( true );
		caps.setNumSamples( 8 );
		canvas = new HelloTriangle( caps );
		canvas.setPreferredSize( new Dimension( CANVAS_WIDTH, CANVAS_HEIGHT ) );

		final GraphicsConfiguration gc = GuiUtil.getSuitableGraphicsConfiguration( GuiUtil.ARGB_COLOR_MODEL );
//		final GraphicsConfiguration gc = GuiUtil.getSuitableGraphicsConfiguration( GuiUtil.RGB_COLOR_MODEL );
		frame = new JFrame( TITLE, gc );
		frame.getRootPane().setDoubleBuffered( true );
		final Container content = frame.getContentPane();
		content.add( canvas, BorderLayout.CENTER );
		frame.pack();
		frame.setDefaultCloseOperation( JFrame.DISPOSE_ON_CLOSE );
		frame.addWindowListener( new WindowAdapter()
		{
			@Override
			public void windowClosing( final WindowEvent e )
			{
				System.out.println( "closing" );
				painterThread.interrupt();
			}
		} );
		frame.setVisible( true );

		painterThread = new PainterThread( () ->
		{
			System.out.println( "paint" );
			canvas.repaint();
		});
		painterThread.start();
	}
}
