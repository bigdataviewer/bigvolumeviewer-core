out vec4 FragColor;

uniform mat4 ipvm;

uniform vec3 sourcemin;
uniform vec3 sourcemax;



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

void main()
{
    const vec2 viewportSize = vec2( 640 * 2, 480 * 2 );

    // frag coord in NDC
    vec2 uv = 2 * gl_FragCoord.xy / viewportSize - 1;

    // NDC of frag on near and far plane
    vec4 front = vec4( uv, -1, 1 );
    vec4 back = vec4( uv, 1, 1 );

    // calculate eye ray in world space
    vec4 mfront = ipvm * front;
    mfront *= 1 / mfront.w;
    vec4 mback = ipvm * back;
    mback *= 1 / mback.w;

    // find intersection with box
    float tnear, tfar;
    intersectBox( mfront.xyz, (mback - mfront).xyz, sourcemin, sourcemax, tnear, tfar );
    if ( tnear < tfar )
        FragColor = vec4( 0, 0, 0, 0.5 );
    else
        FragColor = vec4( 0, 1, 0, 0.5 );
}
