package tpietzsch.example2;

import bvv.util.Bvv;
import bvv.util.BvvFunctions;
import bvv.util.BvvSource;
import ij.IJ;
import ij.ImagePlus;
import mpicbg.spim.data.SpimDataException;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;

public class SmallVolumeExample
{
	public static void main( final String[] args ) throws SpimDataException
	{
//		final String xmlFilename = "/Users/pietzsch/workspace/data/111010_weber_full.xml";
//		final String xmlFilename = "/Users/pietzsch/Desktop/data/TGMM_METTE/Pdu_H2BeGFP_CAAXmCherry_0123_20130312_192018.corrected/dataset_hdf5.xml";
//		final String xmlFilename = "/Users/pietzsch/Desktop/data/MAMUT/MaMuT_demo_dataset/MaMuT_Parhyale_demo.xml";

//		final SpimDataMinimal spimData = new XmlIoSpimDataMinimal().load( xmlFilename );
//		final List< BvvStackSource< ? > > sources = BvvFunctions.show( spimData, Bvv.options().ditherWidth( 0 ) );
//		sources.get( 0 ).setDisplayRange( 0, 6000 );
//		sources.get( 1 ).setDisplayRange( 0, 6000 );
//		sources.get( 2 ).setDisplayRange( 0, 6000 );
//		sources.get( 0 ).setColor( new ARGBType( 0xffff0000 ) );
//		sources.get( 1 ).setColor( new ARGBType( 0xff00ff00 ) );
//		sources.get( 2 ).setColor( new ARGBType( 0xff0000ff ) );



//		final ImagePlus imp = IJ.openImage( "/Users/pietzsch/workspace/data/t1-head.tif" );
//		final Img< UnsignedShortType > img = ImageJFunctions.wrapShort( imp );
//		final BvvSource source = BvvFunctions.show( img, "t1-head" );
//
//		final ImagePlus imp2 = IJ.openImage( "/Users/pietzsch/workspace/data/t1-head-8bit.tif" );
//		final Img< UnsignedByteType > img2 = ImageJFunctions.wrapByte( imp2 );
//		final AffineTransform3D t2 = new AffineTransform3D();
//		t2.translate( 100, 0, 0 );
//		final BvvSource source2 = BvvFunctions.show( img2, "t1-head-8bit", Bvv.options().addTo( source ).sourceTransform( t2 ) );
//
//		source.setColor( new ARGBType( 0xff00ff00 ) );
//		source.setDisplayRange( 0, 555 );
//
//		source2.setColor( new ARGBType( 0xff00ff00 ) );
//		source2.setDisplayRange( 0, 255 );


		final ImagePlus imp = IJ.openImage( "/Users/pietzsch/workspace/data/first-instar-brain.tif" );
		final Img< ARGBType > img = ImageJFunctions.wrapRGBA( imp );
		BvvFunctions.show( img,"first-instar-brain", Bvv.options().sourceTransform( 0.5350003, 0.5350003, 2 ) );


	}
}
