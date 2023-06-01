/*-
 * #%L
 * Volume rendering of bdv datasets
 * %%
 * Copyright (C) 2018 - 2021 Tobias Pietzsch
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
package bvv.core.example2;

import bvv.core.multires.SourceStacks;
import java.util.ArrayList;
import java.util.List;

public final class VolumeShaderSignature
{
	public enum PixelType
	{
		USHORT,
		UBYTE,
		ARGB
	}

	public static final class VolumeSignature
	{
		private final SourceStacks.SourceStackType sourceStackType;
		private final PixelType pixelType;

		public VolumeSignature( final SourceStacks.SourceStackType sourceStackType, final PixelType pixelType )
		{
			this.sourceStackType = sourceStackType;
			this.pixelType = pixelType;
		}

		public SourceStacks.SourceStackType getSourceStackType()
		{
			return sourceStackType;
		}

		public PixelType getPixelType()
		{
			return pixelType;
		}

		@Override
		public boolean equals( final Object o )
		{
			if ( this == o )
				return true;
			if ( ! ( o instanceof VolumeSignature ) )
				return false;
			final VolumeSignature that = ( VolumeSignature ) o;
			return sourceStackType == that.sourceStackType && pixelType == that.pixelType;
		}

		@Override
		public int hashCode()
		{
			int result = sourceStackType.hashCode();
			result = 31 * result + pixelType.hashCode();
			return result;
		}
	}

	private final List< VolumeSignature > volumeSignatures;

	public VolumeShaderSignature( final List< VolumeSignature > volumeSignatures )
	{
		this.volumeSignatures = new ArrayList<>( volumeSignatures );
	}

	public List< VolumeSignature > getVolumeSignatures()
	{
		return volumeSignatures;
	}

	public final int numVolumes()
	{
		return volumeSignatures.size();
	}

	@Override
	public boolean equals( final Object o )
	{
		if ( this == o )
			return true;
		if ( ! ( o instanceof VolumeShaderSignature ) )
			return false;
		final VolumeShaderSignature that = ( VolumeShaderSignature ) o;
		return volumeSignatures.equals( that.volumeSignatures );
	}

	@Override
	public int hashCode()
	{
		return volumeSignatures.hashCode();
	}
}
