package tpietzsch.backend;

import tpietzsch.shadergen.Shader;

public interface GpuContext
{
	// build and cache and bind
	void use( Shader shader );

	// from cached shader thingie and gl context, build SetUniforms
	SetUniforms getUniformSetter( Shader shader );
}
