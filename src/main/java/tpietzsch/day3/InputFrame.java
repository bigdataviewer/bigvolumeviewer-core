package tpietzsch.day3;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.scijava.ui.behaviour.MouseAndKeyHandler;
import org.scijava.ui.behaviour.util.InputActionBindings;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;

import tpietzsch.util.PainterThread;

public class InputFrame
{
	public static boolean DEBUG = true;

	private final GLEventListener glEventListener;

	public final PainterThread painterThread;

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

	private final long t0;

	private final InputActionBindings keybindings;

	private final TriggerBehaviourBindings triggerbindings;

	public long dTimeMillis()
	{
		return System.currentTimeMillis() - t0;
	}

	public InputFrame(
			final String title,
			final int width,
			final int height,
			final GLEventListener glEventListener )
	{
		this.glEventListener = glEventListener;

		final GLCapabilities capsReqUser = new GLCapabilities( GLProfile.getGL2GL3() );
		canvas = new GLCanvas( capsReqUser );
		getCanvas().addGLEventListener( wrapper );
		getCanvas().setPreferredSize( new Dimension( width, height ) );

		painterThread = new PainterThread( getCanvas()::repaint );

		final JFrame frame = new JFrame( title );
		frame.getRootPane().setDoubleBuffered( true );
		frame.getContentPane().add( getCanvas(), BorderLayout.CENTER );
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

		keybindings = new InputActionBindings();
		triggerbindings = new TriggerBehaviourBindings();

		SwingUtilities.replaceUIActionMap( frame.getRootPane(), getKeybindings().getConcatenatedActionMap() );
		SwingUtilities.replaceUIInputMap( frame.getRootPane(), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, getKeybindings().getConcatenatedInputMap() );

		final MouseAndKeyHandler mouseAndKeyHandler = new MouseAndKeyHandler();
		mouseAndKeyHandler.setInputMap( getTriggerbindings().getConcatenatedInputTriggerMap() );
		mouseAndKeyHandler.setBehaviourMap( getTriggerbindings().getConcatenatedBehaviourMap() );

//		implements KeyListener, MouseListener, MouseWheelListener, MouseMotionListener, FocusListener
		getCanvas().addKeyListener( mouseAndKeyHandler );
		getCanvas().addMouseListener( mouseAndKeyHandler );
		getCanvas().addMouseWheelListener( mouseAndKeyHandler );
		getCanvas().addMouseMotionListener( mouseAndKeyHandler );
		getCanvas().addFocusListener( mouseAndKeyHandler );

		t0 = System.currentTimeMillis();
		frame.setVisible( true );
		painterThread.start();
	}

	public InputActionBindings getKeybindings()
	{
		return keybindings;
	}

	public TriggerBehaviourBindings getTriggerbindings()
	{
		return triggerbindings;
	}

	public GLCanvas getCanvas()
	{
		return canvas;
	}

	public static void dbgln( final String string )
	{
		if ( DEBUG )
			System.out.println( string );
	}
}
