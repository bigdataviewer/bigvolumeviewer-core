package tpietzsch.example2;

import java.util.ArrayList;
import java.util.List;
import tpietzsch.multires.SourceStacks;

public final class VolumeShaderSignature
{
	public enum PixelType
	{
		USHORT,
		UBYTE
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
