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
package bvv.core.example2;

import java.util.stream.IntStream;
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
import static bdv.TransformEventHandler3D.DRAG_ROTATE;
import static bdv.TransformEventHandler3D.DRAG_ROTATE_FAST;
import static bdv.TransformEventHandler3D.DRAG_ROTATE_FAST_KEYS;
import static bdv.TransformEventHandler3D.DRAG_ROTATE_KEYS;
import static bdv.TransformEventHandler3D.DRAG_ROTATE_SLOW;
import static bdv.TransformEventHandler3D.DRAG_ROTATE_SLOW_KEYS;
import static bdv.TransformEventHandler3D.DRAG_TRANSLATE;
import static bdv.TransformEventHandler3D.DRAG_TRANSLATE_KEYS;
import static bdv.TransformEventHandler3D.KEY_BACKWARD_Z;
import static bdv.TransformEventHandler3D.KEY_BACKWARD_Z_FAST;
import static bdv.TransformEventHandler3D.KEY_BACKWARD_Z_FAST_KEYS;
import static bdv.TransformEventHandler3D.KEY_BACKWARD_Z_KEYS;
import static bdv.TransformEventHandler3D.KEY_BACKWARD_Z_SLOW;
import static bdv.TransformEventHandler3D.KEY_BACKWARD_Z_SLOW_KEYS;
import static bdv.TransformEventHandler3D.KEY_FORWARD_Z;
import static bdv.TransformEventHandler3D.KEY_FORWARD_Z_FAST;
import static bdv.TransformEventHandler3D.KEY_FORWARD_Z_FAST_KEYS;
import static bdv.TransformEventHandler3D.KEY_FORWARD_Z_KEYS;
import static bdv.TransformEventHandler3D.KEY_FORWARD_Z_SLOW;
import static bdv.TransformEventHandler3D.KEY_FORWARD_Z_SLOW_KEYS;
import static bdv.TransformEventHandler3D.KEY_ZOOM_IN;
import static bdv.TransformEventHandler3D.KEY_ZOOM_IN_FAST;
import static bdv.TransformEventHandler3D.KEY_ZOOM_IN_FAST_KEYS;
import static bdv.TransformEventHandler3D.KEY_ZOOM_IN_KEYS;
import static bdv.TransformEventHandler3D.KEY_ZOOM_IN_SLOW;
import static bdv.TransformEventHandler3D.KEY_ZOOM_IN_SLOW_KEYS;
import static bdv.TransformEventHandler3D.KEY_ZOOM_OUT;
import static bdv.TransformEventHandler3D.KEY_ZOOM_OUT_FAST;
import static bdv.TransformEventHandler3D.KEY_ZOOM_OUT_FAST_KEYS;
import static bdv.TransformEventHandler3D.KEY_ZOOM_OUT_KEYS;
import static bdv.TransformEventHandler3D.KEY_ZOOM_OUT_SLOW;
import static bdv.TransformEventHandler3D.KEY_ZOOM_OUT_SLOW_KEYS;
import static bdv.TransformEventHandler3D.ROTATE_LEFT;
import static bdv.TransformEventHandler3D.ROTATE_LEFT_FAST;
import static bdv.TransformEventHandler3D.ROTATE_LEFT_FAST_KEYS;
import static bdv.TransformEventHandler3D.ROTATE_LEFT_KEYS;
import static bdv.TransformEventHandler3D.ROTATE_LEFT_SLOW;
import static bdv.TransformEventHandler3D.ROTATE_LEFT_SLOW_KEYS;
import static bdv.TransformEventHandler3D.ROTATE_RIGHT;
import static bdv.TransformEventHandler3D.ROTATE_RIGHT_FAST;
import static bdv.TransformEventHandler3D.ROTATE_RIGHT_FAST_KEYS;
import static bdv.TransformEventHandler3D.ROTATE_RIGHT_KEYS;
import static bdv.TransformEventHandler3D.ROTATE_RIGHT_SLOW;
import static bdv.TransformEventHandler3D.ROTATE_RIGHT_SLOW_KEYS;
import static bdv.TransformEventHandler3D.SCROLL_Z;
import static bdv.TransformEventHandler3D.SCROLL_Z_FAST;
import static bdv.TransformEventHandler3D.SCROLL_Z_FAST_KEYS;
import static bdv.TransformEventHandler3D.SCROLL_Z_KEYS;
import static bdv.TransformEventHandler3D.SCROLL_Z_SLOW;
import static bdv.TransformEventHandler3D.SCROLL_Z_SLOW_KEYS;
import static bdv.TransformEventHandler3D.SELECT_AXIS_X;
import static bdv.TransformEventHandler3D.SELECT_AXIS_X_KEYS;
import static bdv.TransformEventHandler3D.SELECT_AXIS_Y;
import static bdv.TransformEventHandler3D.SELECT_AXIS_Y_KEYS;
import static bdv.TransformEventHandler3D.SELECT_AXIS_Z;
import static bdv.TransformEventHandler3D.SELECT_AXIS_Z_KEYS;
import static bdv.TransformEventHandler3D.ZOOM_NORMAL;
import static bdv.TransformEventHandler3D.ZOOM_NORMAL_KEYS;
import static bdv.tools.CloseWindowActions.CLOSE_DIALOG;
import static bdv.tools.CloseWindowActions.CLOSE_DIALOG_KEYS;
import static bdv.tools.CloseWindowActions.CLOSE_WINDOW;
import static bdv.tools.CloseWindowActions.CLOSE_WINDOW_KEYS;
import static bdv.viewer.NavigationActions.ALIGN_XY_PLANE;
import static bdv.viewer.NavigationActions.ALIGN_XY_PLANE_KEYS;
import static bdv.viewer.NavigationActions.ALIGN_XZ_PLANE;
import static bdv.viewer.NavigationActions.ALIGN_XZ_PLANE_KEYS;
import static bdv.viewer.NavigationActions.ALIGN_ZY_PLANE;
import static bdv.viewer.NavigationActions.ALIGN_ZY_PLANE_KEYS;
import static bdv.viewer.NavigationActions.NEXT_TIMEPOINT;
import static bdv.viewer.NavigationActions.NEXT_TIMEPOINT_KEYS;
import static bdv.viewer.NavigationActions.PREVIOUS_TIMEPOINT;
import static bdv.viewer.NavigationActions.PREVIOUS_TIMEPOINT_KEYS;
import static bdv.viewer.NavigationActions.SET_CURRENT_SOURCE;
import static bdv.viewer.NavigationActions.SET_CURRENT_SOURCE_KEYS_FORMAT;
import static bdv.viewer.NavigationActions.TOGGLE_FUSED_MODE;
import static bdv.viewer.NavigationActions.TOGGLE_FUSED_MODE_KEYS;
import static bdv.viewer.NavigationActions.TOGGLE_GROUPING;
import static bdv.viewer.NavigationActions.TOGGLE_GROUPING_KEYS;
import static bdv.viewer.NavigationActions.TOGGLE_INTERPOLATION;
import static bdv.viewer.NavigationActions.TOGGLE_SOURCE_VISIBILITY;
import static bdv.viewer.NavigationActions.TOGGLE_SOURCE_VISIBILITY_KEYS_FORMAT;

