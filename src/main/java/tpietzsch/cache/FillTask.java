package tpietzsch.cache;

public interface FillTask
{
	ImageBlockKey< ? > getKey();

	void fill( UploadBuffer buffer );
}
