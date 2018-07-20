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
	private File xmlFile;

	@Parameter
	private int windowWidth = 640;

	@Parameter
	private int windowHeight = 480;

	@Parameter
	private int renderWidth = 512;

	@Parameter
	private int renderHeight = 512;

	@Parameter
	private int ditherWidth = 8;

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

		final int windowWidth = 640;
		final int windowHeight = 480;
		final int renderWidth = 640;
		final int renderHeight = 480;
		final int ditherWidth = 8;
		final int numDitherSamples = 8;
		final int cacheBlockSize = 32;

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
