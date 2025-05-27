/*-
 * #%L
 * Volume rendering of bdv datasets
 * %%
 * Copyright (C) 2018 - 2025 Tobias Pietzsch
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package bvv.core.render;

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
