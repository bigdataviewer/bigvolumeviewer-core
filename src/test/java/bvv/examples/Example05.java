package bvv.examples;

import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import bvv.util.BvvFunctions;
import bvv.util.BvvStackSource;
import java.util.List;
import mpicbg.spim.data.SpimDataException;
import net.imglib2.type.numeric.ARGBType;

public class Example05
{
	/**
	 * Show BDV multi-channel, -angle, etc datasets as cached multi-resolution stacks.
	 */
	public static void main( final String[] args ) throws SpimDataException
	{
		final String xmlFilename = "/Users/pietzsch/workspace/data/111010_weber_full.xml";
		final SpimDataMinimal spimData = new XmlIoSpimDataMinimal().load( xmlFilename );

		final List< BvvStackSource< ? > > sources = BvvFunctions.show( spimData );

		sources.get( 0 ).setDisplayRange( 0, 6000 );
		sources.get( 1 ).setDisplayRange( 0, 6000 );
		sources.get( 2 ).setDisplayRange( 0, 6000 );
		sources.get( 0 ).setColor( new ARGBType( 0xffff0000 ) );
		sources.get( 1 ).setColor( new ARGBType( 0xff00ff00 ) );
		sources.get( 2 ).setColor( new ARGBType( 0xff0000ff ) );
	}
}
