// intersect ray with a box
// http://www.siggraph.org/education/materials/HyperGraph/raytrace/rtinter3.htm
void intersectBox( vec3 r_o, vec3 r_d, vec3 boxmin, vec3 boxmax, out float tnear, out float tfar )
{
	// compute intersection of ray with all six bbox planes
	vec3 invR = 1 / r_d;
	vec3 tbot = invR * ( boxmin - r_o );
	vec3 ttop = invR * ( boxmax - r_o );

	// re-order intersections to find smallest and largest on each axis
	vec3 tmin = min(ttop, tbot);
	vec3 tmax = max(ttop, tbot);

	// find the largest tmin and the smallest tmax
	tnear = max( max( tmin.x, tmin.y ), max( tmin.x, tmin.z ) );
	tfar = min( min( tmax.x, tmax.y ), min( tmax.x, tmax.z ) );
}