public class BigVolumeViewerActions
{
	/**
	 * Command descriptions for commands re-used from {@link
	 * BigVolumeViewerActions}, {@link bdv.tools.CloseWindowActions}, {@link
	 * bdv.viewer.NavigationActions}, {@link bdv.TransformEventHandler3D}.
	 * <p>
	 * The descriptions are re-declared here with scope {@link
	 * KeyConfigScopes#BIGVOLUMEVIEWER}.
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
			// Commands re-used from bdv.BigDataViewerActions
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



			// Commands re-used from bdv.tools.CloseWindowActions
			descriptions.add( CLOSE_WINDOW, CLOSE_WINDOW_KEYS, "Close the active window." );
			descriptions.add( CLOSE_DIALOG, CLOSE_DIALOG_KEYS, "Close the active dialog." );



			// Commands re-used from bdv.viewer.NavigationActions
			descriptions.add( TOGGLE_INTERPOLATION, new String[] { "not mapped" }, "Switch between nearest-neighbor and n-linear interpolation mode in BigDataViewer." );
			descriptions.add( TOGGLE_FUSED_MODE, TOGGLE_FUSED_MODE_KEYS, "TODO" );
			descriptions.add( TOGGLE_GROUPING, TOGGLE_GROUPING_KEYS, "TODO" );

			final String[] numkeys = new String[] { "1", "2", "3", "4", "5", "6", "7", "8", "9", "0" };
			IntStream.range( 0, numkeys.length ).forEach( i -> {
				descriptions.add( String.format( SET_CURRENT_SOURCE, i ), new String[] { String.format( SET_CURRENT_SOURCE_KEYS_FORMAT, numkeys[ i ] ) }, "TODO" );
				descriptions.add( String.format( TOGGLE_SOURCE_VISIBILITY, i ), new String[] { String.format( TOGGLE_SOURCE_VISIBILITY_KEYS_FORMAT, numkeys[ i ] ) }, "TODO" );
			} );

			descriptions.add( NEXT_TIMEPOINT, NEXT_TIMEPOINT_KEYS, "TODO" );
			descriptions.add( PREVIOUS_TIMEPOINT, PREVIOUS_TIMEPOINT_KEYS, "TODO" );
			descriptions.add( ALIGN_XY_PLANE, ALIGN_XY_PLANE_KEYS, "TODO" );
			descriptions.add( ALIGN_ZY_PLANE, ALIGN_ZY_PLANE_KEYS, "TODO" );
			descriptions.add( ALIGN_XZ_PLANE, ALIGN_XZ_PLANE_KEYS, "TODO" );



			// Commands re-used from bdv.TransformEventHandler3D
			descriptions.add( DRAG_TRANSLATE, DRAG_TRANSLATE_KEYS, "Pan the view by mouse-dragging." );
			descriptions.add( ZOOM_NORMAL, ZOOM_NORMAL_KEYS, "Zoom in by scrolling." );
			descriptions.add( SELECT_AXIS_X, SELECT_AXIS_X_KEYS, "Select X as the rotation axis for keyboard rotation." );
			descriptions.add( SELECT_AXIS_Y, SELECT_AXIS_Y_KEYS, "Select Y as the rotation axis for keyboard rotation." );
			descriptions.add( SELECT_AXIS_Z, SELECT_AXIS_Z_KEYS, "Select Z as the rotation axis for keyboard rotation." );

			descriptions.add( DRAG_ROTATE, DRAG_ROTATE_KEYS, "Rotate the view by mouse-dragging." );
			descriptions.add( SCROLL_Z, SCROLL_Z_KEYS, "Translate in Z by scrolling." );
			descriptions.add( ROTATE_LEFT, ROTATE_LEFT_KEYS, "Rotate left (counter-clockwise) by 1 degree." );
			descriptions.add( ROTATE_RIGHT, ROTATE_RIGHT_KEYS, "Rotate right (clockwise) by 1 degree." );
			descriptions.add( KEY_ZOOM_IN, KEY_ZOOM_IN_KEYS, "Zoom in." );
			descriptions.add( KEY_ZOOM_OUT, KEY_ZOOM_OUT_KEYS, "Zoom out." );
			descriptions.add( KEY_FORWARD_Z, KEY_FORWARD_Z_KEYS, "Translate forward in Z." );
			descriptions.add( KEY_BACKWARD_Z, KEY_BACKWARD_Z_KEYS, "Translate backward in Z." );

			descriptions.add( DRAG_ROTATE_FAST, DRAG_ROTATE_FAST_KEYS, "Rotate the view by mouse-dragging (fast)." );
			descriptions.add( SCROLL_Z_FAST, SCROLL_Z_FAST_KEYS, "Translate in Z by scrolling (fast)." );
			descriptions.add( ROTATE_LEFT_FAST, ROTATE_LEFT_FAST_KEYS, "Rotate left (counter-clockwise) by 10 degrees." );
			descriptions.add( ROTATE_RIGHT_FAST, ROTATE_RIGHT_FAST_KEYS, "Rotate right (clockwise) by 10 degrees." );
			descriptions.add( KEY_ZOOM_IN_FAST, KEY_ZOOM_IN_FAST_KEYS, "Zoom in (fast)." );
			descriptions.add( KEY_ZOOM_OUT_FAST, KEY_ZOOM_OUT_FAST_KEYS, "Zoom out (fast)." );
			descriptions.add( KEY_FORWARD_Z_FAST, KEY_FORWARD_Z_FAST_KEYS, "Translate forward in Z (fast)." );
			descriptions.add( KEY_BACKWARD_Z_FAST, KEY_BACKWARD_Z_FAST_KEYS, "Translate backward in Z (fast)." );

			descriptions.add( DRAG_ROTATE_SLOW, DRAG_ROTATE_SLOW_KEYS, "Rotate the view by mouse-dragging (slow)." );
			descriptions.add( SCROLL_Z_SLOW, SCROLL_Z_SLOW_KEYS, "Translate in Z by scrolling (slow)." );
			descriptions.add( ROTATE_LEFT_SLOW, ROTATE_LEFT_SLOW_KEYS, "Rotate left (counter-clockwise) by 0.1 degree." );
			descriptions.add( ROTATE_RIGHT_SLOW, ROTATE_RIGHT_SLOW_KEYS, "Rotate right (clockwise) by 0.1 degree." );
			descriptions.add( KEY_ZOOM_IN_SLOW, KEY_ZOOM_IN_SLOW_KEYS, "Zoom in (slow)." );
			descriptions.add( KEY_ZOOM_OUT_SLOW, KEY_ZOOM_OUT_SLOW_KEYS, "Zoom out (slow)." );
			descriptions.add( KEY_FORWARD_Z_SLOW, KEY_FORWARD_Z_SLOW_KEYS, "Translate forward in Z (slow)." );
			descriptions.add( KEY_BACKWARD_Z_SLOW, KEY_BACKWARD_Z_SLOW_KEYS, "Translate backward in Z (slow)." );
		}
	}

	/**
	 * Create BigVolumeViewer actions and install them in the specified
	 * {@link Actions}.
	 * <p>
	 * Note that
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
