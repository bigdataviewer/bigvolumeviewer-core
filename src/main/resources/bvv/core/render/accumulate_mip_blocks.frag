if (vis)
{
	float x = sampleVolume(wpos);
	v = max(v, convert(x));
}
