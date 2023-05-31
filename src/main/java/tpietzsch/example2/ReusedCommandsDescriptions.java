package tpietzsch.example2;

import java.util.stream.IntStream;
import org.scijava.plugin.Plugin;
import org.scijava.ui.behaviour.io.gui.CommandDescriptionProvider;
import org.scijava.ui.behaviour.io.gui.CommandDescriptions;

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

/**
 * CommandDescriptions for Actions/Behaviours re-used from BDV
 */
@Plugin( type = CommandDescriptionProvider.class )
public class ReusedCommandsDescriptions extends CommandDescriptionProvider
{
	public ReusedCommandsDescriptions()
	{
		super( KeyConfigScopes.BIGVOLUMEVIEWER, KeyConfigContexts.BIGVOLUMEVIEWER );
	}

	@Override
	public void getCommandDescriptions( final CommandDescriptions descriptions )
	{
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
