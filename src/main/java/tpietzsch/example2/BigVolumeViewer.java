package tpietzsch.example2;

import java.io.File;
import mpicbg.spim.data.SpimDataException;
import net.imagej.ImageJ;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@Plugin(type = Command.class, menuPath = "Plugins>BigDataViewer>Volume Rendering Tech Demo")
public class BigVolumeViewer implements Command
{

	@Parameter(label="Select an image file", style="extensions:xml")
	private File xmlFile = new File( "/Users/pietzsch/workspace/data/111010_weber_full.xml" );

	@Parameter
	private int windowWidth = 640;

	@Parameter
	private int windowHeight = 480;

	@Parameter
	private int renderWidth = 512;

	@Parameter
	private int renderHeight = 512;

	@Parameter( choices = { "none", "2x2", "4x4", "8x8" } )
	private String dithering = "none";

	@Parameter(min="1", max="8", style="slider")
	private int numDitherSamples = 8;

	@Parameter
	private int cacheBlockSize = 32;

	@Override
	public void run()
	{
		final String xmlFilename = "/Users/pietzsch/workspace/data/111010_weber_full.xml";
//		final String xmlFilename = "/Users/pietzsch/Desktop/data/TGMM_METTE/Pdu_H2BeGFP_CAAXmCherry_0123_20130312_192018.corrected/dataset_hdf5.xml";
//		final String xmlFilename = "/Users/pietzsch/Desktop/data/MAMUT/MaMuT_demo_dataset/MaMuT_Parhyale_demo.xml";

		final int ditherWidth;
		switch ( dithering )
		{
		case "none":
		default:
			ditherWidth = 1;
			break;
		case "2x2":
			ditherWidth = 2;
			break;
		case "4x4":
			ditherWidth = 4;
			break;
		case "8x8":
			ditherWidth = 8;
			break;
		}

		try
		{
			Example9.run( xmlFilename, windowWidth, windowHeight, renderWidth, renderHeight, ditherWidth, numDitherSamples, cacheBlockSize );
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
