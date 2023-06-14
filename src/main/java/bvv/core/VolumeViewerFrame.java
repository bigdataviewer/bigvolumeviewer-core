/*-
 * #%L
 * Volume rendering of bdv datasets
 * %%
 * Copyright (C) 2018 - 2023 Tobias Pietzsch
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
package bvv.core;

import bdv.TransformEventHandler;
import bdv.ui.BdvDefaultCards;
import bdv.ui.CardPanel;
import bdv.ui.appearance.AppearanceManager;
import bdv.ui.keymap.KeymapManager;
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
import org.scijava.ui.behaviour.util.Behaviours;
import org.scijava.ui.behaviour.util.InputActionBindings;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import bdv.cache.CacheControl;
import bdv.viewer.SourceAndConverter;

/**
 * A {@link JFrame} containing a {@link VolumeViewerPanel} and associated
 * {@link InputActionBindings}.
 *
 * @author Tobias Pietzsch
 */
public class VolumeViewerFrame extends JFrame
{
	private final VolumeViewerPanel viewer;

	private final CardPanel cards;

	private final SplitPanel splitPanel;

	private final InputActionBindings keybindings;

	private final TriggerBehaviourBindings triggerbindings;

	private final Behaviours transformBehaviours;

	private final KeymapManager keymapManager;

	private final AppearanceManager appearanceManager;

	public VolumeViewerFrame(
			final List< SourceAndConverter< ? > > sources,
			final int numTimepoints,
			final CacheControl cache,
			final VolumeViewerOptions optional )
	{
		this( sources, numTimepoints, cache, new KeymapManager( BigVolumeViewer.configDir ), new AppearanceManager( BigVolumeViewer.configDir ), optional );
	}

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
			final KeymapManager keymapManager,
			final AppearanceManager appearanceManager,
			final VolumeViewerOptions optional )
	{
		super( "BigVolumeViewer" );
		this.keymapManager = keymapManager;
		this.appearanceManager = appearanceManager;
		viewer = new VolumeViewerPanel( sources, numTimepoints, cacheControl, optional );

		keybindings = new InputActionBindings();
		triggerbindings = new TriggerBehaviourBindings();

		cards = new CardPanel();
		BdvDefaultCards.setup( cards, viewer, viewer.getConverterSetups() );
		splitPanel = new SplitPanel( viewer, cards );

		getRootPane().setDoubleBuffered( true );
//		add( viewer, BorderLayout.CENTER );
		add( splitPanel, BorderLayout.CENTER );
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

		// TODO: getRootPanel --> viewer ???
		SwingUtilities.replaceUIActionMap( getRootPane(), keybindings.getConcatenatedActionMap() );
		SwingUtilities.replaceUIInputMap( getRootPane(), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, keybindings.getConcatenatedInputMap() );

		final MouseAndKeyHandler mouseAndKeyHandler = new MouseAndKeyHandler();
		mouseAndKeyHandler.setInputMap( triggerbindings.getConcatenatedInputTriggerMap() );
		mouseAndKeyHandler.setBehaviourMap( triggerbindings.getConcatenatedBehaviourMap() );
		mouseAndKeyHandler.setKeypressManager( optional.values.getKeyPressedManager(), viewer.getDisplay().getComponent() );
		viewer.getDisplay().addHandler( mouseAndKeyHandler );

		transformBehaviours = new Behaviours( optional.values.getInputTriggerConfig(), "bdv" );
		transformBehaviours.install( triggerbindings, "transform" );

		final TransformEventHandler tfHandler = viewer.getTransformEventHandler();
		tfHandler.install( transformBehaviours );
	}

	public VolumeViewerPanel getViewerPanel()
	{
		return viewer;
	}

	public CardPanel getCardPanel()
	{
		return cards;
	}

	public SplitPanel getSplitPanel()
	{
		return splitPanel;
	}

	public InputActionBindings getKeybindings()
	{
		return keybindings;
	}

	public TriggerBehaviourBindings getTriggerbindings()
	{
		return triggerbindings;
	}

	/**
	 * Get {@code Behaviours} hook where TransformEventHandler behaviours are installed.
	 * This is installed in {@link #getTriggerbindings} with the id "transform".
	 * The hook can be used to update the keymap and install additional behaviours.
	 */
	public Behaviours getTransformBehaviours()
	{
		return transformBehaviours;
	}

	public ConverterSetups getConverterSetups()
	{
		return viewer.getConverterSetups();
	}
}
