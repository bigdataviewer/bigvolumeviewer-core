package bvv.debug;

import java.util.ArrayList;
import java.util.List;

import net.imglib2.img.Img;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;

import bdv.cache.SharedQueue;
import bdv.util.volatiles.VolatileViews;
import bdv.viewer.ConverterSetups;
import bdv.viewer.SourceAndConverter;

import bvv.vistools.Bvv;
import bvv.vistools.BvvFunctions;
import bvv.vistools.BvvOptions;


public class DebugVolumeNumber
{
	public static < T extends RealType< T > & NativeType< T >> void main( final String[] args )
	{
		int nMaxVolumesToTry = 6;
		int nVolumeEdge = 50;

        double spreadCoeff = 1.01;
        
		int numThreads = 8;
		int numQueueLevels = 10;
		SharedQueue queue = new SharedQueue( numThreads, numQueueLevels );
		
		//test different image types
		List<Object> types = new ArrayList<>();
		types.add( new UnsignedByteType() );
		types.add( new UnsignedShortType() );
		
		final int [] maxVal = new int[2];
		maxVal[0] = 255;
		maxVal[1] = 65535;
			
		final Bvv bvv = BvvFunctions.show(BvvOptions.options().frameTitle( "Test max number of volumes" ));

		int[] bestGrid = findOptimalGridDimensions(nMaxVolumesToTry);
		int nx = bestGrid[0];
        int ny = bestGrid[1];

        double spacingX = nVolumeEdge * spreadCoeff;
        double spacingY = nVolumeEdge * spreadCoeff;
        double spacingZ = nVolumeEdge * spreadCoeff;

		for (int i = 0; i < nMaxVolumesToTry; i++) {
            int gridX = i % nx;
            int gridY = (i / nx) % ny;
            int gridZ = i / (nx * ny);

            double px = gridX * spacingX;
            double py = gridY * spacingY;
            double pz = gridZ * spacingZ;
            final AffineTransform3D t = new AffineTransform3D();
            t.translate( px, py, pz );
            String sTitle = Integer.toString( gridX ) + " " + Integer.toString( gridY ) + " " + Integer.toString( gridZ );
            
            //make a random type
            int ind = ( int ) Math.round(Math.random());
            @SuppressWarnings( "unchecked" )
			final Img< ? > rai = GenerateVolumes.makeCachedCellImg((T)types.get( ind ), nVolumeEdge, 128, maxVal[ind], false );
                BvvFunctions.show( VolatileViews.wrapAsVolatile(rai, queue), sTitle, 
            		Bvv.options().addTo( bvv ).sourceTransform( t ));       
		}
		//assign random color
		final List< SourceAndConverter< ? > > sacList = bvv.getBvvHandle().getViewerPanel().state().getSources();
		final ConverterSetups convS = bvv.getBvvHandle().getConverterSetups();
		int sN = 0;
		for(final SourceAndConverter< ? > sac : sacList)
		{

			float hue = (float) sN / nMaxVolumesToTry;
		    int rgb = java.awt.Color.HSBtoRGB(hue, 0.8f, 1.0f);
			convS.getConverterSetup( sac ).setColor( new ARGBType(rgb) );
		    sN++;
		}
	}
	
	public static int[] findOptimalGridDimensions(int n) {
        int s = (int) Math.ceil(Math.cbrt(n));
        
        int[] bestGrid = new int[]{1, 1, n};
        int minVolumeDiff = Integer.MAX_VALUE;
        int minShapeDiff = Integer.MAX_VALUE;

        // Search dimensions around the cube root boundary
        for (int x = 1; x <= s + 1; x++) {
            for (int y = x; y <= s + 1; y++) {
                int z = (int) Math.ceil((double) n / (x * y));
                if (z < y) continue; // Keep x <= y <= z

                int volume = x * y * z;
                int volDiff = volume - n;
                int shapeDiff = (y - x) + (z - y) + (z - x);

                // Prefer minimum empty space, then most cubic aspect ratio
                if (volDiff < minVolumeDiff || (volDiff == minVolumeDiff && shapeDiff < minShapeDiff)) {
                    minVolumeDiff = volDiff;
                    minShapeDiff = shapeDiff;
                    bestGrid[0] = z;
                    bestGrid[1] = y;
                    bestGrid[2] = x;
                }
            }
        }
        
        return bestGrid;
    }
}