package tpietzsch.example2;

import java.io.File;
import mpicbg.spim.data.SpimDataException;
import net.imagej.ImageJ;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@Plugin(type = Command.class, menuPath = "Plugins>BigDataViewer>Volume Rendering Tech Demo")
public class BigVolumeViewerCommand implements Command
{

	@Parameter( label = "Select a BDV xml file", style = "extensions:xml" )
	private File xmlFile = new File( "/Users/pietzsch/workspace/data/111010_weber_full.xml" );

	@Parameter( label = "Window width" )
	private int windowWidth = 640;

	@Parameter( label = "Window height" )
	private int windowHeight = 480;

	@Parameter( label = "Render width" )
	private int renderWidth = 512;

	@Parameter( label = "Render height" )
	private int renderHeight = 512;

	@Parameter( label = "Dither window size",
			choices = { "none (always render full resolution)", "2x2", "3x3", "4x4", "5x5", "6x6", "7x7", "8x8" } )
	private String dithering = "3x3";

	@Parameter( label = "Number of dither samples",
			description = "Pixels are interpolated from this many nearest neighbors when dithering. This is not very expensive, it's fine to turn it up to 8.",
			min="1",
			max="8",
			style="slider")
	private int numDitherSamples = 8;

	@Parameter( label = "GPU cache tile size" )
	private int cacheBlockSize = 32;

	@Parameter( label = "GPU cache size (in MB)",
				description = "The size of the GPU cache texture will match this as close as possible with the given tile size." )
	private int maxCacheSizeInMB = 300;

	@Parameter( label = "Camera distance",
				description = "Distance from camera to z=0 plane. In units of pixel width." )
	private double dCam = 2000;

	@Parameter( label = "Clip distance",
	description = "Visible depth away from z=0 in both directions. In units of pixel width. MUST BE SMALLER THAN CAMERA DISTANCE!")
	private double dClip = 1000;

	@Override
	public void run()
	{
		final String xmlFilename = xmlFile.getAbsolutePath();
		//"/Users/pietzsch/workspace/data/111010_weber_full.xml";
//		final String xmlFilename = "/Users/pietzsch/Desktop/data/TGMM_METTE/Pdu_H2BeGFP_CAAXmCherry_0123_20130312_192018.corrected/dataset_hdf5.xml";
//		final String xmlFilename = "/Users/pietzsch/Desktop/data/MAMUT/MaMuT_demo_dataset/MaMuT_Parhyale_demo.xml";

		final int ditherWidth;
		switch ( dithering )
		{
		case "none (always render full resolution)":
		default:
			ditherWidth = 1;
			break;
		case "2x2":
			ditherWidth = 2;
			break;
		case "3x3":
			ditherWidth = 3;
			break;
		case "4x4":
			ditherWidth = 4;
			break;
		case "5x5":
			ditherWidth = 5;
			break;
		case "6x6":
			ditherWidth = 6;
			break;
		case "7x7":
			ditherWidth = 7;
			break;
		case "8x8":
			ditherWidth = 8;
			break;
		}

		try
		{
			BigVolumeViewer.run( xmlFilename, windowWidth, windowHeight, renderWidth, renderHeight, ditherWidth, numDitherSamples, cacheBlockSize, maxCacheSizeInMB, dCam, dClip );
		}
		catch ( SpimDataException e )
		{
			throw new RuntimeException( e );
		}
	}

	public static void main( String[] args )
	{
		ImageJ ij = new ImageJ();
		ij.ui().showUI();
	}
}
