/*
 * #%L
 * BigDataViewer core classes with minimal dependencies.
 * %%
 * Copyright (C) 2012 - 2023 BigDataViewer developers.
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

import org.scijava.plugin.Plugin;
import org.scijava.ui.behaviour.io.gui.CommandDescriptionProvider;
import org.scijava.ui.behaviour.io.gui.CommandDescriptions;
import org.scijava.ui.behaviour.util.Actions;

import static bdv.BigDataViewerActions.BRIGHTNESS_SETTINGS;
import static bdv.BigDataViewerActions.BRIGHTNESS_SETTINGS_KEYS;
import static bdv.BigDataViewerActions.COLLAPSE_CARDS;
import static bdv.BigDataViewerActions.COLLAPSE_CARDS_KEYS;
import static bdv.BigDataViewerActions.EXPAND_CARDS;
import static bdv.BigDataViewerActions.EXPAND_CARDS_KEYS;
import static bdv.BigDataViewerActions.GO_TO_BOOKMARK;
import static bdv.BigDataViewerActions.GO_TO_BOOKMARK_KEYS;
import static bdv.BigDataViewerActions.GO_TO_BOOKMARK_ROTATION;
import static bdv.BigDataViewerActions.GO_TO_BOOKMARK_ROTATION_KEYS;
import static bdv.BigDataViewerActions.LOAD_SETTINGS;
import static bdv.BigDataViewerActions.LOAD_SETTINGS_KEYS;
import static bdv.BigDataViewerActions.MANUAL_TRANSFORM;
import static bdv.BigDataViewerActions.MANUAL_TRANSFORM_KEYS;
import static bdv.BigDataViewerActions.PREFERENCES_DIALOG;
import static bdv.BigDataViewerActions.PREFERENCES_DIALOG_KEYS;
import static bdv.BigDataViewerActions.SAVE_SETTINGS;
import static bdv.BigDataViewerActions.SAVE_SETTINGS_KEYS;
import static bdv.BigDataViewerActions.SET_BOOKMARK;
import static bdv.BigDataViewerActions.SET_BOOKMARK_KEYS;
import static bdv.BigDataViewerActions.bookmarks;
import static bdv.BigDataViewerActions.manualTransform;
import static bdv.BigDataViewerActions.toggleDialogAction;

public class BigVolumeViewerActions
{
	/*
	 * Command descriptions for commands re-used from {@link BigDataViewerActions}.
	 */
	@Plugin( type = CommandDescriptionProvider.class )
	public static class Descriptions extends CommandDescriptionProvider
	{
		public Descriptions()
		{
			super( KeyConfigScopes.BIGVOLUMEVIEWER, KeyConfigContexts.BIGVOLUMEVIEWER );
		}

		@Override
		public void getCommandDescriptions( final CommandDescriptions descriptions )
		{
			descriptions.add( BRIGHTNESS_SETTINGS, BRIGHTNESS_SETTINGS_KEYS, "Show the Brightness&Colors dialog." );
			descriptions.add( MANUAL_TRANSFORM, MANUAL_TRANSFORM_KEYS, "Toggle manual transformation mode." );
			descriptions.add( SAVE_SETTINGS, SAVE_SETTINGS_KEYS, "Save the BigDataViewer settings to a settings.xml file." );
			descriptions.add( LOAD_SETTINGS, LOAD_SETTINGS_KEYS, "Load the BigDataViewer settings from a settings.xml file." );
			descriptions.add( EXPAND_CARDS, EXPAND_CARDS_KEYS, "Expand and focus the BigDataViewer card panel" );
			descriptions.add( COLLAPSE_CARDS, COLLAPSE_CARDS_KEYS, "Collapse the BigDataViewer card panel" );
			descriptions.add( SET_BOOKMARK, SET_BOOKMARK_KEYS, "Set a labeled bookmark at the current location." );
			descriptions.add( GO_TO_BOOKMARK, GO_TO_BOOKMARK_KEYS, "Retrieve a labeled bookmark location." );
			descriptions.add( GO_TO_BOOKMARK_ROTATION, GO_TO_BOOKMARK_ROTATION_KEYS, "Retrieve a labeled bookmark, set only the orientation." );
			descriptions.add( PREFERENCES_DIALOG, PREFERENCES_DIALOG_KEYS, "Show the Preferences dialog." );
		}
	}

	/**
	 * Create BigVolumeViewer actions and install them in the specified
	 * {@link Actions}.
	 *
	 * @param actions
	 *            navigation actions are installed here.
	 * @param bvv
	 *            Actions are targeted at this {@link BigVolumeViewer}.
	 */
	public static void install( final Actions actions, final BigVolumeViewer bvv )
	{
		toggleDialogAction( actions, bvv.brightnessDialog, BRIGHTNESS_SETTINGS, BRIGHTNESS_SETTINGS_KEYS );
		toggleDialogAction( actions, bvv.preferencesDialog, PREFERENCES_DIALOG, PREFERENCES_DIALOG_KEYS );
		bookmarks( actions, bvv.bookmarkEditor );
		manualTransform( actions, bvv.manualTransformationEditor );
		actions.runnableAction( bvv::loadSettings, LOAD_SETTINGS, LOAD_SETTINGS_KEYS );
		actions.runnableAction( bvv::saveSettings, SAVE_SETTINGS, SAVE_SETTINGS_KEYS );
		actions.runnableAction( bvv::expandAndFocusCardPanel, EXPAND_CARDS, EXPAND_CARDS_KEYS );
		actions.runnableAction( bvv::collapseCardPanel, COLLAPSE_CARDS, COLLAPSE_CARDS_KEYS );
	}
}
