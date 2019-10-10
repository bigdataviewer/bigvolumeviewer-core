package tpietzsch.example2;

import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.LinAlgHelpers;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import tpietzsch.backend.Texture3D;
import tpietzsch.util.MatrixMath;

/**
 * TODO
 */
public class SimpleVolume
{
	private final Texture3D texture;

	private final AffineTransform3D sourceToWorld;

	private final Vector3f sourceMax;

	private final Matrix4f ims;

	/**
	 * @param texture with source data
	 * @param sourceTransform transforms source coordinates to world coordinates
	 * @param sourceMin minimum source coordinates
	 * @param sourceMax maximum source coordinates
	 */
	public SimpleVolume( final Texture3D texture, final AffineTransform3D sourceTransform, final Vector3f sourceMin, final Vector3f sourceMax )
	{
		this.texture = texture;
		this.sourceToWorld = sourceTransform;
		this.sourceMax = sourceMax.sub( sourceMin, new Vector3f() );

		AffineTransform3D t = new AffineTransform3D();
		t.translate( sourceMin.x(), sourceMin.y(), sourceMin.z() );
		t.preConcatenate( sourceTransform );
		this.ims = MatrixMath.affine( t, new Matrix4f() ).invert();
	}

	public Texture3D getVolumeTexture()
	{
		return texture;
	}

	public Matrix4f getIms()
	{
		return ims;
	}

	/**
	 * Get the size of a voxel in world coordinates.
	 * Take a source voxel (0,0,0)-(1,1,1) and transform it to world coordinates.
	 * Take the minimum of the transformed voxels edge lengths.
	 */
	public double getVoxelSizeInWorldCoordinates()
	{
		final double[] tzero = new double[ 3 ];
		sourceToWorld.apply( new double[ 3 ], tzero );

		final double[] one = new double[ 3 ];
		final double[] tone = new double[ 3 ];
		double voxelSize = Double.POSITIVE_INFINITY;
		for ( int i = 0; i < 3; ++i )
		{
			for ( int d = 0; d < 3; ++d )
				one[ d ] = d == i ? 1 : 0;
			sourceToWorld.apply( one, tone );
			LinAlgHelpers.subtract( tone, tzero, tone );
			voxelSize = Math.min( voxelSize, LinAlgHelpers.length( tone ) );
		}
		return voxelSize;
	}

	public Vector3f getSourceMax()
	{
		return sourceMax;
	}
}
