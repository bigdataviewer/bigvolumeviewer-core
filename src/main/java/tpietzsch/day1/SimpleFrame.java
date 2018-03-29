package tpietzsch.day1;

import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import tpietzsch.util.PainterThread;

public class SimpleFrame
{
	public static boolean DEBUG = true;

	private final GLEventListener glEventListener;

	private final PainterThread painterThread;

	private final GLCanvas canvas;

	private final GLEventListener wrapper = new GLEventListener()
	{
		@Override
		public void init( final GLAutoDrawable drawable )
		{
			dbgln( "init" );
			glEventListener.init( drawable );
		}

		@Override
		public void dispose( final GLAutoDrawable drawable )
		{
			dbgln( "dispose" );
			glEventListener.dispose( drawable );
		}

		@Override
		public void display( final GLAutoDrawable drawable )
		{
			dbgln( "display" );
			glEventListener.display( drawable );
		}

		@Override
		public void reshape( final GLAutoDrawable drawable, final int x, final int y, final int width, final int height )
		{
			dbgln( "reshape" );
			glEventListener.reshape( drawable, x, y, width, height );
		}
	};

	public SimpleFrame(
			String title,
			int width,
			int height,
			GLEventListener glEventListener )
	{
		this.glEventListener = glEventListener;

		final GLCapabilities capsReqUser = new GLCapabilities( GLProfile.getGL2GL3() );
		canvas = new GLCanvas( capsReqUser );
		canvas.addGLEventListener( wrapper );
		canvas.setPreferredSize( new Dimension( width, height ) );

		painterThread = new PainterThread( canvas::repaint );

//		final GraphicsConfiguration gc = GuiUtil.getSuitableGraphicsConfiguration( GuiUtil.ARGB_COLOR_MODEL );
//		final GraphicsConfiguration gc = GuiUtil.getSuitableGraphicsConfiguration( GuiUtil.RGB_COLOR_MODEL );
//		final JFrame frame = new JFrame( title, gc );
		final JFrame frame = new JFrame( title );
		frame.getRootPane().setDoubleBuffered( true );
		frame.getContentPane().add( canvas, BorderLayout.CENTER );
		frame.pack();
		frame.setDefaultCloseOperation( JFrame.DISPOSE_ON_CLOSE );
		frame.addWindowListener( new WindowAdapter()
		{
			@Override
			public void windowClosing( final WindowEvent e )
			{
				dbgln( "stopping painterThread..." );
				painterThread.interrupt();
			}
		} );
		frame.setVisible( true );
		painterThread.start();
	}

	public static void dbgln( String string )
	{
		if ( DEBUG )
			System.out.println( string );
	}
}
