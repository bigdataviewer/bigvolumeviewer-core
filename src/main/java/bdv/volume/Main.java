package bdv.volume;

import static bdv.volume.FindRequiredBlocks.getRequiredBlocksParallelProjection;

import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Actions;

import bdv.BigDataViewer;
import bdv.export.ProgressWriterConsole;
import bdv.viewer.Source;
import bdv.viewer.ViewerOptions;
import bdv.viewer.ViewerPanel;
import bdv.viewer.state.ViewerState;
import mpicbg.spim.data.SpimDataException;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedByteType;

public class Main
{
	private final BigDataViewer bdv;

	private final int[] blockSize;

	public Main( final BigDataViewer bdv )
	{
		this.bdv = bdv;

		blockSize = new int[] { 64, 64, 64 };

		setupVolumeRendering( bdv );
	}

	void render()
	{
		System.out.println( "\nrender\n" );

		final ViewerPanel viewer = bdv.getViewer();

		final int w = viewer.getDisplay().getWidth();
		final int h = viewer.getDisplay().getHeight();
		final int dd = 100;
		System.out.println( "w, h, dd = " + w + ", " + h + ", " + dd );
		System.out.println( "blockSize = " + blockSize[ 0 ] + ", " + blockSize[ 1 ] + ", " + blockSize[ 2 ] );

		final ViewerState state = viewer.getState();
		final int si = state.getCurrentSource();
		final int timepoint = state.getCurrentTimepoint();
		final AffineTransform3D globalToViewer = new AffineTransform3D();
		state.getViewerTransform( globalToViewer );

		@SuppressWarnings( "unchecked" )
		final Source< UnsignedByteType > source = ( Source< UnsignedByteType > )
				state.getSources().get( si ).getSpimSource();

		final AffineTransform3D sourceToScreen = new AffineTransform3D();

		final int numLevels = source.getNumMipmapLevels();
		System.out.println( "numLevels = " + numLevels );

		for ( int level = 0; level < numLevels; ++level )
		{
			System.out.println( "\nlevel = " + level );
			source.getSourceTransform( timepoint, level, sourceToScreen );
			sourceToScreen.preConcatenate( globalToViewer );

			final long[] sourceSize = new long[ 3 ];
			source.getSource( timepoint, level ).dimensions( sourceSize );
			System.out.println( "sourceSize = " + sourceSize[ 0 ] + ", " + sourceSize[ 1 ] + ", " + sourceSize[ 2 ] );

			final RequiredBlocks required = getRequiredBlocksParallelProjection(
					sourceToScreen, w, h, dd, blockSize, sourceSize );
			System.out.println( required );
		}
	}

	void setupVolumeRendering( final BigDataViewer bdv )
	{
		final Actions actions = new Actions( new InputTriggerConfig(), "all" );
		actions.install( bdv.getViewerFrame().getKeybindings(), "volume" );
		actions.runnableAction( this::render, "Render", "R" );
	}




	public static void main( final String[] args ) throws SpimDataException
	{
		final String fn = "/Users/pietzsch/workspace/data/111010_weber_full.xml";

		System.setProperty( "apple.laf.useScreenMenuBar", "true" );
		final BigDataViewer bdv = BigDataViewer.open(
				fn,
				"bdv",
				new ProgressWriterConsole(),
				ViewerOptions.options() );

		new Main( bdv );
	}
}
