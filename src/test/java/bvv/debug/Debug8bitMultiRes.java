package bvv.debug;

import java.util.List;

import net.imglib2.type.numeric.ARGBType;

import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import bvv.vistools.BvvFunctions;
import bvv.vistools.BvvOptions;
import bvv.vistools.BvvStackSource;
import mpicbg.spim.data.SpimDataException;

public class Debug8bitMultiRes
{
	public static void main( final String[] args ) throws SpimDataException
	{
		final String xmlFilename8bit = "src/test/resources/bvv/debug/export_rand_8bit.xml";
		final SpimDataMinimal spimData8 = new XmlIoSpimDataMinimal().load( xmlFilename8bit );
		
		final String xmlFilename16bit = "src/test/resources/bvv/debug/export_rand_16bit.xml";
		final SpimDataMinimal spimData16 = new XmlIoSpimDataMinimal().load( xmlFilename16bit );

		final List< BvvStackSource< ? > > sources8 = BvvFunctions.show( spimData8 );
		final List< BvvStackSource< ? > > sources16 = BvvFunctions.show( spimData16, BvvOptions.options().addTo( sources8.get( 0 ).getBvvHandle() ));

		sources8.get( 0 ).setDisplayRange( 0, 255 );
		sources8.get( 0 ).setColor( new ARGBType( 0xff00ff00 ) );
		sources16.get( 0 ).setDisplayRange( 0, 255 );
		sources16.get( 0 ).setColor( new ARGBType( 0xffff0000 ) );
	}
}
