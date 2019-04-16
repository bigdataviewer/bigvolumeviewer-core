uniform mat4 im;
uniform vec3 sourcemin;
uniform vec3 sourcemax;

void intersectBox( vec3 r_o, vec3 r_d, vec3 boxmin, vec3 boxmax, out float tnear, out float tfar );

void intersectBoundingBox( vec4 wfront, vec4 wback, out float tnear, out float tfar )
{
	vec4 mfront = im * wfront;
	vec4 mback = im * wback;
	intersectBox( mfront.xyz, (mback - mfront).xyz, sourcemin, sourcemax, tnear, tfar );
}

uniform sampler3D volume;

float volTexture( vec4 wpos )
{
	vec3 pos = (im * wpos).xyz + 0.5;
	return texture( volume, pos / textureSize( volume, 0 ) ).r;
}
