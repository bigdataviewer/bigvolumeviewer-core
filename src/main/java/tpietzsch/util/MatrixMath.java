package tpietzsch.util;

import net.imglib2.realtransform.AffineTransform3D;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public class MatrixMath
{

	/**
	 * Setup view and projection frustum transformation such that a camera looks at (the center of) the (BDV) screen plane.
	 *
	 * @param dCam distance camera to z=0 plane
	 * @param dClip visible depth away from z=0 in both directions
	 * @param screenWidth the width of the screen
	 * @param screenHeight the height of the screen
	 * @param screenPadding additional padding on all sides of the screen plane
	 * @param matrix perspective projection frustum transformation is applied to this matrix
	 */
	public static Matrix4f screenPerspective( double dCam, final double dClip, final double screenWidth, final double screenHeight, final double screenPadding, Matrix4f matrix )
	{
		double r0 = ( screenWidth + screenPadding ) / 2;
		double l0 = -r0;
		double t0 = ( screenHeight + screenPadding ) / 2;
		double b0 = -t0;

		double p = ( dCam - dClip ) / dCam;
		double l = l0 * p;
		double t = t0 * p;
		double r = r0 * p;
		double b = b0 * p;

		double n = dCam - dClip;
		double f = dCam + dClip;

		matrix
				.frustum( ( float ) l, ( float ) r, ( float ) b, ( float ) t, ( float ) n, ( float ) f )
				.scale( 1.0f, -1.0f, -1.0f )
				.translate( ( float ) ( -screenWidth / 2 ), ( float ) ( -screenHeight / 2 ), ( float ) dCam );
		return matrix;

		// frustum by hand...
		/*
		float m00 = ( float ) ( 2 * n / ( r - l ) );
		float m11 = ( float ) ( 2 * n / ( t - b ) );
		float m02 = ( float ) ( ( r + l ) / ( r - l ) );
		float m12 = ( float ) ( ( t + b ) / ( t - b ) );
		float m22 = ( float ) ( -( f + n ) / ( f - n ) );
		float m23 = ( float ) ( -2 * f * n / ( f - n ) );

		matrix.set(
				m00, 0, 0, 0,
				0, m11, 0, 0,
				m02, m12, m22, -1,
				0,0,m23,0
		);
		*/
	}

	/**
	 * Apply a {@link AffineTransform3D} to the specified matrix.
	 *
	 * @param affine the affine transformation
	 * @param matrix affine transformation is applied to this matrix
	 */
	public static Matrix4f affine( final AffineTransform3D affine, final Matrix4f matrix )
	{
		final float[] m = new float[ 16 ];
		for ( int c = 0; c < 4; ++c )
			for ( int r = 0; r < 3; ++r )
				m[ c * 4 + r ] = ( float ) affine.get( r, c );
		m[ 3 ] = 0f;
		m[ 7 ] = 0f;
		m[ 11 ] = 0f;
		m[ 15 ] = 1f;

		if ( ( matrix.properties() & Matrix4fc.PROPERTY_IDENTITY ) != 0 )
			matrix.set( m );
		else if ( ( matrix.properties() & Matrix4fc.PROPERTY_IDENTITY ) != 0 )
			matrix.mulAffine( new Matrix4f().set( m ) );
		else
			matrix.mul( new Matrix4f().set( m ) );

		return matrix;
	}


	private MatrixMath()
	{
	}
}