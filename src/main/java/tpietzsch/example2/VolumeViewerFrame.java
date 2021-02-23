/*-
 * #%L
 * Volume rendering of bdv datasets
 * %%
 * Copyright (C) 2018 - 2021 Tobias Pietzsch
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package tpietzsch.example2;

import bdv.TransformEventHandler;
import bdv.ui.BdvDefaultCards;
import bdv.ui.CardPanel;
import bdv.ui.splitpanel.SplitPanel;
import bdv.viewer.ConverterSetups;
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

import bdv.cache.CacheControl;
import bdv.viewer.SourceAndConverter;
import tpietzsch.example2.VolumeViewerPanel.RenderScene;

public class VolumeViewerFrame extends JFrame
{
	private final VolumeViewerPanel viewer;

//	private final CardPanel cards;
//
//	private final SplitPanel splitPanel;

	private final InputActionBindings keybindings;

	private final TriggerBehaviourBindings triggerbindings;

	private final Actions actions;

	private final Behaviours behaviours;

	/**
	 *
	 * @param sources
	 *            the {@link SourceAndConverter sources} to display.
	 * @param numTimepoints
	 *            number of available timepoints.
	 * @param cacheControl
	 *            handle to cache. This is used to control io timing.
	 * @param optional
	 *            optional parameters. See {@link VolumeViewerOptions}.
	 */
	public VolumeViewerFrame(
			final List< SourceAndConverter< ? > > sources,
			final int numTimepoints,
			final CacheControl cacheControl,
			final RenderScene renderScene,
			final VolumeViewerOptions optional )
	{
		super( "BigVolumeViewer" );
		viewer = new VolumeViewerPanel( sources, numTimepoints, cacheControl, renderScene, optional );

		keybindings = new InputActionBindings();
		triggerbindings = new TriggerBehaviourBindings();

//		cards = new CardPanel();
//		BdvDefaultCards.setup( cards, viewer, setups ); // TODO
//		splitPanel = new SplitPanel( viewer, cards );

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
		viewer.getDisplay().addHandler( mouseAndKeyHandler );

		// TODO: should be a field?
		final Behaviours transformBehaviours = new Behaviours( optional.values.getInputTriggerConfig(), "bdv" );
		transformBehaviours.install( triggerbindings, "transform" );

		final TransformEventHandler tfHandler = viewer.getTransformEventHandler();
		tfHandler.install( transformBehaviours );
	}

	public VolumeViewerPanel getViewerPanel()
	{
		return viewer;
	}

//	public CardPanel getCardPanel()
//	{
//		return cards;
//	}
//
//	public SplitPanel getSplitPanel()
//	{
//		return splitPanel;
//	}

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

	public ConverterSetups getConverterSetups()
	{
		return viewer.getConverterSetups();
	}
}
