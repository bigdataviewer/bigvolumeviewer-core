if (vis)
{
	float x = sampleVolume(wpos, volumeCache, cacheSize, blockSize, paddedBlockSize, cachePadOffset);
	v = max(v, convert(x));
}
