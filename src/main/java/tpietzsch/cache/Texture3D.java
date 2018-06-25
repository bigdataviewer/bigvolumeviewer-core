package tpietzsch.cache;

public interface Texture3D extends Texture
{
	@Override
	default int texDims()
	{
		return 3;
	}
}
