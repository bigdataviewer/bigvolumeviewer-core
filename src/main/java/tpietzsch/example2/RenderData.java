package tpietzsch.example2;

import net.imglib2.realtransform.AffineTransform3D;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

public class RenderData
{
	private final Matrix4f pv;
	private int timepoint;
	private final AffineTransform3D renderTransformWorldToScreen;
	private double dCam;
	private double dClipNear;
	private double dClipFar;
	private double screenWidth;
	private double screenHeight;

	/**
	 * @param pv
	 * @param timepoint timepoint index
	 */
	public RenderData(
			final Matrix4fc pv,
			final int timepoint,
			final AffineTransform3D renderTransformWorldToScreen,
			final double dCam,
			final double dClipNear,
			final double dClipFar,
			final double screenWidth,
			final double screenHeight )
	{
		this.pv = new Matrix4f( pv );
		this.timepoint = timepoint;
		this.renderTransformWorldToScreen = renderTransformWorldToScreen;
		this.dCam = dCam;
		this.dClipNear = dClipNear;
		this.dClipFar = dClipFar;
		this.screenWidth = screenWidth;
		this.screenHeight = screenHeight;
	}

	public RenderData()
	{
		this.pv = new Matrix4f();
		this.renderTransformWorldToScreen = new AffineTransform3D();
	}

	public void set( final RenderData other )
	{
		this.pv.set( other.pv );
		this.timepoint = other.timepoint;
		this.renderTransformWorldToScreen.set( other.renderTransformWorldToScreen );
		this.dCam = other.dCam;
		this.dClipNear = other.dClipNear;
		this.dClipFar = other.dClipFar;
		this.screenWidth = other.screenWidth;
		this.screenHeight = other.screenHeight;
	}

	public Matrix4f getPv()
	{
		return pv;
	}

	public int getTimepoint()
	{
		return timepoint;
	}

	public AffineTransform3D getRenderTransformWorldToScreen()
	{
		return renderTransformWorldToScreen;
	}

	public double getDCam()
	{
		return dCam;
	}

	public double getDClipNear()
	{
		return dClipNear;
	}

	public double getDClipFar()
	{
		return dClipFar;
	}

	public double getScreenWidth()
	{
		return screenWidth;
	}

	public double getScreenHeight()
	{
		return screenHeight;
	}
}
