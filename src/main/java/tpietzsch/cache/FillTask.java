package tpietzsch.cache;

public interface FillTask
{
	ImageBlockKey< ? > getKey();

	boolean containsData();

	void fill( UploadBuffer buffer );
}
