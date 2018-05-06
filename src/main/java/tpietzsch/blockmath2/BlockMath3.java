package tpietzsch.blockmath2;

import bdv.spimdata.SpimDataMinimal;
import bdv.spimdata.XmlIoSpimDataMinimal;
import java.util.ArrayList;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.sequence.MultiResolutionSetupImgLoader;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.joml.Matrix4f;
import tpietzsch.util.MatrixMath;

public class BlockMath3
{
	public static void main( final String[] args ) throws SpimDataException
	{
		final String xmlFilename = "/Users/pietzsch/workspace/data/111010_weber_full.xml";
		final SpimDataMinimal spimData = new XmlIoSpimDataMinimal().load( xmlFilename );
		MultiResolutionSetupImgLoader< UnsignedShortType > sil = ( MultiResolutionSetupImgLoader< UnsignedShortType > ) spimData.getSequenceDescription().getImgLoader().getSetupImgLoader( 0 );

		final Matrix4f model = new Matrix4f();
		final Matrix4f view = new Matrix4f();
		final Matrix4f projection = new Matrix4f();
		final AffineTransform3D sourceTransform = spimData.getViewRegistrations().getViewRegistration( 1, 0 ).getModel();
		MatrixMath.affine( sourceTransform, model );
		view.set( new float[] { 0.56280f, -0.13956f, 0.23033f, 0.00000f, 0.00395f, 0.53783f, 0.31621f, 0.00000f, -0.26928f, -0.28378f, 0.48603f, 0.00000f, 96.02715f, 211.68768f, -186.46806f, 1.00000f } );
		projection.set( new float[] { 5.40541f, 0.00000f, 0.00000f, 0.00000f, -0.00000f, -6.89655f, -0.00000f, -0.00000f, -0.00000f, -0.00000f, 2.00000f, 1.00000f, -1729.72974f, 1655.17236f, 1000.00000f, 2000.00000f } );
		final Matrix4f pvm = new Matrix4f( projection ).mul( view ).mul( model );

		ArrayList< RaiLevel > raiLevels = new ArrayList<>();
		final int numMipmapLevels = sil.numMipmapLevels();
		for ( int level = 0; level < numMipmapLevels; level++ )
		{
			final RandomAccessibleInterval< UnsignedShortType > rai = sil.getImage( 1, level );
			final double[] resolution = sil.getMipmapResolutions()[ level ];
			final RaiLevel raiLevel = new RaiLevel( level, resolution, rai );
			raiLevels.add( raiLevel );
			System.out.println( raiLevel );
		}
		long[] dim = new long[ 3 ];
		raiLevels.get( 0 ).rai.dimensions( dim );
	}
}
