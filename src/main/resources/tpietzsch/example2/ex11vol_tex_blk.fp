if (vis)
{
	float x = blockTexture(wpos, volumeCache, cacheSize, blockSize, paddedBlockSize, cachePadOffset);
	v = max(v, convert(x));
}
