package bvv.debug;

import java.util.List;

import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedShortType;

import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import bvv.vistools.Bvv;
import bvv.vistools.BvvFunctions;
import bvv.vistools.BvvHandle;
import bvv.vistools.BvvStackSource;

import ij.IJ;
import ij.ImagePlus;
import mpicbg.spim.data.SpimDataException;

public class DebugHalfPixel
{
	public static void main( final String[] args )
	{
		//load multires
		final String xmlFilename = "src/test/resources/halfpixeltest/cliptest1ch.xml";
		SpimDataMinimal spimData = null;
		try {
			spimData = new XmlIoSpimDataMinimal().load( xmlFilename );
		} catch (SpimDataException e) {
			e.printStackTrace();
		}		
		List< BvvStackSource< ? > > sources = BvvFunctions.show( spimData );
		sources.get( 0 ).setColor( new ARGBType(0x0000FF00) );
		BvvHandle handle = sources.get( 0 ).getBvvHandle();
		
		//load simple stack
		final ImagePlus imp = IJ.openImage( "src/test/resources/halfpixeltest/cliptest1ch.tif" );
		final Img< UnsignedShortType > img = ImageJFunctions.wrapShort( imp );
		final BvvStackSource< UnsignedShortType > source = BvvFunctions.show( img, "SimpleStack", Bvv.options().addTo( handle ) );
		source.setColor( new ARGBType(0x00FF0000) );
	}
}
