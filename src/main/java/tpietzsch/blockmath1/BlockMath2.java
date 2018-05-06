package tpietzsch.blockmath1;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import tpietzsch.util.MatrixMath;

public class BlockMath2
{
	private final double screenPadding = 0;

	private double dCam = 10;
	private double dClip = 5;
	private double screenWidth = 10;
	private double screenHeight = 1;

	void run()
	{
		final Matrix4f projection = MatrixMath.screenPerspective( dCam, dClip, screenWidth, screenHeight, screenPadding, new Matrix4f() );
		final Matrix4f invProjection = projection.invert( new Matrix4f() );

		final Vector3f pNear = new Vector3f();
		final Vector3f pFarMinusNear = new Vector3f();
		invProjection.unprojectInvRay( 0.5f, 0.5f, new int[] { 0, 0, 1, 1 }, pNear, pFarMinusNear );

		System.out.println( "pNear = " + pNear );
		System.out.println( "pFarMinusNear = " + pFarMinusNear );
	}

	public static void main( final String[] args )
	{
		new BlockMath2().run();
	}
}
