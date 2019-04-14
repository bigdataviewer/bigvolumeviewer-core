package tpietzsch.example2;

import bdv.tools.brightness.ConverterSetup;
import bdv.viewer.SourceAndConverter;
import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import org.scijava.ui.behaviour.MouseAndKeyHandler;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Actions;
import org.scijava.ui.behaviour.util.Behaviours;
import org.scijava.ui.behaviour.util.InputActionBindings;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;
import tpietzsch.example2.VolumeViewerPanel.RenderScene;
import tpietzsch.multires.Stacks;

public class VolumeViewerFrame extends JFrame
{
	private final VolumeViewerPanel viewer;

	private final InputActionBindings keybindings;

	private final TriggerBehaviourBindings triggerbindings;

	private final Actions actions;

	private final Behaviours behaviours;

	/**
	 *
	 * @param sources
	 *            the {@link SourceAndConverter sources} to display.
	 * @param optional
	 *            optional parameters. See {@link VolumeViewerOptions}.
	 */
	public VolumeViewerFrame(
			final List< SourceAndConverter< ? > > sources,
			final List< ? extends ConverterSetup > converterSetups,
			final Stacks stacks,
			final RenderScene renderScene,
			final VolumeViewerOptions optional )
	{
		super( "BigVolumeViewer" );

		viewer = new VolumeViewerPanel( sources, converterSetups, stacks, renderScene, optional );
		keybindings = new InputActionBindings();
		triggerbindings = new TriggerBehaviourBindings();

		final VolumeViewerOptions.Values options = optional.values;

		getRootPane().setDoubleBuffered( true );
		add( viewer, BorderLayout.CENTER );
		pack();
		setDefaultCloseOperation( WindowConstants.DISPOSE_ON_CLOSE );
		addWindowListener( new WindowAdapter()
		{
			@Override
			public void windowClosing( final WindowEvent e )
			{
				viewer.stop();
			}
		} );

		final InputTriggerConfig keyConfig = options.getInputTriggerConfig();
		actions = new Actions( keyConfig, "all" );
		actions.install( keybindings, "default" );
		behaviours = new Behaviours( keyConfig, "all" );
		behaviours.install( triggerbindings, "default" );

		SwingUtilities.replaceUIActionMap( getRootPane(), keybindings.getConcatenatedActionMap() );
		SwingUtilities.replaceUIInputMap( getRootPane(), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, keybindings.getConcatenatedInputMap() );

		final MouseAndKeyHandler mouseAndKeyHandler = new MouseAndKeyHandler();
		mouseAndKeyHandler.setInputMap( triggerbindings.getConcatenatedInputTriggerMap() );
		mouseAndKeyHandler.setBehaviourMap( triggerbindings.getConcatenatedBehaviourMap() );
		mouseAndKeyHandler.setKeypressManager( options.getKeyPressedManager(), viewer.getDisplay() );
		viewer.addHandlerToCanvas( mouseAndKeyHandler );

		final Behaviours behaviours = new Behaviours( keyConfig, "bdv" );
		behaviours.install( triggerbindings, "transform" );
		viewer.getTransformEventHandler().install( behaviours );
	}

	public VolumeViewerPanel getViewerPanel()
	{
		return viewer;
	}

	public Actions getDefaultActions()
	{
		return actions;
	}

	public Behaviours getDefaultBehaviours()
	{
		return behaviours;
	}

	public InputActionBindings getKeybindings()
	{
		return keybindings;
	}

	public TriggerBehaviourBindings getTriggerbindings()
	{
		return triggerbindings;
	}
}
