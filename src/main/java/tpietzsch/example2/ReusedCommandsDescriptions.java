package tpietzsch.example2;

import java.util.stream.IntStream;
import org.scijava.plugin.Plugin;
import org.scijava.ui.behaviour.io.gui.CommandDescriptionProvider;
import org.scijava.ui.behaviour.io.gui.CommandDescriptions;

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

	}
}
